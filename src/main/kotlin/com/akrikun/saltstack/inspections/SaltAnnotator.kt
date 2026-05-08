package com.akrikun.saltstack.inspections

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.akrikun.saltstack.SaltSettings

/**
 * Lint-style inspections for SLS/Jinja files:
 *  - duplicate top-level keys (state IDs)
 *  - tab characters
 *  - trailing whitespace
 *  - unclosed Jinja blocks
 */
class SaltAnnotator : ExternalAnnotator<SaltAnnotator.Input, List<SaltAnnotator.Issue>>() {

    data class Input(val text: String, val isPillar: Boolean)
    data class Issue(
        val severity: HighlightSeverity,
        val range: TextRange,
        val message: String,
    )

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input {
        val path = file.virtualFile?.path ?: return Input(file.text, false)
        val pillarRoots = SaltSettings.getInstance().pillarRoots
        val isPillar = pillarRoots.any { root ->
            path.contains("/$root/") || path.contains("\\$root\\")
        } || path.contains("/pillar/") || path.contains("/pillars/")
        return Input(file.text, isPillar)
    }

    override fun doAnnotate(input: Input?): List<Issue> {
        if (input == null) return emptyList()
        val settings = SaltSettings.getInstance()
        if (!settings.lintEnabled) return emptyList()

        val issues = mutableListOf<Issue>()
        val text = input.text
        val lines = text.split("\n")
        val lineOffsets = IntArray(lines.size)
        var off = 0
        for (i in lines.indices) {
            lineOffsets[i] = off
            off += lines[i].length + 1
        }

        if (settings.checkTabs) checkTabs(lines, lineOffsets, issues)
        if (settings.checkTrailingWhitespace) checkTrailingWhitespace(lines, lineOffsets, issues)
        if (settings.checkJinjaBlocks) checkUnclosedJinjaBlocks(lines, lineOffsets, issues)
        if (settings.checkDuplicateKeys) checkDuplicateTopLevelKeys(lines, lineOffsets, issues)

        // State-specific checks (skip pillar files)
        if (!input.isPillar) {
            if (settings.checkEmptyStates) checkEmptyStateBlocks(lines, lineOffsets, issues)
            if (settings.checkRequisiteRefs) checkRequisiteRefs(lines, lineOffsets, issues)
        }

        return issues
    }

    override fun apply(file: PsiFile, annotationResult: List<Issue>?, holder: AnnotationHolder) {
        if (annotationResult == null) return
        for (issue in annotationResult) {
            holder.newAnnotation(issue.severity, issue.message)
                .range(issue.range)
                .create()
        }
    }

    // === Checks ===

    private fun checkTabs(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        for ((i, line) in lines.withIndex()) {
            val tabIdx = line.indexOf('\t')
            if (tabIdx >= 0) {
                issues.add(Issue(
                    HighlightSeverity.ERROR,
                    TextRange(offsets[i] + tabIdx, offsets[i] + tabIdx + 1),
                    "Tab character — Salt/YAML requires spaces for indentation",
                ))
            }
        }
    }

    private fun checkTrailingWhitespace(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        val re = Regex("(\\s+)$")
        for ((i, line) in lines.withIndex()) {
            if (line.trim().isEmpty()) continue
            val m = re.find(line) ?: continue
            val start = line.length - m.value.length
            issues.add(Issue(
                HighlightSeverity.WEAK_WARNING,
                TextRange(offsets[i] + start, offsets[i] + line.length),
                "Trailing whitespace",
            ))
        }
    }

