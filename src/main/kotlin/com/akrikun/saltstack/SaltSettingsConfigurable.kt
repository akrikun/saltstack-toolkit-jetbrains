package com.akrikun.saltstack

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class SaltSettingsConfigurable : Configurable {

    private val settings = SaltSettings.getInstance()

    private val stateRootsField = JBTextField()
    private val pillarRootsField = JBTextField()
    private val lintEnabled = JBCheckBox("Enable linter")
    private val checkTabs = JBCheckBox("Warn on tab characters")
    private val checkTrailingWhitespace = JBCheckBox("Warn on trailing whitespace")
    private val checkJinjaBlocks = JBCheckBox("Check unclosed Jinja blocks")
    private val checkDuplicateKeys = JBCheckBox("Check duplicate top-level keys")
    private val checkEmptyStates = JBCheckBox("Warn on empty state blocks")
    private val checkRequisiteRefs = JBCheckBox("Check requisite references")
    private val formatOnSave = JBCheckBox("Auto-format on save")
    private val enforceDashTags = JBCheckBox("Enforce {%- ... %} dash style")

    private var panel: JComponent? = null

    override fun getDisplayName(): String = "SaltStack Toolkit"

    override fun createComponent(): JComponent {
        // Initialize fields with current settings values
        reset()

        return panel {
            group("Paths") {
                row("State roots (comma-separated):") {
                    cell(stateRootsField)
                        .comment("Relative to project root or absolute. Used for Go-to-Definition. Default: salt, srv/salt")
                }
                row("Pillar roots (comma-separated):") {
                    cell(pillarRootsField)
                        .comment("Relative to project root or absolute. Default: pillar, srv/pillar")
                }
            }
            group("Linter") {
                row { cell(lintEnabled) }
                row { cell(checkTabs) }
                row { cell(checkTrailingWhitespace) }
                row { cell(checkJinjaBlocks) }
                row { cell(checkDuplicateKeys) }
                row { cell(checkEmptyStates) }
                row { cell(checkRequisiteRefs) }
            }
            group("Formatting") {
                row { cell(formatOnSave) }
                row { cell(enforceDashTags) }
            }
        }.also { panel = it }
    }

    override fun isModified(): Boolean {
        return parseRoots(stateRootsField.text) != settings.stateRoots
            || parseRoots(pillarRootsField.text) != settings.pillarRoots
            || lintEnabled.isSelected != settings.lintEnabled
            || checkTabs.isSelected != settings.checkTabs
            || checkTrailingWhitespace.isSelected != settings.checkTrailingWhitespace
            || checkJinjaBlocks.isSelected != settings.checkJinjaBlocks
            || checkDuplicateKeys.isSelected != settings.checkDuplicateKeys
            || checkEmptyStates.isSelected != settings.checkEmptyStates
            || checkRequisiteRefs.isSelected != settings.checkRequisiteRefs
            || formatOnSave.isSelected != settings.formatOnSave
            || enforceDashTags.isSelected != settings.enforceDashTags
    }

    override fun apply() {
        settings.stateRoots = parseRoots(stateRootsField.text).toMutableList()
        settings.pillarRoots = parseRoots(pillarRootsField.text).toMutableList()
        settings.lintEnabled = lintEnabled.isSelected
        settings.checkTabs = checkTabs.isSelected
        settings.checkTrailingWhitespace = checkTrailingWhitespace.isSelected
        settings.checkJinjaBlocks = checkJinjaBlocks.isSelected
        settings.checkDuplicateKeys = checkDuplicateKeys.isSelected
        settings.checkEmptyStates = checkEmptyStates.isSelected
        settings.checkRequisiteRefs = checkRequisiteRefs.isSelected
        settings.formatOnSave = formatOnSave.isSelected
        settings.enforceDashTags = enforceDashTags.isSelected
    }

    override fun reset() {
        stateRootsField.text = settings.stateRoots.joinToString(", ")
        pillarRootsField.text = settings.pillarRoots.joinToString(", ")
        lintEnabled.isSelected = settings.lintEnabled
        checkTabs.isSelected = settings.checkTabs
        checkTrailingWhitespace.isSelected = settings.checkTrailingWhitespace
        checkJinjaBlocks.isSelected = settings.checkJinjaBlocks
        checkDuplicateKeys.isSelected = settings.checkDuplicateKeys
        checkEmptyStates.isSelected = settings.checkEmptyStates
        checkRequisiteRefs.isSelected = settings.checkRequisiteRefs
        formatOnSave.isSelected = settings.formatOnSave
        enforceDashTags.isSelected = settings.enforceDashTags
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun parseRoots(text: String): List<String> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
