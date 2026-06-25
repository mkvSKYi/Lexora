# More Reading Themes + Premium Fonts — Design

**Date:** 2026-06-25
**Status:** Approved
**Builds on:** the reader appearance settings (`ReaderSettingsSheet`, `ReaderThemePreset`, `EpubPreferencesMapper`) + the Readium navigator host (`EpubReaderFragment`), on `main`.

## 1. Purpose

Expand the reading experience: add five curated **themes** (Paper, Nord,
Solarized Dark, Gruvbox, Dusk) on top of Light/Sepia/Dark/AMOLED, and bundle five
premium **reading fonts** (Literata, Lora, Atkinson Hyperlegible, Inter,
OpenDyslexic) selectable in the Aa sheet.

## 2. Research findings (Readium 3.1.2)

- **Custom theme colors:** `EpubPreferences.backgroundColor` + `textColor` accept
  arbitrary `org.readium.r2.navigator.preferences.Color`, on top of the base
  `Theme` (LIGHT/SEPIA/DARK). So any palette is expressible as a base theme + a
  background/text color pair.
- **Custom fonts:** declared on the navigator's
  `EpubNavigatorFragment.Configuration` builder:
  `servedAssets = listOf("fonts/.*")` (serve bundled assets) +
  `addFontFamilyDeclaration(FontFamily("Literata"), alternates = listOf(FontFamily.SERIF)) { addFontFace { addSource("fonts/Literata.ttf"); setFontWeight(100..900) } }`.
  The chosen `fontFamily` is then set via `EpubPreferences.fontFamily = FontFamily("Literata")`.
  Variable fonts (one file, all weights) are bundled where available; static
  families bundle Regular + Bold font-faces.

## 3. Themes

`ReaderThemePreset` gains `PAPER, NORD, SOLARIZED_DARK, GRUVBOX, DUSK`.
`EpubPreferencesMapper.withTheme` maps each to a base `Theme` + background/text:

| Preset | Base theme | background | text |
| --- | --- | --- | --- |
| PAPER | LIGHT | `#F5EFE0` | `#2B2620` |
| NORD | DARK | `#2E3440` | `#ECEFF4` |
| SOLARIZED_DARK | DARK | `#002B36` | `#93A1A1` |
| GRUVBOX | DARK | `#282828` | `#EBDBB2` |
| DUSK | DARK | `#20232E` | `#C8CCDA` |

(Existing: LIGHT → Theme.LIGHT no override; SEPIA → Theme.SEPIA; DARK → Theme.DARK;
AMOLED → Theme.DARK + black/white.) `presetOf` reverse-maps by the stored
`backgroundColor` first (each custom theme has a unique bg), falling back to
`theme` for LIGHT/SEPIA/DARK. The Aa sheet's `THEME_SWATCHES` list gains a swatch
per new preset showing its real bg/text colors.

## 4. Fonts

Bundle variable/curated OFL fonts in `feature/reader/src/main/assets/fonts/`:
- **Literata** (serif, variable) — alternates `SERIF`.
- **Lora** (serif, variable) — alternates `SERIF`.
- **Atkinson Hyperlegible** (sans, high-legibility) — alternates `SANS_SERIF`.
- **Inter** (sans, variable) — alternates `SANS_SERIF`.
- **OpenDyslexic** (accessibility) — alternates `SANS_SERIF`; reuse Readium's
  `FontFamily.OPEN_DYSLEXIC` name.

`EpubReaderFragment` switches its `EpubNavigatorFragment.Configuration` to the
builder form, keeping `selectionActionModeCallback = NoOpActionModeCallback`,
adding `servedAssets = listOf("fonts/.*")` and one `addFontFamilyDeclaration` per
font (with the bundled asset href + weight range / Regular+Bold faces). The Aa
sheet's `FontFamilyRow` gains a chip per font (`onPrefsChange(prefs.copy(fontFamily = FontFamily("…")))`),
each chip previewing in that font (loaded from the same asset as a Compose
`FontFamily`). Keep Default/Serif/Sans-serif.

## 5. Architecture / Files

- `feature/reader/src/main/assets/fonts/` — the bundled `.ttf` files.
- `feature/reader/.../settings/ReaderThemePreset.kt` — the enum (+5 presets).
- `feature/reader/.../settings/EpubPreferencesMapper.kt` — per-preset bg/text;
  `presetOf` by background.
- `feature/reader/.../EpubReaderFragment.kt` — Configuration builder + font
  declarations + `servedAssets`.
- `feature/reader/.../settings/ReaderSettingsSheet.kt` — `THEME_SWATCHES` (+5) and
  `FontFamilyRow` chips (+5), with per-chip font preview.

No data-layer change (theme/font already persist via the existing
`EpubPreferences` JSON in DataStore).

## 6. Error Handling / Edge Cases

- A bundled font that fails to load → the WebView falls back to the alternate
  (`SERIF`/`SANS_SERIF`); never blank text.
- A persisted `fontFamily`/theme from before this change still maps (unknown
  fontFamily → Readium falls back; `presetOf` returns the closest preset or null).
- Custom-color themes set BOTH `backgroundColor` and `textColor` so links/selection
  from the base `Theme` stay legible.
- APK size grows by the bundled fonts (~1–3 MB total, variable fonts preferred to
  stay lean) — acceptable.

## 7. Testing

- **`EpubPreferencesMapper`** (pure, JVM): `withTheme(preset)` sets the expected
  `theme` + `backgroundColor` + `textColor` for every preset; `presetOf` round-trips
  each preset (including the five custom-color ones) back to itself.
- **On-device smoke** (S25 Ultra): open the Aa sheet → each new theme swatch
  applies its bg/text to the page; each new font chip changes the reading font;
  switching back to Light/Default still works; previously-persisted theme/font
  still render.

## 8. Risks

1. **Font declaration wiring** — the `servedAssets` pattern + asset href must let
   the WebView fetch the bundled fonts; verified on-device (the font visibly
   changes). If a font doesn't apply, check the served path + the `FontFamily`
   name match.
2. **`presetOf` ambiguity** — two presets must not share a background color; the
   table above keeps each unique. The mapper test asserts every round-trip.
3. **Font licensing** — all five are OFL/Apache (bundling permitted); keep the
   license files alongside the fonts.
