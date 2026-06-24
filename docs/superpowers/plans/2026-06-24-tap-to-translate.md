# Tap-to-Translate Implementation Plan (Plan 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a word (or selecting a phrase) in the reader shows an offline English→Ukrainian translation in a compact popover.

**Architecture:** A new `:feature:translation` module owns translation: a `TranslationEngine` interface, an ML Kit implementation, a `TranslationViewModel` state machine, and a `TranslationPopover` Composable — all Readium-free and unit-testable via a fake engine. `:feature:reader` gains a JavaScript bridge into the Readium navigator WebView that resolves the tapped word (and uses Readium's native selection for phrases), disables tap-to-turn (swipe still pages), and drives the popover.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Coroutines, ML Kit Translate (`com.google.mlkit:translate`), `kotlinx-coroutines-play-services` (for `.await()` on ML Kit Tasks), Readium 3.1.2. Test: JUnit4, MockK, Turbine, kotlinx-coroutines-test.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin only, Compose + Material 3. MVVM + repository, unidirectional Flow, Hilt DI.
- ALL dependency versions via the catalog `gradle/libs.versions.toml`; no hardcoded versions in module build files.
- New module `:feature:translation` (namespace `com.reader.feature.translation`). Reader integration lives in the existing `:feature:reader`.
- Translation is OFFLINE only (ML Kit). The `TranslationEngine` interface must be the only thing callers depend on, so a future DeepL engine slots in without changing callers. No API keys / secrets in this plan.
- NO persistence, NO dictionary data, NO save-word UI, NO theme work in this plan (deferred to Plans 3/4).
- `flowOf` mock ViewModel tests: drain Turbine completion with `cancelAndIgnoreRemainingEvents()` after assertions; add `@OptIn(ExperimentalCoroutinesApi::class)` to test classes using `Dispatchers.setMain` for pristine output.
- Verify exact Readium 3.1.2 APIs (input/tap listener, JS evaluation/injection, `currentSelection`, disabling tap navigation) and the current ML Kit Translate artifact version via context7 at implementation time — do not hardcode guesses; pin in the catalog.
- Modules that compile against Readium need `isCoreLibraryDesugaringEnabled` (already enabled in `:feature:reader`).

---

### Task 1: `:feature:translation` module + interface + types

**Files:**
- Create: `feature/translation/build.gradle.kts`
- Create: `feature/translation/src/main/AndroidManifest.xml`
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/TranslationEngine.kt`
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/TranslationPopupState.kt`
- Modify: `settings.gradle.kts` (include `:feature:translation`)
- Modify: `gradle/libs.versions.toml` (ML Kit + coroutines-play-services aliases)

**Interfaces:**
- Produces:
  - `interface TranslationEngine { suspend fun ensureModelsReady(): Result<Unit>; suspend fun translate(text: String): Result<String> }`
  - `sealed interface TranslationPopupState { data object Loading : TranslationPopupState; data class Result(val source: String, val translation: String) : TranslationPopupState; data class Error(val message: String) : TranslationPopupState }`

- [ ] **Step 1: Create the module build file**

`feature/translation/build.gradle.kts` applies android-library + kotlin + ksp + hilt + compose plugins, `namespace = "com.reader.feature.translation"`, `minSdk 26`, `compileSdk 36`, depends on Hilt, Coroutines, Compose BOM + Material3 (for the popover, added in Task 3), ML Kit translate, and `kotlinx-coroutines-play-services`. Mirror an existing feature module's plugin/exclusion setup (`feature/library/build.gradle.kts`). Test deps: junit4, mockk, turbine, kotlinx-coroutines-test. All via catalog aliases.

- [ ] **Step 2: Add catalog aliases**

In `gradle/libs.versions.toml` add (verify current stable versions via context7 — ML Kit translate is typically `17.0.x`, play-services-coroutines tracks the coroutines version):
```
mlkitTranslate = "17.0.3"
[libraries]
mlkit-translate = { module = "com.google.mlkit:translate", version.ref = "mlkitTranslate" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
```
(Reuse the existing `coroutines` version ref for play-services.)

- [ ] **Step 3: Register the module**

Add `include(":feature:translation")` to `settings.gradle.kts`.

- [ ] **Step 4: Define the interface and state types**

`TranslationEngine.kt`:
```kotlin
package com.reader.feature.translation

interface TranslationEngine {
    /** Ensure the EN→UK models are downloaded and ready. */
    suspend fun ensureModelsReady(): Result<Unit>

    /** Translate English [text] to Ukrainian. Assumes models are ready. */
    suspend fun translate(text: String): Result<String>
}
```

`TranslationPopupState.kt`:
```kotlin
package com.reader.feature.translation

sealed interface TranslationPopupState {
    data object Loading : TranslationPopupState
    data class Result(val source: String, val translation: String) : TranslationPopupState
    data class Error(val message: String) : TranslationPopupState
}
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew :feature:translation:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/translation settings.gradle.kts gradle/libs.versions.toml
git commit -m "feat: add translation module with engine interface and popup state"
```

---

### Task 2: ML Kit translation engine

**Files:**
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/MlKitTranslationEngine.kt`
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/di/TranslationModule.kt`

**Interfaces:**
- Consumes: `TranslationEngine` (Task 1).
- Produces: `class MlKitTranslationEngine @Inject constructor() : TranslationEngine`, bound to `TranslationEngine` in `TranslationModule`.

- [ ] **Step 1: Implement the engine**

ML Kit Translate is a thin, device-dependent wrapper (no unit test — verified by build + the on-device smoke test in Task 6). Implement EN→UK with a reused client. Verify the exact ML Kit API names via context7 before finalizing.

`MlKitTranslationEngine.kt`:
```kotlin
package com.reader.feature.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTranslationEngine @Inject constructor() : TranslationEngine {

    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.UKRAINIAN)
                .build(),
        )
    }

    override suspend fun ensureModelsReady(): Result<Unit> = runCatching {
        translator.downloadModelIfNeeded().await()
    }

    override suspend fun translate(text: String): Result<String> = runCatching {
        translator.translate(text).await()
    }
}
```

- [ ] **Step 2: Bind via Hilt**

`TranslationModule.kt`:
```kotlin
package com.reader.feature.translation.di

import com.reader.feature.translation.MlKitTranslationEngine
import com.reader.feature.translation.TranslationEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {
    @Binds
    @Singleton
    abstract fun bindTranslationEngine(impl: MlKitTranslationEngine): TranslationEngine
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :feature:translation:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/translation
git commit -m "feat: add ml kit en-uk translation engine"
```

---

### Task 3: TranslationViewModel state machine (TDD)

**Files:**
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/TranslationViewModel.kt`
- Test: `feature/translation/src/test/kotlin/com/reader/feature/translation/TranslationViewModelTest.kt`

**Interfaces:**
- Consumes: `TranslationEngine`, `TranslationPopupState` (Task 1).
- Produces: `@HiltViewModel class TranslationViewModel @Inject constructor(engine: TranslationEngine)`:
  - `val popupState: StateFlow<TranslationPopupState?>` (null = no popover shown)
  - `fun onTextSelected(text: String)` — blank/whitespace text is ignored (stays null); otherwise emits `Loading`, ensures models, translates, emits `Result` or `Error`.
  - `fun dismiss()` — sets state back to null.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:translation:testDebugUnitTest`
Expected: FAIL — `TranslationViewModel` unresolved.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.reader.feature.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val engine: TranslationEngine,
) : ViewModel() {

    private val _popupState = MutableStateFlow<TranslationPopupState?>(null)
    val popupState: StateFlow<TranslationPopupState?> = _popupState.asStateFlow()

    fun onTextSelected(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _popupState.value = TranslationPopupState.Loading
        viewModelScope.launch {
            val ready = engine.ensureModelsReady()
            if (ready.isFailure) {
                _popupState.value = TranslationPopupState.Error(
                    "Translation needs a one-time download. Connect to the internet and tap again.",
                )
                return@launch
            }
            engine.translate(trimmed)
                .onSuccess { _popupState.value = TranslationPopupState.Result(trimmed, it) }
                .onFailure { _popupState.value = TranslationPopupState.Error("Couldn't translate. Try again.") }
        }
    }

    fun dismiss() {
        _popupState.value = null
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:translation:testDebugUnitTest`
Expected: PASS (3 tests, pristine output).

- [ ] **Step 5: Commit**

```bash
git add feature/translation
git commit -m "feat: add translation view model state machine"
```

---

### Task 4: TranslationPopover Composable

**Files:**
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/TranslationPopover.kt`

**Interfaces:**
- Consumes: `TranslationPopupState` (Task 1).
- Produces: `@Composable fun TranslationPopover(state: TranslationPopupState, onDismiss: () -> Unit, modifier: Modifier = Modifier)` — a compact card rendering Loading (spinner), Result (source text + Ukrainian translation), or Error (message). The reader supplies positioning; this composable only renders content.

- [ ] **Step 1: Implement the popover content**

`TranslationPopover.kt`: a Material3 `Surface`/`Card` (small elevation, rounded) sized to content. `when (state)`:
- `Loading` → a row with a small `CircularProgressIndicator` and "Translating…".
- `Result` → `Column`: the `source` in a muted label style, the `translation` in `bodyLarge`/emphasized.
- `Error` → the message in `bodyMedium` with an error color.
Keep it minimal — no save/copy buttons (deferred to Plan 3). The card itself does not handle outside-tap dismissal (the reader's `Popup` does); `onDismiss` is provided for an optional close affordance but is not required to be wired to a button in this task.

- [ ] **Step 2: Build to verify**

Run: `./gradlew :feature:translation:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/translation
git commit -m "feat: add translation popover composable"
```

---

### Task 5: Readium navigator bridge — tap-to-word + selection + disable tap-paging

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/SelectionEvent.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/EpubReaderFragment.kt`
- Modify: `feature/reader/build.gradle.kts` (depend on `:feature:translation` — used in Task 6; declare now)

**Interfaces:**
- Produces:
  - `data class SelectionEvent(val text: String, val rectInView: android.graphics.RectF)` — a resolved word or selection with its bounding rect in the navigator view's coordinate space.
  - The navigator host exposes a callback/`Flow` `onSelection: (SelectionEvent) -> Unit` that fires when the user taps a word or makes a selection.

**This is the highest-risk task. Verify every Readium API via context7 before coding. If the tap-to-word JS bridge cannot be made to work after honest investigation, STOP and report BLOCKED with the specific Readium limitation — do not ship a guess.**

- [ ] **Step 1: Spike — confirm the Readium 3.1.2 hooks (context7)**

Using context7 (Readium / `org.readium.kotlin-toolkit`), confirm and write down in the task report:
1. How to receive a tap with its point: Readium 3.x `InputListener` / `addInputListener` and the `TapEvent` shape (point in which coordinate space).
2. How to run JavaScript in the current resource WebView and get a result back (e.g. an `evaluateJavascript`/`runJavaScript`-style API on the navigator, or a script-injection + message-channel mechanism).
3. How to read the current text selection: `EpubNavigatorFragment.currentSelection()` and the `Selection` shape (text + rect).
4. How to disable tap-to-turn so a single tap does NOT page (so our handler owns taps), while horizontal swipe still pages — via `EpubNavigatorFragment.Configuration` or input-listener consumption.

- [ ] **Step 2: Implement word resolution JS**

Add a JS snippet that, given a tap point `(x, y)` in the WebView, resolves the word: `document.caretRangeFromPoint(x, y)`, expand the range to word boundaries (walk the text node / use `Intl.Segmenter` or whitespace/punctuation boundaries), and return `{ word, left, top, right, bottom }` (rect in CSS px relative to the WebView). Keep the JS in a Kotlin string constant in `EpubReaderFragment` (or a small `WordResolver.kt`).

- [ ] **Step 3: Wire tap → word → SelectionEvent**

In `EpubReaderFragment`, register the input listener. On tap: run the word-resolution JS at the tap point, parse the JSON result, convert the CSS-px rect to view pixels (apply WebView density/scroll), build a `SelectionEvent(word, rectInView)`, and invoke `onSelection`. Consume the tap so it does NOT page-turn. On a Readium selection change, build a `SelectionEvent` from `currentSelection()` similarly.

- [ ] **Step 4: Disable tap-to-turn, keep swipe**

Configure the navigator so single taps don't navigate (per the Step 1 finding). Verify horizontal swipe still turns pages.

- [ ] **Step 5: On-device verification (logging)**

Build, install, open the book on the S25 Ultra, and verify via logcat that tapping a word logs the resolved word + rect, a phrase selection logs the selected text, and a tap does NOT turn the page while a swipe does.
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "SelectionEvent\|word="
```
Expected: tapping "habits" logs `word=habits` with a plausible rect; swiping still pages; tapping does not page.

- [ ] **Step 6: Commit**

```bash
git add feature/reader
git commit -m "feat: resolve tapped word and selection from the readium navigator"
```

---

### Task 6: Wire the popover into the reader + on-device smoke test

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `TranslationViewModel` + `TranslationPopover` + `TranslationPopupState` (Tasks 1–4), `SelectionEvent` + the navigator `onSelection` callback (Task 5).

- [ ] **Step 1: Hold the anchor + obtain the translation ViewModel**

In `ReaderScreen` (the reader composable that hosts the navigator), obtain `val translationVm: TranslationViewModel = hiltViewModel()`, collect `val popup by translationVm.popupState.collectAsStateWithLifecycle()`, and keep the latest `SelectionEvent.rectInView` in a `remember { mutableStateOf<RectF?>(null) }` so the popover can be anchored.

- [ ] **Step 2: Drive translation from selection events**

Wire the navigator's `onSelection` callback (Task 5): on each `SelectionEvent`, store its rect and call `translationVm.onTextSelected(event.text)`.

- [ ] **Step 3: Show the popover anchored near the word**

When `popup != null` and a rect is held, render the `TranslationPopover(state = popup, onDismiss = { translationVm.dismiss() })` inside a Compose `Popup` whose offset is derived from the stored rect (convert view px → Compose dp using the local density). Dismiss the `Popup` on outside tap and on page turn (call `translationVm.dismiss()` and clear the rect).

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: On-device smoke test (the feature)**

Install on the S25 Ultra and verify the end-to-end feature:
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
```
Manually: open the book → tap an English word → a popover appears near it with the Ukrainian translation (first tap may show Loading while the model downloads) → select a phrase → it translates → swiping still turns pages, tapping a word does not. Capture a screenshot via `adb exec-out screencap -p > shot.png` to confirm the popover renders. (The controller verifies this on device.)

- [ ] **Step 6: Commit**

```bash
git add feature/reader
git commit -m "feat: show translation popover on word tap in the reader"
```

---

## Self-Review Notes

- **Spec coverage:** tap word (Tasks 5–6), phrase/sentence selection (Task 5 `currentSelection` + Task 6), offline ML Kit behind `TranslationEngine` (Tasks 1–2), model download-on-demand + loading (Tasks 2–3), compact popover with loading/result/error (Tasks 3–4, 6), tap-translates / swipe-pages gesture model (Task 5). DeepL, dictionary, saved words, themes are explicitly out of scope and absent by design.
- **Type consistency:** `TranslationEngine.ensureModelsReady()/translate()`, `TranslationPopupState.{Loading,Result(source,translation),Error(message)}`, `TranslationViewModel.{popupState: StateFlow<TranslationPopupState?>, onTextSelected(text), dismiss()}`, `SelectionEvent(text, rectInView)` are used identically across tasks.
- **Risk:** Tasks 5–6 depend on Readium 3.1.2 JS-bridge specifics that must be verified via context7; Task 5 carries an explicit BLOCKED-escalation instruction rather than a guess. Tasks 1–4 are device-independent and fully unit-tested where logic exists.
- **Verification gap lesson from Plan 1:** the Task 6 smoke test exercises the actual tap-to-translate feature on device, not just app launch.
