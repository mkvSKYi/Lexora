# Reading Appearance & Settings — Design (Plan A)

**Date:** 2026-06-24
**Status:** Approved
**Builds on:** Plans 1–2 (reader + tap-to-translate), merged to `main`.

## 1. Purpose

Let the reader customize the reading experience from an in-reader appearance
sheet (the "Aa" control): themes, typography, page mode, plus app-level
brightness and warmth for comfortable night reading. Settings persist globally
across books. Table of contents and a progress indicator are a separate
follow-up plan (Plan B) and are out of scope here, but this plan introduces the
reader chrome both will share.

## 2. Scope

### In scope (Plan A)
- **Reader chrome:** a top bar (back + Aa button) and a bottom bar area that
  auto-hide while reading and re-appear via a dedicated affordance (NOT a center
  tap — that resolves a word). The chrome is the shared surface Plan B extends.
- **Appearance sheet (Aa):** a bottom sheet with:
  - **Themes:** light / sepia / dark / AMOLED-black (presets).
  - **Typography:** font size, font family, line height (line spacing), page margins.
  - **Page mode:** paginated ↔ vertical scroll.
- **Brightness:** in-app override of the window brightness (slider).
- **Warmth:** an amber overlay with adjustable opacity (blue-light reduction).
- **Persistence:** all of the above persist globally (apply to every book) via DataStore.
- Live preview: changes apply to the open book immediately.

### Out of scope (later plans)
- Table of contents navigation + reading-progress indicator (Plan B; the chrome
  introduced here is the hook).
- Custom background/text color picker (presets only this plan).
- Per-book (vs global) settings.
- Bookmarks, in-book search, page-turn animation styles.

## 3. Gesture / Chrome Reveal (resolves the tap conflict)

Tap resolves a word, long-press a sentence, swipe turns the page — so a
center-tap cannot toggle chrome. Instead:
- The reader keeps a **top bar** (already present with a back button) and adds an
  **Aa** action to it. The top bar auto-hides after a few seconds of reading and
  re-shows on a small edge affordance (a tap in the top status-bar strip, or a
  thin always-visible handle). The exact reveal affordance is finalized in the
  plan; the constraint is: it must not collide with word-tap/sentence-long-press/swipe.
- The Aa button opens the appearance bottom sheet.

## 4. Appearance via Readium Preferences

Readium's navigator exposes a typed preferences system. Apply settings through it
rather than custom CSS where possible:
- `EpubPreferences` carries `theme`, `fontSize`, `fontFamily`, `lineHeight`,
  `pageMargins`, `scroll` (paginated vs scroll), etc.
- An `EpubPreferencesEditor` (from `EpubNavigatorFactory.createPreferencesEditor`)
  maps UI controls to preference changes; the result is submitted to the live
  navigator via `navigator.submitPreferences(preferences)`.
- Theme presets map to Readium `Theme` (Light/Sepia/Dark) plus, for AMOLED-black,
  a true-black background applied via the preferences/`textColor`/`backgroundColor`
  surface (or a thin custom CSS layer only if Readium's theme set lacks pure black).
- The exact 3.1.2 API (editor construction, preference keys, the `Preferences`
  JSON serializer, `submitPreferences`) MUST be verified via context7 at
  implementation time — Readium 3.x differs from 2.x. This is the main spike.

App-level (outside Readium):
- **Brightness:** set `window.attributes.screenBrightness` (0..1) on the host
  activity while reading; restore on exit.
- **Warmth:** a `Box` overlay above the navigator with an amber color and
  alpha bound to the warmth value; pointer-transparent so it doesn't block taps.

## 5. Persistence

- `ReaderPreferencesRepository` in `:core:data`, backed by **DataStore
  (Preferences)**, stores: the serialized Readium `EpubPreferences` (JSON via
  Readium's serializer), `brightness: Float?` (null = system), `warmth: Float`
  (0..1). Exposed as a `Flow<ReaderPreferences>` and an update API.
- **Global**, not per-book: one settings set applies to all books.
- On reader open: load persisted preferences, submit them to the navigator, apply
  brightness/warmth. On change: persist + apply live.

## 6. Architecture

- `:core:data`:
  - `ReaderPreferences` (domain: serialized epub prefs JSON + brightness + warmth).
  - `ReaderPreferencesRepository` (interface) + `DataStoreReaderPreferencesRepository` (Hilt-bound).
- `:core:designsystem`: reading-theme tokens (the 4 presets' colors), reused by the sheet UI.
- `:feature:reader`:
  - `ReaderChrome` (top/bottom bars, auto-hide state).
  - `ReaderSettingsSheet` (Aa bottom sheet: theme presets, typography sliders, page-mode toggle, brightness, warmth).
  - `ReaderViewModel` extended: hold current `EpubPreferences`, expose apply/update, persist via the repository; expose brightness/warmth state.
  - The navigator host applies submitted preferences to the live `EpubNavigatorFragment`.

Each unit has one responsibility: persistence (repo), preference mapping
(viewmodel/editor wrapper), and UI (sheet/chrome) are separable and testable.

## 7. Error Handling

- Missing/corrupt persisted preferences → fall back to sensible defaults
  (system theme, default font size), never crash.
- A font family unsupported by Readium → defaults applied; no error surfaced.
- Brightness restore on reader exit even if the screen is left abruptly.

## 8. Testing

- `ReaderPreferencesRepository`: unit tests (in-memory/temp DataStore) — round-trip
  of epub-prefs JSON + brightness + warmth; defaults on empty/corrupt.
- Theme-preset → preference mapping: unit-tested (preset enum → expected Readium
  preference values).
- Navigator application, sheet UI, brightness/warmth, auto-hide chrome:
  on-device smoke test — change each control and confirm the open book updates
  live (theme/size/spacing/margins/page-mode/brightness/warmth), and the setting
  survives closing and reopening the book.

## 9. Spikes / Risks

1. **Spike (required):** Readium 3.1.2 preferences API — `createPreferencesEditor`,
   `EpubPreferences` keys, JSON serializer, `submitPreferences`. Prove a theme +
   font-size change applies live before building the full sheet.
2. AMOLED-true-black: confirm Readium's theme set can express pure black, else a
   minimal CSS layer.
3. Chrome reveal affordance that doesn't collide with tap/long-press/swipe.
