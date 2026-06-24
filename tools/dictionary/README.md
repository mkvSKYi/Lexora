# Dictionary build tool

Dev-time tool (not shipped in the Android app). Turns a Wiktextract/kaikki
English-Wiktionary JSONL extract into a compact SQLite `dictionary.db` that the
app bundles as an asset.

Stdlib only — needs `python3` (3.9+). Tests use `pytest`.

## Output schema

```sql
entries(headword TEXT PRIMARY KEY, ipa TEXT, pos TEXT,
        definitions TEXT, uk_translations TEXT)   -- definitions/uk_translations are JSON arrays
forms(form TEXT PRIMARY KEY, headword TEXT)        -- inflected form -> base headword
```

`parse_entry` pulls, per object:
- `headword` = `word` (lowercased)
- `ipa` = first `sounds[].ipa`
- `pos` = `pos`
- `definitions` = all `senses[].glosses`
- `uk_translations` = `translations[].word` where `lang_code == "uk"`

`extract_forms` maps `senses[].form_of[].word` to `(inflected, base)`.

Field names verified against real kaikki English per-word JSONL
(`word`, `pos`, `sounds[].ipa`, `senses[].glosses`, `senses[].form_of[].word`,
`translations[].lang_code` / `translations[].word`).

## 1. Get the kaikki extract

Download the postprocessed English JSONL dump from
<https://kaikki.org/dictionary/English/> (see the "raw data" / download links;
one JSON object per line). It is large (multiple GB).

You can also grab a few per-word files for smoke tests, e.g.:

```bash
curl -s https://kaikki.org/dictionary/English/meaning/d/do/dog.jsonl
```

## 2. Get a frequency word list

Any plain-text frequency list works — one word per line, or `word count`
(the first whitespace-separated token is used). The build only keeps headwords
present in this list, which keeps `dictionary.db` small. A common choice is the
Google/Norvig `count_1w.txt` or a wordfreq-derived top-N list.

## 3. Build

```bash
python3 build_dictionary.py \
  --input path/to/kaikki-en.jsonl \
  --freq  path/to/freq.txt \
  --out   dictionary.db \
  [--cap 50000]
```

`--cap` optionally limits the number of inserted entries.

## Test

```bash
cd tools/dictionary
python3 -m pytest -q
```
