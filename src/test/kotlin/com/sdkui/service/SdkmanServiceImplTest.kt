package com.sdkui.service

import com.sdkui.model.VersionStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdkmanServiceImplTest {
    private val sdkmanInit = File(System.getProperty("user.home"), ".sdkman/bin/sdkman-init.sh")

    @Test
    fun `parseCandidates extracts name and version from sdk list output`() {
        val raw = """
            ================================================================================
            Available Candidates
            ================================================================================
            q-quit                                                      /-search Down-j Up-k

             Java (21.0.11-tem)                                              (j) java

             $ sdk install java

             Kotlin (2.1.20)                                               (k) kotlin

             $ sdk install kotlin
        """.trimIndent()
        val result = SdkmanServiceImpl.parseCandidates(raw)
        assertEquals(2, result.size)
        assertEquals("java", result[0].name)
        assertEquals("21.0.11-tem", result[0].version)
        assertEquals("kotlin", result[1].name)
        assertEquals("2.1.20", result[1].version)
    }

    @Test
    fun `parseDefaults extracts candidate-to-identifier map`() {
        val raw = """
            Using:

            java: 21.0.11-tem
            kotlin: 2.1.20
            gradle: 8.13
        """.trimIndent()
        val result = SdkmanServiceImpl.parseDefaults(raw)
        assertEquals(mapOf("java" to "21.0.11-tem", "kotlin" to "2.1.20", "gradle" to "8.13"), result)
    }

    @Test
    fun `parseDefaults returns empty map when nothing is in use`() {
        val raw = "No candidates are in use currently."
        assertEquals(emptyMap(), SdkmanServiceImpl.parseDefaults(raw))
    }

    @Test
    fun `parseJavaVersions extracts vendor, version, and identifier`() {
        val raw = """
            ================================================================================
            Available Java Versions for macOS ARM 64bit
            ================================================================================
             Vendor        | Use | Version      | Dist    | Status     | Identifier
            --------------------------------------------------------------------------------
             Temurin       |     | 21.0.11      | tem     |            | 21.0.11-tem
             Temurin       |     | 17.0.15      | tem     | installed  | 17.0.15-tem
             Corretto      |     | 21.0.11      | amzn    |            | 21.0.11-amzn
                           |     |              |         |            |
        """.trimIndent()
        val all = SdkmanServiceImpl.parseJavaVersions(raw, vendor = null)
        assertEquals(3, all.size)
        assertEquals("Temurin", all[0].vendor)
        assertEquals("21.0.11-tem", all[0].identifier)
        assertEquals(VersionStatus.AVAILABLE, all[0].status)

        val temurin = SdkmanServiceImpl.parseJavaVersions(raw, vendor = "Temurin")
        assertEquals(2, temurin.size)
        assertTrue(temurin.all { it.vendor == "Temurin" })
    }

    @Test
    fun `parseGenericVersions extracts version tokens`() {
        val raw = """
            ================================================================================
            Available Kotlin Versions
            ================================================================================
                 2.1.20             2.0.21             2.0.0             1.9.25
                 1.9.24             1.9.23
        """.trimIndent()
        val result = SdkmanServiceImpl.parseGenericVersions(raw)
        assertEquals(6, result.size)
        assertEquals("2.1.20", result[0].number)
        assertEquals("2.1.20", result[0].identifier)
        assertEquals(VersionStatus.AVAILABLE, result[0].status)
    }

    @Test
    fun `integration - listCandidates returns non-empty list`() = runTest {
        Assumptions.assumeTrue(sdkmanInit.exists(), "SDKMAN not installed — skipping integration test")
        val service = SdkmanServiceImpl()
        val result = service.listCandidates()
        assertTrue(result.isSuccess, "listCandidates failed: ${result.exceptionOrNull()?.message}")
        assertTrue(result.getOrThrow().isNotEmpty())
        assertTrue(result.getOrThrow().any { it.name == "java" })
    }

    @Test
    fun `integration - getCurrentDefaults returns map`() = runTest {
        Assumptions.assumeTrue(sdkmanInit.exists(), "SDKMAN not installed — skipping integration test")
        val service = SdkmanServiceImpl()
        val result = service.getCurrentDefaults()
        assertTrue(result.isSuccess, "getCurrentDefaults failed: ${result.exceptionOrNull()?.message}")
    }
}