    private fun checkUnclosedJinjaBlocks(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        val openers = setOf("for", "if", "block", "macro", "call", "filter", "raw", "set")
        val closerToOpener = mapOf(
            "endfor" to "for", "endif" to "if", "endblock" to "block",
            "endmacro" to "macro", "endcall" to "call", "endfilter" to "filter", "endraw" to "raw",
            "endset" to "set",
        )
        val tagRe = Regex("\\{%-?\\s*(\\w+)")
        val stack = ArrayDeque<Pair<String, Int>>()

        for ((i, line) in lines.withIndex()) {
            for (m in tagRe.findAll(line)) {
                val kw = m.groupValues[1]
                when (kw) {
                    in openers -> {
                        // `set` has two forms:
                        //   - Assignment: {% set X = expr %} — single tag, NO endset
                        //   - Block:      {% set X %}...{% endset %} — multi-tag, endset required
                        if (kw == "set" && isAssignmentSet(line, m.range.first)) continue
                        stack.addLast(kw to i)
                    }
                    in closerToOpener.keys -> {
                        val expected = closerToOpener[kw]
                        if (stack.isEmpty()) {
                            issues.add(Issue(
                                HighlightSeverity.ERROR,
                                TextRange(offsets[i] + m.range.first, offsets[i] + m.range.last + 1),
                                "Unexpected {%- $kw %} — no matching {%- $expected %}",
                            ))
                        } else {
                            val (top, _) = stack.last()
                            if (top == expected) {
                                stack.removeLast()
                            } else {
                                issues.add(Issue(
                                    HighlightSeverity.ERROR,
                                    TextRange(offsets[i] + m.range.first, offsets[i] + m.range.last + 1),
                                    "Mismatched: expected {%- end$top %} but got {%- $kw %}",
                                ))
                                stack.removeLast()
                            }
                        }
                    }
                }
            }
        }

        for ((kw, line) in stack) {
            val lineLen = lines[line].length
            issues.add(Issue(
                HighlightSeverity.ERROR,
                TextRange(offsets[line], offsets[line] + lineLen),
                "Unclosed {%- $kw %} block — missing {%- end$kw %}",
            ))
        }
    }

    private fun checkEmptyStateBlocks(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        val stateIdRe = Regex("^([a-zA-Z_][\\w.\\-/() ]*):$")
        val moduleRe = Regex("^\\s+[\\w]+\\.[\\w]+:")

        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("{%") || trimmed.startsWith("{{")) continue
            val m = stateIdRe.matchEntire(line) ?: continue

            // Look ahead for a module call
            var hasModule = false
            for (j in (i + 1) until lines.size) {
                val next = lines[j]
                if (next.isBlank()) continue
                val nextTrim = next.trimStart()
                if (nextTrim.startsWith("{%") || nextTrim.startsWith("{#")) continue
                if (stateIdRe.matchEntire(next) != null) break
                if (next.matches(Regex("^[a-zA-Z].*"))) break
                if (moduleRe.containsMatchIn(next)) { hasModule = true; break }
                if (next.startsWith(" ") || next.startsWith("\t")) { hasModule = true; break }
            }

