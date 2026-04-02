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

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.tang.intellij.lua.autorequire.completion.RequireModuleIndex
import java.awt.BorderLayout
import java.util.Arrays
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Project-level settings panel for the Lua Auto Require plugin.
 *
 * Accessible via **Settings → EmmyLua → Auto Require 路径**.
 *
 * Allows the user to add/remove the directory paths that [RequireModuleIndex]
 * will scan when building its require-statement index.  Changes take effect
 * immediately: clicking **Apply** triggers [RequireModuleIndex.rebuildIndex].
 */
class AutoRequireSettingsPanel(private val project: Project) : Configurable {

    private val dataModel = DefaultListModel<String>()
    private val pathList = JBList(dataModel)
    private lateinit var rootPanel: JPanel

    override fun getDisplayName(): String = "Auto Require 路径"

    override fun createComponent(): JComponent {
        pathList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val listPanel = JPanel(BorderLayout())
        listPanel.add(
            ToolbarDecorator.createDecorator(pathList)
                .setAddAction { addPath() }
                .setRemoveAction { removePath() }
                .createPanel(),
            BorderLayout.CENTER
        )
        listPanel.border = IdeBorderFactory.createTitledBorder(
            "Directories to scan for require statements", false
        )

        rootPanel = JPanel(BorderLayout())
        rootPanel.add(listPanel, BorderLayout.NORTH)

        reset()
        return rootPanel
    }

    private fun addPath() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select a directory to scan"
        }
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        val path = chosen.path
        if (!containsPath(path)) {
            dataModel.addElement(path)
        }
    }

    private fun removePath() {
        val idx = pathList.selectedIndex
        if (idx >= 0) dataModel.remove(idx)
    }

    private fun containsPath(path: String): Boolean =
        (0 until dataModel.size()).any { dataModel.getElementAt(it) == path }

    private fun currentPaths(): Array<String> =
        Array(dataModel.size()) { dataModel.getElementAt(it) }

    override fun isModified(): Boolean {
        val manager = AutoRequireSourceRootManager.getInstance(project)
        return !Arrays.equals(manager.getSourceRootPaths(), currentPaths())
    }

    override fun apply() {
        val manager = AutoRequireSourceRootManager.getInstance(project)
        manager.setSourceRootPaths(currentPaths())
        // Trigger an immediate rebuild of the index
        RequireModuleIndex.getInstance(project).rebuildIndex()
    }

    override fun reset() {
        dataModel.clear()
        val manager = AutoRequireSourceRootManager.getInstance(project)
        manager.getSourceRootPaths().forEach { dataModel.addElement(it) }
    }
}
