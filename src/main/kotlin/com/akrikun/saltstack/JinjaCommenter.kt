package com.akrikun.saltstack

import com.intellij.lang.Commenter

/**
 * Comment markers for `.jinja` / `.j2` files.
 *
 * Jinja's native comment is `{# ... #}` (block). There's no line-comment
 * primitive, so we use the block form for both line and block actions —
 * Cmd+/ will wrap the selection/line in `{# ... #}`.
 */
class JinjaCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null
    override fun getBlockCommentPrefix(): String = "{#"
    override fun getBlockCommentSuffix(): String = "#}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
