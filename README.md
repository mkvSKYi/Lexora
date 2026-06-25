# Reader — a modern Android EPUB reader with tap-to-translate

A polished, portfolio-grade EPUB reader for Android. Tap any English word to see
its Ukrainian translation and dictionary entry, save vocabulary as you read, and
read in a premium, fully themeable interface — all offline.

> Built with a modern Android stack: Kotlin, Jetpack Compose, Material 3, Hilt,
> Room, Coroutines/Flow, and the [Readium](https://github.com/readium/kotlin-toolkit)
> EPUB navigator.

## Features

**Reading**
- Import and read EPUBs (Readium navigator hosted in Compose).
- Table of contents, a progress scrubber, and an always-on progress hairline.
- 9 reading themes (Light, Sepia, Dark, AMOLED, Paper, Nord, Solarized Dark,
  Gruvbox, Dusk) and 5 bundled premium fonts (Literata, Lora, Atkinson
  Hyperlegible, Inter, OpenDyslexic), plus font size, line spacing, and margins.
- A floating "glass" chrome, a **right-edge swipe to adjust brightness**, and a
  warmth overlay.

**Translate & learn (the headline feature)**
- **Tap a word** → an offline EN→UK translation with the dictionary entry (IPA,
  part of speech, definitions). A bundled 40k-word Wiktionary database is the
  source; ML Kit on-device translation fills the gaps.
- **Long-press** → translate the whole sentence.
- **Save** words to a vocabulary list; mark them **learned** (the card turns
  green), filter All/Learning/Learned, see your learned-progress stats, and tap a
  saved word to revisit its definitions.

**Library**
- An "Aurora" bookshelf with a Continue-reading hero, generated gradient cover
  art, per-book progress, and a long-press menu (details / delete).

Everything works **offline** — translation, dictionary, and fonts are all bundled.

## Tech & architecture

- **Language / UI:** Kotlin, Jetpack Compose, Material 3.
- **DI:** Hilt. **Persistence:** Room (+ DataStore for reading preferences).
- **Async:** Coroutines / Flow (unidirectional MVVM).
- **EPUB:** Readium Kotlin Toolkit 3.1.2.
- **Translation:** Google ML Kit (offline EN→UK) behind a `TranslationEngine`
  interface; a bundled SQLite Wiktionary dictionary.

Multi-module by responsibility:

```
:app                      navigation + host Activity
:core:designsystem        theme, typography
:core:database            Room entities/DAOs + migrations
:core:data               repositories, importer, preferences
:core:dictionary          bundled offline dictionary (40k words)
:feature:library          bookshelf + import
:feature:reader           Readium navigator, themes/fonts, gestures, brightness
:feature:translation      translation engine, dictionary/word UI
:feature:saved            saved-words vocabulary screen
```

The offline dictionary is built by a dev script in `tools/dictionary/` from a
kaikki.org Wiktextract extract.

## Build & run

Requirements: Android Studio (JDK 21), Android SDK (compileSdk 36), a device or
emulator on API 26+.

```bash
./gradlew :app:installDebug      # build + install on a connected device
./gradlew testDebugUnitTest      # run the unit tests
```

There are no API keys or secrets to configure — the app is offline-first.

## Status

A personal side project, built feature-by-feature (brainstorm → spec → plan →
test-driven implementation → review). Design docs and implementation plans live
under `docs/superpowers/`.

## Licenses

Bundled reading fonts are SIL Open Font License 1.1 (see
`feature/reader/src/main/assets/fonts/LICENSES.txt`). Dictionary data derives
from English Wiktionary via [kaikki.org](https://kaikki.org) (CC BY-SA).
