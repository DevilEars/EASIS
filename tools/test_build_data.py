"""Curriculum generation and its checks, on a small synthetic course. No Eldamo XML needed."""
import copy
import unittest

from build_data import build_curriculum, check_curriculum, course_version


def tok(text, lemma, feats=()):
    return {"text": text, "punct": "", "lemma": lemma, "features": list(feats),
            "gloss": "", "resolution": "eldamo-element"}


PHRASES = [
    {"id": "w1", "textId": "w1", "line": 1, "tokens": [tok("a", "A", ["plural"]), tok("b", "B")]},
    {"id": "g-02", "textId": "g", "line": 2, "tokens": [tok("c", "C", ["plural"]), tok("x", None)]},
    {"id": "g-01", "textId": "g", "line": 1, "tokens": [tok("a", "A"), tok("c", "C", ["genitive"])]},
]
SKEL = {
    "texts": ["w1", "g"], "goal": "g", "verses": [[1, 1], [2, 2]],
    "foundations": [{"id": "pron", "entry": "p", "at": "w1"}],
    "features": [{"feature": "genitive", "entry": "genitive", "label": "Genitive"},
                 {"feature": "plural", "entry": "plural nouns", "label": "Plural"}],
}
LESSONS = {
    "pron": {"summary": "Sounds.", "feature": None},
    "feat-plural": {"summary": "Plurals.", "feature": "plural"},
    "feat-genitive": {"summary": "Genitive.", "feature": "genitive"},
}


class BuildCurriculumTests(unittest.TestCase):
    def test_one_session_per_line_in_text_order(self):
        self.assertEqual(build_curriculum(PHRASES, SKEL), [
            {"n": 1, "phrase": "w1", "label": "Warm-up 1 of 1", "lemmas": ["A", "B"], "lessons": ["pron", "feat-plural"]},
            {"n": 2, "phrase": "g-01", "label": "Line 1 of 2", "verse": 1, "lemmas": ["C"], "lessons": ["feat-genitive"]},
            {"n": 3, "phrase": "g-02", "label": "Line 2 of 2", "verse": 2, "lemmas": [], "lessons": []},
        ])

    def test_generated_course_passes_its_checks(self):
        self.assertEqual(check_curriculum(build_curriculum(PHRASES, SKEL), PHRASES, LESSONS, SKEL), [])

    def test_word_read_before_it_is_taught_fails(self):
        s = build_curriculum(PHRASES, SKEL)
        s[1]["lemmas"].remove("C"); s[2]["lemmas"].append("C")
        self.assertIn("session 2: C is read before it is taught", check_curriculum(s, PHRASES, LESSONS, SKEL))

    def test_feature_used_before_it_is_taught_fails(self):
        s = build_curriculum(PHRASES, SKEL)
        s[1]["lessons"] = []; s[2]["lessons"] = ["feat-genitive"]
        self.assertIn("session 2: feature genitive is used before it is taught",
                      check_curriculum(s, PHRASES, LESSONS, SKEL))

    def test_word_taught_twice_fails(self):
        s = build_curriculum(PHRASES, SKEL)
        s[2]["lemmas"].append("A")
        self.assertIn("lemma taught twice: A", check_curriculum(s, PHRASES, LESSONS, SKEL))

    def test_unknown_lesson_and_phrase_fail(self):
        skel = copy.deepcopy(SKEL)
        skel["foundations"][0]["at"] = "nope"
        s = build_curriculum(PHRASES, SKEL)
        s[0]["lessons"].append("feat-missing")
        problems = check_curriculum(s, PHRASES, LESSONS, skel)
        self.assertIn("skeleton: foundation pron attaches to unknown phrase nope", problems)
        self.assertIn("session 1: unknown lesson feat-missing", problems)

    def test_unlisted_feature_and_empty_text_fail(self):
        skel = copy.deepcopy(SKEL)
        skel["features"] = skel["features"][:1]
        skel["texts"] = ["w1", "g", "ghost"]
        problems = check_curriculum(build_curriculum(PHRASES, SKEL), PHRASES, LESSONS, skel)
        self.assertIn("skeleton: feature plural is used but not listed", problems)
        self.assertIn("skeleton: text ghost has no phrases", problems)

    def test_course_version_is_stable_and_changes_with_content(self):
        s = build_curriculum(PHRASES, SKEL)
        self.assertEqual(course_version(s), course_version(copy.deepcopy(s)))
        self.assertEqual(12, len(course_version(s)))
        s[2]["lemmas"].append("Z")
        self.assertNotEqual(course_version(build_curriculum(PHRASES, SKEL)), course_version(s))


if __name__ == "__main__":
    unittest.main()
