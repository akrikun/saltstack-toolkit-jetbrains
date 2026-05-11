package com.akrikun.saltstack.navigation

/**
 * Extract the `salt://...` path from a line, if the cursor falls inside it.
 * Handles quoted and unquoted forms; strips trailing query (?), hash (#) and comments.
 *
 *   source: salt://foo/bar.conf            → "foo/bar.conf"
 *   source: 'salt://foo/bar.conf'          → "foo/bar.conf"
 *   - "salt://foo/bar.conf?env=base"       → "foo/bar.conf"
 *   - salt://foo/bar.conf  # comment       → "foo/bar.conf"
 *
 * Cursor convention: `cursorChar` is the column within `line`; the URI span is
 * treated as half-open [start, end) — clicking exactly past the URI returns null.
 *
 * Pure function for testability.
 */
/**
 * Extract the first quoted argument of `salt.fast_yaml.hosts(...)` or
 * `salt['fast_yaml.hosts'](...)`, if the cursor sits inside it.
 *
 *   salt.fast_yaml.hosts("common_meta")             → "common_meta"
 *   salt.fast_yaml.hosts('common_meta', ...)        → "common_meta"
 *   salt['fast_yaml.hosts']("common_meta")          → "common_meta"
 *
 * Pure function for testability.
 */
fun extractFastYamlArg(line: String, cursorChar: Int): String? {
    val re = Regex("(?:salt\\.fast_yaml\\.hosts|salt\\[['\"]fast_yaml\\.hosts['\"]\\])\\s*\\(\\s*['\"]([^'\"]+)['\"]")
    for (m in re.findAll(line)) {
        val arg = m.groupValues[1]
        val argStart = line.indexOf(arg, m.range.first)
        val argEnd = argStart + arg.length
        if (cursorChar in argStart..argEnd) return arg
    }
    return null
}

fun extractSaltUri(line: String, cursorChar: Int): String? {
    // Match includes ?query and #fragment so the cursor-in-token check works
    // even when the user clicks inside the query/hash; we strip those after.
    val re = Regex("salt://([^\\s'\"]+)")
    for (m in re.findAll(line)) {
        val start = m.range.first
        val end = m.range.last + 1 // exclusive
        if (cursorChar < start || cursorChar >= end) continue
        return m.groupValues[1].split('?', '#', limit = 2)[0]
    }
    return null
}
