# Reading Navigation (TOC & Progress) Implementation Plan (Plan B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A TOC sheet to jump between chapters and a bottom bar showing percentage + current chapter with a draggable scrubber to seek through the book.

**Architecture:** Pure `TocResolver` functions (flatten the Readium TOC tree, detect the current chapter from a locator, map a fraction to a position) are unit-tested in isolation. `ReaderViewModel` loads the publication's TOC + positions on open and exposes them plus `currentProgression`/`currentChapterTitle`/`seekTo`/`goTo`; the navigator host gains a `go(Locator)` hook. `ReaderTocSheet` (nested list) and `ReaderBottomBar` (percent + chapter + scrubber) plug into the existing `ReaderChrome` (top TOC button + bottom slot).

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Coroutines/Flow, Readium 3.1.2 (`tableOfContents()`, `positions()`, `Navigator.go`). Test: JUnit4, Robolectric (for Readium `Locator`/`Url`), MockK, Turbine.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin only, Compose + Material 3. MVVM + repository, unidirectional Flow, Hilt DI.
- ALL versions via the catalog; no hardcoded versions in module build files.
- Module `:feature:reader` only.
- Builds on Plan A's `ReaderChrome` (top bar with back + Aa; an `onAa`/reveal model and an empty `bottomBar` slot). Add a TOC action to the top bar and fill the bottom slot — do NOT regress the appearance sheet, brightness/warmth, translation popover, or navigator wiring.
- Seeking in reflowable EPUB is per-resource: map an overall fraction to a concrete position via `publication.positions()` and `Navigator.go`. Commit the seek on slider RELEASE, not on every drag tick.
- Verify the exact Readium 3.1.2 API (`tableOfContents()`, `positions()`, `locatorFromLink`/`Link`→`Locator`, `Navigator.go`) via context7 before the Readium-touching tasks; if overall-percent seeking can't be expressed cleanly, fall back to position-index seeking and note it.
- `flowOf`/Turbine tests: drain with `cancelAndIgnoreRemainingEvents()`; `@OptIn(ExperimentalCoroutinesApi::class)` where `Dispatchers.setMain` is used. Readium `Locator`/`Url` construction in tests needs Robolectric (static `android.graphics`/`Uri` init).

---

### Task 1: TocEntry + TocResolver (pure logic)

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/navigation/TocEntry.kt`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/navigation/TocResolver.kt`
- Test: `feature/reader/src/test/kotlin/com/reader/feature/reader/navigation/TocResolverTest.kt`

**Interfaces:**
- Consumes: Readium `Link`, `Locator`, `Publication` (for `locatorFromLink` in a later task; Task 1 only flattens).
- Produces:
  - `data class TocEntry(val title: String, val href: String, val depth: Int, val locator: Locator?)` — a flat, depth-tagged TOC item. `locator` may be null until resolved (Task 2 fills it).
  - `object TocResolver`:
    - `fun flatten(links: List<Link>, depth: Int = 0): List<TocEntry>` — depth-first flatten of the TOC tree (each `Link.children` recursed at `depth+1`); `title` defaults to the href if a link has no title; `locator = null` here.
    - `fun currentEntryIndex(entries: List<TocEntry>, currentHref: String): Int?` — the index of the deepest/last entry whose `href` (path without fragment) equals the current resource href at or before the current one; null if none/empty.
    - `fun positionForFraction(positions: List<Locator>, fraction: Float): Locator?` — the position whose `locations.totalProgression` is closest to `fraction.coerceIn(0f,1f)`; null if `positions` is empty.

- [ ] **Step 1: Spike the Readium TOC/Link/Locator shape (context7)**

Confirm via context7: `Link.title: String?`, `Link.href` (a `Url` — how to get its path string, e.g. `href.toString()` and stripping the `#fragment`), `Link.children: List<Link>`; `Locator.href` (Url) and `Locator.locations.totalProgression: Double?`. Note the exact accessors in the report.

- [ ] **Step 2: Write the failing test (Robolectric)**

```kotlin
package com.reader.feature.reader.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.shared.publication.Link

@RunWith(RobolectricTestRunner::class)
class TocResolverTest {
    private fun link(title: String, href: String, children: List<Link> = emptyList()) =
        Link(href = org.readium.r2.shared.util.Url(href)!!, title = title, children = children)

    @Test fun flatten_tags_depth_depth_first() {
        val toc = listOf(
            link("Ch1", "ch1.html", listOf(link("Ch1.1", "ch1.html#s1"))),
            link("Ch2", "ch2.html"),
        )
        val flat = TocResolver.flatten(toc)
        assertEquals(listOf("Ch1", "Ch1.1", "Ch2"), flat.map { it.title })
        assertEquals(listOf(0, 1, 0), flat.map { it.depth })
    }

    @Test fun current_entry_is_deepest_at_or_before() {
        val entries = listOf(
            TocEntry("Ch1", "ch1.html", 0, null),
            TocEntry("Ch2", "ch2.html", 0, null),
        )
        assertEquals(1, TocResolver.currentEntryIndex(entries, "ch2.html"))
        assertEquals(0, TocResolver.currentEntryIndex(entries, "ch1.html"))
    }

    @Test fun fraction_maps_to_closest_position_and_empty_is_null() {
        assertNull(TocResolver.positionForFraction(emptyList(), 0.5f))
    }
}
```

