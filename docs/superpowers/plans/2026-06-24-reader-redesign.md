# Reader Redesign + Brightness Swipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a right-side vertical-swipe brightness gesture (with a sleek vertical-bar indicator) and give the reader chrome a premium "Aurora" redesign (floating glass top/bottom bars + an always-visible progress hairline).

**Architecture:** A pure `nextBrightness` helper maps a vertical drag to a 0..1 brightness; a right-side `detectVerticalDragGestures` zone in `ReaderScreen` drives `viewModel.setBrightness` (live) and a `BrightnessIndicator`. `ReaderChrome`'s top bar and `ReaderBottomBar` are restyled to floating translucent surfaces; a thin progress hairline is always shown at the bottom.

**Tech Stack:** Kotlin, Compose, Material 3, Readium (host unchanged). Test: JUnit4 (pure helper).

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin + Compose + Material 3. ALL versions via the catalog; no hardcoded versions in module build files.
- The gesture zone draws OVER the WebView but must use `detectVerticalDragGestures` (claims ONLY vertical-dominant drags past slop) so word-tap, long-press, and horizontal page-turn pass through untouched. (This repo's warmth overlay deliberately has no pointerInput; the popover scrim is carefully scoped — overlays here can steal taps.)
- Brightness model (existing, do not change): `ReaderViewModel.brightness: StateFlow<Float?>` (null = system default, else 0..1) and `fun setBrightness(value: Float?)`; applied to `window.attributes.screenBrightness`. Base value when null = `0.5f`.
- Accent color `Color(0xFF9B8CFF)` (the library's aurora accent; redefine locally in `:feature:reader`, do not import the library's `internal`).
- Chrome glass must stay legible over light AND dark reading themes → use a dark translucent base regardless of theme.
- Keep these contracts unchanged: `ReaderChrome(visible, onBack, onToc, onAa, onRevealStripTap, bottomBar, content)`, `ReaderBottomBar(progression: Float, chapterTitle: String?, onSeek: (Float) -> Unit)`, the top reveal strip + `onRevealStripTap`, and the seek "commit-on-release" behavior.

---

### Task 1: Brightness swipe gesture + indicator

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/brightness/BrightnessGesture.kt`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/brightness/BrightnessIndicator.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`
- Test: `feature/reader/src/test/kotlin/com/reader/feature/reader/brightness/BrightnessGestureTest.kt`

**Interfaces:**
- Consumes: `ReaderViewModel.setBrightness(Float?)`, `ReaderViewModel.brightness: StateFlow<Float?>`.
- Produces:
  - `fun nextBrightness(current: Float, dragDeltaPx: Float, heightPx: Float): Float` — `(current - dragDeltaPx / heightPx).coerceIn(0f, 1f)` (guards `heightPx <= 0` → returns `current`).
  - `@Composable fun BrightnessIndicator(level: Float, modifier: Modifier)` — a vertical bar.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.reader.feature.reader.brightness

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessGestureTest {
    @Test fun swipe_up_increases() {
        // negative dragDelta (finger moves up) → brighter
        assertEquals(0.6f, nextBrightness(0.5f, -100f, 1000f), 0.0001f)
    }

    @Test fun swipe_down_decreases() {
        assertEquals(0.4f, nextBrightness(0.5f, 100f, 1000f), 0.0001f)
    }

    @Test fun clamps_to_unit_range() {
        assertEquals(1f, nextBrightness(0.95f, -200f, 1000f), 0.0001f)
        assertEquals(0f, nextBrightness(0.05f, 200f, 1000f), 0.0001f)
    }

    @Test fun zero_height_is_safe() {
        assertEquals(0.5f, nextBrightness(0.5f, 100f, 0f), 0.0001f)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*BrightnessGestureTest*"`
Expected: FAIL — `nextBrightness` unresolved.

- [ ] **Step 3: Implement the pure helper**

`BrightnessGesture.kt`:
```kotlin
package com.reader.feature.reader.brightness

/** Brightness after a vertical drag. Up (negative delta) brightens; a full-height drag spans 0..1. */
fun nextBrightness(current: Float, dragDeltaPx: Float, heightPx: Float): Float {
    if (heightPx <= 0f) return current
    return (current - dragDeltaPx / heightPx).coerceIn(0f, 1f)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*BrightnessGestureTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Implement the indicator**

`BrightnessIndicator.kt` — a vertical rounded pill near the right edge, vertically centered:
- A `Box` (≈8 dp wide, ≈160 dp tall, `RoundedCornerShape(50)`) with a translucent dark track; a fill `Box` aligned bottom whose height = `level` of the track, colored with the accent `Color(0xFF9B8CFF)`.
- Above the bar, a small sun glyph (`Icons.Filled.LightMode`) tinted white and a `"${(level*100).roundToInt()}%"` label.
- A wrapping `Column` (centered items) with a translucent dark rounded background so it reads on any page. Theme tokens / the accent only. No `pointerInput` (display only).

- [ ] **Step 6: Wire the gesture zone into ReaderScreen**

In `ReaderScreen.kt`, inside the content `Box` that holds `EpubReader` + the warmth overlay (so the zone draws over the WebView), add a right-side gesture zone + the indicator. Use the screen height for the mapping and a coroutine to fade the indicator out ~600 ms after the drag ends:
```kotlin
// state near the other reader state
var indicatorLevel by remember { mutableStateOf<Float?>(null) } // non-null = visible
val brightnessScope = rememberCoroutineScope()
var hideJob by remember { mutableStateOf<Job?>(null) }
val density = LocalDensity.current
val currentBrightnessValue = brightness // already collected: Float?

BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val heightPx = with(density) { maxHeight.toPx() }
    // Right ~40% vertical-drag zone. detectVerticalDragGestures claims only vertical drags,
    // so taps (translate), long-press, and horizontal page-turns pass through to the WebView.
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.4f)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        hideJob?.cancel()
                        val base = indicatorLevel ?: (currentBrightnessValue ?: 0.5f)
                        val next = nextBrightness(base, dragAmount, heightPx)
                        indicatorLevel = next
                        viewModel.setBrightness(next)
                    },
                    onDragEnd = {
                        hideJob = brightnessScope.launch {
                            delay(600)
                            indicatorLevel = null
                        }
                    },
                )
            },
    )
    indicatorLevel?.let { level ->
        BrightnessIndicator(
            level = level,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
        )
    }
}
```
Add imports: `androidx.compose.foundation.gestures.detectVerticalDragGestures`, `androidx.compose.foundation.layout.BoxWithConstraints`, `fillMaxHeight`, `androidx.compose.ui.platform.LocalDensity`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.launch`, `rememberCoroutineScope`. (If `BoxWithConstraints`/`LocalDensity` already imported, skip.)

