# Reading Appearance & Settings Implementation Plan (Plan A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An in-reader "Aa" appearance sheet lets the user change theme, typography, page mode, brightness, and warmth, applied live and persisted globally across books.

**Architecture:** A `ReaderPreferencesRepository` (DataStore) in `:core:data` persists a serialized Readium `EpubPreferences` plus app-level brightness/warmth. `:feature:reader` maps theme presets to `EpubPreferences`, submits them to the live `EpubNavigatorFragment` via Readium's preferences API, hosts a reader chrome (top bar + Aa button, auto-hiding) and a bottom-sheet settings panel, and applies brightness (window) + warmth (amber overlay).

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, DataStore, kotlinx-serialization-json, Readium 3.1.2 preferences API. Test: JUnit4, MockK, Turbine, Robolectric (DataStore), kotlinx-coroutines-test.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin only, Compose + Material 3. MVVM + repository, unidirectional Flow, Hilt DI.
- ALL dependency versions via the catalog `gradle/libs.versions.toml`; no hardcoded versions in module build files.
- Settings are GLOBAL (one set applies to every book), not per-book.
- Appearance is applied through Readium's typed preferences (`EpubPreferences` / `EpubPreferencesEditor` / `submitPreferences`) — NOT ad-hoc CSS — except a minimal CSS layer only if a preset (AMOLED true-black) cannot be expressed otherwise.
- Brightness = host-window `screenBrightness` override (restored on reader exit). Warmth = a pointer-transparent amber overlay above the navigator.
- Reader chrome reveal must NOT use a center tap (that resolves a word); it must not collide with word-tap / sentence-long-press / swipe-page.
- Verify the exact Readium 3.1.2 preferences API (editor construction, `EpubPreferences` keys/types, the kotlinx-serialization JSON form, `submitPreferences`) via context7 before coding the Readium-touching tasks. Pin any new dep (kotlinx-serialization) in the catalog.
- `flowOf`/Turbine tests: drain with `cancelAndIgnoreRemainingEvents()` after assertions; `@OptIn(ExperimentalCoroutinesApi::class)` on classes using `Dispatchers.setMain`.

---

### Task 1: ReaderPreferences domain + DataStore repository

