# Saved Words Redesign + Learned/Stats/Filter/Define — Design

**Date:** 2026-06-25
**Status:** Approved
**Builds on:** `:feature:saved` (Saved Words list), `:core:database`/`:core:data` (`saved_words`), the Aurora redesign (library/reader), and `:feature:translation` (`WordLookupViewModel`/`WordDictionarySheet`), all on `main`.

## 1. Purpose

The Saved Words screen is a plain list. Redesign it into a premium "Aurora Dark"
vocabulary screen and add features: **mark a word as learned** (its card turns
green), a **stats header** with learned progress, **All / Learning / Learned**
filter chips, and **tap a word to see its dictionary definitions** (reusing the
reader's dictionary sheet).

## 2. Data — the `learned` flag

- `SavedWordEntity` gains `val learned: Boolean = false`; the domain `SavedWord`
  gains `val learned: Boolean`; mappers pass it through.
- **Room migration v2→v3** (DB `version = 3`): `MIGRATION_2_3` runs
  `ALTER TABLE saved_words ADD COLUMN learned INTEGER NOT NULL DEFAULT 0`,
  registered in `DatabaseModule` alongside `MIGRATION_1_2`. Existing saved words
  survive and default to not-learned.
- `SavedWordDao`: `@Query("UPDATE saved_words SET learned = :learned WHERE id = :id") suspend fun updateLearned(id: Long, learned: Boolean)`.
- `SavedWordsRepository`: `suspend fun markLearned(id: Long, learned: Boolean)` → `dao.updateLearned(...)`.

## 3. ViewModel

`SavedWordsViewModel` exposes:
- `uiState: StateFlow<SavedWordsUiState>` where `Content` carries the FULL list
  `words: List<SavedWord>` plus computed `learnedCount: Int` / `totalCount: Int`.
- A `filter: StateFlow<SavedWordsFilter>` (`ALL` | `LEARNING` | `LEARNED`), with
  `setFilter(...)`. The screen derives the visible list from `words` + `filter`.
- `toggleLearned(id: Long, learned: Boolean)` → `repo.markLearned`.
- `delete(id: Long)` (existing).

Stats are computed from the full list so they don't change with the filter.

## 4. Screen (Aurora Dark)

A single scrolling `LazyColumn` over the dark canvas with a top aurora glow:
- **Header**: large "Saved Words" title + a small subtitle (e.g. "N words").
- **Stats card**: an elevated rounded card — total + learned counts and a
  `LinearProgressIndicator` (accent) showing `learnedCount / totalCount`
  ("12 of 40 learned"). Hidden when there are no words.
- **Filter chips**: `All` · `Learning` · `Learned` (Material 3 `FilterChip`),
  reflecting `filter`.
- **Word cards** (one per visible word): elevated rounded card — the term
  (`titleMedium`, bold), the translation (accent, `bodyLarge`), the context
  sentence (quoted, muted, italic, max 2 lines) when present, and a footer
  "book · date". Two actions: a **learned toggle** (a circle that fills with a
  green check when learned) and a **delete** (trash). Tapping the card body opens
  the dictionary sheet (§5).
- **Learned styling**: a learned card uses a green-tinted container + a green
  accent + the filled check badge, so learned words read as "done" at a glance.
- **Empty state**: an aurora-ringed glyph + "No saved words yet" + a one-liner.

Accent `Color(0xFF9B8CFF)` (redefined locally, matching the other modules);
learned green `Color(0xFF3DDC84)`-family.

## 5. Tap a word → dictionary definitions

`:feature:saved` adds a dependency on `:feature:translation`. The screen holds a
`WordLookupViewModel` (`hiltViewModel()`); tapping a word card calls
`wordVm.onWord(word.term)` and shows `WordDictionarySheet(state, …)`.
`WordDictionarySheet` gains an optional `showSave: Boolean = true`; the
saved-words screen passes `showSave = false` (the word is already saved), so the
sheet is read-only (word + IPA + POS + definitions + the single translation, with
the existing 5-cap "Show more"). The reader keeps `showSave = true`.

## 6. Error Handling / Edge Cases

- Migration must not drop rows; `DEFAULT 0` gives existing words `learned = false`.
- Toggling learned is idempotent; the list updates reactively via the Flow.
- `totalCount == 0` → no stats card, show the empty state.
- A word with no dictionary entry → the sheet shows its ML Kit translation (the
  dictionary sheet already handles dict-miss); definitions may be empty.
- Filter `LEARNED`/`LEARNING` with an empty result → a small "nothing here" row
  (not the full empty state).

## 7. Architecture / Files

- `:core:database` — `SavedWordEntity.learned`; `MIGRATION_2_3` + `version = 3`;
  `SavedWordDao.updateLearned`; `DatabaseModule` registers the migration.
- `:core:data` — `SavedWord.learned` + mapper; `SavedWordsRepository.markLearned`.
- `:feature:translation` — `WordDictionarySheet(showSave: Boolean = true)`.
- `:feature:saved` — `build.gradle.kts` adds `:feature:translation`;
  `SavedWordsViewModel` (filter + stats + toggleLearned); `SavedWordsUiState`
  (Content carries counts) + a `SavedWordsFilter` enum; the redesigned
  `SavedWordsScreen` (header, stats, chips, aurora cards, learned-green,
  tap-to-define).

## 8. Testing

- **Migration v2→v3** (Robolectric): open a v2 DB with a saved word, run the
  migration, assert the row survives and `learned == false`.
- **`SavedWordsViewModel`** (fake repo): `toggleLearned` delegates; stats
  (`learnedCount`/`totalCount`) computed; the filtered list for each
  `SavedWordsFilter`.
- **`SavedWordsRepository.markLearned`** (in-memory Room): updates the row.
- **On-device smoke** (S25 Ultra): the screen renders premium (header, stats,
  chips, cards); toggling learned turns a card green and bumps the progress;
  filtering works; tapping a word shows its definitions sheet; delete still works.

## 9. Risks

1. **DB migration** — the one irreversible-on-failure step; covered by the
   migration test asserting row survival + default.
2. **`:feature:saved` → `:feature:translation` dependency** — ensure no Hilt/graph
   cycle (translation doesn't depend on saved); build verifies.
3. **Reusing `WordDictionarySheet`** — the `showSave` flag must not regress the
   reader's Save path (default true keeps it).
