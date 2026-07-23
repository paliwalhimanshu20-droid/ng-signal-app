package com.jarvis.os.app.core.deployment

import com.jarvis.os.app.data.model.SecurityFinding
import com.jarvis.os.app.data.model.SecuritySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASDP-001 Phase 5/16 "Security Review" -- "Detect: Secrets, API keys,
 * passwords, private keys, large binaries, unexpected executables,
 * dangerous file locations. Surface warnings to the owner before
 * publication."
 *
 * Only scans ADDED and MODIFIED files, deliberately -- an UNCHANGED
 * file is, by definition, already live in the target repository
 * (ChangeAnalyzer only marks something UNCHANGED when its git blob
 * hash matches what's already on the branch), so re-flagging it here
 * would be re-litigating something already accepted, not reviewing
 * this publish. DELETED files have no local content to scan.
 *
 * Pattern matching, not a real secret-scanning service (no network
 * call, no third-party API) -- same "heuristic, not certain" honesty
 * ASDP-001's own feasibility doc asks Phase 2 to state about itself.
 * False negatives are possible (a secret that doesn't match any
 * pattern here); false positives are possible (a 40-character hex
 * string that happens to look like a key but isn't). This scanner errs
 * toward flagging, and every BLOCKING finding is something the owner
 * can review and can still choose to investigate the file directly --
 * it isn't a silent auto-delete.
 */
@Singleton
class SecurityScanner @Inject constructor() {

    suspend fun scan(candidateFiles: List<ExtractedFile>): List<SecurityFinding> = withContext(Dispatchers.IO) {
        val findings = mutableListOf<SecurityFinding>()

        for (file in candidateFiles) {
            findings += scanPath(file.relativePath)
            findings += scanSize(file)

            if (looksLikeText(file.relativePath) && file.sizeBytes in 1..MAX_TEXT_SCAN_BYTES) {
                findings += scanContent(file)
            }
        }

        findings
    }

    private fun scanPath(path: String): List<SecurityFinding> {
        val lower = path.lowercase()
        val findings = mutableListOf<SecurityFinding>()

        val executableExtensions = listOf(".exe", ".dll", ".so", ".apk", ".bat", ".cmd", ".msi", ".app")
        if (executableExtensions.any { lower.endsWith(it) }) {
            findings += SecurityFinding(path, SecuritySeverity.WARNING, "Unexpected executable/binary file type ('${lower.substringAfterLast('.')}') -- review before publishing.")
        }
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) {
            findings += SecurityFinding(path, SecuritySeverity.INFO, "Shell script -- confirm this is expected in the package.")
        }
        if (lower.startsWith(".github/workflows/") && (lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
            findings += SecurityFinding(path, SecuritySeverity.INFO, "Modifies a GitHub Actions workflow -- this changes what runs automatically on this repository.")
        }
        val dangerousLocations = listOf(".ssh/", ".aws/credentials", ".aws/config", ".npmrc", ".pypirc", ".netrc")
        if (dangerousLocations.any { lower.contains(it) }) {
            findings += SecurityFinding(path, SecuritySeverity.BLOCKING, "Path matches a well-known credential-storage location -- this archive should not be publishing to it.")
        }
        return findings
    }

    private fun scanSize(file: ExtractedFile): List<SecurityFinding> {
        if (file.sizeBytes > LARGE_BINARY_WARNING_BYTES && !looksLikeText(file.relativePath)) {
            val mb = file.sizeBytes / (1024 * 1024)
            return listOf(SecurityFinding(file.relativePath, SecuritySeverity.WARNING, "Large binary file (${mb} MB) -- confirm this belongs in source control."))
        }
        return emptyList()
    }

    private fun scanContent(file: ExtractedFile): List<SecurityFinding> {
        val text = runCatching { file.absolutePath.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        val findings = mutableListOf<SecurityFinding>()

        if (PRIVATE_KEY_HEADER.containsMatchIn(text)) {
            findings += SecurityFinding(file.relativePath, SecuritySeverity.BLOCKING, "Contains a private key block (PEM header detected).")
        }
        if (AWS_ACCESS_KEY.containsMatchIn(text)) {
            findings += SecurityFinding(file.relativePath, SecuritySeverity.BLOCKING, "Contains what looks like an AWS access key ID.")
        }
        if (GITHUB_TOKEN.containsMatchIn(text)) {
            findings += SecurityFinding(file.relativePath, SecuritySeverity.BLOCKING, "Contains what looks like a GitHub personal access token.")
        }
        if (GENERIC_SECRET_ASSIGNMENT.containsMatchIn(text)) {
            findings += SecurityFinding(file.relativePath, SecuritySeverity.WARNING, "Contains what looks like a hardcoded password/secret/API key assignment -- review before publishing.")
        }
        if (SLACK_TOKEN.containsMatchIn(text)) {
            findings += SecurityFinding(file.relativePath, SecuritySeverity.BLOCKING, "Contains what looks like a Slack API token.")
        }

        return findings
    }

    private fun looksLikeText(path: String): Boolean {
        val binaryExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "zip", "jar", "apk", "so", "dll", "exe", "ttf", "otf", "woff", "woff2", "mp3", "mp4", "mov", "pdf", "class")
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext !in binaryExtensions
    }

    companion object {
        private const val LARGE_BINARY_WARNING_BYTES = 10L * 1024 * 1024
        private const val MAX_TEXT_SCAN_BYTES = 5L * 1024 * 1024

        private val PRIVATE_KEY_HEADER = Regex("-----BEGIN (RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----")
        private val AWS_ACCESS_KEY = Regex("AKIA[0-9A-Z]{16}")
        private val GITHUB_TOKEN = Regex("(ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]{20,}")
        private val SLACK_TOKEN = Regex("xox[baprs]-[A-Za-z0-9-]{10,}")
        private val GENERIC_SECRET_ASSIGNMENT = Regex(
            "(?i)(api[_-]?key|secret|password|passwd|token)\\s*[:=]\\s*['\"][A-Za-z0-9+/_\\-]{12,}['\"]",
        )
    }
}
