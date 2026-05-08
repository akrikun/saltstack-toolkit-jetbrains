package com.akrikun.saltstack.navigation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for pillar key usages search.
 *
 * Tracks indirect pillar references via:
 *  - Local Jinja aliases: `{% set foo = pillar.x %}` → `foo.y` references `pillar.x.y`
 *  - Imported dict vars: `{% from "X.jinja" import bar %}` where X.jinja has
 *    `{% set bar = { 'key': pillar.foo.key } %}` → `bar.key` references `pillar.foo.key`
 */
@Service(Service.Level.PROJECT)
class PillarUsageCache(private val project: Project) : Disposable {

    @Volatile
    private var fileListCache: List<File>? = null
    private val fileEntryCache = ConcurrentHashMap<String, FileEntry>()

    data class FileEntry(
        val mtime: Long,
        val text: String,
        val aliases: Map<String, List<String>>,
        val dictMaps: Map<String, Map<String, List<String>>>,
        val imports: Map<String, String>,
    )

    init {
        VirtualFileManager.getInstance().addAsyncFileListener(
            object : AsyncFileListener {
                override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
                    var listInvalid = false
                    val toEvict = mutableListOf<String>()
                    for (event in events) {
                        if (!isWatchedFile(event.path)) continue
                        when (event) {
                            is VFileCreateEvent -> listInvalid = true
                            is VFileDeleteEvent -> { listInvalid = true; toEvict.add(event.path) }
                            is VFileMoveEvent -> { listInvalid = true; toEvict.add(event.path) }
                            is VFileContentChangeEvent -> toEvict.add(event.path)
                            else -> {}
                        }
                    }
                    if (!listInvalid && toEvict.isEmpty()) return null
                    return object : AsyncFileListener.ChangeApplier {
                        override fun afterVfsChange() {
                            if (listInvalid) fileListCache = null
                            for (p in toEvict) fileEntryCache.remove(p)
                        }
                    }
                }
            },
            this,
        )

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: Document) {
                    val vf = FileDocumentManager.getInstance().getFile(document) ?: return
                    fileEntryCache.remove(vf.path)
                }
            })
    }

    override fun dispose() {
        fileEntryCache.clear()
        fileListCache = null
    }

    fun invalidateAll() {
        fileListCache = null
        fileEntryCache.clear()
    }

    private fun isWatchedFile(path: String): Boolean {
        val ext = path.substringAfterLast('.').lowercase()
        return ext == "sls" || ext == "jinja" || ext == "j2" || ext == "jinja2"
    }

    private fun getResolvedStateRoots(): List<String> {
        val settings = com.akrikun.saltstack.SaltSettings.getInstance()
        val result = mutableListOf<String>()
        for (root in settings.stateRoots) {
            if (File(root).isAbsolute) {
                result.add(root)
            } else {
                project.basePath?.let { result.add(File(it, root).absolutePath) }
            }
        }
        return result
    }

    private fun getStateFiles(): List<File> {
        fileListCache?.let { return it }
        val roots = getResolvedStateRoots()
        val seen = mutableSetOf<String>()
        val files = mutableListOf<File>()
        for (root in roots) {
            val baseFile = File(root)
            if (!baseFile.isDirectory) continue
            baseFile.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in setOf("sls", "jinja", "j2", "jinja2") }
                .forEach { f -> if (seen.add(f.absolutePath)) files.add(f) }
        }
        fileListCache = files
        return files
    }

    private fun getEntry(file: File): FileEntry? {
        val mtime = file.lastModified()
        val cached = fileEntryCache[file.absolutePath]
        if (cached != null && cached.mtime == mtime) return cached
        return try {
            val text = file.readText()
            val aliases = findPillarAliases(text)
            val entry = FileEntry(
                mtime = mtime,
                text = text,
                aliases = aliases,
                dictMaps = findDictMaps(text, aliases),
                imports = findJinjaImports(text),
            )
            fileEntryCache[file.absolutePath] = entry
            entry
        } catch (e: Exception) {
            null
        }
    }

    /** Find usages of a pillar key path. Returns list of (file, offset) pairs. */
    fun findUsages(searchPath: List<String>): List<Pair<File, Int>> {
        if (searchPath.isEmpty()) return emptyList()
        val files = getStateFiles()
        val entries = files.mapNotNull { f -> getEntry(f)?.let { f to it } }.toMap()
        val stateRoots = getResolvedStateRoots()

        val results = mutableListOf<Pair<File, Int>>()
        val directRe = buildPathRegex(searchPath)

        for ((file, entry) in entries) {
            val text = entry.text

            // 1. Direct pillar.X.Y access
            collectMatches(text, directRe, file, results)

            // 2. Local aliases
            for ((varName, aliasPath) in entry.aliases) {
                if (!isPrefix(aliasPath, searchPath)) continue
                val remaining = searchPath.drop(aliasPath.size)
                val re = if (remaining.isEmpty()) {
                    Regex("\\b${Regex.escape(varName)}\\b")
                } else {
                    buildVarPathRegex(varName, remaining)
                }
                collectMatches(text, re, file, results)
            }

            // 3. Cross-file: imports
            for ((importedVar, importPath) in entry.imports) {
                val sourceFile = resolveImport(importPath, stateRoots, files) ?: continue
                val sourceEntry = entries[sourceFile] ?: continue

                // Case A: imported var is a dict map
                val dictMap = sourceEntry.dictMaps[importedVar]
                if (dictMap != null) {
                    for ((key, keyPath) in dictMap) {
                        if (!isPrefix(keyPath, searchPath)) continue
                        val remaining = searchPath.drop(keyPath.size)
                        val baseRe = "\\b${Regex.escape(importedVar)}\\.${Regex.escape(key)}"
                        val re = if (remaining.isEmpty()) {
                            Regex("$baseRe\\b")
                        } else {
                            val partGroup = remaining.joinToString("") { p ->
                                val e = Regex.escape(p)
                                "(?:\\.${e}|\\[[\"']${e}[\"']\\])"
                            }
                            Regex("$baseRe$partGroup")
                        }
                        collectMatches(text, re, file, results)
                    }
                }

                // Case B: imported var is itself an alias
                val sourceAlias = sourceEntry.aliases[importedVar]
                if (sourceAlias != null && isPrefix(sourceAlias, searchPath)) {
                    val remaining = searchPath.drop(sourceAlias.size)
                    val re = if (remaining.isEmpty()) {
                        Regex("\\b${Regex.escape(importedVar)}\\b")
                    } else {
                        buildVarPathRegex(importedVar, remaining)
                    }
                    collectMatches(text, re, file, results)
                }
            }
        }

        // Deduplicate by (file, offset)
        return results.distinctBy { "${it.first.absolutePath}:${it.second}" }
    }

    /** Find usages and group by state name (first dir component under stateRoots). */
    fun findUsagesByState(searchPath: List<String>): Map<String, List<Pair<File, Int>>> {
        val locations = findUsages(searchPath)
        val stateRoots = getResolvedStateRoots()
        val result = LinkedHashMap<String, MutableList<Pair<File, Int>>>()
        for (loc in locations) {
            val stateName = getStateName(loc.first.absolutePath, stateRoots) ?: continue
            result.getOrPut(stateName) { mutableListOf() }.add(loc)
        }
        return result
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): PillarUsageCache =
            project.getService(PillarUsageCache::class.java)
    }
}

