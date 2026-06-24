# Saved Words — Design (Plan 3a)

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** Plans 1–2 + A + B (reader, tap-to-translate, appearance, navigation), merged to `main`. This is the first half of Plan 3; the offline Wiktionary dictionary is Plan 3b (separate, follows this).

## 1. Purpose

When the user translates a word (or phrase) in the reader, let them save it to a
personal vocabulary list with one tap, and review/manage that list on a dedicated
screen. This is independent of the offline dictionary (Plan 3b) and ships first.

## 2. Scope

### In scope (Plan 3a)
- A **Save** button on the translation popover (the `Result` state — word OR phrase).
- Persist a saved entry: term, Ukrainian translation, context sentence (best-effort),
  source book id + title, created-at timestamp.
- A **Saved Words** screen: a list of saved entries (term + translation + context +
  book), with delete; empty state; reached from a button in the Library top bar.
- De-duplication: saving the same term from the same book updates the existing
  entry rather than creating a duplicate.

### Out of scope (later)
- The offline Wiktionary dictionary card (IPA, POS, definitions) — Plan 3b. (When
  3b lands, saved entries can carry richer fields, but 3a stores only what the
  translation popover already has.)
- Flashcards / spaced repetition over saved words.
- Tapping a saved entry to jump back to its book location.
- Export/sync.

## 3. Save Flow

- The translation popover (`:feature:translation`) gains an `onSave: () -> Unit`
  callback and a Save button (shown only for the `Result` state). The popover does
  NOT persist anything itself — it raises the intent.
- The reader (`ReaderScreen`) owns the Save wiring because it knows the `bookId`
  and `bookTitle`: on Save it builds a `SavedWord` from the current popover
  `Result` (term = source, translation = translation) + book id/title + now, and
  calls `SavedWordsRepository.save(...)`.
- **Context sentence (best-effort):** for a word tap, the reader resolves the
  enclosing sentence at the saved word's location via the existing sentence
  resolver (Plan 2's `SentenceResolver`), storing it as `contextSentence`. For a
  phrase/sentence save (long-press) the term already is the phrase, so
  `contextSentence` is null. If sentence resolution fails or isn't available,
  `contextSentence` is null. Saving never blocks on context resolution.
- Brief visual confirmation after save (e.g. the Save button shows a "saved" state
  or a snackbar). Re-saving the same term+book is idempotent (updates timestamp).

## 4. Data Model

- `:core:database`: `SavedWordEntity`
  `(id: Long PK autoGenerate, term: String, translation: String, contextSentence: String?, bookId: Long, bookTitle: String, createdAt: Long)`,
  with a unique index on `(term, bookId)` for de-duplication (upsert on conflict).
  Add `SavedWordDao` (`observeAll(): Flow<List<SavedWordEntity>>` ordered by
  `createdAt` desc, `upsert(entity)`, `deleteById(id)`, `existsByTermAndBook(...)`).
  Bump `ReaderDatabase` version with a migration that creates the table (don't wipe
  existing books/progress).
- `:core:data`: `SavedWord` domain model + `SavedWordsRepository`
  (`observe(): Flow<List<SavedWord>>`, `suspend fun save(word: SavedWord)`,
  `suspend fun delete(id: Long)`), Room-backed, Hilt-bound, with entity↔domain mappers.

## 5. Saved Words Screen (`:feature:saved`)

A NEW module `:feature:saved` (portfolio-clean separation):
- `SavedWordsViewModel` (`@HiltViewModel`, injects `SavedWordsRepository`):
  `uiState: StateFlow<SavedWordsUiState>` (Loading / Content(list) ), `fun delete(id)`.
- `SavedWordsScreen(onBack: () -> Unit, viewModel = hiltViewModel())`: a `Scaffold`
  with a top bar (title + back), a `LazyColumn` of saved entries (term bold +
  translation + muted context + book title/date), a delete affordance per row
  (trailing icon or swipe-to-dismiss), and an empty state "No saved words yet".
- Access + navigation: a "Saved" `IconButton` (a bookmark/list icon) in the
  Library top bar → navigates to a new `saved` route in the app `NavHost`
  (`ReaderNavHost`), with back to the library.

## 6. Architecture / Modules

- `:core:database` — `SavedWordEntity`, `SavedWordDao`, DB version bump + migration.
- `:core:data` — `SavedWord`, `SavedWordsRepository` (+ impl, DI, mappers).
- `:feature:translation` — Save button + `onSave` callback on `TranslationPopover`.
- `:feature:reader` — wire `onSave` to build the `SavedWord` (bookId/title +
  best-effort context) and call the repository.
- `:feature:saved` (NEW) — `SavedWordsScreen` + `SavedWordsViewModel`.
- `:app` — Library top-bar "Saved" button + `saved` nav route; depends on `:feature:saved`.

Each unit has one responsibility: persistence (entity/dao/repo), the save intent
(popover button), the save wiring (reader), and the list UI (`:feature:saved`).

## 7. Error Handling

- Save failure (DB error) → caught; a brief "Couldn't save" message; never crash.
- Re-saving the same term+book → upsert (no duplicate, no error).
- Context-sentence resolution failure → `contextSentence = null`, save still succeeds.
- Deleting a non-existent id → no-op.
- DB migration must not drop existing `books`/`reading_progress` data.

## 8. Testing

- `SavedWordDao` (Room in-memory): upsert + observe order; unique `(term, bookId)`
  upsert replaces; delete; the migration preserves existing tables.
- `SavedWordsRepository`: entity↔domain mapping; save/delete delegate; dedupe.
- `SavedWordsViewModel` (fake repo): Loading→Content; delete removes from the list.
- On-device smoke: translate a word → tap Save → open the Saved screen from the
  library → the entry appears with translation + context + book → delete it →
  it disappears. Re-saving the same word doesn't duplicate.

## 9. Risks

1. Room DB version bump + migration on a device that already has books/progress —
   the migration must be additive (create table only). Verify the existing data
   survives on-device (the test book/library should remain after upgrade).
2. Best-effort context capture reuses Plan 2's sentence resolver at save time;
   if that coordination is awkward, `contextSentence` is null (acceptable for 3a).
