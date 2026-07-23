package com.jarvis.os.app.core.deployment

import com.jarvis.os.app.data.model.SecuritySeverity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SecurityScannerTest {

    private lateinit var tempDir: File
    private val scanner = SecurityScanner()

    @Before
    fun setUp() {
        tempDir = File.createTempFile("security_scanner_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun extractedFile(relativePath: String, content: String): ExtractedFile {
        val file = File(tempDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return ExtractedFile(relativePath, file, file.length(), gitBlobSha1 = "irrelevant-for-this-test")
    }

    @Test
    fun `a private key block is a blocking finding`() = runTest {
        val file = extractedFile("keys/id_rsa", "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA\n-----END RSA PRIVATE KEY-----")
        val findings = scanner.scan(listOf(file))
        assertTrue(findings.any { it.severity == SecuritySeverity.BLOCKING && it.message.contains("private key", ignoreCase = true) })
    }

    @Test
    fun `an AWS access key id is a blocking finding`() = runTest {
        val file = extractedFile("config/prod.env", "AWS_KEY=AKIAABCDEFGHIJKLMNOP")
        val findings = scanner.scan(listOf(file))
        assertTrue(findings.any { it.severity == SecuritySeverity.BLOCKING && it.path == "config/prod.env" })
    }

    @Test
    fun `a hardcoded password assignment is a warning, not blocking`() = runTest {
        val file = extractedFile("src/db.py", "password = \"SuperSecretValue123\"")
        val findings = scanner.scan(listOf(file))
        assertTrue(findings.any { it.severity == SecuritySeverity.WARNING && it.message.contains("password", ignoreCase = true) })
    }

    @Test
    fun `ordinary source code produces no findings`() = runTest {
        val file = extractedFile("src/main.kt", "fun main() { println(\"hello world\") }")
        val findings = scanner.scan(listOf(file))
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a ssh directory path is a blocking finding regardless of content`() = runTest {
        val file = extractedFile(".ssh/id_rsa.pub", "ssh-rsa AAAAB3NzaC1yc2EA not-actually-sensitive")
        val findings = scanner.scan(listOf(file))
        assertTrue(findings.any { it.severity == SecuritySeverity.BLOCKING && it.path == ".ssh/id_rsa.pub" })
    }

    @Test
    fun `an unexpected exe file is flagged as a warning`() = runTest {
        val file = extractedFile("tools/setup.exe", "not real binary content, just a test placeholder")
        val findings = scanner.scan(listOf(file))
        assertTrue(findings.any { it.severity == SecuritySeverity.WARNING && it.path == "tools/setup.exe" })
    }

    @Test
    fun `a large non-text file is flagged as a warning`() = runTest {
        val file = File(tempDir, "assets/large.bin").apply { parentFile?.mkdirs() }
        file.writeBytes(ByteArray(11 * 1024 * 1024))
        val extracted = ExtractedFile("assets/large.bin", file, file.length(), gitBlobSha1 = "irrelevant")
        val findings = scanner.scan(listOf(extracted))
        assertTrue(findings.any { it.severity == SecuritySeverity.WARNING && it.message.contains("Large binary") })
    }
}
