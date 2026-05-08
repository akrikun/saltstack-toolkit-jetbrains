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
        val openers = setOf("for", "if", "block", "macro", "call", "filter", "raw")
        val closerToOpener = mapOf(
            "endfor" to "for", "endif" to "if", "endblock" to "block",
            "endmacro" to "macro", "endcall" to "call", "endfilter" to "filter", "endraw" to "raw",
        )
        val tagRe = Regex("\\{%-?\\s*(\\w+)")
        val stack = ArrayDeque<Pair<String, Int>>()

        for ((i, line) in lines.withIndex()) {
            for (m in tagRe.findAll(line)) {
                val kw = m.groupValues[1]
                when (kw) {
                    in openers -> stack.addLast(kw to i)
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
        val stateIdRe = Regex("^([a-zA-Z_][\\w.\\-/() ]*):$")
        val requisiteStartRe = Regex("^\\s+-\\s+(require|watch|onchanges|onfail|prereq|listen|use|require_in|watch_in|onchanges_in|onfail_in|prereq_in|listen_in):")
        val refRe = Regex("^\\s{6,}-\\s+([\\w][\\w.\\-/() ]*)$")

        // Collect all local state IDs first
        val stateIds = mutableSetOf<String>()
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("{%")) continue
            val m = stateIdRe.matchEntire(line) ?: continue
            stateIds.add(m.groupValues[1])
        }

        var inRequisite = false
        for ((i, line) in lines.withIndex()) {
            if (requisiteStartRe.containsMatchIn(line)) {
                inRequisite = true
                continue
            }
            if (inRequisite) {
                val m = refRe.matchEntire(line)
                if (m != null) {
                    val ref = m.groupValues[1].trim()
                    if (ref.contains("{{") || ref.contains("{%") || ref.contains(":")) continue
                    if (!stateIds.contains(ref) && !ref.contains("/") && !ref.contains(".")) {
                        val startCol = line.indexOf(ref)
                        issues.add(Issue(
                            HighlightSeverity.WEAK_WARNING,
                            TextRange(offsets[i] + startCol, offsets[i] + startCol + ref.length),
                            "State ID \"$ref\" not found in this file (could be from an included SLS)",
                        ))
                    }
                } else if (line.isNotBlank() && !line.matches(Regex("^\\s{6,}.*"))) {
                    inRequisite = false
                }
            }
        }
    }

    private fun checkDuplicateTopLevelKeys(lines: List<String>, offsets: IntArray, issues: MutableList<Issue>) {
        val keyRe = Regex("^([a-zA-Z_][\\w.\\-/() ]*):$")
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