(Adjust `Link`/`Url` construction to the exact 3.1.2 API from Step 1; keep the assertions' intent.)

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*TocResolverTest*"`
Expected: FAIL — unresolved `TocResolver`/`TocEntry`.

- [ ] **Step 4: Implement TocEntry + TocResolver**

`TocEntry.kt` — the data class above.

`TocResolver.kt` — `flatten` recurses depth-first appending `TocEntry(link.title ?: href, hrefString, depth, null)`; `currentEntryIndex` strips fragments and returns the index of the last entry matching `currentHref` (fall back to the last entry whose href appears at or before `currentHref` in `entries` order); `positionForFraction` returns `positions.minByOrNull { abs((it.locations.totalProgression ?: 0.0) - fraction) }`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :feature:reader:testDebugUnitTest --tests "*TocResolverTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/reader
git commit -m "feat: add toc resolver for chapter and position math"
```

---

### Task 2: ViewModel TOC/positions loading + navigator go hook

**Files:**
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/EpubReaderFragment.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `TocResolver`/`TocEntry` (Task 1); Readium `Publication.tableOfContents()`, `Publication.positions()`, `Publication.locatorFromLink(Link)`, `Navigator.go(Locator)`.
- Produces:
  - `ReaderViewModel`: `val toc: StateFlow<List<TocEntry>>` (loaded on Ready, each entry's `locator` resolved via `locatorFromLink`), `val currentProgression: StateFlow<Float>` (from the current locator), `val currentChapterTitle: StateFlow<String?>` (via `TocResolver.currentEntryIndex` over `toc`), `fun goTo(locator: Locator)`, `fun seekTo(fraction: Float)` (resolves via `TocResolver.positionForFraction` over the loaded positions then `goTo`).
  - The navigator host gains a navigate hook: extend `ReaderNavigatorHost.Session` with `goTo: (Locator) -> Unit` the fragment fulfils by calling `navigator.go(locator)`; the ViewModel's `goTo`/`seekTo` route through it.

- [ ] **Step 1: Spike (context7)**

Confirm: `suspend fun Publication.tableOfContents(): List<Link>`, `suspend fun Publication.positions(): List<Locator>`, `Publication.locatorFromLink(Link): Locator?`, and `Navigator.go(Locator, …): Boolean`/suspend signature. Note in the report. (Tasks 3/4 reuse.)

- [ ] **Step 2: Load TOC + positions + derive progression/chapter**

In `ReaderViewModel`, when the publication is Ready, launch a load: `flatten(publication.tableOfContents())` then resolve each entry's `locator` via `locatorFromLink`; store `positions = publication.positions()`. Set `toc`. Derive `currentProgression` from the existing current-locator source (the reader already tracks `onLocatorChanged`); derive `currentChapterTitle` from `TocResolver.currentEntryIndex(toc, currentHref)`.

- [ ] **Step 3: Add the navigator go hook + goTo/seekTo**

Extend `ReaderNavigatorHost.Session` with `goTo: (Locator) -> Unit = {}`. In `EpubReaderFragment`, set it (or collect a navigate channel) so it calls `navigator.go(locator)` on `viewLifecycleOwner.lifecycleScope`. In `ReaderScreen`, wire the Session's `goTo` to the navigator. `ReaderViewModel.goTo(locator)` invokes the host hook; `seekTo(fraction)` resolves `positionForFraction(positions, fraction)` and calls `goTo`.

- [ ] **Step 4: On-device verification**

Build + install. Temporarily (debug-only, removed before commit) call `seekTo(0.5f)` on open and confirm the book jumps to ~50%; log `currentChapterTitle`/`currentProgression` and confirm they update as you page.
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug; ~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
```
Expected: a programmatic seek moves the page; chapter/progression update on paging. Remove the debug trigger before committing.

- [ ] **Step 5: Commit**

```bash
git add feature/reader
git commit -m "feat: load toc and positions and drive navigator seek"
```

---

### Task 3: TOC sheet + top-bar TOC button

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/navigation/ReaderTocSheet.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/chrome/ReaderChrome.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `TocEntry` + `ReaderViewModel.toc`/`currentChapterTitle`/`goTo` (Task 2).
- Produces: `@Composable fun ReaderTocSheet(entries: List<TocEntry>, currentHref: String?, onEntryClick: (TocEntry) -> Unit, onDismiss: () -> Unit)` — a `ModalBottomSheet` (or full-height sheet) with a `LazyColumn` of entries, indented by `depth`, the current entry highlighted; tap → `onEntryClick`. Empty `entries` → a "No chapters" state.

- [ ] **Step 1: Implement the TOC sheet**

`ReaderTocSheet.kt`: `ModalBottomSheet`; `LazyColumn` over `entries`; each row `Modifier.padding(start = (16 + depth*16).dp)`, title text, highlighted background/weight when its href matches `currentHref`; click calls `onEntryClick(entry)`. Empty list → centered "No chapters".

- [ ] **Step 2: Add the TOC action to the chrome top bar**

In `ReaderChrome`, add an `onToc: () -> Unit` param and a TOC `IconButton` (e.g. a list icon) in the top bar next to the Aa action. Update the `ReaderChrome` call site.

- [ ] **Step 3: Wire the sheet in `ReaderScreen`**

A `tocVisible` state; `onToc = { tocVisible = true }`; show `ReaderTocSheet(entries = toc, currentHref = currentChapterHref, onEntryClick = { viewModel.goTo(it.locator!!); tocVisible = false }, onDismiss = { tocVisible = false })`. Skip entries with a null `locator` on click (no-op).

- [ ] **Step 4: On-device verification**

Build + install. Open a book → reveal chrome → tap TOC → the chapter list appears (nested/indented), the current chapter highlighted → tap a different chapter → the reader jumps there and the sheet closes. Screenshot the TOC to `…/scratchpad/toc_*.png`. No crash.

- [ ] **Step 5: Commit**

```bash
git add feature/reader
git commit -m "feat: add toc sheet with chapter navigation"
```

---

### Task 4: Bottom progress bar with scrubber

**Files:**
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/navigation/ReaderBottomBar.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `ReaderViewModel.currentProgression`/`currentChapterTitle`/`seekTo` (Task 2).
- Produces: `@Composable fun ReaderBottomBar(progression: Float, chapterTitle: String?, onSeek: (Float) -> Unit)` — a surface with the chapter title, a `Slider` (0..1) bound to `progression`, and a percent label. The slider tracks a local drag value; it calls `onSeek(value)` on RELEASE (`onValueChangeFinished`), not on every tick.

- [ ] **Step 1: Implement the bottom bar**

`ReaderBottomBar.kt`: a `Surface`/`Column` — top row: `chapterTitle ?: ""` (start) and `"${(displayValue*100).roundToInt()}%"` (end); below: a `Slider(value = drag ?: progression, onValueChange = { drag = it }, onValueChangeFinished = { drag?.let(onSeek); drag = null })`, where `drag` is a `remember { mutableStateOf<Float?>(null) }`. While dragging, the percent label reflects `drag`.

- [ ] **Step 2: Plug it into the chrome bottom slot**

In `ReaderScreen`, pass `bottomBar = { ReaderBottomBar(progression = currentProgression, chapterTitle = currentChapterTitle, onSeek = viewModel::seekTo) }` into `ReaderChrome` (replacing the empty slot from Plan A). Collect the StateFlows with `collectAsStateWithLifecycle`.

- [ ] **Step 3: On-device verification**

Build + install. Open a book → reveal chrome → the bottom bar shows the current percent + chapter; drag the scrubber to ~75% and release → the reader jumps there, the percent updates, the chapter title updates; page forward and confirm the percent/chapter advance. Screenshot to `…/scratchpad/bottombar_*.png`. Confirm the appearance sheet (Aa), translation tap, and swipe still work. No crash.

- [ ] **Step 4: Commit**

```bash
git add feature/reader
git commit -m "feat: add bottom progress bar with seek scrubber"
```

---

## Self-Review Notes

- **Spec coverage:** TOC button (Task 3), nested TOC + current highlight + tap-to-jump (Tasks 1–3), bottom bar percent + chapter + scrubber seek (Tasks 1–2, 4), seek-by-fraction via positions (Tasks 1–2), commit-on-release (Task 4). Empty-TOC and seek-failure handling are covered (Tasks 1, 3). Bookmarks/search/time-left are out of scope.
- **Type consistency:** `TocEntry(title, href, depth, locator)`, `TocResolver.{flatten, currentEntryIndex, positionForFraction}`, `ReaderViewModel.{toc, currentProgression, currentChapterTitle, goTo, seekTo}`, `ReaderNavigatorHost.Session.goTo`, `ReaderTocSheet(entries, currentHref, onEntryClick, onDismiss)`, `ReaderBottomBar(progression, chapterTitle, onSeek)` used consistently.
- **Risk:** Tasks 1–2 depend on Readium 3.1.2 `tableOfContents()`/`positions()`/`locatorFromLink`/`go` — front-loaded context7 spikes (1.1, 2.1) with a position-index fallback if percent-seek isn't clean; Task 1 is pure and fully unit-tested.
- **Verification:** every UI/Readium task ends with an on-device check of the actual feature (TOC jump, scrubber seek), per the Plan-1 lesson; the appearance/translation features must be re-confirmed intact in Task 4.
