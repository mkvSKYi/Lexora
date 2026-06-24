# Reader Redesign + Brightness Swipe — Design

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** `:feature:reader` (Readium navigator host, `ReaderChrome`, `ReaderBottomBar`, warmth/brightness, tap-to-translate), on `main`.

## 1. Purpose

Two things: (1) a **right-side vertical-swipe brightness gesture** (swipe up =
brighter, down = dimmer) with a sleek indicator, like a video player; and (2) a
**premium "Aurora" redesign of the reader chrome** (top bar, bottom bar, an
always-visible progress hairline) so the open book feels as crafted as the
redesigned library. The page text itself stays Readium-rendered (existing
light/sepia/dark/AMOLED themes); the redesign is the chrome, indicators, and motion.

## 2. Brightness Swipe Gesture

- A full-height gesture zone on the **right ~40%** of the screen (below the top
  reveal strip, so it doesn't fight chrome reveal), drawn over the WebView with a
  `pointerInput { detectVerticalDragGestures(...) }`. The detector only claims a
  **vertical-dominant** drag (past touch slop), so word-taps, long-press, and
  horizontal page-turns still pass through to the WebView untouched.
- On drag, brightness moves: a full content-height drag spans the whole 0..1
  range. It starts from the current effective brightness
  (`brightness ?: DEFAULT 0.5`), and each drag step applies
  `next = (level - dragDeltaPx / heightPx).coerceIn(0f, 1f)` (up = negative
  delta = brighter). It calls the existing `viewModel.setBrightness(next)` live
  (same path the Aa slider uses; the slider reactively follows).
- A pure `nextBrightness(current, dragDeltaPx, heightPx)` helper holds the math
  (unit-tested).

### Brightness indicator (vertical bar, right edge)
- `BrightnessIndicator(level: Float, visible: Boolean)` — a tall rounded pill near
  the right edge, vertically centered: a track + a fill rising to `level`, an
  aurora accent, a small sun glyph + "NN%". It appears while dragging and **fades
  out ~600 ms** after the gesture ends (a reset timer + `animateFloat`/
  `AnimatedVisibility`). It never blocks input (drawn above, but `level`-only,
  no pointerInput).

## 3. Premium Chrome (Aurora)

- **Top bar** (`ReaderChrome`): replace the flat `TopAppBar` with a floating,
  translucent rounded "glass" surface (low-alpha dark + subtle aurora tint,
  rounded, slight elevation) holding back · chapter title · TOC · Aa. Same
  `AnimatedVisibility` show/hide, refined to slide+fade. The top reveal strip and
  the `onRevealStripTap` behavior stay.
- **Bottom bar** (`ReaderBottomBar`): a floating translucent rounded card with the
  percent + chapter title + the seek slider, restyled to match (aurora accent on
  the slider/active track). Same `progression`/`chapterTitle`/`onSeek` contract.
- **Progress hairline:** a thin (≈2 dp) reading-progress bar pinned to the very
  bottom edge, reflecting `currentProgression`, **always visible** (even when the
  chrome is hidden) as a low-key aurora indicator. Rendered in `ReaderScreen`
  behind the chrome bars.
- Reuse the library's accent (`AuroraAccent` = `Color(0xFF9B8CFF)`). The chrome's
  glass must stay legible over both light and dark reading themes (use a dark
  translucent scrim regardless of theme, so controls read on a white page too).

## 4. Architecture / Files

- `:feature:reader`:
  - `BrightnessGesture.kt` — pure `nextBrightness(current: Float, dragDeltaPx: Float, heightPx: Float): Float`.
  - `BrightnessIndicator.kt` — the vertical-bar composable.
  - `ReaderScreen.kt` — add the right-side gesture zone (calls `viewModel.setBrightness`, drives the indicator state) + the always-on progress hairline; pass `currentProgression`.
  - `chrome/ReaderChrome.kt` — restyle the top bar to the floating glass surface.
  - `navigation/ReaderBottomBar.kt` — restyle to the floating translucent card.
- No data-layer or ViewModel API changes (brightness setter/state already exist).

## 5. Edge Cases / Error Handling

- Gesture must NOT steal taps/long-press/page-turns — the vertical-drag detector
  only claims vertical-dominant drags; verified on-device (this repo has a known
  WebView-overlay-steals-taps gotcha — the warmth overlay deliberately has no
  pointerInput, and the popover scrim is carefully scoped).
- Brightness clamps to 0..1; starting from `null` uses 0.5 as the base.
- The gesture zone sits below the popover scrim, so an open translation
  popover/dictionary sheet takes touch priority (no brightness change while a card
  is up).
- Indicator hidden at rest (no level shown until the first drag of a session).
- Hairline at `progression = 0` shows an empty track (not absent), so the bottom
  edge always has the thin line.

## 6. Testing

- `nextBrightness` (pure): up increases, down decreases, clamps at 0 and 1, base
  from a mid value.
- On-device smoke (S25 Ultra): right-side swipe up brightens / down dims with the
  vertical-bar indicator fading out; **word-tap still translates, long-press still
  translates a sentence, horizontal swipe still turns the page, the top reveal +
  Aa/TOC still work**; the redesigned top/bottom bars render premium over a dark
  AND a light theme; the progress hairline shows at the bottom with chrome hidden.

## 7. Risks

1. **Overlay stealing input** (the main one) — mitigated by using
   `detectVerticalDragGestures` (claims only vertical drags) and verified
   on-device against tap/long-press/page-turn.
2. **Chrome legibility across themes** — the glass uses a dark translucent base so
   controls stay readable on a white sepia/light page; verified on-device on light
   + dark.
3. **Brightness write chattiness** — `setBrightness` persists on each drag step
   (same as the existing slider); acceptable.
