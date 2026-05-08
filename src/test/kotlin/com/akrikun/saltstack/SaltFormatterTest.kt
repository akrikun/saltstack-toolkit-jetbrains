package com.akrikun.saltstack

import com.akrikun.saltstack.formatting.SaltFormatter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaltFormatterTest {

    // === enforceDash=true (default behavior) ===

    @Test
    fun `opening tag gets dash when enforced`() {
        assertEquals("{%- if x %}", SaltFormatter.normalizeJinjaExpressions("{% if x %}", true))
        assertEquals("{%- if x %}", SaltFormatter.normalizeJinjaExpressions("{%if x%}", true))
    }

    @Test
    fun `closing dash is preserved (whitespace semantics)`() {
        // CRITICAL: -%} controls runtime whitespace stripping. Must never be removed.
        assertEquals("{%- if x -%}", SaltFormatter.normalizeJinjaExpressions("{%- if x -%}", true))
        assertEquals("{%- if x -%}", SaltFormatter.normalizeJinjaExpressions("{% if x -%}", true))
        assertEquals("{{- foo -}}", SaltFormatter.normalizeJinjaExpressions("{{- foo -}}", true))
    }

    @Test
    fun `opening dash on var output is preserved`() {
        assertEquals("{{- foo }}", SaltFormatter.normalizeJinjaExpressions("{{- foo }}", true))
        assertEquals("{{ foo }}", SaltFormatter.normalizeJinjaExpressions("{{ foo }}", true))
    }

    @Test
    fun `multi-spaced tags are normalized`() {
        assertEquals("{{ foo }}", SaltFormatter.normalizeJinjaExpressions("{{   foo   }}", true))
        assertEquals("{%- if x %}", SaltFormatter.normalizeJinjaExpressions("{%-   if x   %}", true))
    }

    // === enforceDash=false ===

    @Test
    fun `with enforceDash false, opening stays without dash`() {
        assertEquals("{% if x %}", SaltFormatter.normalizeJinjaExpressions("{% if x %}", false))
        assertEquals("{%- if x %}", SaltFormatter.normalizeJinjaExpressions("{%- if x %}", false))
    }

    // === Comments preserved verbatim ===

    @Test
    fun `comment content is not touched`() {
        val before = "{# preserve  this   spacing #}"
        assertEquals(before, SaltFormatter.normalizeJinjaTags(before, true))
    }

    @Test
    fun `code outside comments is normalized comment block intact`() {
        val input = "{%if x%} {# do not touch  this  #} {{var}}"
        val output = SaltFormatter.normalizeJinjaTags(input, true)
        assertTrue(output.contains("{# do not touch  this  #}"), "comment must be preserved")
        assertTrue(output.contains("{%- if x %}"), "code outside comment must be normalized")
        assertTrue(output.contains("{{ var }}"), "code outside comment must be normalized")
    }

    // === Idempotency ===

    @Test
    fun `formatter is idempotent on already-formatted input`() {
        val cases = listOf(
            "{%- if x %}",
            "{%- for item in items -%}",
            "{{ var }}",
            "{{- var -}}",
            "{# comment #}",
        )
        for (input in cases) {
            val once = SaltFormatter.normalizeJinjaExpressions(input, true)
            val twice = SaltFormatter.normalizeJinjaExpressions(once, true)
            assertEquals(once, twice, "not idempotent: $input -> $once -> $twice")
        }
    }

    // === Standalone closing tag on continuation line ===

    @Test
    fun `standalone closer is not touched`() {
        // Was a regression previously: regex inserted a leading space.
        assertEquals("%}", SaltFormatter.normalizeJinjaExpressions("%}", true))
        assertEquals("}}", SaltFormatter.normalizeJinjaExpressions("}}", true))
    }

    @Test
    fun `multiline-set continuation is preserved`() {
        assertEquals("{%- set nginx = {", SaltFormatter.normalizeJinjaExpressions("{%- set nginx = {", true))
    }
}