// ===== Helper functions (top-level, package-private) =====

private fun collectMatches(text: String, re: Regex, file: File, out: MutableList<Pair<File, Int>>) {
    for (match in re.findAll(text)) {
        val lastChar = match.value.lastOrNull()
        if (lastChar != ']' && lastChar != '\'' && lastChar != '"') {
            val after = text.getOrNull(match.range.last + 1)
            if (after != null && (after.isLetterOrDigit() || after == '_')) continue
        }
        out.add(file to match.range.first)
    }
}

private fun buildPathRegex(path: List<String>): Regex {
    val partGroup = path.joinToString("") { p ->
        val e = Regex.escape(p)
        "(?:\\.${e}|\\[[\"']${e}[\"']\\])"
    }
    val chained = "pillar$partGroup"
    val colonPath = path.joinToString(":") { Regex.escape(it) }
    val pillarGet = "pillar\\.get\\s*\\(\\s*[\"']$colonPath(?=[:\"'])"
    val saltPillarGet = "salt\\[[\"']pillar\\.get[\"']\\]\\s*\\(\\s*[\"']$colonPath(?=[:\"'])"
    return Regex("(?:$chained|$pillarGet|$saltPillarGet)")
}

private fun buildVarPathRegex(varName: String, path: List<String>): Regex {
    val partGroup = path.joinToString("") { p ->
        val e = Regex.escape(p)
        "(?:\\.${e}|\\[[\"']${e}[\"']\\])"
    }
    return Regex("\\b${Regex.escape(varName)}$partGroup")
}

private fun isPrefix(prefix: List<String>, full: List<String>): Boolean {
    if (prefix.size > full.size) return false
    for (i in prefix.indices) if (prefix[i] != full[i]) return false
    return true
}

