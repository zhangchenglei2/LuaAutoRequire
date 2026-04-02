/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.autorequire.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.tang.intellij.lua.autorequire.project.AutoRequireSourceRootManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Project-level service that maintains an index of Lua require statements.
 *
 * Scans all configured source root directories for `.lua` files and extracts
 * patterns of the form:
 *
 *   local UIConfig = require("Game.Mod.BaseMod.Client.Config.UIConfig")
 *
 * Builds a map from variable-name → List<RequireModuleInfo> so that the
 * completion contributor can suggest and auto-insert require statements.
 *
 * Supports incremental updates via VFS bulk-file-listener events.
 */
@Service(Service.Level.PROJECT)
class RequireModuleIndex(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(RequireModuleIndex::class.java)
        private const val LOG_PREFIX = "LuaAutoRequire"

        fun getInstance(project: Project): RequireModuleIndex =
            project.getService(RequireModuleIndex::class.java)

        /**
         * Matches require calls in these forms:
         *   local VarName = require("path.to.module")
         *   local VarName = require('path.to.module')
         *   local VarName = require "path.to.module"
         *   local VarName = require 'path.to.module'
         */
        private val REQUIRE_PATTERN: Pattern = Pattern.compile(
            """local\s+(\w+)\s*=\s*require\s*\(?\s*["']([^"']+)["']\s*\)?"""
        )

        private fun isLuaFile(file: VirtualFile): Boolean =
            !file.isDirectory && file.extension?.lowercase() == "lua"
    }

    // varName -> List<RequireModuleInfo>  (thread-safe)
    private val indexMap = ConcurrentHashMap<String, MutableList<RequireModuleInfo>>()

    /** True once the initial full scan has completed */
    private val isBuilt = AtomicBoolean(false)

    /** Guard against concurrent warm-up calls */
    private val isBuilding = AtomicBoolean(false)

    init {
        // Incremental update: react to VFS events
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    val file = event.file ?: continue
                    if (!isLuaFile(file)) continue

                    when (event) {
                        is VFileDeleteEvent -> removeFileEntries(file)
                        is VFileContentChangeEvent -> {
                            removeFileEntries(file)
                            parseAndIndex(file)
                        }
                        is VFileCreateEvent -> parseAndIndex(file)
                    }
                }
            }
        })
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Triggers a background warm-up scan. Called by [RequireModuleIndexStartupActivity]
     * when a project is opened.
     */
    fun warmUp() {
        if (isBuilt.get() || isBuilding.get()) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "EmmyLua: Building require module index…",
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                if (!isBuilding.compareAndSet(false, true)) return
                try {
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.0
                    indicator.text = "Collecting Lua source roots…"

                    val sourceRoots = collectSourceRoots()
                    if (sourceRoots.isEmpty()) {
                        LOG.warn("$LOG_PREFIX: no source roots configured – index will be empty")
                        isBuilt.set(true)
                        return
                    }

                    indicator.text = "Scanning .lua files…"
                    indicator.fraction = 0.1
                    val allLuaFiles = mutableListOf<VirtualFile>()
                    sourceRoots.forEach { collectLuaFiles(it, allLuaFiles) }

                    val total = allLuaFiles.size
                    LOG.info("$LOG_PREFIX: found $total .lua files in ${sourceRoots.size} source root(s)")

                    allLuaFiles.forEachIndexed { idx, file ->
                        indicator.fraction = 0.1 + 0.9 * (idx.toDouble() / total)
                        indicator.text2 = file.name
                        parseAndIndex(file)
                    }

                    isBuilt.set(true)
                    LOG.info("$LOG_PREFIX: index ready – ${indexMap.size} unique var names")
                } finally {
                    isBuilding.set(false)
                }
            }

            override fun onSuccess() {
                LOG.info("$LOG_PREFIX: warm-up finished, entries=${indexMap.size}")
            }
        })
    }

    /** Returns true when the initial scan is complete */
    fun isReady(): Boolean = isBuilt.get()

    /**
     * Returns all [RequireModuleInfo] entries for a given variable name.
     * Returns an empty list if the index is not yet ready.
     */
    fun getModulesByName(varName: String): List<RequireModuleInfo> {
        if (!isBuilt.get()) return emptyList()
        return indexMap[varName]?.toList() ?: emptyList()
    }

    /**
     * Returns all indexed variable names (used for prefix matching in the contributor).
     * Returns an empty set if the index is not yet ready.
     */
    fun getAllVarNames(): Set<String> {
        if (!isBuilt.get()) return emptySet()
        return indexMap.keys.toSet()
    }

    /**
     * Clears the index and re-runs [warmUp]. Call this when the source root
     * configuration changes (e.g. user edits the settings panel).
     */
    fun rebuildIndex() {
        indexMap.clear()
        isBuilt.set(false)
        warmUp()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun collectSourceRoots(): Set<VirtualFile> {
        val roots = mutableSetOf<VirtualFile>()
        val lfs = LocalFileSystem.getInstance()
        val manager = AutoRequireSourceRootManager.getInstance(project)

        // 1. Paths configured via the settings panel
        for (path in manager.getSourceRootPaths()) {
            if (path.isBlank()) continue
            lfs.findFileByPath(path)?.takeIf { it.isDirectory }?.let { roots.add(it) }
        }

        // 2. Module source roots from the project model
        roots.addAll(manager.getModuleSourceRoots())

        LOG.info("$LOG_PREFIX: source roots = ${roots.map { it.path }}")
        return roots
    }

    private fun collectLuaFiles(dir: VirtualFile, result: MutableList<VirtualFile>) {
        if (!dir.isValid) return
        for (child in dir.children) {
            if (child.isDirectory) collectLuaFiles(child, result)
            else if (isLuaFile(child)) result.add(child)
        }
    }

    /** Remove every index entry that originated from [file] */
    private fun removeFileEntries(file: VirtualFile) {
        val emptyKeys = mutableListOf<String>()
        for ((key, list) in indexMap) {
            list.removeAll { it.sourceFile == file }
            if (list.isEmpty()) emptyKeys.add(key)
        }
        emptyKeys.forEach { indexMap.remove(it) }
    }

    /**
     * Parses [file] for `local X = require("...")` patterns and adds them to the index.
     * Duplicate (same varName + same requirePath) entries are silently ignored.
     */
    private fun parseAndIndex(file: VirtualFile) {
        if (!file.isValid || file.isDirectory) return
        try {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            val matcher = REQUIRE_PATTERN.matcher(content)
            while (matcher.find()) {
                val varName = matcher.group(1)?.takeIf { it.isNotBlank() } ?: continue
                val requirePath = matcher.group(2)?.trim()?.takeIf { it.isNotBlank() } ?: continue

                val info = RequireModuleInfo(varName, requirePath, file)
                indexMap.getOrPut(varName) { mutableListOf() }.let { list ->
                    if (list.none { it.requirePath == requirePath }) {
                        list.add(info)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("$LOG_PREFIX: failed to parse ${file.path}: ${e.message}")
        }
    }
}
