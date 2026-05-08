package com.akrikun.saltstack

import com.intellij.lang.Language

object SlsLanguage : Language("SLS") {
    private fun readResolve(): Any = SlsLanguage
    override fun getDisplayName(): String = "SaltStack SLS"
}
