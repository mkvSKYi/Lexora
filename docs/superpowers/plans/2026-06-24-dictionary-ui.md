# Offline Dictionary UI & Reader Integration Implementation Plan (Plan 3b-2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a single word opens a dictionary bottom sheet (IPA, part of speech, English definitions, Ukrainian translations — or the ML Kit translation when the word isn't in the dictionary), with Save; phrase long-press keeps the existing popover.

**Architecture:** A `WordLookupViewModel` in `:feature:translation` queries `:core:dictionary` and falls back to ML Kit; a `WordDictionarySheet` Composable renders the lookup state in a Material 3 `ModalBottomSheet`. The reader tags taps as word-vs-phrase (`SelectionEvent.isWord`) and routes word→sheet, phrase→existing popover; Save reuses the existing reader save flow.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Coroutines, `:core:dictionary` (`DictionaryRepository`), ML Kit (`TranslationEngine`). Test: JUnit4, MockK, Turbine, kotlinx-coroutines-test.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin only, Compose + Material 3. MVVM + repository, unidirectional Flow, Hilt DI.
- ALL dependency versions via the catalog; no hardcoded versions in module build files.
- Single-word tap → dictionary bottom sheet (dict entry, else ML Kit translation in the same sheet). Phrase/sentence long-press → the EXISTING translation popover (unchanged), including its "first tap dismisses" + anchor + Save behavior.
- Reuse the existing `SavedWord` schema (term + translation + context + book); the sheet's Save hands `(term, translation)` to the reader's existing `saveCurrentWord`.
- Displayed `definitions`/`translations` are deduped order-preserving (`List.distinct()`).
- `flowOf`/Turbine tests: drain with `cancelAndIgnoreRemainingEvents()`; `@OptIn(ExperimentalCoroutinesApi::class)` where `Dispatchers.setMain` is used.

---

### Task 1: WordLookupViewModel (dictionary + ML Kit fallback)

**Files:**
- Modify: `feature/translation/build.gradle.kts` (add `implementation(project(":core:dictionary"))`)
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/WordLookupState.kt`
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/WordLookupViewModel.kt`
- Test: `feature/translation/src/test/kotlin/com/reader/feature/translation/WordLookupViewModelTest.kt`

**Interfaces:**
- Consumes: `com.reader.core.dictionary.DictionaryRepository.lookup(word): DictionaryEntry?` (`DictionaryEntry(headword, ipa: String?, partOfSpeech: String?, definitions: List<String>, translations: List<String>)`); `com.reader.feature.translation.TranslationEngine` (`ensureModelsReady(): Result<Unit>`, `translate(text): Result<String>`).
- Produces:
  - `sealed interface WordLookupState { data object Loading; data class Entry(val word: String, val ipa: String?, val partOfSpeech: String?, val definitions: List<String>, val translations: List<String>); data class Machine(val word: String, val translation: String); data class Error(val message: String) }`.
  - `@HiltViewModel WordLookupViewModel @Inject constructor(dictionary: DictionaryRepository, engine: TranslationEngine)`: `val lookupState: StateFlow<WordLookupState?>` (null = sheet hidden), `fun onWord(word: String)`, `fun dismiss()`.

- [ ] **Step 1: Add the dependency**

In `feature/translation/build.gradle.kts` add `implementation(project(":core:dictionary"))` (the catalog already covers everything else).

- [ ] **Step 2: Write the failing test**

```kotlin
package com.reader.feature.translation

import app.cash.turbine.test
import com.reader.core.dictionary.DictionaryEntry
import com.reader.core.dictionary.DictionaryRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordLookupViewModelTest {
    private val dictionary = mockk<DictionaryRepository>()
    private val engine = mockk<TranslationEngine>()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun dictionary_hit_emits_entry_deduped() = runTest {
        coEvery { dictionary.lookup("dog") } returns DictionaryEntry(
            "dog", "/dɒɡ/", "noun", listOf("An animal.", "An animal."), listOf("собака", "собака", "пес"),
        )
        val vm = WordLookupViewModel(dictionary, engine)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("dog")
            assertEquals(WordLookupState.Loading, awaitItem())
            val e = awaitItem()
            assertTrue(e is WordLookupState.Entry)
            e as WordLookupState.Entry
            assertEquals(listOf("An animal."), e.definitions)        // deduped
            assertEquals(listOf("собака", "пес"), e.translations)    // deduped, order kept
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dictionary_miss_falls_back_to_machine_translation() = runTest {
        coEvery { dictionary.lookup("xylophone") } returns null
        coEvery { engine.ensureModelsReady() } returns Result.success(Unit)
        coEvery { engine.translate("xylophone") } returns Result.success("ксилофон")
        val vm = WordLookupViewModel(dictionary, engine)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("xylophone")
            assertEquals(WordLookupState.Loading, awaitItem())
            assertEquals(WordLookupState.Machine("xylophone", "ксилофон"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun blank_word_is_ignored() = runTest {
        val vm = WordLookupViewModel(dictionary, engine)
        vm.lookupState.test {
            assertNull(awaitItem())
            vm.onWord("   ")
            expectNoEvents()
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:translation:testDebugUnitTest --tests "*WordLookupViewModelTest*"`
Expected: FAIL — unresolved `WordLookupViewModel`/`WordLookupState`.

