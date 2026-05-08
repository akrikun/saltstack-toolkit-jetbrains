package com.akrikun.saltstack.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class SaltDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = originalElement ?: element ?: return null
        val word = target.text ?: return null

        // Look up state module
        STATE_DOCS[word]?.let { return formatDoc(word, it) }
        EXEC_DOCS[word]?.let { return formatDoc(word, it) }
        REQUISITE_DOCS[word]?.let { return formatDoc(word, it) }
        BUILTIN_DOCS[word]?.let { return formatDoc(word, it) }

        // Try matching module.function patterns from neighbors
        val line = readLine(target.containingFile, target) ?: return null
        for ((key, doc) in STATE_DOCS) {
            if (line.contains("$key:")) return formatDoc(key, doc)
        }
        for ((key, doc) in EXEC_DOCS) {
            if (line.contains("salt['$key']") || line.contains("salt.$key")) return formatDoc(key, doc)
        }

        return null
    }

    private fun readLine(file: PsiFile?, element: PsiElement): String? {
        val doc = file?.viewProvider?.document ?: return null
        val offset = element.textOffset
        val lineNum = doc.getLineNumber(offset)
        return doc.getText(com.intellij.openapi.util.TextRange(
            doc.getLineStartOffset(lineNum),
            doc.getLineEndOffset(lineNum),
        ))
    }

    private fun formatDoc(name: String, body: String): String =
        "<b>$name</b><br><br>${body.replace("\n", "<br>")}"

    companion object {
        val STATE_DOCS = mapOf(
            "file.managed" to "Manage a file — download from salt:// or http://, apply template, set ownership/permissions.\n\nKey params: name, source, template, user, group, mode, makedirs, context",
            "file.directory" to "Ensure a directory exists with the correct ownership and permissions.\n\nKey params: name, user, group, mode, makedirs, recurse",
            "file.absent" to "Ensure a file or directory does not exist.\n\nKey params: name",
            "file.symlink" to "Create a symbolic link.\n\nKey params: name, target, force",
            "pkg.installed" to "Ensure a package is installed.\n\nKey params: name, pkgs, version, refresh, fromrepo",
            "service.running" to "Ensure a service is running.\n\nKey params: name, enable, sig, init_delay",
            "cmd.run" to "Run a shell command.\n\nKey params: name, creates, unless, onlyif, cwd, runas, env",
            "user.present" to "Ensure a user exists.\n\nKey params: name, uid, gid, home, shell, createhome, groups",
            "git.latest" to "Clone or pull the latest from a git repo.\n\nKey params: name, target, rev, branch, force_reset, identity",
            "docker_container.running" to "Ensure a Docker container is running.\n\nKey params: name, image, port_bindings, volumes, environment, restart_policy",
            "test.nop" to "No-op state. Useful as a requisite target or placeholder.",
        )

        val EXEC_DOCS = mapOf(
            "sdb.get" to "Retrieve a value from an SDB backend.\n\nUsage: salt['sdb.get']('sdb://profile/path/to/key')",
            "sdb.get_or_set_hash" to "Get or auto-generate a hash value in SDB.\n\nUseful for passwords and secrets.",
            "defaults.merge" to "Deep-merge two dictionaries.\n\nUseful for merging pillar data with defaults in map.jinja files.",
            "grains.filter_by" to "Select a value from a dict based on a grain.\n\nCommon grains: id, os, oscodename, osmajorrelease.",
            "pillar.get" to "Get a pillar value with optional default (supports nested keys with ':').",
            "fast_yaml.hosts" to "(Custom module) Load host metadata from YAML.\n\nReturns a dict of host entries.",
            "saltutil.runner" to "Execute a Salt runner function from a state/pillar.",
        )

        val REQUISITE_DOCS = mapOf(
            "require" to "This state will not run until the required state completes successfully.",
            "watch" to "Like require, but also triggers a restart/reload when the watched state changes.",
            "onchanges" to "This state runs only if the dependency state made changes.",
            "onfail" to "This state runs only if the dependency state fails.",
            "prereq" to "Runs this state before the dependency if the dependency would make changes.",
            "listen" to "Like watch, but the triggered action runs at the end of the state run.",
        )

        val BUILTIN_DOCS = mapOf(
            "salt" to "Dictionary of Salt execution modules. Usage: salt['module.function'](args)",
            "pillar" to "Dictionary of Pillar data for this minion. Usage: pillar.key, pillar['key']",
            "grains" to "Dictionary of minion Grains (system info). Common keys: id, os, oscodename.",
            "opts" to "Dictionary of Salt minion configuration options.",
            "mine" to "Access Salt Mine data (data shared between minions).",
        )
    }
}
