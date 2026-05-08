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

    fun normalizeJinjaExpressions(text: String, enforceDash: Boolean): String {
        var t = text

        // Opening {% — enforce dash
        t = if (enforceDash) {
            t.replace(Regex("\\{%-?\\s*(?![\\s%])"), "{%- ")
        } else {
            Regex("\\{%(-?)\\s*(?![\\s%])").replace(t) { m ->
                if (m.groupValues[1].isNotEmpty()) "{%- " else "{% "
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

        return t
    }
}
