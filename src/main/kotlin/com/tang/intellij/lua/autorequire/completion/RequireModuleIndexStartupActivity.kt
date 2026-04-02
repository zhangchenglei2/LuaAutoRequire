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

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Warms up the [RequireModuleIndex] after a project has finished loading.
 *
 * Registered as `<postStartupActivity>` in plugin.xml so that the background
 * scan starts automatically and shows a progress bar to the user.
 */
class RequireModuleIndexStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        RequireModuleIndex.getInstance(project).warmUp()
    }
}
