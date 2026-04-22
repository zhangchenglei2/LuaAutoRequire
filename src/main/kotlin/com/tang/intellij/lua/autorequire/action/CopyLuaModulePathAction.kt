/*
 * Copyright (c) 2017. zhangchenglei2(adce@qq.com)
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

package com.tang.intellij.lua.autorequire.action

import com.intellij.ide.actions.DumbAwareCopyPathProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * 右键菜单 Action：复制 Lua 文件的模块路径（点分隔格式）.
 *
 * 继承 DumbAwareCopyPathProvider，自动获得右侧预览面板、复制到剪贴板等标准行为。
 * 只需实现 getPathToElement 返回模块路径字符串即可。
 *
 * 转换规则：
 *  1. 取文件相对于 Git 仓库根目录的路径（回退到项目根目录）
 *  2. 将路径分隔符 `/` 替换为 `.`
 *  3. 移除 `.lua` 文件扩展名
 *
 * 例如：src/lua/Test.lua → src.lua.Test
 *
 * @author zhangchenglei2
 * @release 1.0.0
 */
class CopyLuaModulePathAction : DumbAwareCopyPathProvider() {

    /**
     * 返回 Lua 文件的点分隔模块路径，仅对 .lua 文件返回非 null 值.
     *
     * 返回 null 时，基类会自动隐藏该菜单项。
     *
     * @param project 当前项目
     * @param virtualFile 目标文件
     * @param editor 当前编辑器（可为 null）
     * @return 点分隔的模块路径，非 .lua 文件返回 null
     */
    override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): String? {
        if (virtualFile == null || virtualFile.isDirectory) return null
        if (virtualFile.extension?.lowercase() != "lua") return null
        return resolveModulePath(virtualFile, project)
    }

    /**
     * 将 VirtualFile 解析为点分隔的 Lua 模块路径.
     *
     * 优先使用 Git 仓库根目录计算相对路径；
     * 若文件不在 Git 仓库内，则回退到项目基础目录。
     *
     * @param file 目标 Lua 文件
     * @param project 当前项目
     * @return 点分隔的模块路径，若无法解析则返回 null
     */
    private fun resolveModulePath(file: VirtualFile, project: Project): String? {
        val filePath = file.path

        // 优先尝试从 Git 仓库根目录计算相对路径
        val gitRoot = findGitRoot(filePath)
        if (gitRoot != null) {
            val normalizedRoot = gitRoot.replace('\\', '/')
            val normalizedFile = filePath.replace('\\', '/')
            if (normalizedFile.startsWith("$normalizedRoot/")) {
                val relativePath = normalizedFile.removePrefix("$normalizedRoot/")
                return convertPathToModulePath(relativePath)
            }
        }

        // 回退：使用项目基础目录
        val projectBasePath = project.basePath ?: return null
        val normalizedBase = projectBasePath.replace('\\', '/')
        val normalizedFile = filePath.replace('\\', '/')
        if (normalizedFile.startsWith("$normalizedBase/")) {
            val relativePath = normalizedFile.removePrefix("$normalizedBase/")
            return convertPathToModulePath(relativePath)
        }

        return null
    }

    /**
     * 从文件路径向上查找 Git 仓库根目录（含 .git 目录的最近祖先目录）.
     *
     * @param filePath 文件的绝对路径
     * @return Git 仓库根目录的绝对路径（使用 `/` 分隔），若未找到则返回 null
     */
    private fun findGitRoot(filePath: String): String? {
        var dir = File(filePath).parentFile
        while (dir != null) {
            if (File(dir, ".git").exists()) {
                return dir.absolutePath.replace('\\', '/')
            }
            dir = dir.parentFile
        }
        return null
    }

    /**
     * 将相对文件路径转换为 Lua 模块路径.
     *
     * 例如：src/lua/Test.lua → src.lua.Test
     *
     * @param relativePath 相对路径字符串（使用 `/` 分隔）
     * @return 点分隔的模块路径
     */
    private fun convertPathToModulePath(relativePath: String): String {
        return relativePath
            .removeSuffix(".lua")
            .replace('/', '.')
            .replace('\\', '.')
    }
}