            if (!hasModule) {
                issues.add(Issue(
                    HighlightSeverity.WARNING,
                    TextRange(offsets[i], offsets[i] + m.groupValues[1].length),
                    "State ID \"${m.groupValues[1]}\" has no state module call",
                ))
            }
        }
    }

    private fun checkRequisiteRefs(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        for (issue in findUnknownRequisiteRefs(lines, offsets)) {
            issues.add(issue)
        }
    }

    companion object {
        /**
         * Decide if `{% set ... %}` at `tagStart` in `text` is an assignment form
         * (no endset needed) vs a block form (`{% set NAME %}...{% endset %}`).
         *
         * Heuristic: after `set`, look up to the closing `%}`/`-%}`. Assignment
         * form starts with a target list (`var`, `ns.foo`, `a, b`) followed by `=`.
         * Block form has no `=` at the start; filters/named-args (`upper(first=true)`)
         * are NOT before `=` and so do not falsely match.
         */
        @JvmStatic
        fun isAssignmentSet(text: String, tagStart: Int): Boolean {
            val tail = text.substring(tagStart)
            val setHead = Regex("^\\{%-?\\s*set\\b").find(tail) ?: return false
            val afterSet = tail.substring(setHead.value.length)
            val closeIdx = Regex("-?%\\}").find(afterSet)?.range?.first ?: afterSet.length
            val checkRange = afterSet.substring(0, closeIdx).trimStart()
            return Regex("^[\\w.]+(?:\\s*,\\s*[\\w.]+)*\\s*=").containsMatchIn(checkRange)
        }

        /**
         * Find requisite refs that don't match a top-level state ID in `lines`.
         * Returns Issues with absolute offsets computed from `lineOffsets`.
         *
         * Improvements over the previous regex-based approach:
         *  - flexible indentation (no hardcoded 6-space requirement); uses indent
         *    relative to the requisite-block header (`- require:`).
         *  - typed requisites (`- file: foo`, `- pkg: nginx`) target by-name
         *    across formulas — skipped entirely to avoid false positives.
         *  - skips path-like (`/etc/foo`, `.substate`) and Jinja-templated refs.
         */
        @JvmStatic
        fun findUnknownRequisiteRefs(lines: List<String>, lineOffsets: IntArray): List<Issue> {
            val stateIdRe = Regex("^([a-zA-Z_][\\w.\\-/() ]*):(?:\\s|$)")
            val stateIds = mutableSetOf<String>()
            for (line in lines) {
                if (line.trimStart().startsWith("{%")) continue
                val m = stateIdRe.find(line) ?: continue
                stateIds.add(m.groupValues[1])
            }

            // Allow trailing whitespace and an optional `# comment` after the colon.
            val requisiteHeaderRe = Regex(
                "^(\\s+)-\\s+(?:require|watch|onchanges|onfail|prereq|listen|use|" +
                "require_in|watch_in|onchanges_in|onfail_in|prereq_in|listen_in)(?:_any)?:\\s*(?:#.*)?$"
            )
            val entryRe = Regex("^(\\s+)-\\s+(?:(\\w+):\\s+)?([\\w][\\w.\\-/() ]*)\\s*$")

            val issues = mutableListOf<Issue>()
            var requisiteIndent = -1

            for ((i, line) in lines.withIndex()) {
                if (line.isBlank()) continue

                val headerMatch = requisiteHeaderRe.matchEntire(line)
                if (headerMatch != null) {
                    requisiteIndent = headerMatch.groupValues[1].length
                    continue
                }
                if (requisiteIndent < 0) continue

                val indent = line.length - line.trimStart().length
                if (indent <= requisiteIndent) {
                    requisiteIndent = -1
                    val nextHeader = requisiteHeaderRe.matchEntire(line)
                    if (nextHeader != null) requisiteIndent = nextHeader.groupValues[1].length
                    continue
                }

                val entryMatch = entryRe.matchEntire(line) ?: continue
                val moduleType: String? = entryMatch.groups[2]?.value
                val ref = entryMatch.groupValues[3].trim()

                if (ref.contains("{{") || ref.contains("{%")) continue
                // Typed requisites target by-name across formulas — never local state IDs.
                if (moduleType != null) continue
                // Untyped path-like refs aren't local state IDs. (Note: `ref` cannot
                // start with `.` because entryRe's first character class is `[\w]`,
                // but the contains("/") branch is reachable.)
                if (ref.contains("/")) continue

                if (!stateIds.contains(ref)) {
                    val startCol = line.indexOf(ref, indent)
                    issues.add(Issue(
                        HighlightSeverity.WEAK_WARNING,
                        TextRange(lineOffsets[i] + startCol, lineOffsets[i] + startCol + ref.length),
                        "State ID \"$ref\" not found in this file (could be from an included SLS)",
                    ))
                }
            }
            return issues
        }
    }

    private fun checkDuplicateTopLevelKeys(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        // Match top-level keys with or without inline values: "key:" or "key: value"
        val keyRe = Regex("^([a-zA-Z_][\\w.\\-/() ]*):(?:\\s.*|$)")
        val keys = mutableMapOf<String, MutableList<Int>>()

        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("{%") || trimmed.startsWith("{{")) continue
            val m = keyRe.matchEntire(line) ?: continue
            keys.getOrPut(m.groupValues[1]) { mutableListOf() }.add(i)
        }

        for ((id, lineNums) in keys) {
            if (lineNums.size <= 1) continue
            for (ln in lineNums) {
                val others = lineNums.filter { it != ln }.joinToString(", ") { (it + 1).toString() }
                issues.add(Issue(
                    HighlightSeverity.ERROR,
                    TextRange(offsets[ln], offsets[ln] + id.length),
                    "Duplicate key \"$id\" (also on line${if (lineNums.size > 2) "s" else ""} $others)",
                ))
            }
        }
    }
}
