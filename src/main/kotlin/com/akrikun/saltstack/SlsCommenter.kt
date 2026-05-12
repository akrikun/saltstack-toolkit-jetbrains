package com.akrikun.saltstack

import com.intellij.lang.Commenter

/**
 * Provides line/block comment markers for `.sls` files so Cmd+/ (Comment with
 * Line Comment) and the right-click "Comment with..." actions work.
 *
 * SLS is YAML-with-Jinja:
 *   line  : `#` (YAML)
 *   block : `{# ... #}` (Jinja — also valid in SLS since Salt pre-renders Jinja)
 */
class SlsCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "#"
    override fun getBlockCommentPrefix(): String = "{#"
    override fun getBlockCommentSuffix(): String = "#}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
