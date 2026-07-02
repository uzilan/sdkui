package com.sdkui.viewmodel

import com.sdkui.FakeSdkmanService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
}
