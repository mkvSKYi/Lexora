# Library Book Context Menu (Details + Delete) — Design

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** the existing `:feature:library` bookshelf, `:core:data` `LibraryRepository`, and `:core:database` (books / reading_progress / saved_words), all on `main`.

## 1. Purpose

Let the user long-press a book in the Library to open a context menu with two
actions: **Details** (view book info) and **Delete book** (remove the book and
all of its data, with confirmation). A normal tap still opens the reader.

## 2. Scope

### In scope
- Long-press a book card → a Material 3 `ModalBottomSheet` context menu.
- **Details** → a dialog showing title, author, "Added <date>", and "<N>% read".
- **Delete book** → a confirmation dialog, then a complete removal of the book:
  the EPUB file, the cover file, the `reading_progress` row, the `saved_words`
  rows for that book, and the `books` row.
- The bookshelf updates reactively (it already observes a Flow).

### Out of scope
- Rename / edit metadata; Share EPUB; Mark-as-finished; multi-select; a
  "finished" shelf. (Considered and deferred.)
- Any change to how books are imported or opened.

## 3. Interaction & UI

- `BookCard` gains an `onLongClick` via `Modifier.combinedClickable(onClick, onLongClick)`
  (`onClick` unchanged — opens the reader).
- Long-press sets the selected book in the ViewModel/screen state → shows
  `BookContextMenuSheet` (Material 3 `ModalBottomSheet`, consistent with
  `ReaderTocSheet`/`WordDictionarySheet`):
  - A small header: cover thumbnail + title + author.
  - Row **Details** (info icon).
  - Row **Delete book** (delete icon, `colorScheme.error` tint).
- **Details** → dismiss the menu, show `BookDetailsDialog` (Material 3
  `AlertDialog`): title (bold), author, "Added <formatted date>",
  "<N>% read" (rounded). A single "Close" button.
- **Delete book** → dismiss the menu, show a confirmation `AlertDialog`:
  title "Delete book?", text `Delete "<title>"? This also removes its reading
  progress and saved words.`, a `colorScheme.error` "Delete" confirm + "Cancel".
  Confirm → `viewModel.deleteBook(book)`.

## 4. Data & Deletion

`books` (`id, title, author, coverPath, filePath, addedAt, lastOpenedAt`),
`reading_progress` (PK `bookId`, `percent`), `saved_words` (`bookId`, …). There
is no DB-level cascade, so deletion is orchestrated explicitly.

- **`LibraryRepository.deleteBookCompletely(book: Book)`** (in `:core:data`,
  runs on `Dispatchers.IO`):
  1. Delete the EPUB file at `book.filePath` and the cover at `book.coverPath`
     (best-effort: missing files are ignored, never throw).
  2. `SavedWordDao.deleteByBookId(book.id)`.
  3. `ReadingProgressDao.deleteByBookId(book.id)` (new DAO method).
  4. `BookDao.deleteBook(book.id)` (already exists).
- New DAO queries:
  - `SavedWordDao`: `@Query("DELETE FROM saved_words WHERE bookId = :bookId") suspend fun deleteByBookId(bookId: Long)`.
  - `ReadingProgressDao`: `@Query("DELETE FROM reading_progress WHERE bookId = :bookId") suspend fun deleteByBookId(bookId: Long)`.
- The `Book` domain model gains `addedAt: Long` (mapped from `BookEntity.addedAt`)
  for the Details dialog. Progress for Details comes from
  `LibraryRepository.progressPercent(bookId: Long): Double` (reads
  `reading_progress.percent`, 0.0 when absent).

The deletion order (files → child rows → book row) means a failure mid-way never
leaves a `books` row pointing at deleted data; the books row is removed last.

## 5. Architecture

- `:core:database` — add `deleteByBookId` to `SavedWordDao` and
  `ReadingProgressDao`.
- `:core:data` — `LibraryRepository.deleteBookCompletely(book)` +
  `progressPercent(bookId)`; `Book` domain gains `addedAt`; the entity→domain
  mapper passes it through.
- `:feature:library` — `BookCard.onLongClick`; `BookContextMenuSheet`;
  `BookDetailsDialog`; the delete-confirm dialog; `LibraryViewModel.deleteBook(book)`
  + the selected-book menu/dialog state.

Each unit keeps one responsibility: the repository owns the multi-table+file
deletion; the ViewModel owns menu/dialog state and delegates; the composables are
presentation only.

## 6. Error Handling

- Missing files on delete → ignored (best-effort), the DB rows are still removed.
- Deleting the currently-open book is allowed; the user is in the Library to do
  it, and the bookshelf simply drops it.
- A DB failure during delete surfaces as the book remaining in the list (no
  crash); no partial-UI state because the list is Flow-driven from the DB.
- Empty/zero progress → "0% read".

## 7. Testing

- **`LibraryViewModel.deleteBook`** (fake repository) → calls
  `deleteBookCompletely(book)`.
- **`DefaultLibraryRepository.deleteBookCompletely`** (in-memory Room +
  temp files) → asserts the EPUB + cover files are gone and the `books`,
  `reading_progress`, and `saved_words` rows for that book are all removed, while
  another book's rows/files are untouched.
- **`progressPercent`** → returns the stored percent, 0.0 when no row.
- **On-device smoke:** long-press a book → menu; Details shows info; Delete →
  confirm → the book disappears and its files/rows are gone; a normal tap still
  opens the reader.

## 8. Risks

1. **Orphans / partial deletion** — mitigated by the files→children→book order
   and the repository test asserting full cleanup.
2. **Long-press vs tap** — `combinedClickable` must keep the existing tap-to-open
   behavior intact; the on-device smoke verifies both gestures.