**Files:**
- Create: `core/data/src/main/kotlin/com/reader/core/data/preferences/ReaderPreferences.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/preferences/ReaderPreferencesRepository.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/preferences/DataStoreReaderPreferencesRepository.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/preferences/di/PreferencesModule.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/preferences/DataStoreReaderPreferencesRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `data class ReaderPreferences(val epubPreferencesJson: String?, val brightness: Float?, val warmth: Float)` — `epubPreferencesJson` is the Readium-serialized `EpubPreferences` (null = none yet); `brightness` null = follow system; `warmth` 0f..1f (0 = off). Default: `ReaderPreferences(null, null, 0f)`.
  - `interface ReaderPreferencesRepository`: `fun observe(): Flow<ReaderPreferences>`, `suspend fun setEpubPreferencesJson(json: String)`, `suspend fun setBrightness(value: Float?)`, `suspend fun setWarmth(value: Float)`.
  - `DataStoreReaderPreferencesRepository @Inject constructor(@ApplicationContext context: Context)` bound in `PreferencesModule`.

- [ ] **Step 1: Add DataStore dep to `:core:data`**

In `core/data/build.gradle.kts` add `implementation(libs.androidx.datastore.preferences)` (catalog alias already exists). Test deps already include robolectric + coroutines-test (mirror `:core:database`); add them if missing.

- [ ] **Step 2: Write the failing repository test (Robolectric, temp DataStore)**

```kotlin
package com.reader.core.data.preferences

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreReaderPreferencesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repo = DataStoreReaderPreferencesRepository(context)

    @Test fun defaults_when_empty() = runTest {
        repo.observe().test {
            val p = awaitItem()
            assertNull(p.epubPreferencesJson)
            assertNull(p.brightness)
            assertEquals(0f, p.warmth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun persists_each_field() = runTest {
        repo.setEpubPreferencesJson("""{"theme":"dark"}""")
        repo.setBrightness(0.4f)
        repo.setWarmth(0.3f)
        repo.observe().test {
            val p = awaitItem()
            assertEquals("""{"theme":"dark"}""", p.epubPreferencesJson)
            assertEquals(0.4f, p.brightness)
            assertEquals(0.3f, p.warmth)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

(Robolectric gives each test a fresh app context, so the DataStore file is empty per run. If the framework reuses files, use a unique DataStore name per test via a constructor seam.)

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*DataStoreReaderPreferencesRepositoryTest*"`
Expected: FAIL — unresolved `DataStoreReaderPreferencesRepository`.

- [ ] **Step 4: Implement the domain + repository**

`ReaderPreferences.kt` — the data class above.

`ReaderPreferencesRepository.kt` — the interface above.

`DataStoreReaderPreferencesRepository.kt` — a `Context.dataStore` via `preferencesDataStore(name = "reader_prefs")`, keys `stringPreferencesKey("epub_prefs")`, `floatPreferencesKey("brightness")`, `floatPreferencesKey("warmth")`. `observe()` maps `dataStore.data` to `ReaderPreferences` (brightness absent → null). The setters use `dataStore.edit {}`. `setBrightness(null)` removes the key.

`PreferencesModule.kt` — `@Binds` the impl to `ReaderPreferencesRepository` in `SingletonComponent`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*DataStoreReaderPreferencesRepositoryTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/data
git commit -m "feat: persist reader appearance preferences via datastore"
```

---

### Task 2: Theme presets → EpubPreferences mapping

**Files:**
- Modify: `feature/reader/build.gradle.kts` (add `:core:data` is already a dep; add kotlinx-serialization-json + the readium preferences artifact if separate — verify via context7)
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/ReaderThemePreset.kt`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/EpubPreferencesMapper.kt`
- Test: `feature/reader/src/test/kotlin/com/reader/feature/reader/settings/EpubPreferencesMapperTest.kt`

**Interfaces:**
- Consumes: Readium `EpubPreferences`, `Theme`.
- Produces:
  - `enum class ReaderThemePreset { LIGHT, SEPIA, DARK, AMOLED }`.
  - `object EpubPreferencesMapper`:
    - `fun withTheme(base: EpubPreferences, preset: ReaderThemePreset): EpubPreferences`
    - `fun presetOf(prefs: EpubPreferences): ReaderThemePreset?` (reverse — for showing the selected preset).

- [ ] **Step 1: Spike the EpubPreferences API (context7)**

Verify via context7: the `org.readium.r2.navigator.epub.EpubPreferences` constructor/`copy` keys and types for `theme`, `fontSize`, `fontFamily`, `lineHeight`, `pageMargins`, `scroll`; the `org.readium.r2.navigator.preferences.Theme` enum (LIGHT/SEPIA/DARK); and how `backgroundColor`/`textColor` preferences are typed (for AMOLED). Write the confirmed shapes in the task report.

- [ ] **Step 2: Write the failing mapping test**

```kotlin
package com.reader.feature.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme

class EpubPreferencesMapperTest {
    @Test fun light_preset_maps_to_light_theme() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.LIGHT)
        assertEquals(Theme.LIGHT, out.theme)
    }

    @Test fun dark_preset_maps_to_dark_theme() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.DARK)
        assertEquals(Theme.DARK, out.theme)
    }

    @Test fun preset_roundtrips_for_sepia() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.SEPIA)
        assertEquals(ReaderThemePreset.SEPIA, EpubPreferencesMapper.presetOf(out))
    }
}
```

(Adjust `EpubPreferences()`/`Theme` construction to the exact 3.1.2 API confirmed in Step 1; keep the assertions' intent.)

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*EpubPreferencesMapperTest*"`
Expected: FAIL — unresolved `EpubPreferencesMapper`/`ReaderThemePreset`.

- [ ] **Step 4: Implement the mapping**

`ReaderThemePreset.kt` — the enum.

