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

package com.tang.intellij.lua.autorequire.project

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Persists the list of directories that the Auto Require feature should scan
 * when building its require-statement index.
 *
 * Configuration is stored in `.idea/lua-auto-require.xml` (project-local, not
 * shared via VCS by default – add it to `.gitignore` if desired).
 */
@Service(Service.Level.PROJECT)
@State(name = "AutoRequireSourceRootManager", storages = [Storage("lua-auto-require.xml")])
class AutoRequireSourceRootManager(val project: Project) :
    PersistentStateComponent<AutoRequireSourceRootManager.State> {

    companion object {
        fun getInstance(project: Project): AutoRequireSourceRootManager =
            project.getService(AutoRequireSourceRootManager::class.java)
    }

    private var myState = State()

    // -------------------------------------------------------------------------
    // PersistentStateComponent
    // -------------------------------------------------------------------------

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    // -------------------------------------------------------------------------
    // Configured search paths (absolute file-system paths)
    // -------------------------------------------------------------------------

    /** Returns the list of absolute directory paths configured in the settings panel. */
    fun getSourceRootPaths(): Array<String> = myState.sourceRootPaths.toTypedArray()

    /**
     * Replaces the configured paths with [paths] and persists the change.
     * Call [com.tang.intellij.lua.autorequire.completion.RequireModuleIndex.rebuildIndex]
     * afterwards to make the change effective immediately.
     */
    fun setSourceRootPaths(paths: Array<String>) {
        myState.sourceRootPaths = paths.toMutableList()
        project.scheduleSave()
    }

    // -------------------------------------------------------------------------
    // Module source roots from the project model
    // -------------------------------------------------------------------------

    /**
     * Returns all module source roots registered in the IntelliJ project model.
     * These are automatically included in the scan even without explicit configuration.
     */
    fun getModuleSourceRoots(): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
            result.addAll(ModuleRootManager.getInstance(module).sourceRoots)
        }
        // Also resolve manually configured URL-based roots
        for (url in myState.sourceRootPaths) {
            VirtualFileManager.getInstance().findFileByUrl("file://$url")?.let { result.add(it) }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // State bean
    // -------------------------------------------------------------------------

    class State {
        /** Absolute filesystem paths of directories to scan for require statements */
        var sourceRootPaths: MutableList<String> = mutableListOf()
    }
}
