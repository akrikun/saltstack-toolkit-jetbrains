package com.akrikun.saltstack.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.akrikun.saltstack.SaltIcons

class SaltCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val doc = parameters.editor.document
                    val offset = parameters.offset
                    val lineNum = doc.getLineNumber(offset)
                    val lineStart = doc.getLineStartOffset(lineNum)
                    val currentLine = doc.getText(TextRange(lineStart, offset))
                    val isSls = parameters.originalFile.language == com.akrikun.saltstack.SlsLanguage

                    // === Jinja-context completions (work in any supported language) ===

                    // Inside salt['...'] or salt. — suggest exec modules
                    if (currentLine.contains("salt[") || currentLine.contains("salt.")) {
                        SALT_EXEC_MODULES.forEach { m ->
                            result.addElement(
                                LookupElementBuilder.create(m)
                                    .withIcon(SaltIcons.SLS_FILE)
                                    .withTypeText("execution module"),
                            )
                        }
                        return
                    }

                    // After "pillar.", "grains.", "sdb.", "defaults." — suggest known funcs
                    val dotMatch = DOT_MATCH_REGEX.find(currentLine)
                    if (dotMatch != null) {
                        DOT_METHODS[dotMatch.groupValues[1]]?.forEach { (name, detail) ->
                            result.addElement(
                                LookupElementBuilder.create(name)
                                    .withIcon(SaltIcons.SLS_FILE)
                                    .withTypeText(detail),
                            )
                        }
                        return
                    }

                    // === SLS-only completions below ===
                    if (!isSls) return

                    // If we're inside an indented "- " entry, suggest module params
                    val parentModule = findParentStateModule(doc, lineNum)
                    if (parentModule != null && currentLine.matches(Regex("^\\s+-\\s*\\w*\$"))) {
                        STATE_PARAMS[parentModule]?.forEach { p ->
                            result.addElement(
                                LookupElementBuilder.create(p)
                                    .withIcon(SaltIcons.SLS_FILE)
                                    .withTypeText("$parentModule param"),
                            )
                        }
                        REQUISITES.forEach { r ->
                            result.addElement(
                                LookupElementBuilder.create(r)
                                    .withIcon(SaltIcons.SLS_FILE)
                                    .withTypeText("requisite"),
                            )
                        }
                        return
                    }

                    // Default: state modules + exec modules
                    for ((module, doc2) in STATE_MODULES) {
                        result.addElement(
                            LookupElementBuilder.create(module)
                                .withIcon(SaltIcons.SLS_FILE)
                                .withTypeText("state module")
                                .withTailText("  $doc2", true),
                        )
                    }
                    SALT_EXEC_MODULES.forEach { m ->
                        result.addElement(
                            LookupElementBuilder.create(m)
                                .withIcon(SaltIcons.SLS_FILE)
                                .withTypeText("execution module"),
                        )
                    }
                }
            },
        )
    }

    private fun findParentStateModule(doc: com.intellij.openapi.editor.Document, lineNum: Int): String? {
        val moduleRe = Regex("^\\s+([\\w]+\\.[\\w]+):\\s*\$")
        for (i in (lineNum - 1) downTo 0) {
            val text = doc.getText(TextRange(doc.getLineStartOffset(i), doc.getLineEndOffset(i)))
            if (text.isBlank()) continue
            if (text.matches(Regex("^[a-zA-Z].*"))) return null
            val m = moduleRe.matchEntire(text)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    companion object {
        // Precompiled — `addCompletions` runs on every keystroke, so we don't
        // want to re-allocate this regex every time.
        private val DOT_MATCH_REGEX = Regex("(pillar|grains|sdb|defaults)\\.\\w*$")

        // Methods suggested after `pillar.`, `grains.`, `sdb.`, `defaults.` in any context.
        val DOT_METHODS: Map<String, List<Pair<String, String>>> = mapOf(
            "pillar" to listOf(
                "get" to "Get a pillar value with optional default",
                "items" to "Get all pillar items",
                "keys" to "List pillar keys",
                "raw" to "Get raw pillar dict",
            ),
            "grains" to listOf(
                "get" to "Get a grains value with default",
                "filter_by" to "Pick a value based on grain match",
                "items" to "Get all grains",
            ),
            "sdb" to listOf(
                "get" to "Retrieve value from SDB backend",
                "set" to "Set value in SDB backend",
                "get_or_set_hash" to "Auto-generate and store secret",
            ),
            "defaults" to listOf(
                "merge" to "Deep-merge two dicts (used in map.jinja)",
                "get" to "Get a defaults value",
            ),
        )

        val STATE_MODULES = mapOf(
            "file.managed" to "Manage a file",
            "file.directory" to "Manage a directory",
            "file.absent" to "Ensure file/dir absent",
            "file.symlink" to "Create symlink",
            "file.recurse" to "Recursively deploy a directory",
            "file.append" to "Append text to file",
            "file.replace" to "Replace pattern in file",
            "file.copy" to "Copy file or dir",
            "file.serialize" to "Serialize data to file",
            "file.blockreplace" to "Manage block of text",
            "file.comment" to "Comment a line",
            "file.uncomment" to "Uncomment a line",
            "file.tidied" to "Remove old files",
            "pkg.installed" to "Ensure package installed",
            "pkg.latest" to "Ensure latest version",
            "pkg.removed" to "Ensure package removed",
            "pkg.purged" to "Ensure package purged",
            "pkgrepo.managed" to "Manage package repository",
            "pkgrepo.absent" to "Remove package repository",
            "service.running" to "Ensure service running",
            "service.dead" to "Ensure service stopped",
            "service.enabled" to "Ensure service enabled",
            "service.disabled" to "Ensure service disabled",
            "cmd.run" to "Run shell command",
            "cmd.script" to "Run a script",
            "cmd.wait" to "Run only when notified",
            "user.present" to "Ensure user exists",
            "user.absent" to "Ensure user absent",
            "group.present" to "Ensure group exists",
            "group.absent" to "Ensure group absent",
            "cron.present" to "Ensure cron job",
            "cron.absent" to "Remove cron job",
            "git.latest" to "Clone/pull git repo",
            "git.present" to "Ensure bare git repo",
            "pip.installed" to "Ensure pip package installed",
            "pip.removed" to "Ensure pip package removed",
            "archive.extracted" to "Extract archive",
            "module.run" to "Run execution module",
            "docker_container.running" to "Ensure container running",
            "docker_container.absent" to "Ensure container absent",
            "docker_container.stopped" to "Ensure container stopped",
            "docker_image.present" to "Ensure image present",
            "docker_image.absent" to "Ensure image absent",
            "docker_network.present" to "Ensure network present",
            "docker_volume.present" to "Ensure volume present",
            "mount.mounted" to "Ensure filesystem mounted",
            "mount.unmounted" to "Ensure filesystem unmounted",
            "test.nop" to "No-op state",
            "test.succeed_without_changes" to "Always succeeds",
            "test.fail_without_changes" to "Always fails",
            "grains.present" to "Set grains value",
            "grains.absent" to "Remove grains value",
            "grains.list_present" to "Ensure value in list",
            "environ.setenv" to "Set env variable",
            "ini.options_present" to "Manage INI file",
        )

        val REQUISITES = listOf(
            "require", "watch", "onchanges", "onfail", "prereq", "listen", "use",
            "require_in", "watch_in", "onchanges_in", "onfail_in", "prereq_in", "listen_in",
            "require_any", "watch_any", "onchanges_any", "onfail_any",
        )

        val SALT_EXEC_MODULES = listOf(
            "sdb.get", "sdb.get_or_set_hash", "sdb.set",
            "defaults.merge", "file.file_exists", "file.directory_exists",
            "grains.filter_by", "grains.get", "cmd.run", "cmd.run_all",
            "pillar.get", "saltutil.runner",
            "fast_yaml.hosts",
        )

        val STATE_PARAMS: Map<String, List<String>> = mapOf(
            "file.managed" to listOf(
                "name", "source", "source_hash", "template", "user", "group",
                "mode", "makedirs", "context", "defaults", "backup",
                "contents", "contents_pillar", "encoding",
            ),
            "file.directory" to listOf("name", "user", "group", "mode", "makedirs", "recurse", "clean"),
            "file.symlink" to listOf("name", "target", "force", "user", "group"),
            "file.recurse" to listOf(
                "name", "source", "user", "group", "dir_mode", "file_mode",
                "template", "clean", "include_empty",
            ),
            "file.append" to listOf("name", "text", "source", "template"),
            "file.replace" to listOf("name", "pattern", "repl", "count", "flags", "bufsize"),
            "pkg.installed" to listOf("name", "pkgs", "version", "refresh", "fromrepo", "skip_verify"),
            "pkg.latest" to listOf("name", "pkgs", "refresh"),
            "pkg.removed" to listOf("name", "pkgs"),
            "pkgrepo.managed" to listOf("name", "humanname", "key_url", "file", "dist", "comps"),
            "service.running" to listOf("name", "enable", "sig", "init_delay", "reload"),
            "service.dead" to listOf("name", "enable"),
            "cmd.run" to listOf(
                "name", "creates", "unless", "onlyif", "cwd", "runas", "env",
                "shell", "timeout", "output_loglevel",
            ),
            "cmd.script" to listOf("name", "source", "template", "cwd", "runas", "env"),
            "user.present" to listOf(
                "name", "uid", "gid", "home", "shell", "createhome",
                "groups", "optional_groups", "password",
            ),
            "user.absent" to listOf("name", "purge", "force"),
            "group.present" to listOf("name", "gid", "system", "members"),
            "cron.present" to listOf(
                "name", "user", "minute", "hour", "daymonth", "month", "dayweek",
                "comment", "identifier",
            ),
            "git.latest" to listOf(
                "name", "target", "rev", "branch", "force_reset",
                "identity", "user", "depth",
            ),
            "pip.installed" to listOf("name", "pkgs", "requirements", "bin_env", "upgrade"),
            "archive.extracted" to listOf(
                "name", "source", "source_hash", "user", "group",
                "if_missing", "enforce_toplevel", "options",
            ),
            "docker_container.running" to listOf(
                "name", "image", "port_bindings", "volumes", "environment",
                "restart_policy", "command", "network_mode", "hostname",
            ),
            "mount.mounted" to listOf("name", "device", "fstype", "opts", "mkmnt"),
            "ini.options_present" to listOf("name", "sections"),
        )
    }
}