private fun findPillarAliases(text: String): Map<String, List<String>> {
    val aliases = mutableMapOf<String, List<String>>()
    val setRe = Regex("\\{%-?\\s*set\\s+(\\w+)\\s*=\\s*pillar((?:\\.\\w+|\\[['\"]?\\w+['\"]?\\])+)")
    for (m in setRe.findAll(text)) {
        val varName = m.groupValues[1]
        val chainStr = m.groupValues[2]
        val path = mutableListOf<String>()
        val chainRe = Regex("\\.(\\w+)|\\[['\"]?(\\w+)['\"]?\\]")
        for (cm in chainRe.findAll(chainStr)) {
            val grp = cm.groupValues[1].ifEmpty { cm.groupValues[2] }
            if (grp.isNotEmpty()) path.add(grp)
        }
        if (path.isNotEmpty()) aliases[varName] = path
    }
    return aliases
}

private fun findDictMaps(text: String, aliases: Map<String, List<String>>): Map<String, Map<String, List<String>>> {
    val result = mutableMapOf<String, Map<String, List<String>>>()
    val startRe = Regex("\\{%-?\\s*set\\s+(\\w+)\\s*=\\s*\\{")
    for (m in startRe.findAll(text)) {
        val varName = m.groupValues[1]
        val dictStart = m.range.last + 1
        val dictEnd = findMatchingBrace(text, dictStart)
        if (dictEnd < 0) continue
        val body = text.substring(dictStart, dictEnd)
        val map = parseDictBody(body, aliases)
        if (map.isNotEmpty()) result[varName] = map
    }
    return result
}

private fun findMatchingBrace(text: String, start: Int): Int {
    var depth = 1
    var inStr: Char? = null
    var i = start
    while (i < text.length) {
        val c = text[i]
        if (inStr != null) {
            if (c == inStr && (i == 0 || text[i - 1] != '\\')) inStr = null
        } else {
            when (c) {
                '"', '\'' -> inStr = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        i++
    }
    return -1
}

private fun parseDictBody(body: String, aliases: Map<String, List<String>>): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    val entryRe = Regex("['\"]?(\\w+)['\"]?\\s*:\\s*(\\w+(?:\\.\\w+|\\[['\"]?\\w+['\"]?\\])*)")
    for (m in entryRe.findAll(body)) {
        val key = m.groupValues[1]
        val expr = m.groupValues[2]
        val resolved = resolveChainExpr(expr, aliases) ?: continue
        result[key] = resolved
    }
    return result
}

private fun resolveChainExpr(expr: String, aliases: Map<String, List<String>>): List<String>? {
    val partsRe = Regex("^(\\w+)((?:\\.\\w+|\\[['\"]?\\w+['\"]?\\])*)$")
    val m = partsRe.matchEntire(expr) ?: return null
    val head = m.groupValues[1]
    val tail = m.groupValues[2]
    val tailPath = mutableListOf<String>()
    val chainRe = Regex("\\.(\\w+)|\\[['\"]?(\\w+)['\"]?\\]")
    for (cm in chainRe.findAll(tail)) {
        val grp = cm.groupValues[1].ifEmpty { cm.groupValues[2] }
        if (grp.isNotEmpty()) tailPath.add(grp)
    }
    if (head == "pillar") return tailPath
    val aliasPath = aliases[head] ?: return null
    return aliasPath + tailPath
}

private fun findJinjaImports(text: String): Map<String, String> {
    val imports = mutableMapOf<String, String>()
    val re = Regex("\\{%-?\\s*from\\s+[\"']([^\"']+)[\"']\\s+import\\s+([\\w,\\s]+?)(?:\\s+with\\s+context)?\\s*-?%\\}")
    for (m in re.findAll(text)) {
        val fromPath = m.groupValues[1]
        val names = m.groupValues[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (name in names) imports[name] = fromPath
    }
    return imports
}

private fun resolveImport(importPath: String, stateRoots: List<String>, files: List<File>): File? {
    val fileSet = files.associateBy { it.absolutePath }
    for (root in stateRoots) {
        val candidate = File(root, importPath).absolutePath
        fileSet[candidate]?.let { return it }
    }
    return null
}

private fun getStateName(filePath: String, stateRoots: List<String>): String? {
    for (root in stateRoots) {
        if (filePath.startsWith(root + File.separator)) {
            val rel = filePath.substring(root.length + 1)
            val firstSep = rel.indexOf(File.separator)
            return if (firstSep >= 0) rel.substring(0, firstSep)
            else rel.substringBeforeLast('.')
        }
    }
    return null
}
