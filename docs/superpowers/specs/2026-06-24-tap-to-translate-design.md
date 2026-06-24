# Tap-to-Translate (EN→UK) — Design (Plan 2)

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** Plan 1 (foundation, library, reader) — merged to `main`.

## 1. Purpose

Add the app's defining feature on top of the working reader: tapping a word, or
selecting a phrase/sentence, shows an English→Ukrainian translation in a compact
popover anchored near the text. Translation is offline via ML Kit. Online DeepL,
single-word dictionary data, and saving words are explicitly deferred to later
plans.

## 2. Scope

### In scope (Plan 2)
- Tap a single word in the reader → translate EN→UK.
- Select a phrase/sentence (Readium's native selection) → translate EN→UK.
- Offline translation via ML Kit, behind a `TranslationEngine` interface.
- ML Kit EN/UK model download on first use, with a loading state.
- Compact popover near the tapped word/selection showing source text + Ukrainian
  translation, with loading and error states.
- Reader gesture model: tap translates; page turns happen on horizontal swipe
  (tap-to-turn disabled).

### Out of scope (later plans)
- Online DeepL engine (Plan 2 follow-up): the `TranslationEngine` interface is
  built so DeepL slots in without touching callers.
- Single-word dictionary data — definitions, IPA, part of speech (Plan 3).
- Saving tapped words / vocabulary list (Plan 3).
- Reading themes / typography controls (Plan 4).
- "Wi-Fi only" model-download preference (deferred; downloads over any network).

## 3. New Module: `:feature:translation`

Pure translation, no Readium dependency, independently testable.

- `TranslationEngine` (interface):
  - `suspend fun ensureModelsReady(): Result<Unit>` — ensure the EN+UK models are
    downloaded and ready.
  - `suspend fun translate(text: String): Result<String>` — translate EN→UK;
    assumes models ready (caller calls `ensureModelsReady` first, or the engine
    ensures internally and surfaces a download error).
- `MlKitTranslationEngine` — wraps `com.google.mlkit:translate`. Builds a
  `Translator` for English→Ukrainian, `downloadModelIfNeeded()`, reuses a single
  client, closes it when the owning scope ends. Bound via Hilt.
- `TranslationPopover` (Composable) + a small UI state model
  (`TranslationPopupState`: `Loading | Result(source, translation) | Error(message)`).
  Given source text + an anchor rectangle, renders the popover content.

The module exposes a clean surface the reader drives; it knows nothing about
WebViews or Readium.

## 4. Reader Integration (`:feature:reader`) — the JS bridge

This is the highest-risk part.

- **Word tap:** inject JavaScript into the navigator's WebView that, on a tap,
  resolves the word at the tap point — `document.caretRangeFromPoint(x, y)`
  expanded to word boundaries via `Range` — and posts `{ word, rectInWebView }`
  back to Kotlin through a JS bridge (`@JavascriptInterface` or Readium's
  script-injection / message channel).
- **Phrase/sentence:** use Readium's built-in selection (`currentSelection()`),
  which returns the selected text and its bounding rect.
- **Gesture model:** configure the navigator so a single tap does NOT turn the
  page (our handler owns taps); horizontal swipe still turns pages.
- **Anchoring:** convert the WebView-relative rect to screen coordinates, then to
  Compose coordinates, and show a Compose `Popup`/popover at that offset. Dismiss
  on tap-outside / scroll / page turn.
- The exact Readium 3.1.2 APIs (input/tap listener, script injection mechanism,
  `currentSelection`, and how to disable tap-to-turn) MUST be verified against
  current docs (context7) at implementation time — Readium 3.x differs from 2.x.
  This is a required spike before the bridge is finalized.

## 5. Data Flow

```
tap word / select phrase
  → (text, anchorRect) delivered to the reader
  → TranslationViewModel.onTextSelected(text, anchorRect)
  → state = Loading; ensureModelsReady() (download-on-demand)
  → translate(text)
  → state = Result(text, translation)  →  popover shows it
  (any step fails → state = Error(message) in the popover)
```

`TranslationViewModel` lives in the reader feature (it coordinates reader events
with the translation engine) and depends only on the `TranslationEngine`
interface and the popover state model from `:feature:translation`.

## 6. Error Handling

- Model not yet downloaded + no network on first use → `Error` state: "Translation
  needs a one-time download. Connect to the internet and tap again."
- Translation failure → `Error` state with a short message; popover stays
  dismissible.
- Empty/whitespace tap resolution (tapped between words) → no popover.

## 7. Testing

- `:feature:translation`: unit-test the popover state logic and the
  `TranslationViewModel` routing with a **fake `TranslationEngine`** (loading →
  result; loading → error on download failure; blank text → no popover). The thin
  ML Kit wrapper is exercised through the fake, not in unit tests (ML Kit needs a
  device / Play services).
- Reader JS bridge + real ML Kit translation: integration/manual on-device smoke
  test — tap a word, see the Ukrainian translation; select a phrase, see it
  translated; confirm tap does not turn the page and swipe does. (Mirrors Plan 1's
  on-device verification, which must exercise the actual feature, not just launch.)

## 8. Dependencies

- `com.google.mlkit:translate` (added to the version catalog).
- No new persistence. No secrets/keys in Plan 2.

## 9. Spikes / Open Risks

1. **Spike (required first):** Readium 3.1.2 tap/input listener + JS injection +
   `currentSelection` + disabling tap-to-turn. Prove word-at-point detection works
   inside the navigator WebView before building the popover on top.
2. Coordinate conversion (WebView → screen → Compose) for accurate popover
   anchoring across scroll/pagination.
3. ML Kit model download size/latency on first use — handled with a loading state.
