package com.akrikun.saltstack

import com.akrikun.saltstack.inspections.SaltAnnotator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsAssignmentSetTest {

    @Test
    fun `assignment form (= present)`() {
        assertTrue(SaltAnnotator.isAssignmentSet("{% set x = 5 %}", 0))
        assertTrue(SaltAnnotator.isAssignmentSet("{%- set x = 5 -%}", 0))
        assertTrue(SaltAnnotator.isAssignmentSet("{% set x = pillar.foo | default({}) %}", 0))
    }

    @Test
    fun `block form (no =) needs endset`() {
        assertFalse(SaltAnnotator.isAssignmentSet("{% set greeting %}", 0))
        assertFalse(SaltAnnotator.isAssignmentSet("{%- set greeting -%}", 0))
    }

    @Test
    fun `multi-line dict assignment is still assignment`() {
        // `{%- set nginx = {` opens a dict literal — still assignment, no endset.
        assertTrue(SaltAnnotator.isAssignmentSet("{%- set nginx = {", 0))
    }

    @Test
    fun `tag at non-zero offset`() {
        assertTrue(SaltAnnotator.isAssignmentSet("  {% set x = 5 %}", 2))
    }

    @Test
    fun `tuple assignment`() {
        assertTrue(SaltAnnotator.isAssignmentSet("{% set a, b = 1, 2 %}", 0))
    }

    @Test
    fun `dotted target is assignment`() {
        assertTrue(SaltAnnotator.isAssignmentSet("{% set ns.foo = 1 %}", 0))
    }

    @Test
    fun `block form with filter and named-arg is NOT assignment`() {
        // Was a false positive: `first=true` contained `=` which broke the
        // simple `includes("=")` heuristic.
        assertFalse(SaltAnnotator.isAssignmentSet("{% set greeting | upper(first=true) %}", 0))
    }

    @Test
    fun `block form with multiple piped filters with kwargs`() {
        assertFalse(SaltAnnotator.isAssignmentSet("{%- set body | trim | replace(old='a', new='b') -%}", 0))
    }

    @Test
    fun `malformed set without name is treated as block`() {
        assertFalse(SaltAnnotator.isAssignmentSet("{% set %}", 0))
    }
}
