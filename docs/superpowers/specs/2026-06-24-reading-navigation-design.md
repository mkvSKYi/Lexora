# Reading Navigation — TOC & Progress — Design (Plan B)

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** Plans 1–2 + Plan A (reader, tap-to-translate, reading appearance), merged to `main`. Plan A introduced the auto-hiding `ReaderChrome` whose top bar and empty bottom slot this plan extends.

## 1. Purpose

Add in-reader navigation: a table-of-contents sheet to jump between chapters, and
a bottom progress bar showing the overall percentage and current chapter with a
draggable scrubber to seek through the book.

## 2. Scope

### In scope (Plan B)
- **TOC button** in the reader top chrome bar (next to the Aa button).
- **TOC sheet:** a nested (hierarchical) list of chapters from the publication's
  table of contents; the current chapter is highlighted; tapping an entry jumps
  to it and closes the sheet.
- **Bottom progress bar** (fills the chrome bottom slot): overall book percentage,
  current chapter title, and a draggable scrubber (0..1) that seeks through the book.

### Out of scope (later)
- Bookmarks, in-book search, annotations/highlights.
- Per-chapter time-left estimates.
- Offline dictionary / saved words (deferred Plan 3); DeepL online engine.

## 3. TOC Sheet

- Source: Readium `publication.tableOfContents()` — a tree of `Link` (each may
  have `children`). Render hierarchically with indentation per depth.
- Current-chapter detection: from the current reading `Locator`, find the deepest
  TOC entry whose resource (`href`) is at or before the current position. This is
  a pure function over the flattened TOC + the current locator — unit-tested.
- Tap an entry → `navigator.go(link.locator)` (or the navigator's go-to-Link API)
  → navigate and dismiss the sheet.
- A book with no/empty TOC → the sheet shows an "No chapters" empty state.

## 4. Bottom Progress Bar

- **Percentage:** from `currentLocator.locations.totalProgression` (0..1), shown
  as a rounded percent.
- **Chapter title:** the current TOC entry's title (from §3 detection), or blank
  if none.
- **Scrubber:** a `Slider` bound to the total progression; dragging seeks. Seeking
  in reflowable EPUB is per-resource, so an overall fraction is mapped to a
  concrete position: use `publication.positions()` (the ordered list of positions,
  each a `Locator` with a `totalProgression`) — pick the position closest to the
  target fraction and `navigator.go(position)`. While dragging, show the target
  percent; commit the seek on release to avoid thrashing the navigator.
- The exact Readium 3.1.2 API (`tableOfContents()`, `positions()`, `Navigator.go`,
  and whether a `goToProgression`-style call exists) MUST be verified via context7
  at implementation time. If overall-percent seeking cannot be expressed cleanly,
  fall back to position-index or chapter-level seeking and note it.

## 5. Architecture

- `:feature:reader`:
  - `ReaderViewModel` extended: load `tableOfContents` + `positions` from the
    `Publication` on open; expose `toc: List<TocEntry>` (flattened with depth),
    `currentProgression: StateFlow<Float>`, `currentChapterTitle: StateFlow<String?>`
    (derived from the current locator + TOC), and a `fun seekTo(fraction: Float)`
    that resolves the fraction to a position and drives the navigator.
  - `TocEntry(title, href, depth, locator)` — a flat, depth-tagged TOC item.
  - `TocResolver` — pure functions: flatten the TOC tree to `List<TocEntry>`, and
    `currentEntry(toc, locator): TocEntry?`, and `positionForFraction(positions, f): Locator?`. Unit-tested.
  - `ReaderTocSheet` (nested list, current highlight) and `ReaderBottomBar`
    (percent + chapter + scrubber) Composables.
  - The navigator host gains a way to `go(locator)` (the fragment exposes a
    navigate hook on the `Session`, mirroring how preferences/selection are wired).
- Chrome: `ReaderChrome` top bar gains a TOC action; `ReaderBottomBar` is passed
  into the existing `bottomBar` slot.

Each unit has one responsibility: TOC/position math is a pure resolver (testable),
the navigator-drive is a thin host hook, and the sheets/bar are presentation.

## 6. Error Handling

- Empty/missing TOC → "No chapters" empty state; the bottom bar still shows percent.
- `positions()` empty or seek resolution fails → scrubber still shows percent but a
  seek is a no-op (never crash).
- A `go` to an invalid locator → caught; reader stays put.

## 7. Testing

- `TocResolver`: unit tests — flatten a nested TOC to depth-tagged entries;
  `currentEntry` picks the deepest entry at/before a locator; `positionForFraction`
  maps 0/0.5/1.0 to the expected positions (incl. empty-list → null).
- TOC navigation, current highlight, scrubber seek, percent/chapter display:
  on-device smoke test — open the TOC, jump to a chapter, confirm the page moves
  and the bar updates; drag the scrubber and confirm it seeks; confirm the current
  chapter highlights.

## 8. Spikes / Risks

1. **Spike (required):** Readium 3.1.2 `publication.tableOfContents()` shape,
   `publication.positions()`, and `EpubNavigatorFragment.go(...)` — prove a
   chapter jump and a fraction seek work before building the UI.
2. Current-chapter detection across nested TOC + spine ordering.
3. Scrubber seek responsiveness (commit on release, not on every drag tick).
