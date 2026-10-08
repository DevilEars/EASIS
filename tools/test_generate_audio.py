"""Which clips generate_audio.py plans. No Piper, no ffmpeg."""
import json
import tempfile
import unittest
from pathlib import Path

from generate_audio import inventory


class InventoryTests(unittest.TestCase):
    def test_taught_headwords_join_token_words(self):
        with tempfile.TemporaryDirectory() as d:
            files = Path(d)
            (files / "phrases.json").write_text(json.dumps([
                {"id": "p1", "text": "man cenuva", "tokens": [{"text": "man"}, {"text": "cenuva"}]}]), encoding="utf-8")
            (files / "lexicon.json").write_text(json.dumps([
                {"id": "man", "lemma": "man"}, {"id": "ken", "lemma": "cen-"}, {"id": "x", "lemma": "unused"}]),
                encoding="utf-8")
            (files / "curriculum.json").write_text(json.dumps([{"n": 1, "lemmas": ["man", "ken"]}]), encoding="utf-8")
            words, lines = inventory(files)
        self.assertEqual({"man": "man", "cenuva": "cenuva", "cen": "cen-"}, words)
        self.assertEqual({"p1": "man cenuva"}, lines)


if __name__ == "__main__":
    unittest.main()
