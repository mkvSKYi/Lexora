# More Themes + Premium Fonts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add five curated reading themes (Paper, Nord, Solarized Dark, Gruvbox, Dusk) with custom background/text colors, and bundle five premium reading fonts (Literata, Lora, Atkinson Hyperlegible, Inter, OpenDyslexic) selectable in the Aa sheet.

**Architecture:** Themes extend `ReaderThemePreset` + `EpubPreferencesMapper` (each preset = a base Readium `Theme` + a custom `backgroundColor`/`textColor`); the Aa sheet's swatch list gains the new presets. Fonts are bundled `.ttf` assets declared on `EpubNavigatorFragment.Configuration` (`servedAssets` + `addFontFamilyDeclaration`), with a chip per font in the Aa sheet.

**Tech Stack:** Kotlin, Compose, Material 3, Readium 3.1.2 (`EpubPreferences`, `EpubNavigatorFragment.Configuration`, `FontFamily`). Test: JUnit4 (pure mapper).

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin + Compose + Material 3. ALL versions via the catalog; no hardcoded versions in module build files.
- Themes set BOTH `backgroundColor` and `textColor` (Readium `org.readium.r2.navigator.preferences.Color(argbInt)`), on a base `Theme` (LIGHT/DARK). Exact colors (verbatim):
  PAPER = LIGHT, bg `#F5EFE0`, text `#2B2620`; NORD = DARK, bg `#2E3440`, text `#ECEFF4`; SOLARIZED_DARK = DARK, bg `#002B36`, text `#93A1A1`; GRUVBOX = DARK, bg `#282828`, text `#EBDBB2`; DUSK = DARK, bg `#20232E`, text `#C8CCDA`.
- `presetOf` reverse-maps by the stored `backgroundColor` first (each custom theme's bg is unique), falling back to `theme` for LIGHT/SEPIA/DARK/AMOLED.
- Fonts: bundle OFL `.ttf` in `feature/reader/src/main/assets/fonts/` + the license files. Declare via `EpubNavigatorFragment.Configuration { ... servedAssets = listOf("fonts/.*"); addFontFamilyDeclaration(FontFamily("…"), alternates = listOf(FontFamily.SERIF|SANS_SERIF)) { addFontFace { addSource("fonts/<file>.ttf"); setFontWeight(100..900) } } }`. OpenDyslexic reuses `FontFamily.OPEN_DYSLEXIC`.
- Existing `EpubReaderFragment` Configuration keeps `selectionActionModeCallback = NoOpActionModeCallback`.

---

### Task 1: Five new themes (enum + mapper + swatches)

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/ReaderThemePreset.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/EpubPreferencesMapper.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/ReaderSettingsSheet.kt`
- Test: `feature/reader/src/test/kotlin/com/reader/feature/reader/settings/EpubPreferencesMapperTest.kt`

**Interfaces:**
- Consumes: `EpubPreferences(theme, backgroundColor, textColor)`, `Theme.{LIGHT,SEPIA,DARK}`, `Color(Int)`.
- Produces: `ReaderThemePreset.{PAPER, NORD, SOLARIZED_DARK, GRUVBOX, DUSK}`; `EpubPreferencesMapper.withTheme`/`presetOf` covering them.

- [ ] **Step 1: Write the failing test**

`EpubPreferencesMapperTest.kt`:
```kotlin
package com.reader.feature.reader.settings

import android.graphics.Color as AndroidColor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.Theme
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubPreferencesMapperTest {
    private fun color(hex: String) = Color(AndroidColor.parseColor(hex))

    @Test fun paper_sets_light_theme_and_warm_colors() {
        val p = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.PAPER)
        assertEquals(Theme.LIGHT, p.theme)
        assertEquals(color("#F5EFE0"), p.backgroundColor)
        assertEquals(color("#2B2620"), p.textColor)
    }

    @Test fun every_preset_round_trips() {
        for (preset in ReaderThemePreset.entries) {
            val prefs = EpubPreferencesMapper.withTheme(EpubPreferences(), preset)
            assertEquals(preset, EpubPreferencesMapper.presetOf(prefs))
        }
    }
}
```
(Robolectric is needed because `android.graphics.Color.parseColor` is an Android API.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*EpubPreferencesMapperTest*"`
Expected: FAIL — `ReaderThemePreset.PAPER` unresolved.

