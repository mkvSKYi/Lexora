# Offline Dictionary — UI & Reader Integration — Design (Plan 3b-2)

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** Plan 3b-1 (`:core:dictionary` backend + bundled `dictionary.db`), merged to `main`. This completes Plan 3b.

## 1. Purpose

Surface the offline dictionary in the reader: tapping a single word opens a
bottom sheet showing its IPA, part of speech, English definitions, and Ukrainian
translations (falling back to the ML Kit machine translation when the word isn't
in the dictionary). Long-pressing a phrase/sentence keeps the existing compact
ML Kit popover. Saving from the sheet reuses the existing saved-words flow.

## 2. Scope

### In scope (3b-2)
- `WordLookupViewModel`: on a single-word tap, query `DictionaryRepository`; if
  found, expose the dictionary entry; if not, fall back to ML Kit translation.
- `WordDictionarySheet` (Material 3 `ModalBottomSheet`): word + IPA + POS +
  English definitions + Ukrainian translations (or the ML Kit translation when no
  entry) + a Save button + Loading/Error states.
- Reader integration: a single-word tap opens the dictionary sheet; a phrase
  long-press keeps the existing translation popover. Save persists via the
  existing reader save flow (`SavedWord`).
- Wire `:app` → `:core:dictionary` so the `dictionary.db` asset ships in the APK.
- Dedupe (order-preserving) the displayed translations/definitions.

### Out of scope (later)
- Changing the `SavedWord` schema (still term + translation + context + book).
- Saving richer dictionary fields (IPA/POS/definitions) into saved words.
- DeepL; example sentences; audio pronunciation; dictionary search screen.

## 3. Tap Routing (word vs phrase)

`SelectionEvent` gains `val isWord: Boolean`. The reader's word-tap path
(`WordResolver`) emits `isWord = true`; the long-press sentence path
(`SentenceResolver`) emits `isWord = false`. In `ReaderScreen`:
- `isWord = true` → `WordLookupViewModel.onWord(text)` + show the
  `WordDictionarySheet`.
- `isWord = false` → the existing `TranslationViewModel.onTextSelected(text)` +
  the existing popover (unchanged).

The "first tap dismisses" behavior, anchor handling, and Save wiring for the
popover path stay exactly as they are.

## 4. WordLookupViewModel

`@HiltViewModel WordLookupViewModel(dictionary: DictionaryRepository, engine: TranslationEngine)`:
- `val lookupState: StateFlow<WordLookupState?>` (null = sheet hidden).
- `WordLookupState` (sealed): `Loading`; `Entry(word, ipa, partOfSpeech, definitions, translations)`; `Machine(word, translation)`; `Error(message)`.
- `fun onWord(word: String)`: blank → ignore; else `Loading`; `dictionary.lookup(word)` →
  if non-null → `Entry(...)` (dedupe lists, order-preserving); else
  `engine.ensureModelsReady()` + `engine.translate(word)` → `Machine(word, translation)`
  or `Error` on failure.
- `fun dismiss()` → null.

This mirrors `TranslationViewModel`'s shape and lives in `:feature:translation`
(which now depends on `:core:dictionary`).

## 5. WordDictionarySheet

`@Composable fun WordDictionarySheet(state: WordLookupState, onSave: (term: String, translation: String) -> Unit, onDismiss: () -> Unit)`:
- `ModalBottomSheet` (more room than the popover).
- `Entry`: word (`headlineSmall`/bold) + IPA (`bodyMedium`, muted) + POS
  (`labelMedium`, muted); a section of Ukrainian translations (chips or a list);
  a section of English definitions (numbered list). A **Save** button → saves the
  word + its first Ukrainian translation (or first definition if none).
- `Machine`: word + the ML Kit Ukrainian translation + Save (saves word + that translation).
- `Loading` → progress; `Error` → message + dismiss.
- The Save callback hands `(term, translation)` up to the reader, which persists
  with the book id/title + best-effort context (the existing `saveCurrentWord`).

## 6. Architecture / Modules

- `:feature:translation` — adds a dependency on `:core:dictionary`;
  `WordLookupViewModel` + `WordDictionarySheet`.
- `:feature:reader` — `SelectionEvent.isWord`; `ReaderScreen` routes word→sheet,
  phrase→popover; passes the dictionary sheet's Save up to `saveCurrentWord`.
- `:app` — adds `implementation(project(":core:dictionary"))` so the asset is packaged.

Each unit keeps one responsibility: lookup state machine (viewmodel), presentation
(sheet), routing/persistence wiring (reader).

## 7. Error Handling

- Word not in dictionary AND ML Kit fails/unavailable → `Error` state (dismissible);
  never crash.
- Dictionary DB missing/corrupt → `lookup` already returns null (3b-1) → ML Kit fallback.
- Empty/whitespace word → ignored (no sheet).
- Save failure → reuse the existing reader save path's behavior.

## 8. Testing

- `WordLookupViewModel` (fake `DictionaryRepository` + fake `TranslationEngine`):
  dict-hit → `Entry` (with deduped lists); dict-miss → `Machine`; ML-Kit-fail →
  `Error`; blank → no emission.
- `WordDictionarySheet` — build-verified (Compose).
- On-device smoke: tap a word in the dictionary (e.g. "dog") → sheet shows IPA +
  POS + definitions + Ukrainian; tap a word NOT in the dictionary → sheet shows
  the ML Kit translation; Save from the sheet → the word appears in Saved Words;
  long-press a sentence → the popover still works; the "first tap dismisses"
  popover behavior is intact.

## 9. Risks

1. Routing two surfaces (sheet for words, popover for phrases) through the
   existing tap pipeline without regressing the tap-dismiss/Save/anchor behavior —
   the integration task verifies all paths on-device.
2. Dictionary coverage: most words have IPA/POS/EN-definitions but only ~4.5% have
   Ukrainian translations (3b-1), so the sheet often shows defs + the ML Kit
   translation rather than a Wiktionary one. Acceptable; the ML Kit fallback fills
   the gap.
