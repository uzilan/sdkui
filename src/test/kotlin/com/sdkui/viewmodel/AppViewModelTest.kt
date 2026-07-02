package com.sdkui.viewmodel

import com.sdkui.FakeSdkmanService
import com.sdkui.model.Overlay
import com.sdkui.model.VersionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @Test
    fun `initial state is empty`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        with(vm.state.value) {
            assertEquals(emptyList(), candidates)
            assertNull(selectedCandidate)
            assertFalse(loading)
        }
    }

    @Test
    fun `loadCandidatesAndDefaults populates state`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        advanceUntilIdle()
        with(vm.state.value) {
            assertEquals(FakeSdkmanService.CANDIDATES, candidates)
            assertEquals(FakeSdkmanService.DEFAULTS, currentDefaults)
            assertFalse(loading)
        }
    }

    @Test
    fun `loadCandidatesAndDefaults on failure sets statusMessage`() = runTest {
        val fake = FakeSdkmanService().apply {
            candidatesResult = Result.failure(RuntimeException("sdk not found"))
        }
        val vm = AppViewModel(fake, this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        advanceTimeBy(1) // run the failure coroutine (no delay), but not the 3s status clear
        assertTrue(vm.state.value.statusMessage.contains("sdk not found"))
    }

    @Test
    fun `setStatusMessage clears after 3 seconds`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.setStatusMessage("hello")
        assertEquals("hello", vm.state.value.statusMessage)
        advanceTimeBy(3_001)
        assertEquals("", vm.state.value.statusMessage)
    }

    @Test
    fun `selectCandidate loads versions and auto-selects first`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        advanceUntilIdle()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        with(vm.state.value) {
            assertEquals(FakeSdkmanService.CANDIDATES[0], selectedCandidate)
            assertEquals(10, versions.size)
            assertEquals(versions[0], selectedVersion)
            assertFalse(loading)
        }
    }

    @Test
    fun `loadVersions assigns DEFAULT status from currentDefaults`() = runTest {
        val root = Files.createTempDirectory("sdkui-test").toFile()
        val vm = AppViewModel(FakeSdkmanService(), this, root.absolutePath)
        vm.loadCandidatesAndDefaults()   // sets currentDefaults["java"] = "21.0.11-tem"
        advanceUntilIdle()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        val java21 = vm.state.value.versions.first { it.identifier == "21.0.11-tem" }
        assertEquals(VersionStatus.DEFAULT, java21.status)
    }

    @Test
    fun `loadVersions assigns INSTALLED status from filesystem`() = runTest {
        val root = Files.createTempDirectory("sdkui-test").toFile()
        File(root, "candidates/java/25.0.3-tem").mkdirs()
        val vm = AppViewModel(FakeSdkmanService(), this, root.absolutePath)
        vm.loadCandidatesAndDefaults()
        advanceUntilIdle()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        val java25 = vm.state.value.versions.first { it.identifier == "25.0.3-tem" }
        assertEquals(VersionStatus.INSTALLED, java25.status)
    }

    @Test
    fun `selectVendor filters versions by vendor`() = runTest {
        val fake = FakeSdkmanService()
        val vm = AppViewModel(fake, this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        fake.versionsResult = Result.success(FakeSdkmanService.JAVA_VERSIONS.filter { it.vendor == "Corretto" })
        vm.selectVendor("Corretto")
        advanceUntilIdle()
        assertEquals("Corretto", vm.state.value.selectedVendor)
        assertTrue(vm.state.value.versions.all { it.vendor == "Corretto" })
    }

    @Test
    fun `selectVersion updates selectedVersion`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        val target = vm.state.value.versions[3]
        vm.selectVersion(target)
        assertEquals(target, vm.state.value.selectedVersion)
    }

    @Test
    fun `loadVersions on error sets statusMessage`() = runTest {
        val fake = FakeSdkmanService().apply {
            versionsResult = Result.failure(RuntimeException("parse error"))
        }
        val vm = AppViewModel(fake, this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceTimeBy(1) // run the failure coroutine (no delay), but not the 3s status clear
        assertTrue(vm.state.value.statusMessage.contains("parse error"))
    }

    @Test
    fun `installSelected streams progress and refreshes versions`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        vm.installSelected()
        advanceUntilIdle()
        // overlay dismissed after install
        assertNull(vm.state.value.overlay)
        // versions reloaded
        assertEquals(10, vm.state.value.versions.size)
    }

    @Test
    fun `installSelected does nothing when no version selected`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.installSelected()
        advanceUntilIdle()
        assertNull(vm.state.value.overlay)
    }

    @Test
    fun `setDefaultSelected updates currentDefaults and reloads versions`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.loadCandidatesAndDefaults()
        vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
        advanceUntilIdle()
        val newDefault = vm.state.value.versions.first { it.identifier == "26.0.1-tem" }
        vm.selectVersion(newDefault)
        vm.setDefaultSelected()
        advanceUntilIdle()
        assertEquals("26.0.1-tem", vm.state.value.currentDefaults["java"])
        assertEquals(VersionStatus.DEFAULT, vm.state.value.versions.first { it.identifier == "26.0.1-tem" }.status)
    }

    @Test
    fun `closeOverlay clears overlay`() = runTest {
        val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
        vm.openProgress("test")
        assertTrue(vm.state.value.overlay is Overlay.Progress)
        vm.closeOverlay()
        assertNull(vm.state.value.overlay)
    }
}
