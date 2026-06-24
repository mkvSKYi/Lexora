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
