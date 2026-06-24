# Offline Dictionary — Data & Backend — Design (Plan 3b-1)

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** Plans 1–2 + A + B + 3a, merged to `main`. This is the first half of Plan 3b (offline Wiktionary dictionary); the dictionary bottom-sheet UI + reader integration is Plan 3b-2 (follows this).

## 1. Purpose

Provide an offline English→Ukrainian dictionary backend: a compact, bundled
SQLite database built from Wiktionary data, plus a `DictionaryRepository` that
looks up a tapped word (handling inflected forms) and returns its IPA, part of
speech, English definitions, and Ukrainian translations. No UI in this plan —
3b-2 consumes this backend.

## 2. Scope

### In scope (3b-1)
- A **build script** (`tools/dictionary/`, Python) that turns Wiktionary
  (Wiktextract / kaikki.org) data into a compact SQLite `dictionary.db`:
  `entries(headword, ipa, pos, definitions_json, uk_translations_json)` and
  `forms(form, headword)` (inflected form → base lemma).
- Trim to a configurable set of common lemmas (target ~30–50k) via a frequency
  list, to keep the bundled size reasonable.
- A new module **`:core:dictionary`** exposing:
  - `DictionaryEntry(headword, ipa: String?, partOfSpeech: String?, definitions: List<String>, translations: List<String>)`.
  - `DictionaryRepository.lookup(word: String): DictionaryEntry?` — normalizes
    (trim/lowercase), tries `entries` by headword, then `forms` → headword →
    `entries`; returns null when absent.
- The `dictionary.db` packaged in `:core:dictionary` assets and opened read-only
  via Room `createFromAsset`.

### Out of scope (3b-2 and later)
- The word bottom-sheet UI and reader integration (3b-2).
- The ML Kit fallback wiring (3b-2 decides dictionary-hit → sheet vs miss → ML Kit).
- Saving richer dictionary fields into `SavedWord` (keep the existing schema).
- Languages other than EN→UK; example sentences; audio.

## 3. Build Script (`tools/dictionary/`)

A dev-time Python script (not shipped in the app), run once to (re)generate the
asset:
- **Input:** a Wiktextract JSONL extract of English Wiktionary (kaikki.org) and a
  frequency word list (top ~30–50k English lemmas).
- **Per entry** whose `word` is in the frequency set: collect `pos`; the first
  IPA from `sounds[].ipa`; English definitions from `senses[].glosses`; Ukrainian
  translations from `translations[]` where the language code is `uk`.
- **Forms:** from `senses[].form_of[].word` (and/or `forms` with form-of tags),
  record `(inflected_form → base headword)` rows in `forms`.
- **Output:** `dictionary.db` (SQLite) with the two tables above; `definitions`
  and `uk_translations` stored as JSON arrays in TEXT columns. Indices on
  `entries.headword` and `forms.form`.
- The script is parameterized by the word-count cap so size can be tuned.

**Acquisition risk (the main spike):** the full kaikki English extract is large
(multiple GB). The first task is a feasibility spike — acquire the source, build
a SMALL subset (e.g. ~1–2k words) end-to-end, confirm the schema + a real lookup
+ extrapolate the full size — before committing to the full 30–50k build. If the
full download is impractical here, reduce the word cap and note the achieved size
/ coverage; the schema and repository do not change.

## 4. `:core:dictionary` Module

- `DictionaryEntry` (domain, above).
- `DictionaryRepository` (interface) + `RoomDictionaryRepository`:
  - A read-only Room database over the bundled asset
    (`Room.databaseBuilder(...).createFromAsset("dictionary.db").build()`), with a
    `DictionaryDao`: `getByHeadword(word): DictionaryEntryRow?`,
    `getBaseForm(form): String?`.
  - `lookup(word)`: `val w = word.trim().lowercase()`; try `getByHeadword(w)`;
    if null, `getBaseForm(w)?.let { getByHeadword(it) }`; map the row's JSON
    columns to `List<String>`; return `DictionaryEntry` or null.
  - Hilt-bound; the asset DB is read-only (no migrations, `version = 1`).

The module owns the dictionary data and lookup only — no Readium, no UI.

## 5. Data Flow (lookup)

```
word → trim+lowercase
     → entries[headword == w]?            → DictionaryEntry
     → else forms[form == w] → base
           → entries[headword == base]?   → DictionaryEntry
     → else null   (3b-2: caller falls back to ML Kit)
```

## 6. Error Handling

- Word not found (neither headword nor form) → `null` (not an error).
- Malformed JSON in a row → that field becomes an empty list; never crash.
- Asset DB missing/corrupt at runtime → surfaced as a lookup failure returning
  null; never crash the reader.

## 7. Testing

- **Build script:** a unit test parsing a small hand-made Wiktextract sample
  (a few JSONL lines) → asserts the produced rows (headword/ipa/pos/defs/
  translations) and a form-of mapping.
- **`DictionaryRepository`:** tests against a SMALL hand-built test `dictionary.db`
  (committed as a test asset, a handful of words incl. one inflected form): exact
  headword hit, inflected-form hit via `forms`, miss → null, JSON columns parsed
  to lists.
- The full generated `dictionary.db` is NOT required for unit tests (they use the
  tiny test asset), so tests don't depend on the multi-GB build.

## 8. Risks

1. **Data acquisition / size** (main): the kaikki extract is large; the build
   spike confirms feasibility and the realistic bundled size. The word cap is the
   tuning knob. A very large `dictionary.db` committed to git may warrant Git LFS
   — decided after the spike measures the size.
2. **Form-of coverage:** Wiktextract's form-of data is uneven; lookups for some
   inflections may miss and (in 3b-2) fall back to ML Kit. Acceptable.
3. **Translation quality:** Wiktionary UK translations vary; we present what's
   there alongside the EN definitions.
