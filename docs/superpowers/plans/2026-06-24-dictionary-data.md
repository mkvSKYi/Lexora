# Offline Dictionary Data & Backend Implementation Plan (Plan 3b-1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A bundled offline SQLite dictionary + a `DictionaryRepository.lookup(word)` that returns IPA, part of speech, English definitions, and Ukrainian translations, resolving inflected forms to their base lemma.

**Architecture:** A Python build script turns a Wiktextract/kaikki English extract into a compact `dictionary.db` (`entries` + `forms` tables). A new `:core:dictionary` module copies that read-only asset DB to internal storage on first use and queries it with raw `SQLiteDatabase` (no Room — avoids prepackaged-DB identity-hash friction), exposing a Hilt-bound `DictionaryRepository`.

**Tech Stack:** Python 3 + sqlite3 (build script); Kotlin, Android `SQLiteDatabase`, Hilt, Coroutines. Test: pytest (script), JUnit4 + Robolectric (repository over a tiny test DB).

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin only (app side), Python 3 for the dev build script. Hilt DI. ALL Android dependency versions via the catalog; no hardcoded versions in module build files.
- New module `:core:dictionary` (namespace `com.reader.core.dictionary`). It owns ONLY the dictionary data + lookup — no Readium, no UI (that's 3b-2).
- The dictionary is read-only and bundled in `core/dictionary/src/main/assets/dictionary.db`. Opened via raw `SQLiteDatabase` (copy asset → internal storage on first use, open `OPEN_READONLY`). This intentionally replaces the spec's "Room createFromAsset" to avoid Room's prepackaged-identity requirement; the `DictionaryRepository` surface is unchanged.
- SQLite schema (exact):
  `entries(headword TEXT PRIMARY KEY, ipa TEXT, pos TEXT, definitions TEXT, uk_translations TEXT)`,
  `forms(form TEXT PRIMARY KEY, headword TEXT)`. `definitions`/`uk_translations` are JSON arrays stored as TEXT.
- `lookup` normalizes input as `word.trim().lowercase()`; resolves headword, else form→headword; returns null when absent; never crashes on malformed JSON (bad field → empty list).
- The full `dictionary.db` is NOT needed for unit tests — repository tests use a tiny committed test DB.

---

### Task 1: Dictionary build script (Python)

**Files:**
- Create: `tools/dictionary/build_dictionary.py`
- Create: `tools/dictionary/README.md`
- Create: `tools/dictionary/test_build_dictionary.py`
- Create: `tools/dictionary/sample_wiktextract.jsonl` (a few hand-made lines)

**Interfaces:**
- Produces: a `parse_entry(obj: dict) -> Optional[Entry]` and `extract_forms(obj: dict) -> list[tuple[str, str]]` pair plus a `build(input_jsonl, freq_words: set[str], out_db_path)` driver. `Entry` = `(headword, ipa, pos, definitions: list[str], uk_translations: list[str])`. The DB schema is the one in Global Constraints.

- [ ] **Step 1: Write the sample + failing test**

`sample_wiktextract.jsonl` — 3 lines (adjust field shapes to real Wiktextract once verified in Step 0 below):
```json
{"word":"run","pos":"verb","sounds":[{"ipa":"/ɹʌn/"}],"senses":[{"glosses":["To move quickly on foot."]}],"translations":[{"lang_code":"uk","word":"бігти"},{"lang_code":"de","word":"laufen"}]}
{"word":"running","pos":"verb","senses":[{"glosses":["present participle of run"],"form_of":[{"word":"run"}]}]}
{"word":"dog","pos":"noun","sounds":[{"ipa":"/dɒɡ/"}],"senses":[{"glosses":["A domesticated carnivore."]}],"translations":[{"lang_code":"uk","word":"собака"}]}
```

`test_build_dictionary.py`:
```python
import json, sqlite3, tempfile, os
from build_dictionary import parse_entry, extract_forms, build

def test_parse_entry_extracts_fields():
    obj = json.loads('{"word":"dog","pos":"noun","sounds":[{"ipa":"/dɒɡ/"}],"senses":[{"glosses":["A domesticated carnivore."]}],"translations":[{"lang_code":"uk","word":"собака"},{"lang_code":"de","word":"Hund"}]}')
    e = parse_entry(obj)
    assert e.headword == "dog"
    assert e.ipa == "/dɒɡ/"
    assert e.pos == "noun"
    assert e.definitions == ["A domesticated carnivore."]
    assert e.uk_translations == ["собака"]

def test_extract_forms_maps_inflection_to_base():
    obj = json.loads('{"word":"running","senses":[{"glosses":["present participle of run"],"form_of":[{"word":"run"}]}]}')
    assert ("running", "run") in extract_forms(obj)

def test_build_produces_queryable_db():
    here = os.path.dirname(__file__)
    db = os.path.join(tempfile.mkdtemp(), "d.db")
    build(os.path.join(here, "sample_wiktextract.jsonl"), {"run", "running", "dog"}, db)
    con = sqlite3.connect(db)
    assert con.execute("SELECT ipa FROM entries WHERE headword='run'").fetchone()[0] == "/ɹʌn/"
    assert con.execute("SELECT headword FROM forms WHERE form='running'").fetchone()[0] == "run"
    assert con.execute("SELECT uk_translations FROM entries WHERE headword='run'").fetchone()[0] == '["бігти"]'
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tools/dictionary && python3 -m pytest -q`
Expected: FAIL — `build_dictionary` not importable.

- [ ] **Step 3: Implement the build script**

`build_dictionary.py`:
- `Entry` dataclass `(headword, ipa, pos, definitions, uk_translations)`.
- `parse_entry(obj)`: `headword = obj["word"].strip().lower()`; `pos = obj.get("pos")`; `ipa = next((s["ipa"] for s in obj.get("sounds", []) if s.get("ipa")), None)`; `definitions = [g for sense in obj.get("senses", []) for g in sense.get("glosses", [])]`; `uk_translations = [t["word"] for t in obj.get("translations", []) if t.get("lang_code") == "uk" and t.get("word")]`. Return None if there's no headword. (A pure form-of entry with no defs/translations may still be returned for `forms` extraction; the driver decides whether to insert it into `entries` — only insert into `entries` if it has defs or translations.)
- `extract_forms(obj)`: for each sense's `form_of`, yield `(obj["word"].strip().lower(), fo["word"].strip().lower())`.
- `build(input_jsonl, freq_words, out_db_path)`: create the two tables (+ index on `forms.form`), stream the JSONL; for each line whose lowercased `word` is in `freq_words`: insert into `entries` (when it has defs/translations) and insert all `extract_forms` rows into `forms`. Use `INSERT OR REPLACE`. Store `definitions`/`uk_translations` as `json.dumps(list, ensure_ascii=False)`. Add a `main()` with argparse (`--input`, `--freq`, `--out`, `--cap`) for Task 3.

`README.md`: how to obtain the kaikki extract + a frequency list and run the script.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tools/dictionary && python3 -m pytest -q`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add tools/dictionary
git commit -m "feat: add wiktionary dictionary build script"
```

(Note: confirm the real Wiktextract field names — `word`, `pos`, `sounds[].ipa`, `senses[].glosses`, `senses[].form_of[].word`, `translations[].lang_code/word` — against an actual kaikki English sample before Task 3; adjust `parse_entry`/`extract_forms` and the sample if they differ, keeping the tests' intent.)

---

### Task 2: `:core:dictionary` module — repository over the asset DB

**Files:**
- Create: `core/dictionary/build.gradle.kts`, `core/dictionary/src/main/AndroidManifest.xml`
- Create: `core/dictionary/src/main/kotlin/com/reader/core/dictionary/DictionaryEntry.kt`
- Create: `core/dictionary/src/main/kotlin/com/reader/core/dictionary/DictionaryRepository.kt`
- Create: `core/dictionary/src/main/kotlin/com/reader/core/dictionary/AssetDatabase.kt`
- Create: `core/dictionary/src/main/kotlin/com/reader/core/dictionary/di/DictionaryModule.kt`
- Modify: `settings.gradle.kts` (include `:core:dictionary`)
- Test: `core/dictionary/src/test/kotlin/com/reader/core/dictionary/DictionaryRepositoryTest.kt`
- Test asset: `core/dictionary/src/test/resources/test_dictionary.db`

**Interfaces:**
- Produces:
  - `data class DictionaryEntry(val headword: String, val ipa: String?, val partOfSpeech: String?, val definitions: List<String>, val translations: List<String>)`.
  - `interface DictionaryRepository { suspend fun lookup(word: String): DictionaryEntry? }`.
  - `class SqliteDictionaryRepository @Inject constructor(@ApplicationContext context, ...) : DictionaryRepository` bound in `DictionaryModule`.

- [ ] **Step 1: Create the module + build file + settings include**

`core/dictionary/build.gradle.kts` applies android-library + kotlin + ksp + hilt (mirror `core/database/build.gradle.kts`, minus Room), `namespace = "com.reader.core.dictionary"`, minSdk 26, compileSdk 36, depends on Hilt + Coroutines. Test deps: junit4, robolectric, kotlinx-coroutines-test, androidx-test-core. Add `include(":core:dictionary")` to `settings.gradle.kts`. All via the catalog.

- [ ] **Step 2: Build the tiny test DB**

Create `core/dictionary/src/test/resources/test_dictionary.db` with the exact schema and a few rows (the build needs the real schema; create it with the sqlite3 CLI):
```bash
sqlite3 core/dictionary/src/test/resources/test_dictionary.db <<'SQL'
CREATE TABLE entries(headword TEXT PRIMARY KEY, ipa TEXT, pos TEXT, definitions TEXT, uk_translations TEXT);
CREATE TABLE forms(form TEXT PRIMARY KEY, headword TEXT);
INSERT INTO entries VALUES('run','/ɹʌn/','verb','["To move quickly on foot."]','["бігти"]');
INSERT INTO entries VALUES('dog','/dɒɡ/','noun','["A domesticated carnivore."]','["собака"]');
INSERT INTO forms VALUES('running','run');
SQL
```

- [ ] **Step 3: Write the failing repository test (Robolectric)**

```kotlin
package com.reader.core.dictionary

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DictionaryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    // Point the repository at the test DB file directly via a seam (see Step 4).
    private fun repo(): SqliteDictionaryRepository {
        val dbFile = File("src/test/resources/test_dictionary.db").absoluteFile
        return SqliteDictionaryRepository(context, openOverride = dbFile)
    }

    @Test fun exact_headword_lookup() = runTest {
        val e = repo().lookup("Dog")!!  // normalized to "dog"
        assertEquals("dog", e.headword)
        assertEquals("/dɒɡ/", e.ipa)
        assertEquals("noun", e.partOfSpeech)
        assertEquals(listOf("A domesticated carnivore."), e.definitions)
        assertEquals(listOf("собака"), e.translations)
    }

    @Test fun inflected_form_resolves_via_forms() = runTest {
        val e = repo().lookup("running")!!
        assertEquals("run", e.headword)
        assertEquals(listOf("бігти"), e.translations)
    }

    @Test fun missing_word_returns_null() = runTest {
        assertNull(repo().lookup("zzzznotaword"))
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :core:dictionary:testDebugUnitTest`
Expected: FAIL — `SqliteDictionaryRepository`/`DictionaryEntry` unresolved.

- [ ] **Step 5: Implement the asset DB opener + repository**

`AssetDatabase.kt` — `fun openReadable(context: Context, assetName: String = "dictionary.db", override: File? = null): SQLiteDatabase`: if `override != null` open it directly read-only; else copy `assets/dictionary.db` to `context.filesDir/dictionary.db` if missing (stream copy), then `SQLiteDatabase.openDatabase(path, null, OPEN_READONLY)`. Cache the opened instance.

`DictionaryEntry.kt` — the data class.

`DictionaryRepository.kt` — the interface + `SqliteDictionaryRepository @Inject constructor(@ApplicationContext context: Context, private val openOverride: File? = null)`. `lookup(word)` (on `Dispatchers.IO`): `val w = word.trim().lowercase()`; query `SELECT headword,ipa,pos,definitions,uk_translations FROM entries WHERE headword=? LIMIT 1`; if no row, `SELECT headword FROM forms WHERE form=? LIMIT 1` then re-query entries with the base; map JSON TEXT → `List<String>` via a tolerant parser (`runCatching { JSONArray(...) }` → list, else emptyList). Return `DictionaryEntry` or null. Wrap DB access in `runCatching { ... }.getOrNull()` so a missing/corrupt DB yields null.

`DictionaryModule.kt` — `@Binds` `SqliteDictionaryRepository` → `DictionaryRepository` in `SingletonComponent`. (Hilt provides the `@ApplicationContext`; `openOverride` defaults to null in production.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :core:dictionary:testDebugUnitTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add core/dictionary settings.gradle.kts
git commit -m "feat: add offline dictionary repository over bundled sqlite"
```

---

### Task 3: Generate + bundle the real dictionary.db (data spike)

**Files:**
- Create: `core/dictionary/src/main/assets/dictionary.db` (generated, committed)
- Modify: `.gitattributes` (only if Git LFS is needed — see Step 4)

**This task is the data-acquisition spike. The word cap is the tuning knob: build the largest set that downloads/builds in reasonable time and keeps the asset a sane size. Report the achieved word count + DB size. If the full kaikki download is impractical here, build a smaller cap and say so — the schema/repository do not change.**

- [ ] **Step 1: Acquire the source + a frequency list**

Download a Wiktextract English extract from kaikki.org (e.g. the English dictionary JSONL) and an English frequency word list (top ~30–50k lemmas). Confirm the real JSONL field names match Task 1's `parse_entry` (adjust the script + its sample/test if they differ; keep tests green). Note the download size + any subset you used.

- [ ] **Step 2: Build the DB**

Run the script with a cap:
```bash
cd tools/dictionary
python3 build_dictionary.py --input <extract.jsonl> --freq <freqlist.txt> --cap 40000 --out ../../core/dictionary/src/main/assets/dictionary.db
```
Print the resulting row counts (`entries`, `forms`) and the file size (`ls -lh`).

- [ ] **Step 3: Sanity-verify the real DB**

Query a few known words to confirm content:
```bash
sqlite3 core/dictionary/src/main/assets/dictionary.db "SELECT headword,ipa,pos,uk_translations FROM entries WHERE headword IN ('run','dog','book','consciousness');"
sqlite3 core/dictionary/src/main/assets/dictionary.db "SELECT * FROM forms WHERE form IN ('running','dogs','books') LIMIT 5;"
```
Expected: real IPA + POS + Ukrainian translations for common words, and form→base rows.

- [ ] **Step 4: Decide Git storage by size**

If `dictionary.db` is larger than ~50 MB, configure Git LFS for it (`git lfs track "core/dictionary/src/main/assets/dictionary.db"`, commit `.gitattributes`); otherwise commit it directly. Report the size + choice.

- [ ] **Step 5: On-device sanity (the real asset works)**

Build + install; the repository will copy the asset on first use. Add a TEMP debug call (removed before commit) or a quick Robolectric test that opens the REAL asset DB (not the test one) and asserts `lookup("dog")?.translations` is non-empty — to prove the bundled asset is valid and queryable. (If using the on-device path, query via `run-as ... sqlite3` or a temporary log.)
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```
Expected: the app builds with the asset bundled; a lookup of a common word returns an entry.

- [ ] **Step 6: Commit**

```bash
git add core/dictionary/src/main/assets/dictionary.db .gitattributes
git commit -m "feat: bundle generated offline dictionary database"
```

---

## Self-Review Notes

- **Spec coverage:** build script → SQLite `entries`+`forms` (Task 1); trim by frequency cap (Tasks 1, 3); `:core:dictionary` + `DictionaryEntry` + `DictionaryRepository.lookup` with normalize + form-of + null-miss (Task 2); bundled read-only asset DB (Tasks 2–3); build-script + repository tests on small samples (Tasks 1–2); acquisition/size spike (Task 3). The UI + ML Kit fallback are explicitly 3b-2.
- **Implementation note vs spec:** the spec said "Room createFromAsset"; this plan uses raw `SQLiteDatabase` for the read-only asset to avoid Room's prepackaged-identity-hash requirement. Same `DictionaryRepository` surface, simpler and more robust for an externally-built DB. (Flagged here so the reviewer treats it as an intentional refinement, not drift.)
- **Type consistency:** `DictionaryEntry(headword, ipa, partOfSpeech, definitions, translations)`, `DictionaryRepository.lookup(word): DictionaryEntry?`, schema `entries(headword,ipa,pos,definitions,uk_translations)` + `forms(form,headword)`, and `Entry(headword,ipa,pos,definitions,uk_translations)` (script) are used consistently. Note the column is `pos`/`uk_translations` in SQLite but `partOfSpeech`/`translations` in the Kotlin domain — mapped in the repository.
- **Risk:** Task 3 depends on the real Wiktextract field shapes + download feasibility; Task 1 explicitly says to verify/adjust field names against a real sample, and Task 3's cap absorbs size/time. Tasks 1–2 are fully testable without the multi-GB data.
