# EPUB Reader with Tap-to-Translate (EN→UK) — Design

**Date:** 2026-06-23
**Status:** Approved
**Platform:** Android (primary device: Samsung S25 Ultra, Android 15)

## 1. Purpose

A user-friendly Android EPUB reader whose defining feature is **tap-to-translate**:
tapping a word, phrase, or sentence shows an English→Ukrainian translation inline.
Single-word taps additionally show dictionary data (definition, part of speech,
IPA, multiple senses). The reader ships with carefully designed reading themes
optimized for evening and night reading.

This is a side/portfolio project. The stack is chosen to be modern and to
demonstrate strong Android engineering.

## 2. Scope

### In scope (MVP)
- Import and read local `.epub` files
- Reader rendering with reflowable pagination
- Tap-to-translate: word, phrase, full sentence
- Offline dictionary lookup for single words (definitions, POS, IPA, UK translations)
- Hybrid translation: on-device default, online for higher quality
- Saved words list (term + translation + sentence context) for later review
- Reading themes (light / sepia / dark / AMOLED-black) and typography controls
  (font family, size, line height, margins, brightness, warmth)
- Reading progress persistence per book

### Out of scope (future phases)
- Formats other than EPUB (PDF, MOBI, FB2)
- OPDS online catalogs
- Flashcard / spaced-repetition trainer over saved words
- Text-to-speech
- Cloud sync across devices
- Monetization

## 3. Tech Stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| EPUB engine | Readium Kotlin Toolkit (`EpubNavigatorFragment`) |
| Architecture | MVVM + repository, unidirectional data flow |
| Async | Coroutines + Flow |
| DI | Hilt |
| Local persistence | Room (app data) + read-only SQLite (dictionary) |
| Settings | DataStore (Preferences) |
| Navigation | Navigation Compose |
| On-device MT | ML Kit Translation (downloadable EN↔UK model) |
| Online MT | DeepL API (interchangeable behind interface) |
| Min / Target SDK | minSdk 26 / targetSdk latest stable |

### Module structure
Multi-module Gradle project (portfolio-quality signal):

- `:app` — entry point, navigation host, DI wiring
- `:core:designsystem` — Material 3 theme, typography, reading themes
- `:core:database` — Room entities, DAOs, dictionary DB access
- `:core:data` — repositories, translation engine abstraction
- `:feature:library` — bookshelf, EPUB import
- `:feature:reader` — Readium navigator, tap handling, reading settings
- `:feature:translation` — translation/dictionary lookup, result popup
- `:feature:vocabulary` — saved words list

A multi-module layout is the target. If it slows the first iteration, start with
clean feature packages in `:app` and extract modules incrementally — the package
boundaries above are the contract either way.

## 4. Core Feature — Tap-to-Translate

### Flow
1. User taps a word or selects a phrase/sentence in the reader WebView.
2. An injected JS bridge resolves the gesture:
   - **Tap** → `document.caretRangeFromPoint(x, y)`, expand the range to word
     boundaries.
   - **Selection** → use the active selection range.
   It returns to Kotlin: the selected text, the enclosing sentence (context), and
   screen coordinates of the selection.
3. Kotlin routing:
   - **Single word** → look up the local Wiktionary SQLite (definitions, POS, IPA,
     UK translations) **and** run machine translation of the word in its sentence
     context.
   - **Phrase / sentence** → machine translation only. Offline ML Kit by default;
     online DeepL when network is available and the user has enabled it.
4. Show a bottom-sheet card anchored near the tap: term · IPA · part of speech ·
   senses · UK translation · **Save** button.
5. **Save** persists the entry to the `saved_words` table.

### Abstractions
- `TranslationEngine` — interface; implementations: `MlKitTranslationEngine`
  (offline), `DeepLTranslationEngine` (online). A coordinator picks online when
  available/enabled and falls back to offline otherwise.
- `DictionaryRepository` — single-word lookups against the bundled SQLite.

### Risks
- **Accurate tap-to-word in the WebView** is the hardest part; relies on JS
  injection into the Readium navigator and `caretRangeFromPoint`. Validate early
  with a spike.
- **Dictionary dataset quality and size** (see §6).

## 5. Data Model (Room)

- `Book(id, title, author, coverPath, filePath, addedAt, lastOpenedAt)`
- `ReadingProgress(bookId, locator, percent, updatedAt)` — `locator` is the
  Readium locator (CFI-equivalent) for precise resume.
- `SavedWord(id, term, lemma, translation, ipa, pos, contextSentence, bookId, createdAt)`
- `dictionary.db` — separate read-only SQLite:
  `Entry(headword, pos, ipa, definitions, ukTranslations)`.

## 6. Offline Dictionary (Wiktionary)

An offline build script (Python) consumes machine-readable Wiktionary data from
**Wiktextract / kaikki.org** and produces a compact SQLite with: headword, POS,
IPA, definitions, and Ukrainian translations.

- Indexed by lowercased headword and by lemma for inflected-form lookups.
- Bundled in `assets`, or downloaded on first launch if size (tens of MB) makes
  the APK too large.
- If a tapped word is absent, fall back to machine translation only.

## 7. Themes & Reading

Reading themes: **light / sepia / dark / AMOLED-black**. Typography controls:
font family, font size, line height, page margins, brightness, warmth.

Applied through Readium `EpubPreferences` plus custom injected CSS for the
AMOLED-black background and warmth overlay. Night reading is the priority use
case, so dark/AMOLED themes get first-class polish.

## 8. Screens (MVP)

1. **Library** — bookshelf grid, import `.epub` via file picker.
2. **Reader** — rendered EPUB, tap-to-translate, in-reader settings (theme/typography).
3. **Saved words** — list of saved terms with translation and source context.
4. **Settings** — themes, translation preferences (offline-only vs. allow online),
   dictionary download.

## 9. Error Handling

- EPUB parse failure → clear error state in library, book not added.
- ML Kit model not downloaded → prompt to download; block online-less translation
  until ready.
- Online translation unavailable → silent fallback to offline engine.
- Word not in dictionary → show machine translation only, no dictionary card.

## 10. Testing

- Unit: JUnit, MockK, Turbine for Flow; translation routing and dictionary lookup
  logic covered.
- Data: Room with in-memory database.
- UI: Compose UI tests for library, reader controls, and the translation popup.

## 11. Open Questions / Spikes Before Build

1. Spike: tap-to-word accuracy inside the Readium WebView.
2. Decide bundle-vs-download for the dictionary based on final dataset size.
3. Obtain a DeepL API key (free tier) or confirm an alternative online provider.
