package com.akrikun.saltstack

import com.intellij.lang.Language

object JinjaLanguage : Language("Jinja") {
    private fun readResolve(): Any = JinjaLanguage
    override fun getDisplayName(): String = "Jinja2"
}
