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

package com.tang.intellij.lua.autorequire.completion

import com.intellij.openapi.vfs.VirtualFile

/**
 * Holds the information extracted from a single `local X = require("path")` statement.
 *
 * @param varName     The local variable name, e.g. "UIConfig"
 * @param requirePath The require path string, e.g. "Game.Mod.Config.UIConfig"
 * @param sourceFile  The VirtualFile this entry was parsed from (used for incremental updates)
 */
data class RequireModuleInfo(
    val varName: String,
    val requirePath: String,
    val sourceFile: VirtualFile? = null
)
