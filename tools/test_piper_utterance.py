"""Utterance strings for the Piper adapter. No model, no waveform."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from piper_utterance import line_utterance, word_utterance

ROOT = Path(__file__).resolve().parent.parent
VENV_PYTHON = ROOT.parent / ".venv-piper" / "bin" / "python"

# Literals from docs/specs/neural-reader-audio.md. Not derived in the test.
WORDS = {
    "cirya": "[[ˈkɪɹjɑ.]]",
    "Eärendil": "[[ɛɑˈɹɛndɪl.]]",
    "andúnë": "[[ɑnˈduːnɛ.]]",
    "ancalima": "[[ɑnˈkɑlɪmɑ.]]",
    "né": "[[ˈnɛː.]]",
}
STRESSED = {
    "cirya": "ɪ",
    "Eärendil": "ɛ",
    "andúnë": "uː",
    "ancalima": "ɑ",
    "né": "ɛː",
}
CONSONANTS = set("ptkbdgfvshmnlwjɹ")
LINE = "aiya Eärendil elenion ancalima"
LINE_UTTERANCE = "[[ˈaɪjɑ ɛɑˈɹɛndɪl ɛˈlɛnɪɒn ɑnˈkɑlɪmɑ.]]"


def stressed_vowel(utterance):
    """Vowel after ˈ, onset consonants skipped, length mark kept."""
    inner = utterance[2:-2]
    i = inner.index("ˈ") + 1
    while inner[i] in CONSONANTS:
        i += 1
    vowel = inner[i]
    if inner[i + 1] == "ː":
        vowel += "ː"
    return vowel


class PiperUtteranceTests(unittest.TestCase):
    def test_fixture_words(self):
        for spelling, utterance in WORDS.items():
            with self.subTest(spelling=spelling):
                self.assertEqual(word_utterance(spelling), utterance)

    def test_stressed_vowel_is_the_marked_one(self):
        for spelling, vowel in STRESSED.items():
            with self.subTest(spelling=spelling):
                self.assertEqual(stressed_vowel(word_utterance(spelling)), vowel)

    def test_two_word_line_is_one_block(self):
        utterance = line_utterance("cirya né")
        self.assertEqual(utterance, "[[ˈkɪɹjɑ ˈnɛː.]]")
        self.assertEqual(utterance.count("[["), 1)

    def test_comma_semicolon_and_colon_are_the_same_pause(self):
        paused = "[[ˈkɪɹjɑ, ˈnɛː.]]"
        self.assertEqual(line_utterance("cirya, né"), paused)
        self.assertEqual(line_utterance("cirya; né"), paused)
        self.assertEqual(line_utterance("cirya: né"), paused)
        self.assertNotIn(";", paused)
        self.assertEqual(paused.count("."), 1)

    def test_question_line_ends_in_question_mark(self):
        utterance = line_utterance("cirya né?")
        self.assertTrue(utterance.endswith("?]]"))
        self.assertNotIn(".", utterance)

    def test_listen_line(self):
        self.assertEqual(line_utterance(LINE), LINE_UTTERANCE)

    def test_dry_utterance_prints_listen_list_without_importing_piper(self):
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "piper.py"
            fake.write_text("raise SystemExit('dry run imported piper')\n", encoding="utf-8")
            env = os.environ.copy()
            env["PYTHONPATH"] = tmp + os.pathsep + env.get("PYTHONPATH", "")
            proc = subprocess.run(
                [VENV_PYTHON, "tools/generate_audio.py", "--dry-utterance"],
                cwd=ROOT,
                capture_output=True,
                text=True,
                env=env,
            )
        self.assertEqual(proc.returncode, 0, proc.stderr)
        out = proc.stdout
        for spelling in ("cirya", "Eärendil", "andúnë", "ancalima"):
            self.assertIn(WORDS[spelling], out)
        self.assertIn(LINE_UTTERANCE, out)
        self.assertNotIn("dry run imported piper", proc.stderr + proc.stdout)


if __name__ == "__main__":
    unittest.main()
