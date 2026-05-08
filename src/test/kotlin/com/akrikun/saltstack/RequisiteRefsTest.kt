package com.akrikun.saltstack

import com.akrikun.saltstack.inspections.SaltAnnotator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequisiteRefsTest {

    private fun analyze(src: String): List<SaltAnnotator.Issue> {
        val lines = src.trimStart('\n').split("\n")
        val offsets = IntArray(lines.size)
        var off = 0
        for (i in lines.indices) {
            offsets[i] = off
            off += lines[i].length + 1
        }
        return SaltAnnotator.findUnknownRequisiteRefs(lines, offsets)
    }

    @Test
    fun `4-space indent — known ref OK`() {
        val src = """
local_state:
  cmd.run:
    - name: echo hi

second:
  cmd.run:
    - name: echo bye
    - require:
      - local_state
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `unknown ref is flagged`() {
        val src = """
my_state:
  cmd.run:
    - name: echo hi
    - require:
      - missing_id
"""
        val issues = analyze(src)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("missing_id"))
    }

    @Test
    fun `typed file requisite (id known) is OK`() {
        val src = """
foo:
  file.managed:
    - name: /etc/foo

bar:
  service.running:
    - watch:
      - file: foo
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `typed file requisite to a path is not flagged`() {
        // `- file: /etc/app.conf` — typed targets cross formulas; never flag.
        val src = """
my_state:
  cmd.run:
    - name: do
    - require:
      - file: /etc/app.conf
      - pkg: nginx
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `relative include like dot foo is not flagged`() {
        val src = """
my_state:
  cmd.run:
    - name: do
    - require:
      - .other_formula
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `Jinja-templated ref is not flagged`() {
        val src = """
my_state:
  cmd.run:
    - name: do
    - require:
      - {{ ref }}
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `typed ref to fully-qualified state is not flagged`() {
        val src = """
my_state:
  cmd.run:
    - require:
      - file: nginx.config
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `8-space indent (deeply nested) works`() {
        val src = """
my_state:
        cmd.run:
                - name: do
                - require:
                        - other_id

other_id:
        test.nop
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `state IDs with inline values are still collected`() {
        val src = """
key_with_value: ${'$'}host

other:
  cmd.run:
    - require:
      - key_with_value
"""
        assertEquals(0, analyze(src).size)
    }

    @Test
    fun `exits requisite block when indent decreases`() {
        val src = """
my_state:
  cmd.run:
    - require:
      - foo
    - name: cmd_after_requisite

foo:
  test.nop
"""
        // `cmd_after_requisite` is a value, not a requisite ref. Should not be flagged.
        val issues = analyze(src)
        assertEquals(0, issues.count { it.message.contains("cmd_after_requisite") })
    }
}
