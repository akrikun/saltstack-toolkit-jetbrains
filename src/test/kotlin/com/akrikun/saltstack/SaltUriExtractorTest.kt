package com.akrikun.saltstack

import com.akrikun.saltstack.navigation.extractFastYamlArg
import com.akrikun.saltstack.navigation.extractSaltUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SaltUriExtractorTest {

    @Test
    fun `bare unquoted form`() {
        val line = "    - source: salt://nginx/files/nginx.conf"
        assertEquals("nginx/files/nginx.conf", extractSaltUri(line, 25))
    }

    @Test
    fun `single-quoted form`() {
        val line = "    - source: 'salt://nginx/files/nginx.conf'"
        assertEquals("nginx/files/nginx.conf", extractSaltUri(line, 25))
    }

    @Test
    fun `double-quoted form`() {
        val line = """    - source: "salt://nginx/files/nginx.conf""""
        assertEquals("nginx/files/nginx.conf", extractSaltUri(line, 25))
    }

    @Test
    fun `trailing comment is stripped`() {
        val line = "    - source: salt://nginx/foo.conf  # main config"
        assertEquals("nginx/foo.conf", extractSaltUri(line, 30))
    }

    @Test
    fun `query string is stripped`() {
        val line = "    - source: salt://nginx/foo.conf?env=base"
        assertEquals("nginx/foo.conf", extractSaltUri(line, 30))
    }

    @Test
    fun `hash fragment is stripped`() {
        val line = "    - source: salt://nginx/foo.conf#section"
        assertEquals("nginx/foo.conf", extractSaltUri(line, 30))
    }

    @Test
    fun `cursor on query is still inside the URI token`() {
        // Was a bug: regex excluded ?# from match, so cursor on query never resolved.
        val line = "    - source: salt://foo.conf?env=base"
        val cursorOnQuery = line.indexOf("env")
        assertEquals("foo.conf", extractSaltUri(line, cursorOnQuery))
    }

    @Test
    fun `cursor on hash is still inside the URI token`() {
        val line = "    - source: salt://foo.conf#section"
        val cursorOnHash = line.indexOf("section")
        assertEquals("foo.conf", extractSaltUri(line, cursorOnHash))
    }

    @Test
    fun `cursor at exclusive end returns null`() {
        // Position right after the last char of the URI must NOT match.
        val line = "    - source: salt://foo"
        assertNull(extractSaltUri(line, line.length))
    }

    @Test
    fun `cursor at start (inclusive) returns match`() {
        val line = "    - source: salt://foo"
        assertEquals("foo", extractSaltUri(line, line.indexOf("salt://")))
    }

    @Test
    fun `cursor outside the URI returns null`() {
        val line = "    - source: salt://nginx/foo.conf  # other stuff after"
        assertNull(extractSaltUri(line, 50))
    }

    @Test
    fun `list-form bare salt URI`() {
        val line = "      - salt://nginx/foo.conf"
        assertEquals("nginx/foo.conf", extractSaltUri(line, 15))
    }

    @Test
    fun `returns null when no salt scheme present`() {
        assertNull(extractSaltUri("    - source: /etc/nginx.conf", 10))
    }

    // === fast_yaml.hosts() arg extraction ===

    @Test
    fun `fast_yaml dot form, cursor on arg`() {
        val line = """{%- set meta = salt.fast_yaml.hosts("common_meta") %}"""
        assertEquals("common_meta", extractFastYamlArg(line, line.indexOf("common_meta")))
    }

    @Test
    fun `fast_yaml bracket form`() {
        val line = """{{ salt['fast_yaml.hosts']('common_meta') }}"""
        assertEquals("common_meta", extractFastYamlArg(line, line.indexOf("common_meta")))
    }

    @Test
    fun `fast_yaml with kwargs`() {
        val line = """salt.fast_yaml.hosts("common_meta", attribute="ip")"""
        assertEquals("common_meta", extractFastYamlArg(line, line.indexOf("common_meta")))
    }

    @Test
    fun `fast_yaml cursor outside arg returns null`() {
        val line = """salt.fast_yaml.hosts("common_meta")"""
        assertNull(extractFastYamlArg(line, 1))
    }

    @Test
    fun `not a fast_yaml call returns null`() {
        val line = """salt['pillar.get']('common_meta')"""
        assertNull(extractFastYamlArg(line, line.indexOf("common_meta")))
    }
}
