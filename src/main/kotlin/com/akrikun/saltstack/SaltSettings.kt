package com.akrikun.saltstack

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "SaltStackToolkit", storages = [Storage("saltstack-toolkit.xml")])
class SaltSettings : PersistentStateComponent<SaltSettings.State> {

    data class State(
        var lintEnabled: Boolean = true,
        var checkTabs: Boolean = true,
        var checkTrailingWhitespace: Boolean = true,
        var checkJinjaBlocks: Boolean = true,
        var checkDuplicateKeys: Boolean = true,
        var checkEmptyStates: Boolean = true,
        var checkRequisiteRefs: Boolean = true,
        var formatOnSave: Boolean = true,
        // Default is now `false`: enforcing the `{%- ... %}` dash style changes
        // Jinja runtime whitespace semantics. Users who want it can opt in
        // (Settings → Tools → SaltStack Toolkit).
        var enforceDashTags: Boolean = false,
        var stateRoots: MutableList<String> = mutableListOf("salt", "srv/salt"),
        var pillarRoots: MutableList<String> = mutableListOf("pillar", "srv/pillar"),
    )

    private var state = State()

    var lintEnabled: Boolean
        get() = state.lintEnabled
        set(v) { state.lintEnabled = v }
    var checkTabs: Boolean
        get() = state.checkTabs
        set(v) { state.checkTabs = v }
    var checkTrailingWhitespace: Boolean
        get() = state.checkTrailingWhitespace
        set(v) { state.checkTrailingWhitespace = v }
    var checkJinjaBlocks: Boolean
        get() = state.checkJinjaBlocks
        set(v) { state.checkJinjaBlocks = v }
    var checkDuplicateKeys: Boolean
        get() = state.checkDuplicateKeys
        set(v) { state.checkDuplicateKeys = v }
    var checkEmptyStates: Boolean
        get() = state.checkEmptyStates
        set(v) { state.checkEmptyStates = v }
    var checkRequisiteRefs: Boolean
        get() = state.checkRequisiteRefs
        set(v) { state.checkRequisiteRefs = v }
    var formatOnSave: Boolean
        get() = state.formatOnSave
        set(v) { state.formatOnSave = v }
    var enforceDashTags: Boolean
        get() = state.enforceDashTags
        set(v) { state.enforceDashTags = v }
    var stateRoots: MutableList<String>
        get() = state.stateRoots
        set(v) { state.stateRoots = v }
    var pillarRoots: MutableList<String>
        get() = state.pillarRoots
        set(v) { state.pillarRoots = v }

    override fun getState(): State = state

    override fun loadState(s: State) {
        XmlSerializerUtil.copyBean(s, state)
    }

    companion object {
        @JvmStatic
        fun getInstance(): SaltSettings = ApplicationManager.getApplication().getService(SaltSettings::class.java)
    }
}
