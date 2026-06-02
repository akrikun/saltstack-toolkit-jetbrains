package com.akrikun.saltstack.formatting

/**
 * String-level formatter that:
 *  - converts leading tabs to spaces
 *  - removes trailing whitespace
 *  - normalizes Jinja tag spacing ({% if %} -> {%- if %}, {%if x%} -> {% if x %})
 *  - collapses multiple consecutive blank lines
 *  - ensures final newline
 *
 * Comments {# ... #} are preserved as-is.
 */
object SaltFormatter {

    fun format(text: String, enforceDash: Boolean, indentSpaces: Int = 2): String {
        val indent = " ".repeat(indentSpaces)
        val lines = text.split("\n").toMutableList()
        val out = mutableListOf<String>()

        for (line in lines) {
            // 1. Tabs → spaces (leading only)
            var newLine = line
            val tabMatch = Regex("^(\\t+)").find(newLine)
            if (tabMatch != null) {
                newLine = tabMatch.value.replace("\t", indent) + newLine.substring(tabMatch.value.length)
            }

            // 2. Trailing whitespace
            newLine = newLine.replace(Regex("\\s+$"), "")

            // 3. Jinja tags
            newLine = normalizeJinjaTags(newLine, enforceDash)

            out.add(newLine)
        }

        // 4. Collapse multiple blank lines
        val collapsed = mutableListOf<String>()
        var blanks = 0
        for (l in out) {
            if (l.isBlank()) {
                blanks++
                if (blanks <= 1) collapsed.add(l)
            } else {
                blanks = 0
                collapsed.add(l)
            }
        }

        // 5. Ensure exactly one trailing newline
        while (collapsed.isNotEmpty() && collapsed.last().isBlank()) {
            collapsed.removeAt(collapsed.size - 1)
        }
        return collapsed.joinToString("\n") + "\n"
    }

    fun normalizeJinjaTags(text: String, enforceDash: Boolean): String {
        val segments = mutableListOf<Pair<String, Boolean>>() // (text, isComment)
        var rest = text

        while (rest.isNotEmpty()) {
            val cs = rest.indexOf("{#")
            if (cs < 0) {
                segments.add(rest to false)
                break
            }
            if (cs > 0) segments.add(rest.substring(0, cs) to false)
            val ce = rest.indexOf("#}", cs + 2)
            if (ce < 0) {
                segments.add(rest.substring(cs) to true)
                break
            }
            segments.add(rest.substring(cs, ce + 2) to true)
            rest = rest.substring(ce + 2)
        }

        return segments.joinToString("") { (s, isComment) ->
            if (isComment) s else normalizeJinjaExpressions(s, enforceDash)
        }
    }

    /**
     * Jinja block tags that inject rendered content at their position. A leading
     * whitespace-control dash (`{%-`) on such a tag strips the preceding newline and
     * fuses the injected content onto the previous output line. For pillars that
     * include a file starting with a top-level YAML key (e.g.
     * `{% include "foo.sls" %}` where foo.sls begins `alertmanager:`), this corrupts
     * the document structure and the YAML parser fails ("expected '<document start>',
     * but found '<block mapping start>'"). See the DO-52194 regression.
     *
     * These tags must therefore NEVER carry a whitespace-control dash, regardless of
     * the "enforce dash" setting — any existing dash (leading or trailing) is stripped
     * so the injected content always sits on its own line.
     */
    val CONTENT_INJECTING_TAGS = setOf("include")

    fun normalizeJinjaExpressions(text: String, enforceDash: Boolean): String {
        var t = text

        // Opening {% ... — decide the leading dash per keyword. Content-injecting
        // tags (see CONTENT_INJECTING_TAGS) are forced dashless so they never break
        // the rendered document; all other tags follow the enforceDash setting.
        t = Regex("\\{%(-?)\\s*([a-zA-Z_]\\w*)?").replace(t) { m ->
            val dash = m.groupValues[1]
            val keyword = m.groupValues[2]
            when {
                keyword.isEmpty() -> m.value // standalone/malformed {% (e.g. multi-line tag continuation) — leave as-is
                keyword in CONTENT_INJECTING_TAGS -> "{% $keyword" // never add a dash; strip any existing one
                enforceDash -> "{%- $keyword"
                dash.isNotEmpty() -> "{%- $keyword"
                else -> "{% $keyword"
            }
        }

        // Closing %} — only when there's actual content before
        t = Regex("(?<=[^\\s%])\\s*(-?)%\\}").replace(t) { m ->
            if (m.groupValues[1].isNotEmpty()) " -%}" else " %}"
        }

        // {{ ... }} — preserve dashes, fix spacing
        t = Regex("\\{\\{(-?)\\s*(?![\\s}])").replace(t) { m ->
            if (m.groupValues[1].isNotEmpty()) "{{- " else "{{ "
        }
        t = Regex("(?<=[^\\s{])\\s*(-?)\\}\\}").replace(t) { m ->
            if (m.groupValues[1].isNotEmpty()) " -}}" else " }}"
        }

        // Cleanup: collapse double-spaces inside tags
        t = Regex("(\\{\\{-?\\s)\\s+").replace(t, "$1")
        t = Regex("\\s\\s+((-?)?\\}\\})").replace(t, " $1")
        t = Regex("(\\{%-?\\s)\\s+").replace(t, "$1")
        t = Regex("\\s\\s+((-?)?%\\})").replace(t, " $1")

        // Safety net for content-injecting tags: also drop a trailing `-%}`, which
        // would fuse the *following* line into the injected content. These tags are
        // always single-line, so this never touches multi-line tag continuations.
        for (keyword in CONTENT_INJECTING_TAGS) {
            t = Regex("(\\{%\\s*$keyword\\b[^%]*?)\\s*-%\\}").replace(t, "$1 %}")
        }

        return t
    }
}
