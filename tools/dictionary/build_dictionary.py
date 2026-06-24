"""Build a compact SQLite dictionary.db from Wiktextract/kaikki English JSONL.

Dev-time tool (not shipped in the app). Stdlib only.

Schema:
  entries(headword TEXT PRIMARY KEY, ipa TEXT, pos TEXT,
          definitions TEXT, uk_translations TEXT)
  forms(form TEXT PRIMARY KEY, headword TEXT)

`definitions` and `uk_translations` are JSON arrays stored as TEXT.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
from dataclasses import dataclass
from typing import Iterator, Optional


@dataclass
class Entry:
    headword: str
    ipa: Optional[str]
    pos: Optional[str]
    definitions: list[str]
    uk_translations: list[str]


def parse_entry(obj: dict) -> Optional[Entry]:
    """Extract an Entry from one Wiktextract JSON object, or None if no headword."""
    word = obj.get("word")
    if not word:
        return None
    headword = word.strip().lower()
    if not headword:
        return None

    pos = obj.get("pos")
    ipa = next((s["ipa"] for s in obj.get("sounds", []) if s.get("ipa")), None)
    definitions = [
        g for sense in obj.get("senses", []) for g in sense.get("glosses", [])
    ]
    uk_translations = [
        t["word"]
        for t in obj.get("translations", [])
        if t.get("lang_code") == "uk" and t.get("word")
    ]
    return Entry(headword, ipa, pos, definitions, uk_translations)


def extract_forms(obj: dict) -> list[tuple[str, str]]:
    """Yield (inflected_lowercased, base_lowercased) pairs from senses[].form_of[]."""
    word = obj.get("word")
    if not word:
        return []
    inflected = word.strip().lower()
    if not inflected:
        return []

    forms: list[tuple[str, str]] = []
    for sense in obj.get("senses", []):
        for fo in sense.get("form_of", []):
            base = fo.get("word")
            if not base:
                continue
            base = base.strip().lower()
            if base:
                forms.append((inflected, base))
    return forms


def _create_schema(con: sqlite3.Connection) -> None:
    con.execute(
        "CREATE TABLE IF NOT EXISTS entries ("
        "headword TEXT PRIMARY KEY, ipa TEXT, pos TEXT, "
        "definitions TEXT, uk_translations TEXT)"
    )
    con.execute(
        "CREATE TABLE IF NOT EXISTS forms ("
        "form TEXT PRIMARY KEY, headword TEXT)"
    )
    con.execute("CREATE INDEX IF NOT EXISTS idx_forms_form ON forms(form)")


def _iter_jsonl(path: str) -> Iterator[dict]:
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            yield json.loads(line)


def build(
    input_jsonl: str,
    freq_words: set[str],
    out_db_path: str,
    cap: Optional[int] = None,
) -> None:
    """Stream the JSONL, inserting entries (with defs/translations) and forms.

    Only words whose lowercased headword is in `freq_words` are processed.
    `cap`, if set, limits the number of distinct entries inserted (for testing
    or quick runs in Task 3).
    """
    con = sqlite3.connect(out_db_path)
    try:
        _create_schema(con)
        inserted = 0
        for obj in _iter_jsonl(input_jsonl):
            entry = parse_entry(obj)
            if entry is None or entry.headword not in freq_words:
                continue

            if entry.definitions or entry.uk_translations:
                if cap is not None and inserted >= cap:
                    continue
                con.execute(
                    "INSERT OR REPLACE INTO entries "
                    "(headword, ipa, pos, definitions, uk_translations) "
                    "VALUES (?, ?, ?, ?, ?)",
                    (
                        entry.headword,
                        entry.ipa,
                        entry.pos,
                        json.dumps(entry.definitions, ensure_ascii=False),
                        json.dumps(entry.uk_translations, ensure_ascii=False),
                    ),
                )
                inserted += 1

            for form, base in extract_forms(obj):
                con.execute(
                    "INSERT OR REPLACE INTO forms (form, headword) VALUES (?, ?)",
                    (form, base),
                )
        con.commit()
    finally:
        con.close()


def _load_freq_words(path: str) -> set[str]:
    words: set[str] = set()
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            token = line.strip().split()
            if token:
                words.add(token[0].lower())
    return words


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build dictionary.db from Wiktextract/kaikki English JSONL."
    )
    parser.add_argument("--input", required=True, help="Path to Wiktextract JSONL.")
    parser.add_argument(
        "--freq",
        required=True,
        help="Path to a frequency word list (one word per line, optionally "
        "'word count').",
    )
    parser.add_argument("--out", required=True, help="Output SQLite db path.")
    parser.add_argument(
        "--cap",
        type=int,
        default=None,
        help="Optional cap on the number of entries to insert.",
    )
    args = parser.parse_args()

    freq_words = _load_freq_words(args.freq)
    build(args.input, freq_words, args.out, cap=args.cap)


if __name__ == "__main__":
    main()
