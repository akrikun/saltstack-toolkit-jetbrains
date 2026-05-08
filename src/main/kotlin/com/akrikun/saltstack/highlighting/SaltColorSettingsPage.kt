package com.akrikun.saltstack.highlighting

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.akrikun.saltstack.SaltIcons
import javax.swing.Icon

class SaltColorSettingsPage : ColorSettingsPage {

    private val descriptors = arrayOf(
        AttributesDescriptor("YAML key", SaltSyntaxHighlighter.YAML_KEY),
        AttributesDescriptor("Identifier", SaltSyntaxHighlighter.IDENTIFIER),
        AttributesDescriptor("String", SaltSyntaxHighlighter.STRING),
        AttributesDescriptor("Number", SaltSyntaxHighlighter.NUMBER),
        AttributesDescriptor("YAML comment", SaltSyntaxHighlighter.COMMENT),
        AttributesDescriptor("Jinja tag", SaltSyntaxHighlighter.JINJA_TAG),
        AttributesDescriptor("Jinja expression", SaltSyntaxHighlighter.JINJA_EXPR),
        AttributesDescriptor("Jinja keyword", SaltSyntaxHighlighter.JINJA_KEYWORD),
        AttributesDescriptor("Jinja comment", SaltSyntaxHighlighter.JINJA_COMMENT),
        AttributesDescriptor("Punctuation", SaltSyntaxHighlighter.PUNCTUATION),
    )

    override fun getIcon(): Icon = SaltIcons.SLS_FILE
    override fun getHighlighter(): SyntaxHighlighter = SaltSyntaxHighlighter()
    override fun getDemoText(): String = """
        # Casino server pillar
        {%- import_yaml "defaults.sls" as defaults %}
        {%- set domain = "example.com" %}

        tags:
          nftables: true

        nginx:
          version: 1.18.0
          listen:
            - {{ domain }}

        {%- for entry in items %}
          {{ entry }}: 1
        {%- endfor %}
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, com.intellij.openapi.editor.colors.TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "SaltStack Toolkit"
}