- [ ] **Step 3: Extend the enum**

`ReaderThemePreset.kt`:
```kotlin
enum class ReaderThemePreset {
    LIGHT,
    SEPIA,
    DARK,
    AMOLED,
    PAPER,
    NORD,
    SOLARIZED_DARK,
    GRUVBOX,
    DUSK,
}
```

- [ ] **Step 4: Extend the mapper**

In `EpubPreferencesMapper.kt`, add color constants + the new branches. Use a helper:
```kotlin
    private fun color(hex: String): Color = Color(AndroidColor.parseColor(hex))

    private val PAPER_BG = color("#F5EFE0"); private val PAPER_FG = color("#2B2620")
    private val NORD_BG = color("#2E3440"); private val NORD_FG = color("#ECEFF4")
    private val SOLARIZED_BG = color("#002B36"); private val SOLARIZED_FG = color("#93A1A1")
    private val GRUVBOX_BG = color("#282828"); private val GRUVBOX_FG = color("#EBDBB2")
    private val DUSK_BG = color("#20232E"); private val DUSK_FG = color("#C8CCDA")
```
`withTheme` — add branches (keep the existing four):
```kotlin
            ReaderThemePreset.PAPER -> base.copy(theme = Theme.LIGHT, backgroundColor = PAPER_BG, textColor = PAPER_FG)
            ReaderThemePreset.NORD -> base.copy(theme = Theme.DARK, backgroundColor = NORD_BG, textColor = NORD_FG)
            ReaderThemePreset.SOLARIZED_DARK -> base.copy(theme = Theme.DARK, backgroundColor = SOLARIZED_BG, textColor = SOLARIZED_FG)
            ReaderThemePreset.GRUVBOX -> base.copy(theme = Theme.DARK, backgroundColor = GRUVBOX_BG, textColor = GRUVBOX_FG)
            ReaderThemePreset.DUSK -> base.copy(theme = Theme.DARK, backgroundColor = DUSK_BG, textColor = DUSK_FG)
```
`presetOf` — match by background first, then theme:
```kotlin
    fun presetOf(prefs: EpubPreferences): ReaderThemePreset? =
        when (prefs.backgroundColor) {
            PAPER_BG -> ReaderThemePreset.PAPER
            NORD_BG -> ReaderThemePreset.NORD
            SOLARIZED_BG -> ReaderThemePreset.SOLARIZED_DARK
            GRUVBOX_BG -> ReaderThemePreset.GRUVBOX
            DUSK_BG -> ReaderThemePreset.DUSK
            BLACK -> if (prefs.theme == Theme.DARK) ReaderThemePreset.AMOLED else null
            else -> when (prefs.theme) {
                Theme.LIGHT -> ReaderThemePreset.LIGHT
                Theme.SEPIA -> ReaderThemePreset.SEPIA
                Theme.DARK -> ReaderThemePreset.DARK
                null -> null
            }
        }
```
(`BLACK` already exists in the file for AMOLED. The AMOLED branch in `withTheme` already sets `backgroundColor = BLACK`.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*EpubPreferencesMapperTest*"`
Expected: PASS (2 tests; `every_preset_round_trips` covers all nine presets).

- [ ] **Step 6: Add the swatches**

In `ReaderSettingsSheet.kt`, append to `THEME_SWATCHES`:
```kotlin
    ThemeSwatch(ReaderThemePreset.PAPER, "Paper", ComposeColor(0xFFF5EFE0), ComposeColor(0xFF2B2620)),
    ThemeSwatch(ReaderThemePreset.NORD, "Nord", ComposeColor(0xFF2E3440), ComposeColor(0xFFECEFF4)),
    ThemeSwatch(ReaderThemePreset.SOLARIZED_DARK, "Solarized", ComposeColor(0xFF002B36), ComposeColor(0xFF93A1A1)),
    ThemeSwatch(ReaderThemePreset.GRUVBOX, "Gruvbox", ComposeColor(0xFF282828), ComposeColor(0xFFEBDBB2)),
    ThemeSwatch(ReaderThemePreset.DUSK, "Dusk", ComposeColor(0xFF20232E), ComposeColor(0xFFC8CCDA)),
```
Confirm the `ThemeRow` lays the swatches out in a wrap/scroll (if it's a single `Row`, switch it to a `FlowRow` or horizontally-scrollable `Row` so nine swatches fit — check the existing `ThemeRow` and adapt minimally).

- [ ] **Step 7: Build + commit**

Run: `./gradlew :app:assembleDebug`
```bash
git add feature/reader
git commit -m "feat: add paper, nord, solarized, gruvbox and dusk reading themes"
```

---

### Task 2: Bundle premium fonts + declare + select

**Files:**
- Create: `feature/reader/src/main/assets/fonts/*.ttf` (+ `OFL.txt` license files)
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/EpubReaderFragment.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/settings/ReaderSettingsSheet.kt`

**Interfaces:**
- Consumes: `EpubNavigatorFragment.Configuration { servedAssets; addFontFamilyDeclaration(FontFamily, alternates) { addFontFace { addSource(href); setFontWeight(range) } } }`; `FontFamily(name)`, `FontFamily.SERIF/SANS_SERIF/OPEN_DYSLEXIC`; `EpubPreferences.copy(fontFamily = …)`.
- Produces: the bundled fonts + a chip per font.

- [ ] **Step 1: Acquire the font files**

Download into `feature/reader/src/main/assets/fonts/` (variable where available; static families bundle Regular + Bold). Use these sources (verify each `curl` lands a real `.ttf`, not an HTML error page — check the file size > 50 KB):
```bash
mkdir -p feature/reader/src/main/assets/fonts && cd feature/reader/src/main/assets/fonts
base="https://github.com/google/fonts/raw/main/ofl"
curl -fL "$base/literata/Literata%5Bopsz,wght%5D.ttf" -o Literata.ttf
curl -fL "$base/lora/Lora%5Bwght%5D.ttf" -o Lora.ttf
curl -fL "$base/inter/Inter%5Bopsz,wght%5D.ttf" -o Inter.ttf
# Atkinson Hyperlegible (static): Regular + Bold
curl -fL "$base/atkinsonhyperlegible/AtkinsonHyperlegible-Regular.ttf" -o AtkinsonHyperlegible-Regular.ttf
curl -fL "$base/atkinsonhyperlegible/AtkinsonHyperlegible-Bold.ttf" -o AtkinsonHyperlegible-Bold.ttf
# OpenDyslexic (from its repo): Regular + Bold
od="https://github.com/antijingoist/opendyslexic/raw/master/compiled"
curl -fL "$od/OpenDyslexic-Regular.otf" -o OpenDyslexic-Regular.otf
curl -fL "$od/OpenDyslexic-Bold.otf" -o OpenDyslexic-Bold.otf
ls -lh
```
If a URL 404s, find the current path in the same repo (the family folder name is stable; only the file name pattern varies — e.g. a non-variable Lora is `Lora-Regular.ttf`/`Lora-Bold.ttf`). Adjust the declaration's faces to match what you actually downloaded. Add the matching `OFL.txt`/license next to the fonts.

- [ ] **Step 2: Declare the fonts on the navigator**

In `EpubReaderFragment.kt`, replace the `EpubNavigatorFragment.Configuration(selectionActionModeCallback = NoOpActionModeCallback)` call with the builder form:
```kotlin
            configuration = EpubNavigatorFragment.Configuration {
                selectionActionModeCallback = NoOpActionModeCallback
                servedAssets = listOf("fonts/.*")
                addFontFamilyDeclaration(FontFamily("Literata"), alternates = listOf(FontFamily.SERIF)) {
                    addFontFace { addSource("fonts/Literata.ttf"); setFontWeight(100..900) }
                }
                addFontFamilyDeclaration(FontFamily("Lora"), alternates = listOf(FontFamily.SERIF)) {
                    addFontFace { addSource("fonts/Lora.ttf"); setFontWeight(100..900) }
                }
                addFontFamilyDeclaration(FontFamily("Inter"), alternates = listOf(FontFamily.SANS_SERIF)) {
                    addFontFace { addSource("fonts/Inter.ttf"); setFontWeight(100..900) }
                }
                addFontFamilyDeclaration(FontFamily("Atkinson Hyperlegible"), alternates = listOf(FontFamily.SANS_SERIF)) {
                    addFontFace { addSource("fonts/AtkinsonHyperlegible-Regular.ttf") }
                    addFontFace { addSource("fonts/AtkinsonHyperlegible-Bold.ttf"); setFontWeight(org.readium.r2.navigator.epub.css.FontWeight.BOLD) }
                }
                addFontFamilyDeclaration(FontFamily.OPEN_DYSLEXIC, alternates = listOf(FontFamily.SANS_SERIF)) {
                    addFontFace { addSource("fonts/OpenDyslexic-Regular.otf") }
                    addFontFace { addSource("fonts/OpenDyslexic-Bold.otf"); setFontWeight(org.readium.r2.navigator.epub.css.FontWeight.BOLD) }
                }
            },
```
Imports: `org.readium.r2.navigator.preferences.FontFamily`. (If `setFontWeight(IntRange)` / `FontWeight.BOLD` signatures differ, match the verified API: `MutableFontFaceDeclaration.addSource(String, preload=false)`, `setFontWeight(ClosedRange<Int>)` and `setFontWeight(FontWeight)`.)

- [ ] **Step 3: Add the font chips**

In `ReaderSettingsSheet.kt`'s `FontFamilyRow`, after the existing Default/Serif/Sans-serif chips, add a chip per font. Each chip sets `prefs.copy(fontFamily = FontFamily("…"))` and previews its label in that font (load the asset into a Compose `androidx.compose.ui.text.font.FontFamily` via `Font(...)` from `feature/reader/src/main/assets/fonts/…`, or — if asset-loading a Compose font is awkward — fall back to a sensible system preview family). Names must match the declarations: `FontFamily("Literata")`, `FontFamily("Lora")`, `FontFamily("Inter")`, `FontFamily("Atkinson Hyperlegible")`, `FontFamily.OPEN_DYSLEXIC`. Ensure the chips wrap/scroll (the row may need `FlowRow`/horizontal scroll for the larger set).

- [ ] **Step 4: Build + on-device (S25 Ultra, serial RZCY51G1D6D; adb `~/Library/Android/sdk/platform-tools/adb`)**

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
```
Open a book → Aa sheet. VERIFY (screenshots, `sips -Z 1000`): each **theme** swatch (Paper/Nord/Solarized/Gruvbox/Dusk) applies its bg + text to the page; each **font** chip (Literata/Lora/Atkinson/Inter/OpenDyslexic) visibly changes the reading typeface; switching back to Light/Default still works; the persisted theme/font survive a reopen. Check logcat for FATAL and for any font 404 / "failed to load" warnings (filter `Readium`/`chromium`). If a font doesn't change the page, confirm the served path (`fonts/.*`), the asset file name, and the `FontFamily` name all line up.

- [ ] **Step 5: Commit**

```bash
git add feature/reader
git commit -m "feat: bundle literata, lora, atkinson, inter and opendyslexic reading fonts"
```

---

## Self-Review Notes

- **Spec coverage:** five themes with exact bg/text + base theme (Task 1 enum+mapper); swatches (Task 1); `presetOf` by background round-tripping all presets (Task 1 test); five bundled fonts + navigator declarations + chips (Task 2); on-device verification of themes + fonts (Task 2). No data-layer change (prefs already persist).
- **Type consistency:** `ReaderThemePreset.{PAPER,NORD,SOLARIZED_DARK,GRUVBOX,DUSK}`, `EpubPreferencesMapper.withTheme`/`presetOf`, the exact hex colors, `FontFamily("Literata"|"Lora"|"Inter"|"Atkinson Hyperlegible")` + `FontFamily.OPEN_DYSLEXIC`, `servedAssets = listOf("fonts/.*")`, and the asset file names are used consistently between the declaration (Task 2 Step 2) and the chips (Task 2 Step 3).
- **Risk:** the font-acquisition URLs may shift — Task 2 Step 1 says to verify each download is a real ttf and adjust the face sources to the files actually obtained; the on-device step is the real proof a font applies. `presetOf` correctness (unique backgrounds) is asserted by the round-trip test over all nine presets.
