package com.reader.feature.translation

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModelTest {
    private val engine = mockk<TranslationEngine>()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun successful_translation_emits_loading_then_result() = runTest {
        coEvery { engine.ensureModelsReady() } returns Result.success(Unit)
        coEvery { engine.translate("dog") } returns Result.success("собака")
        val vm = TranslationViewModel(engine)
        vm.popupState.test {
            assertNull(awaitItem())                       // initial
            vm.onTextSelected("dog")
            assertEquals(TranslationPopupState.Loading, awaitItem())
            assertEquals(TranslationPopupState.Result("dog", "собака"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun model_download_failure_emits_error() = runTest {
        coEvery { engine.ensureModelsReady() } returns Result.failure(RuntimeException("no network"))
        val vm = TranslationViewModel(engine)
        vm.popupState.test {
            assertNull(awaitItem())
            vm.onTextSelected("dog")
            assertEquals(TranslationPopupState.Loading, awaitItem())
            val state = awaitItem()
            assert(state is TranslationPopupState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blank_text_is_ignored() = runTest {
        val vm = TranslationViewModel(engine)
        vm.popupState.test {
            assertNull(awaitItem())
            vm.onTextSelected("   ")
            expectNoEvents()
        }
    }
}