- [ ] **Step 7: Build + on-device gesture verification (S25 Ultra, serial RZCY51G1D6D; adb `~/Library/Android/sdk/platform-tools/adb`)**

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
```
Open a book. VERIFY (screenshot each; downscale with `sips -Z 1000` before viewing):
- Swipe up on the RIGHT side → screen brightens + the vertical-bar indicator rises and fades out after release; swipe down → dims. (`input swipe 1200 2200 1200 900 300` = up; reverse = down.)
- **Word-tap on the right still translates** (`input tap` on a word, right side) → translation card.
- **Horizontal swipe still turns the page** (`input swipe 1200 1600 200 1600 200`).
- **Long-press still translates a sentence** (`input swipe x y x y 800` on a word).
- The top reveal strip + Aa/TOC still work.
Check logcat for FATAL. If the gesture zone steals taps/page-turns (the known overlay gotcha), report it — the fix is to ensure the detector never consumes non-vertical gestures (it shouldn't), or narrow the zone.

- [ ] **Step 8: Commit**

```bash
git add feature/reader
git commit -m "feat: right-side swipe to control reader brightness"
```

---

### Task 2: Premium Aurora chrome (top bar, bottom bar, progress hairline)

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/chrome/ReaderChrome.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/navigation/ReaderBottomBar.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: the existing `ReaderChrome`/`ReaderBottomBar` params (unchanged); `currentProgression: Float` (already in `ReaderScreen`).
- Produces: restyled bars + an always-visible progress hairline. No signature changes.

- [ ] **Step 1: Restyle the top bar to a floating glass surface**

In `ReaderChrome.kt`, replace the `TopAppBar` inside the top `AnimatedVisibility` with a floating rounded surface: a `Surface` (or `Box`) with `Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)`, `RoundedCornerShape(24.dp)`, a translucent dark background `Color(0xFF15151B).copy(alpha = 0.82f)`, slight shadow (`Modifier.shadow(8.dp, RoundedCornerShape(24.dp))`), containing a `Row` (height 52.dp, `padding(horizontal = 8.dp)`, `verticalAlignment = CenterVertically`): an `IconButton` back (white tint), a `Spacer(weight=1f)`, an `IconButton` TOC (white), and the `Aa` `TextButton` (accent `Color(0xFF9B8CFF)`). Keep `onBack`/`onToc`/`onAa`. Icons/text tinted for legibility on the dark glass.

- [ ] **Step 2: Restyle the bottom bar to a floating card**

In `ReaderBottomBar.kt`, wrap the content in a floating `Surface`: `Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)`, `RoundedCornerShape(24.dp)`, translucent dark `Color(0xFF15151B).copy(alpha = 0.82f)`, `shadow(8.dp, ...)`. Inside, keep the existing `Column` (percent row + `Slider`) but: white text for the chapter/percent, and set `SliderDefaults.colors(thumbColor = Color(0xFF9B8CFF), activeTrackColor = Color(0xFF9B8CFF), inactiveTrackColor = Color.White.copy(alpha = 0.25f))`. Keep the `drag`/`onValueChangeFinished` commit-on-release logic exactly.

- [ ] **Step 3: Add the always-visible progress hairline**

In `ReaderScreen.kt`, render a thin progress bar pinned to the very bottom of the content `Box`, BEHIND the chrome bars (so the bottom bar covers it when chrome is visible, and it shows when chrome is hidden):
```kotlin
LinearProgressIndicator(
    progress = { currentProgression.coerceIn(0f, 1f) },
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(2.dp),
    color = Color(0xFF9B8CFF),
    trackColor = Color.Transparent,
)
```
Place it as a sibling in the content `Box` BEFORE the `ReaderChrome` bars render the bottom bar (i.e., it's drawn under them). Imports: `LinearProgressIndicator`, `Alignment`, `height`, `Color` (likely already present).

- [ ] **Step 4: Build + on-device**

```
./gradlew :app:installDebug
```
Open a book. VERIFY (screenshots, `sips -Z 1000`): the top bar is a floating dark glass pill with back/TOC/Aa; the bottom bar is a floating card with the accent slider; the thin accent progress hairline shows at the bottom when chrome is hidden; the bars remain legible over BOTH a light/sepia theme and a dark theme (toggle via Aa); reveal/auto-hide + seek still work. Check logcat for FATAL.

- [ ] **Step 5: Commit**

```bash
git add feature/reader
git commit -m "feat: aurora floating reader chrome with progress hairline"
```

---

## Self-Review Notes

- **Spec coverage:** brightness gesture + pure helper (Task 1); vertical-bar indicator with fade (Task 1); chrome top/bottom glass bars (Task 2); always-on progress hairline (Task 2); gesture passes through taps/long-press/page-turn (Task 1 on-device); legibility over light+dark (Task 2 on-device). No ViewModel/data changes.
- **Type consistency:** `nextBrightness(current, dragDeltaPx, heightPx): Float`, `BrightnessIndicator(level, modifier)`, `ReaderViewModel.setBrightness(Float?)`/`brightness: StateFlow<Float?>`, and the unchanged chrome/bottom-bar signatures are used consistently. Accent `Color(0xFF9B8CFF)` defined locally in `:feature:reader`.
- **Risk:** the right-side overlay must not steal taps/page-turns — `detectVerticalDragGestures` claims only vertical drags; Task 1 Step 7 verifies tap/long-press/page-turn on-device before committing. Chrome legibility over light themes uses a dark translucent glass; verified in Task 2.