- [ ] **Step 4: Implement the state + ViewModel**

`WordLookupState.kt` — the sealed interface above.

`WordLookupViewModel.kt`:
```kotlin
package com.reader.feature.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.dictionary.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordLookupViewModel @Inject constructor(
    private val dictionary: DictionaryRepository,
    private val engine: TranslationEngine,
) : ViewModel() {

    private val _lookupState = MutableStateFlow<WordLookupState?>(null)
    val lookupState: StateFlow<WordLookupState?> = _lookupState.asStateFlow()

    fun onWord(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        _lookupState.value = WordLookupState.Loading
        viewModelScope.launch {
            val entry = dictionary.lookup(w)
            if (entry != null) {
                _lookupState.value = WordLookupState.Entry(
                    word = entry.headword,
                    ipa = entry.ipa,
                    partOfSpeech = entry.partOfSpeech,
                    definitions = entry.definitions.distinct(),
                    translations = entry.translations.distinct(),
                )
                return@launch
            }
            val ready = engine.ensureModelsReady()
            if (ready.isFailure) {
                _lookupState.value = WordLookupState.Error(
                    "Translation needs a one-time download. Connect to the internet and tap again.",
                )
                return@launch
            }
            engine.translate(w)
                .onSuccess { _lookupState.value = WordLookupState.Machine(w, it) }
                .onFailure { _lookupState.value = WordLookupState.Error("Couldn't translate. Try again.") }
        }
    }

    fun dismiss() { _lookupState.value = null }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :feature:translation:testDebugUnitTest --tests "*WordLookupViewModelTest*"`
Expected: PASS (3 tests, pristine).

- [ ] **Step 6: Commit**

```bash
git add feature/translation
git commit -m "feat: add word lookup view model with dictionary and ml kit fallback"
```

---

### Task 2: WordDictionarySheet Composable

**Files:**
- Create: `feature/translation/src/main/kotlin/com/reader/feature/translation/WordDictionarySheet.kt`

**Interfaces:**
- Consumes: `WordLookupState` (Task 1).
- Produces: `@Composable fun WordDictionarySheet(state: WordLookupState, onSave: (term: String, translation: String) -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Implement the sheet**

`WordDictionarySheet.kt`: a Material3 `ModalBottomSheet(onDismissRequest = onDismiss)`. `when (state)`:
- `Loading` → a centered `CircularProgressIndicator` with "Looking up…".
- `Entry` → `Column`: `word` (`headlineSmall`, bold); a row with `ipa` (if non-null, `bodyMedium`, `onSurfaceVariant`) and `partOfSpeech` (if non-null, `labelLarge`, `onSurfaceVariant`); if `translations` non-empty, a "Translations" label + the items (e.g. comma-joined or chips); if `definitions` non-empty, a "Definitions" label + a numbered list; a **Save** `Button` calling `onSave(state.word, state.translations.firstOrNull() ?: state.definitions.firstOrNull() ?: "")`.
- `Machine` → `Column`: `word` (`headlineSmall`) + the `translation` (`bodyLarge`) + a **Save** `Button` calling `onSave(state.word, state.translation)`.
- `Error` → the message (`bodyMedium`, error color).
Use theme tokens only (no hardcoded colors). YAGNI: no audio, no example sentences. Disable/omit Save when there's nothing to save (empty translation/definition).

- [ ] **Step 2: Build to verify**

Run: `./gradlew :feature:translation:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/translation
git commit -m "feat: add word dictionary bottom sheet"
```

---

### Task 3: Reader integration — route word→sheet, phrase→popover

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/SelectionEvent.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/EpubReaderFragment.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`
- Modify: `feature/reader/build.gradle.kts` (already depends on `:feature:translation`)
- Modify: `app/build.gradle.kts` (add `implementation(project(":core:dictionary"))`)

