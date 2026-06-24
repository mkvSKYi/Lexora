# Library Redesign — "Aurora Dark" — Design

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** the existing `:feature:library` (bookshelf grid, long-press context menu) + `:core:data`/`:core:database`, on `main`.

## 1. Purpose

The Library currently feels empty and plain: a sparse 2-column grid of generic
placeholder covers, a bare top bar, no hierarchy or motion. Redesign it into a
premium, "Aurora Dark" reading-app home that impresses on first open — generated
gradient cover art, a "Continue reading" hero, progress everywhere, refined
elevated cards, and tasteful motion.

## 2. Visual Language (Aurora Dark)

- **Canvas:** a deep near-black background with a subtle top-down aurora glow
  (a faint vertical gradient using two low-alpha accent colors) behind the header,
  so the screen reads as crafted, not flat black.
- **Accent:** a vibrant violet→cyan family for highlights (progress bars, the hero
  glow, the FAB), drawn from a curated palette (NOT dynamic color, for consistency).
- **Shape & elevation:** large radii (cards 20dp, hero 28dp), soft ambient shadows,
  generous spacing.
- **Type:** large bold screen title; clear title/author hierarchy on cards.
- **Motion:** staggered fade+rise entrance for the hero and grid items; a press
  scale-down (~0.96) on cards for tactility.

## 3. Components

### 3.1 Generated cover art — the keystone
A `BookCover` composable renders every book's cover at a 2:3 ratio:
- **No cover image (the common case):** a deterministic diagonal **gradient**
  derived from the book — `coverPalette(seed)` (pure function) hashes the seed
  (title, falling back to id) into one of ~8 curated vibrant gradient pairs, so a
  given book always gets the same handsome gradient. Over it: the book **title**
  in large bold type and the **author** smaller, padded, with a soft bottom scrim
  for legibility, plus a faint large initial glyph watermark.
- **Cover image present:** the real image (Coil), with a subtle bottom gradient
  scrim so overlaid text/progress stays legible.

This single change removes the "empty placeholder" feeling everywhere it's used.

### 3.2 Continue reading hero
`ContinueReadingCard` — shown when at least one book has been opened
(`lastOpenedAt != null`); it features the most-recently-opened book:
- A wide rounded card (28dp) with the book's cover thumbnail on the left, the
  title + author on the right, a slim **progress bar** + "NN% • Continue", and an
  aurora accent glow. Tapping it opens the reader.

### 3.3 Book grid card (redesign)
`BookCard` — elevated rounded card: the `BookCover` (3.1) with a thin **progress
bar** pinned to the bottom of the cover (hidden at 0%), title (2 lines) + author
beneath. Press scales to ~0.96. Tap opens; **long-press keeps the existing context
menu** (Details/Delete) unchanged.

### 3.4 Header
A large "Library" title with a "<N> books" subtitle and the saved-words bookmark
action; the aurora glow sits behind it. Scrolls with the content (single scroll
container, not a pinned app bar) so the screen breathes.

### 3.5 Empty state (redesign)
`EmptyLibrary` — a centered gradient-ringed book glyph, a headline ("Your library
is empty"), a one-line invite, and a prominent filled **Import EPUB** button (in
addition to the FAB).

## 4. Data

The hero and the per-card progress bars need each book's progress, so the library
emits books **with** progress (one reactive query, no N+1):
- `BookWithProgress(book: Book, percent: Double)` (domain, in `:core:data`).
- `BookDao.observeBooksWithProgress(): Flow<List<BookWithProgressRow>>` via
  `SELECT b.*, COALESCE(rp.percent, 0.0) AS percent FROM books b LEFT JOIN
  reading_progress rp ON rp.bookId = b.id ORDER BY b.lastOpenedAt DESC, b.addedAt DESC`
  (a Room POJO `@Embedded book: BookEntity` + `percent: Double`), mapped to domain.
- `LibraryRepository.observeBooksWithProgress(): Flow<List<BookWithProgress>>`.
- `LibraryViewModel.uiState` switches to carry `List<BookWithProgress>`;
  `LibraryUiState.Content(books: List<BookWithProgress>)`. The most-recently-opened
  book (first row whose `book.lastOpenedAt != null`) is the hero; the grid shows
  all books.

`percent` is a 0..1 fraction (as written by the reader). Existing
`observeBooks()`/`progressPercent`/`deleteBookCompletely` stay; the redesign adds
the with-progress flow alongside.

## 5. Architecture / Files

- `:core:database` — `BookDao.observeBooksWithProgress()` + the `BookWithProgressRow` POJO.
- `:core:data` — `BookWithProgress` domain + mapper; `LibraryRepository.observeBooksWithProgress()`.
- `:feature:library`:
  - `BookCover.kt` — `BookCover(book, modifier)` + pure `coverPalette(seed: String): CoverGradient` + a curated palette.
  - `ContinueReadingCard.kt` — the hero.
  - `LibraryScreen.kt` — restructured into a single scrolling `LazyVerticalGrid`
    (header + hero as full-span items, then the grid); refined `BookCard`;
    redesigned `EmptyLibrary`; entrance animation; long-press menu wiring unchanged.

Each composable file owns one piece; `coverPalette` is pure and unit-tested.

## 6. Error Handling / Edge Cases

- 0% progress → no progress bar shown (card) / hero shows "Start reading".
- No opened book → no hero, grid only.
- Long title/author → ellipsize (title 2 lines, author 1).
- Missing/!= image cover → generated gradient (never a broken image).
- Single book that is the hero → it also appears in the grid (acceptable; the grid
  is "all books").

## 7. Testing

- `coverPalette` (pure) — deterministic: same seed → same gradient; different seeds
  spread across the palette; never returns an out-of-range index.
- `BookDao.observeBooksWithProgress` (Robolectric in-memory Room) — returns each
  book with its `reading_progress.percent`, `0.0` when no progress row, ordered by
  recency.
- `LibraryViewModel` — `uiState` emits `Content(List<BookWithProgress>)`; hero
  selection picks the most-recently-opened book.
- On-device smoke (S25 Ultra): the redesigned screen renders (aurora header, hero
  with progress, gradient cover cards with progress bars, entrance motion); tap
  opens; long-press still shows Details/Delete; empty state looks right (verify by
  temporarily showing it or reasoning from one book).

## 8. Risks

1. **Scope/taste** — this is craft-heavy; the implementation will iterate on-device
   (screenshots) to actually look premium, not just compile.
2. **Generated covers legibility** — the bottom scrim + curated palette must keep
   title/author readable on every gradient; verified on-device.
3. **No regression** — long-press context menu, tap-to-open, and delete must keep
   working through the restructure; the smoke verifies them.
