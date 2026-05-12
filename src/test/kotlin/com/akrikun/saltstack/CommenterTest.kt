package com.akrikun.saltstack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies the comment markers returned by [SlsCommenter] / [JinjaCommenter].
 *
 * Behavior-level (does pressing Cmd+/ actually wrap the selection?) would need
 * the IntelliJ test framework + a fixture file open in an Editor. We pulled
 * that framework out earlier to keep these tests pure/fast; the values asserted
 * here are exactly what the platform reads to drive Cmd+/ and Cmd+Shift+/.
 */
class CommenterTest {

    @Test
    fun `sls line comment is hash`() {
        assertEquals("#", SlsCommenter().lineCommentPrefix)
    }

    @Test
    fun `sls block comment is jinja-style braces`() {
        val c = SlsCommenter()
        assertEquals("{#", c.blockCommentPrefix)
        assertEquals("#}", c.blockCommentSuffix)
    }

    @Test
    fun `sls reports no commented-block markers (we don't auto-uncomment)`() {
        val c = SlsCommenter()
        assertNull(c.commentedBlockCommentPrefix)
        assertNull(c.commentedBlockCommentSuffix)
    }

    @Test
    fun `jinja has no line-comment primitive`() {
        // Jinja's only comment form is the block `{# ... #}`. We expose null
        // so Cmd+/ falls back to the block form.
        assertNull(JinjaCommenter().lineCommentPrefix)
    }

    @Test
    fun `jinja block comment matches jinja syntax`() {
        val c = JinjaCommenter()
        assertEquals("{#", c.blockCommentPrefix)
        assertEquals("#}", c.blockCommentSuffix)
    }
}