**Interfaces:**
- Consumes: `WordLookupViewModel` + `WordDictionarySheet` + `WordLookupState` (Tasks 1–2); the reader's existing `ReaderViewModel.saveCurrentWord(term, translation, contextSentence)`.
- Produces: `SelectionEvent(text, rectInView, contextSentence: String?, isWord: Boolean)`.

- [ ] **Step 1: Tag word vs phrase**

Add `val isWord: Boolean` to `SelectionEvent` (default true for safety). In `EpubReaderFragment.onTapResolveWord`, emit `SelectionEvent(..., isWord = true)`; in `onLongPressResolveSentence`, emit `SelectionEvent(..., isWord = false)`.

- [ ] **Step 2: Package the dictionary asset in the app**

Add `implementation(project(":core:dictionary"))` to `app/build.gradle.kts` so `dictionary.db` ships in the APK (the asset is in `:core:dictionary`). (Verify `:app:assembleDebug` packages it.)

- [ ] **Step 3: Route in ReaderScreen**

In the reader composable that owns the selection handling: obtain `val wordVm: WordLookupViewModel = hiltViewModel()`; collect `val wordState by wordVm.lookupState.collectAsStateWithLifecycle()`; keep the latest tapped word's `contextSentence` (reuse the existing `lastContextSentence`). In the selection handler:
- if `event.isWord` → `lastContextSentence = event.contextSentence`; `wordVm.onWord(event.text)` (do NOT call the popover's `onTextSelected`).
- else → the existing popover path (`anchorRect`/`translationVm.onTextSelected`) unchanged.
When `wordState != null`, render `WordDictionarySheet(state = wordState!!, onSave = { term, translation -> readerViewModel.saveCurrentWord(term, translation, lastContextSentence); showSavedSnackbar(); wordVm.dismiss() }, onDismiss = { wordVm.dismiss() })`. The phrase popover, its catcher/dismiss, anchor, and its Save stay exactly as they are.

- [ ] **Step 4: Build + on-device smoke**

Build + install. End-to-end on the S25 Ultra:
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
```
Manually/adb: open a book → tap a word that's in the dictionary (e.g. a common noun) → the bottom sheet shows IPA + POS + definitions (+ Ukrainian if present) → tap Save → the word appears in Saved Words. Tap a word likely NOT in the dictionary → the sheet shows the ML Kit translation. Long-press a sentence → the existing popover still appears (ML Kit) and its first-tap-dismiss still works. Screenshot the dictionary sheet to `…/scratchpad/dict_*.png`. Check logcat for FATAL.

- [ ] **Step 5: Commit**

```bash
git add feature/reader app
git commit -m "feat: show dictionary sheet on word tap in the reader"
```

---

## Self-Review Notes

- **Spec coverage:** `WordLookupViewModel` dict→Entry / miss→Machine / error, deduped lists (Task 1); `WordDictionarySheet` with IPA/POS/defs/translations/Save + Loading/Error (Task 2); `SelectionEvent.isWord` routing word→sheet / phrase→popover, `:app`→`:core:dictionary`, Save via `saveCurrentWord`, on-device smoke (Task 3). `SavedWord` schema unchanged; no DeepL/audio/search.
- **Type consistency:** `WordLookupState.{Loading, Entry(word,ipa,partOfSpeech,definitions,translations), Machine(word,translation), Error(message)}`, `WordLookupViewModel.{lookupState: StateFlow<WordLookupState?>, onWord(word), dismiss()}`, `WordDictionarySheet(state, onSave:(term,translation)->Unit, onDismiss)`, `SelectionEvent(text, rectInView, contextSentence, isWord)`, `DictionaryEntry(headword, ipa, partOfSpeech, definitions, translations)`, `ReaderViewModel.saveCurrentWord(term, translation, contextSentence)` are used consistently.
- **Risk:** Task 3 routes two surfaces through the existing tap pipeline; it must NOT regress the phrase popover's first-tap-dismiss/anchor/Save — the on-device smoke verifies word-sheet, ML-Kit-fallback, phrase-popover, and Save all together. `ModalBottomSheet` (used for the dictionary sheet, like the existing settings/TOC sheets) dismisses natively, so the word path doesn't need the popover's custom catcher.
- **Verification:** Task 3 ends with an on-device round trip exercising the real bundled dictionary, the ML Kit fallback, Save→Saved Words, and the unchanged phrase popover.