`EpubPreferencesMapper.kt` — `withTheme` returns `base.copy(theme = ...)` for LIGHT/SEPIA/DARK; for AMOLED use `Theme.DARK` plus a true-black `backgroundColor` (and white `textColor`) per the Step 1 finding. `presetOf` inspects `theme` (+ backgroundColor for AMOLED) and returns the matching preset or null.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*EpubPreferencesMapperTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/reader
git commit -m "feat: map theme presets to readium epub preferences"
```

---

### Task 3: Apply preferences to the live navigator + load persisted on open

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/EpubReaderFragment.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `ReaderPreferencesRepository` (Task 1), `EpubPreferences` serializer.
- Produces:
  - `ReaderViewModel` exposes `val epubPreferences: StateFlow<EpubPreferences>` (loaded from the repo, default `EpubPreferences()`), `fun updateEpubPreferences(prefs: EpubPreferences)` (persists the serialized JSON via the repo).
  - The navigator host applies the current `EpubPreferences` to the live `EpubNavigatorFragment` via `submitPreferences`, and re-applies whenever `epubPreferences` changes.

- [ ] **Step 1: Spike serialization + submit (context7)**

Confirm via context7: `EpubPreferences` kotlinx-serialization (the `Json` instance / `EpubPreferences.Companion` serializer), and `EpubNavigatorFragment.submitPreferences(EpubPreferences)`. Note whether a dedicated `Json` from Readium must be used. Add `kotlinx-serialization-json` + the Kotlin serialization plugin to the catalog/module if needed.

- [ ] **Step 2: Load + expose preferences in the ViewModel**

In `ReaderViewModel`, inject `ReaderPreferencesRepository`. On init, map `repo.observe()` → deserialize `epubPreferencesJson` (or `EpubPreferences()` default) into a `MutableStateFlow<EpubPreferences>`; `updateEpubPreferences` sets the flow AND persists `repo.setEpubPreferencesJson(json)`. Deserialization failure → default (never crash).

- [ ] **Step 3: Submit preferences to the navigator on open + on change**

In the navigator host (`ReaderScreen`/`EpubReaderFragment`), collect `epubPreferences` and call `navigator.submitPreferences(prefs)` on the initial `Ready` and on every change. Wire the `Session` to carry the current preferences + a way to observe changes (extend `ReaderNavigatorHost.Session` with an `epubPreferences: StateFlow<EpubPreferences>` the fragment collects on `viewLifecycleOwner.lifecycleScope`).

- [ ] **Step 4: On-device verification**

Build + install. Temporarily call `updateEpubPreferences` with a dark theme + larger font on open (or via a debug action), and confirm the open book visibly switches to dark + larger text, and that reopening the book keeps it (persistence).
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
```
Expected: theme + font change is visible live and survives reopen. Remove any temporary debug trigger before committing.

- [ ] **Step 5: Commit**

```bash
git add feature/reader
git commit -m "feat: apply and persist readium preferences in the reader"
```

---

### Task 4: Reader chrome — top bar with Aa button + auto-hide

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/chrome/ReaderChrome.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Produces: `@Composable fun ReaderChrome(visible: Boolean, onBack: () -> Unit, onAa: () -> Unit, bottomBar: @Composable () -> Unit, content: @Composable () -> Unit)` — overlays an auto-hiding top bar (back + Aa actions) and a bottom slot over the reader content. Plus the reveal logic.

- [ ] **Step 1: Implement the chrome**

