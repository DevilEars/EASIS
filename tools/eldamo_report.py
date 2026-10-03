#!/usr/bin/env python3
"""Eldamo coverage report for the Quenya app.

Usage:
    python tools/eldamo_report.py path/to/eldamo-data.xml

Source: https://github.com/pfstrack/eldamo (src/data/eldamo-data.xml)
Data (c) 2008-2026 Paul Strack, CC BY 4.0 - attribution required in the app.

NOTE: this early report only counts lines that carry <element> token analysis. Some Markirya
lines do not, so its lemma/feature counts are lower bounds. tools/build_data.py resolves those
lines by strict lookup and is the source of truth for the curriculum.

Answers, before any UI exists:
  1. How much late-period Quenya (l="q") data is there?
  2. Does every token of each milestone text resolve to a lexicon entry with a gloss?
  3. How many lemmas / lemma+form pairs must the learner meet per milestone?
  4. Do case/plural endings actually vary by noun class (vocalic vs consonantal stems),
     and if so, is that already explained by the single Eldamo grammar entry each
     feature's lesson is built from, or does the curriculum need an extra teaching unit?
"""
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict

LANG = "q"  # Late Quenya (1950-1973). "nq" = Neo-Quenya, "mq"/"eq" = middle/early.

# Milestones, in teaching order. `phrases` are Eldamo phrase-word `v` values.
# `text` pulls every line of an Eldamo text entry (optionally the first N lines).
MILESTONES = [
    {"id": "elen-sila", "session": 10,
     "phrases": ["elen síla lúmenn’ omentielvo"]},
    {"id": "aiya-earendil", "session": 20,
     "phrases": ["aiya Eärendil elenion ancalima"]},
    {"id": "markirya-12", "session": 40,
     "text": "Markirya", "first_n": 12},
    {"id": "markirya-full", "session": None,  # stretch goal
     "text": "Markirya"},
]


def load(path):
    root = ET.parse(path).getroot()
    words = list(root.iter("word"))
    index = {}
    for w in words:
        index.setdefault((w.get("l"), w.get("v")), w)
    return root, words, index


def global_stats(words):
    q = [w for w in words if w.get("l") == LANG]
    by_pos = Counter(w.get("speech") for w in q)
    # Attested inflected forms live on <ref><inflect form="..."/></ref>.
    inflected = Counter()
    n_forms = 0
    for w in q:
        forms = [i for r in w.findall("ref") for i in r.findall("inflect")]
        if forms:
            inflected[w.get("speech")] += 1
            n_forms += len(forms)
    print(f"Late Quenya entries: {len(q)}")
    print("  by part of speech:", by_pos.most_common(8))
    print(f"  entries with attested inflected forms: {sum(inflected.values())} "
          f"({n_forms} attested forms)")
    print("  ...by part of speech:", inflected.most_common(6))


def phrase_lines(words, milestone):
    if "phrases" in milestone:
        return milestone["phrases"]
    text = next(w for w in words
                if w.get("l") == LANG and w.get("speech") == "text"
                and w.get("v") == milestone["text"])
    lines = [e.get("v") for e in text.findall("element")]
    return lines[: milestone.get("first_n")]


def milestone_report(words, index, milestone):
    phrases = {w.get("v"): w for w in words
               if w.get("l") == LANG and w.get("speech") == "phrase"}
    lemmas, forms, unresolved, no_gloss, no_analysis = set(), set(), [], [], []
    lines = phrase_lines(words, milestone)
    for line in lines:
        pw = phrases.get(line)
        if pw is None:
            unresolved.append(("PHRASE", line))
            continue
        if not pw.findall("element"):
            no_analysis.append(line)   # Eldamo gives no token analysis for this line
        for e in pw.findall("element"):
            l, v, form = e.get("l"), e.get("v"), e.get("form")
            lemmas.add((l, v))
            if form:
                forms.add((l, v, form))
            entry = index.get((l, v))
            if entry is None:
                unresolved.append((l, v))
            elif not entry.get("gloss"):
                no_gloss.append(v)
    tag = f"session {milestone['session']}" if milestone["session"] else "stretch"
    print(f"\n[{milestone['id']}] ({tag})")
    print(f"  lines: {len(lines)}  unique lemmas: {len(lemmas)}  "
          f"lemma+form pairs: {len(forms)}")
    print(f"  lines with NO token analysis in Eldamo (counts below exclude them): "
          f"{len(no_analysis)} {no_analysis[:3]}")
    print(f"  unresolved tokens: {unresolved or 'none'}")
    print(f"  entries without gloss: {no_gloss or 'none'}")


# ---------------------------------------------------------------------------
# Grammar load. The pool sizes below are MEASURED from the data. The session
# estimates are a MODEL: change the constants and re-run. Only real learners
# can calibrate them.
# ---------------------------------------------------------------------------

# Feature -> Eldamo grammar entry that explains it (None = no entry exists).
GRAMMAR_ENTRY = {
    "present": "present", "future": "future", "infinitive": "infinitive",
    "active-participle": "active participle", "allative": "allative",
    "genitive": "genitive", "ablative": "ablative", "locative": "locative",
    "instrumental": "instrumental", "plural": "plural nouns",
    "3rd-sg-poss": "possessive", "1st-pl-inclusive-poss": "possessive",
    "elided": "elision", "intensive": None,
}
MINOR = {"3rd-sg-poss", "1st-pl-inclusive-poss", "intensive", "elided"}
FOUNDATION_SESSIONS = 4  # pronunciation, noun classes, article, word order
# (optimistic, conservative) assumptions
CORE_SESSIONS = (1, 2)     # new-material sessions per core feature
MINOR_SESSIONS = (1, 1)
WORDS_PER_SESSION = (8, 5)


