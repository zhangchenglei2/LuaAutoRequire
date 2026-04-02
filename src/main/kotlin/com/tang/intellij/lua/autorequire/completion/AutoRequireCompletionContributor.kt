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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext

/**
 * Completion contributor that suggests `require` module paths based on the
 * [RequireModuleIndex].
 *
 * Trigger conditions:
 *  - The current file is a Lua file (`.lua`)
 *  - The typed prefix is at least 2 characters long
 *  - The prefix is not a Lua keyword
 *  - At least one matching entry exists in [RequireModuleIndex]
 *
 * On insertion, the contributor automatically prepends
 * `local <VarName> = require("<path>")\n` at the top of the file (unless it's
 * already present).
 */
class AutoRequireCompletionContributor : CompletionContributor() {

    companion object {
        private val LOG = Logger.getInstance(AutoRequireCompletionContributor::class.java)

        private val LUA_KEYWORDS = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or",
            "repeat", "return", "then", "true", "until", "while"
        )
    }

    init {
        // Register for all positions; we filter by file type ourselves
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : com.intellij.codeInsight.completion.CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    resultSet: CompletionResultSet
                ) {
                    addAutoRequireCompletions(parameters, resultSet)
                }
            }
        )
    }

    private fun addAutoRequireCompletions(
        parameters: CompletionParameters,
        resultSet: CompletionResultSet
    ) {
        val position = parameters.position
        val file: PsiFile = position.containingFile ?: return

        // Only activate for Lua files
        if (file.language.id != "Lua" && file.name.endsWith(".lua").not()) return

        val prefix = resultSet.prefixMatcher.prefix

        // Need at least 2 chars to avoid flooding the list too early
        if (prefix.length < 2) return

        // Never trigger on Lua keywords
        if (prefix in LUA_KEYWORDS) return

        val index = RequireModuleIndex.getInstance(file.project)
        if (!index.isReady()) {
            LOG.debug("LuaAutoRequire: index not ready, skipping. file=${file.name}")
            return
        }

        val allVarNames = index.getAllVarNames()
        if (allVarNames.isEmpty()) return

        for (varName in allVarNames) {
            if (!varName.startsWith(prefix, ignoreCase = true)) continue

            val modules = index.getModulesByName(varName)
            for (info in modules) {
                val element = buildLookupElement(info)
                // Priority 80 – above regular word completion but below local variable lookup
                resultSet.addElement(PrioritizedLookupElement.withPriority(element, 80.0))
            }
        }
    }

    /**
     * Builds a [LookupElement] for [info]:
     *  - Main display string = varName
     *  - Extra lookup string = requirePath (ensures identical varNames with different paths are not de-duped)
     *  - Type text = require path shown on the right side
     *  - Tail text = "(auto require)" hint
     *  - Insert handler injects the `local` statement at the top of the file
     */
    private fun buildLookupElement(info: RequireModuleInfo): LookupElement {
        return LookupElementBuilder
            .create(info, info.varName)
            .withLookupString(info.requirePath)
            .withTypeText(info.requirePath, true)
            .withTailText("  (auto require)", true)
            .withInsertHandler(AutoRequireInsertHandler(info))
    }
}

// ---------------------------------------------------------------------------
// Insert handler
// ---------------------------------------------------------------------------

/**
 * Inserts `local <varName> = require("<requirePath>")\n` at offset 0 (file top)
 * unless the same local declaration already exists in the document.
 */
class AutoRequireInsertHandler(private val info: RequireModuleInfo) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val file = context.file
        val document = context.editor.document

        WriteCommandAction.runWriteCommandAction(file.project, "Insert require statement", null, {
            val docText = document.charsSequence.toString()
            if (!isAlreadyRequired(info.varName, docText)) {
                val requireStatement = buildRequireStatement()
                document.insertString(0, requireStatement)
            }
        }, file)
    }

    private fun buildRequireStatement(): String =
        "local ${info.varName} = require(\"${info.requirePath}\")\n"

    /** Returns true when a `local <varName> = require(...)` line is already present. */
    private fun isAlreadyRequired(varName: String, docText: String): Boolean {
        val pattern = Regex("""local\s+${Regex.escape(varName)}\s*=\s*require""")
        return pattern.containsMatchIn(docText)
    }
}