`ReaderChrome.kt`: a `Box` with `content()` filling it, an animated (`AnimatedVisibility`) top app bar (back + an "Aa" `TextButton`/icon) anchored top, and `bottomBar()` anchored bottom. The bars share a `visible` state. Reveal affordance that avoids the tap/long-press/swipe conflict: a thin tap-target strip across the very top edge (the status-bar region, above where text renders) toggles `visible`; the bars also auto-hide after ~3s of no interaction. (Tapping that top strip does not reach the navigator's text, so no word is translated.)

- [ ] **Step 2: Wrap the navigator in `ReaderScreen`**

Replace the current bare top bar in `ReaderScreen` with `ReaderChrome(...)` wrapping the `AndroidFragment` navigator; `onAa` opens the settings sheet (Task 5, stubbed as a `mutableStateOf(false)` for now); `bottomBar = {}` (filled in Plan B). `onBack` keeps the existing behavior.

- [ ] **Step 3: On-device verification**

Build + install. Confirm: the top bar shows on open, auto-hides while reading, re-appears on a top-edge tap; tapping a word still translates (mid-screen), swipe still pages, the Aa button is tappable. No crash.

- [ ] **Step 4: Commit**

```bash
git add feature/reader
git commit -m "feat: add auto-hiding reader chrome with aa button"
```

---

### Task 5: Appearance sheet — theme, typography, page mode

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/ReaderSettingsSheet.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `ReaderThemePreset` + `EpubPreferencesMapper` (Task 2), `ReaderViewModel.epubPreferences`/`updateEpubPreferences` (Task 3).
- Produces: `@Composable fun ReaderSettingsSheet(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit, onDismiss: () -> Unit)` — a Material3 `ModalBottomSheet` with: a theme-preset row (Light/Sepia/Dark/AMOLED swatches), font-size stepper (−/+), font-family selector, line-height slider, page-margins slider, and a paginated↔scroll toggle. Each control produces a new `EpubPreferences` via copy/mapper and calls `onPrefsChange`.

- [ ] **Step 1: Implement the sheet**

`ReaderSettingsSheet.kt`: a `ModalBottomSheet`. Theme row → `EpubPreferencesMapper.withTheme(prefs, preset)`. Font size −/+ → `prefs.copy(fontSize = ...)` clamped to a sane range (verify the unit/range via Task 2's spike; typically a multiplier ~0.5..2.0). Font family → a small fixed list (serif/sans-serif/default) mapped to Readium `FontFamily`. Line height + margins → sliders mapped to `prefs.copy(lineHeight = ...)`/`copy(pageMargins = ...)`. Page mode → `prefs.copy(scroll = Boolean)`. Highlight the active preset via `EpubPreferencesMapper.presetOf(prefs)`.

- [ ] **Step 2: Wire the sheet into `ReaderScreen`**

Show `ReaderSettingsSheet` when the Aa state is true; `prefs = epubPreferences` collected from the ViewModel; `onPrefsChange = viewModel::updateEpubPreferences` (which persists + the navigator re-applies, Task 3); `onDismiss` closes it.

- [ ] **Step 3: On-device verification**

Build + install. Open a book → Aa → change theme (sepia/dark/AMOLED), bump font size, adjust line height + margins, toggle scroll mode — confirm each updates the open book live. Close + reopen the book and the chosen settings persist. Capture a screenshot of a non-default theme (e.g. sepia) with larger text. No crash.

- [ ] **Step 4: Commit**

```bash
git add feature/reader
git commit -m "feat: add appearance settings sheet for theme typography and page mode"
```

---

### Task 6: Brightness + warmth

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/ReaderSettingsSheet.kt`

**Interfaces:**
- Consumes: `ReaderPreferencesRepository` (Task 1).
- Produces: `ReaderViewModel` exposes `val brightness: StateFlow<Float?>` and `val warmth: StateFlow<Float>` + `fun setBrightness(value: Float?)` / `fun setWarmth(value: Float)` (persist via the repo). The sheet gains brightness + warmth sliders. `ReaderScreen` applies brightness to the window and renders the warmth overlay.

- [ ] **Step 1: Expose brightness/warmth in the ViewModel**

Map `repo.observe()` → `brightness`/`warmth` StateFlows; setters persist via `repo.setBrightness`/`setWarmth`.

- [ ] **Step 2: Apply brightness to the window**

In `ReaderScreen`, a `DisposableEffect` that, while the reader is shown, sets the host `Activity`'s `window.attributes.screenBrightness` to the `brightness` value (or `BRIGHTNESS_OVERRIDE_NONE` when null), and RESTORES the previous value `onDispose`. Re-apply when `brightness` changes.

- [ ] **Step 3: Render the warmth overlay**

In `ReaderChrome`/`ReaderScreen`, a full-screen `Box` above the navigator with `Color(0xFFFF8C00).copy(alpha = warmth * MAX_WARMTH_ALPHA)` and `Modifier` that does NOT intercept pointer input (so taps still reach the navigator). `MAX_WARMTH_ALPHA` ~0.4f.

- [ ] **Step 4: Add the sliders to the sheet**

`ReaderSettingsSheet` gains a brightness slider (with a "system" reset) and a warmth slider, wired to the ViewModel setters.

- [ ] **Step 5: On-device verification**

Build + install. Open a book → Aa → drag brightness (screen dims/brightens), drag warmth (amber tint increases) — confirm live, taps still work through the overlay, and both persist across reopen. Confirm window brightness restores after leaving the reader. No crash.

- [ ] **Step 6: Commit**

```bash
git add feature/reader
git commit -m "feat: add brightness and warmth controls to the reader"
```

---

## Self-Review Notes

- **Spec coverage:** chrome + Aa (Tasks 4–5), themes + typography + page mode via Readium prefs (Tasks 2–3, 5), brightness + warmth (Task 6), global persistence via DataStore (Tasks 1, 3, 6), live preview (Tasks 3, 5, 6). TOC + progress are explicitly Plan B (the bottom-bar slot is left open in Task 4).
- **Type consistency:** `ReaderPreferences(epubPreferencesJson, brightness, warmth)`, `ReaderPreferencesRepository.{observe, setEpubPreferencesJson, setBrightness, setWarmth}`, `ReaderThemePreset{LIGHT,SEPIA,DARK,AMOLED}`, `EpubPreferencesMapper.{withTheme, presetOf}`, `ReaderViewModel.{epubPreferences, updateEpubPreferences, brightness, warmth, setBrightness, setWarmth}` are used consistently across tasks.
- **Risk:** Tasks 2–3 depend on Readium 3.1.2 preferences API specifics (front-loaded context7 spikes in 2.1 and 3.1); if `EpubPreferences`/editor/serializer differ materially, the implementer adjusts the mapping/serialization and notes it. Brightness/warmth and persistence are device-independent logic + standard Android.
- **Verification:** every UI/Readium task ends with an on-device check of the actual control (not just app launch), per the Plan-1 lesson.