def milestone_tokens(words, milestone):
    phrases = {w.get("v"): w for w in words
               if w.get("l") == LANG and w.get("speech") == "phrase"}
    toks = []
    for line in phrase_lines(words, milestone):
        toks += [(e.get("l"), e.get("v"), e.get("form") or "")
                 for e in phrases[line].findall("element")]
    return toks


def attested_pool(words):
    """feature -> set of late-Quenya lemmas with an attested example."""
    pool = {}
    for w in words:
        if w.get("l") != LANG:
            continue
        for ref in w.findall("ref"):
            for inf in ref.findall("inflect"):
                for f in (inf.get("form") or "").split():
                    pool.setdefault(f, set()).add(w.get("v"))
    return pool


def grammar_notes_len(words):
    out = {}
    for w in words:
        if w.get("l") == LANG and w.get("speech") == "grammar":
            n = w.find("notes")
            out[w.get("v")] = len(n.text or "") if n is not None else 0
    return out


def grammar_report(words):
    pool, notes = attested_pool(words), grammar_notes_len(words)
    print("\n=== Grammar load ===")
    seen_feats, seen_lemmas, n_read = set(), set(), 0
    rows = []
    for m in MILESTONES:
        toks = milestone_tokens(words, m)
        feats = {f for _, _, form in toks for f in form.split()}
        new = sorted(feats - seen_feats)
        seen_feats |= feats
        seen_lemmas |= {(l, v) for l, v, _ in toks}
        n_read += 1
        rows.append((m, new, len(seen_feats), len(seen_lemmas), n_read))
    for m, new, n_feats, n_lem, n_read in rows:
        print(f"\n[{m['id']}] new features: {new}")
        ests = []
        for i in (0, 1):
            core = sum(1 for f in seen_feats_upto(rows, m) if f not in MINOR)
            minor = sum(1 for f in seen_feats_upto(rows, m) if f in MINOR)
            vocab = -(-n_lem // WORDS_PER_SESSION[i])
            total = (FOUNDATION_SESSIONS + core * CORE_SESSIONS[i]
                     + minor * MINOR_SESSIONS[i] + vocab + n_read)
            ests.append(total)
        target = m["session"]
        print(f"  cumulative: {n_feats} features, {n_lem} lemmas")
        print(f"  estimated sessions needed: {ests[0]} (optimistic) to "
              f"{ests[1]} (conservative); target: {target or 'stretch'}")
    print("\nPer-feature drill pools (attested lemmas) and lesson prose size:")
    for m, new, *_ in rows:
        for f in new:
            g = GRAMMAR_ENTRY.get(f)
            prose = f"{notes.get(g, 0):>6} chars" if g else "  no entry"
            print(f"  {f:24} pool={len(pool.get(f, ())):>4}   "
                  f"grammar entry: {g or '-':18} {prose}")


# ---------------------------------------------------------------------------
# Noun-class dependence (BACKLOG item 1): do case/plural endings differ by
# stem class? Measured from attested (lemma, surface) pairs, not from
# Eldamo's separate <inflect-table>/<class> grammar-paradigm elements, which
# describe other languages' and speeches' declension tables, not Quenya
# nouns specifically.
# ---------------------------------------------------------------------------
CASE_FEATURES = ["plural", "genitive", "allative", "ablative", "locative", "instrumental"]


def _strip_marks(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if not unicodedata.combining(c))


def _stem_class(lemma):
    bare = re.sub(r"[^a-z]", "", _strip_marks(lemma).lower())
    if not bare:
        return "?"
    return "vocalic" if bare[-1] in "aeiou" else f"cons-{bare[-1]}"


def _suffix(lemma, surface):
    a, b = _strip_marks(lemma).lower(), _strip_marks(surface).lower()
    n = 0
    while n < len(a) and n < len(b) and a[n] == b[n]:
        n += 1
    return b[n:] or "(Ø/stem-change)"


def noun_class_report(words):
    nouns = [w for w in words if w.get("l") == LANG and w.get("speech") == "n"]
    print("\n=== Noun-class dependence of case/plural endings ===")
    print(f"(measured over {len(nouns)} late-Quenya nouns' attested <ref><inflect> forms)")
    for feat in CASE_FEATURES:
        by_class = defaultdict(Counter)
        for w in nouns:
            lemma = w.get("v")
            for ref in w.findall("ref"):
                surf = ref.get("v") or ""
                if not surf or " " in surf.strip():
                    continue
                for inf in ref.findall("inflect"):
                    if (inf.get("form") or "").split() == [feat]:
                        by_class[_stem_class(lemma)][_suffix(lemma, surf)] += 1
        total = sum(sum(c.values()) for c in by_class.values())
        print(f"\n  {feat} ({total} attested forms):")
        for cls, ctr in sorted(by_class.items(), key=lambda x: -sum(x[1].values())):
            print(f"    {cls:10} n={sum(ctr.values()):3}  patterns={len(ctr):2}  "
                  f"top={ctr.most_common(4)}")


def seen_feats_upto(rows, milestone):
    feats = set()
    for m, new, *_ in rows:
        feats |= set(new)
        if m is milestone:
            break
    return feats


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    root, words, index = load(sys.argv[1])
    print(f"Eldamo data version {root.get('version')}")
    global_stats(words)
    for m in MILESTONES:
        milestone_report(words, index, m)
    grammar_report(words)
    noun_class_report(words)


if __name__ == "__main__":
    main()
