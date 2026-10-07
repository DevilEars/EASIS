#!/usr/bin/env python3
"""Build the app's data files from Eldamo.  No lesson content is hand-authored.

Usage:  python tools/build_data.py path/to/eldamo-data.xml [--out DIR]

Inputs : eldamo-data.xml (CC BY 4.0, (c) Paul Strack, https://eldamo.org),
         tools/skeleton.json (table of contents: topic order only).
Outputs: lexicon.json, forms.json, phrases.json, lessons.json, curriculum.json,
         meta.json  (default dir: composeApp/src/commonMain/composeResources/files)
Prints a report and exits non-zero if any invariant fails.
"""
import argparse, html, json, math, re, sys, unicodedata
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

LANG = "q"
ROOT = Path(__file__).resolve().parent.parent
SKEL = json.loads((ROOT / "tools" / "skeleton.json").read_text(encoding="utf-8"))
SINGLE_PHRASES = [("elen-sila", "elen síla lúmenn’ omentielvo"),
                  ("aiya-earendil", "aiya Eärendil elenion ancalima")]
EXCLUDE_POS = {"grammar", "text", "phrase", "phoneme", "phonetic-group",
               "phonetic-rule", "phonetics", "?"}
SUPERSCRIPTS = "¹²³⁴⁵⁶⁷⁸⁹⁰"
# ---------------------------------------------------------------- text utils
def strip_marks(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if not unicodedata.combining(c))

def key(s):
    """Accent-, case-, punctuation-insensitive lookup key (k == c)."""
    s = strip_marks(s).lower().translate(str.maketrans("", "", SUPERSCRIPTS))
    s = re.sub(r"[’‘'`´\-?.,;:!\"“”()\[\]…]", "", s)
    return s.replace("k", "c")

def skey(s):
    """Strict key: case/punctuation-insensitive, k == c, but ACCENTS KEPT."""
    s = unicodedata.normalize("NFC", s).lower().translate(str.maketrans("", "", SUPERSCRIPTS))
    s = re.sub(r"[’‘'`´\-?.,;:!\"“”()\[\]…]", "", s)
    return s.replace("k", "c")

def display_lemma(v):
    return v.translate(str.maketrans("", "", SUPERSCRIPTS))

CLEAN = re.compile(r"^[a-záéíóúëäöüâêîôû’\-]+$")
def is_clean(s):
    return bool(CLEAN.match(s))

def html_to_text(frag):
    s = html.unescape(frag or "")
    s = re.sub(r"<li[^>]*>", "\n• ", s)
    s = re.sub(r"</(p|li|blockquote|ul|ol|h\d)>|<br\s*/?>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    paras = [re.sub(r"[ \t]+", " ", p).strip() for p in s.split("\n")]
    return [p for p in paras if p]

STOP_LABELS = re.compile(r"^(Origins?|Conceptual Development|Historical|Development|Etymology)\b", re.I)

def summarize(paras, lo=200, hi=700):
    """Lead of an Eldamo grammar entry: skip a leading table-of-contents bullet list,
    take paragraphs until ~lo chars, never run into 'Origins'/'Conceptual Development'.
    A lead that only introduces the next example ends on ':'; drop that sentence."""
    i = 0
    while i < len(paras) and paras[i].startswith("•") and len(paras[i]) < 80:
        i += 1
    out = ""
    for p in paras[i:]:
        if out and (len(out) >= lo or STOP_LABELS.match(p)):
            break
        out = (out + "\n\n" + p).strip() if out else p
    out = re.sub(r"^Overview:\s*", "", out)
    if len(out) > hi:
        cut = max(out.rfind(". ", 0, hi), out.rfind("? ", 0, hi))
        out = out[: cut + 1] if cut > 200 else out[:hi].rstrip() + "…"
    s = out.rstrip()
    if s.endswith(":"):
        cut = max(s.rfind(". "), s.rfind("? "), s.rfind("! "), s.rfind(".\n"), s.rfind("?\n"), s.rfind("!\n"))
        out = s[:cut + 1] if cut != -1 else s[:-1].rstrip()
    return out


def _check_summarize():
    assert summarize(["The genitive uses -o. Tolkien said:"]) == "The genitive uses -o."
    assert not summarize(["Already fine."]).endswith(":")

# ------------------------------------------------------------------- load
def load(path):
    root = ET.parse(path).getroot()
    words = [w for w in root.iter("word") if w.get("l") == LANG]
    return root.get("version"), words

def build_lexicon(words):
    lex = {}
    for w in words:
        pos = w.get("speech")
        if pos in EXCLUDE_POS:
            continue
        refs = w.findall("ref")
        lex[w.get("v")] = {
            "id": w.get("v"), "lemma": display_lemma(w.get("v")), "pos": pos,
            "gloss": {"en": w.get("gloss") or ""},
            "attestations": len(refs),
            "mark": w.get("mark"),               # raw Eldamo mark, meaning not interpreted
            "deprecated": w.find("deprecated") is not None,
            "confidence": "attested" if refs else "unverified",
        }
    return lex

def build_forms(words, lex):
    """Attested inflected forms: <ref v=surface><inflect form='plural'/></ref>."""
    cand = defaultdict(lambda: defaultdict(list))   # (lemma, feats) -> key -> [(surface, source)]
    for w in words:
        lid = w.get("v")
        if lid not in lex:
            continue
        for ref in w.findall("ref"):
            surf = ref.get("v") or ""
            if " " in surf.strip() or not surf:
                continue
            for inf in ref.findall("inflect"):
                feats = tuple((inf.get("form") or "").split())
                if feats:
                    cand[(lid, feats)][key(surf)].append((surf, ref.get("source") or ""))
    forms = []
    for (lid, feats), by_key in cand.items():
        for k, occ in by_key.items():
            pick = sorted(occ, key=lambda x: (not is_clean(x[0]), x[0] != x[0].lower(),
                                              -occ.count(x)))[0]
            forms.append({"lemma": lid, "surface": pick[0].lower() if is_clean(pick[0].lower()) else pick[0],
                          "features": list(feats), "source": pick[1], "clean": is_clean(pick[0].lower())})
    forms.sort(key=lambda f: (f["lemma"], f["features"], f["surface"]))
    return forms

# ------------------------------------------------------------ phrase tokens
def build_indexes(words, lex, forms):
    surf = defaultdict(list)            # strict key(surface) -> [(lemma, feats)]
    loose = defaultdict(set)            # accent-insensitive key -> lemmas (guesses only)
    for f in forms:
        surf[skey(f["surface"])].append((f["lemma"], tuple(f["features"])))
    lemk = defaultdict(list)
    for lid, e in lex.items():
        lemk[skey(e["lemma"])].append(lid)
        loose[key(e["lemma"])].add(lid)
    for f in forms:
        loose[key(f["surface"])].add(f["lemma"])
    return surf, lemk, loose

def split_tokens(text):
    """Whitespace tokens, each split further on internal hyphens: Eldamo sometimes
    tags the two halves of a hyphenated compound (e.g. "ëar-celumessen") as separate
    <element>s, and splitting here lets build_phrase's position-based alignment see
    them as two tokens instead of one, so each half gets its own lemma/gloss."""
    out = []
    for raw in text.split():
        m = re.match(r"^(.*?)([?.,;:!]*)$", raw)
        word, punct = m.group(1), m.group(2)
        parts = word.split("-")
        if len(parts) > 1 and all(parts):
            out += [(p, "-") for p in parts[:-1]]
            out.append((parts[-1], punct))
        else:
            out.append((word, punct))
    return out

def resolve_token(tok, idx, lex):
    """Strict, accent-sensitive resolution. Never guesses a lemma: accent-only
    matches are recorded as `guess` and left unresolved."""
    surf, lemk, loose = idx
    k = skey(tok)
    ids = lemk.get(k, [])
    if ids:                                   # 1. the token IS a lemma
        ranked = sorted(ids, key=lambda i: -lex[i]["attestations"])
        best = ranked[0]
        # Homonyms (Eldamo's superscripted lemmas) aren't really ambiguous if one
        # is attested far more often than the rest, e.g. cirya¹ "ship" (55) vs.
        # cirya² "cleft, pass" (1) — don't flag that as a guess to the learner.
        dominant = len(ranked) == 1 or lex[best]["attestations"] >= 5 * max(1, lex[ranked[1]]["attestations"])
        return best, [], "lexicon" if dominant else "lexicon-ambiguous", None
    uniq = sorted(set(surf.get(k, [])))
    if uniq:                                  # 2. the token is an attested inflected form
        best = max(uniq, key=lambda c: lex[c[0]]["attestations"])
        return best[0], list(best[1]), "form-index" + ("" if len(uniq) == 1 else "-ambiguous"), None
    guess = sorted(loose.get(key(tok), []))   # 3. accent-insensitive: guess only
    return None, [], "unresolved", (guess[0] if guess else None)

def build_phrase(pid, w, order, idx, lex, extra):
    text = w.get("v")
    toks = split_tokens(text)
    els = w.findall("element")
    tokens, how = [], Counter()
    use_elements = len(els) == len(toks) and len(els) > 0
    for i, (t, p) in enumerate(toks):
        if use_elements and els[i].get("v") in lex:
            lemma, feats, res = els[i].get("v"), (els[i].get("form") or "").split(), "eldamo-element"
        else:
            lemma, feats, res, guess = resolve_token(t, idx, lex)
        if res == "eldamo-element": guess = None
        how[res] += 1
        tok = {"text": t, "punct": p, "lemma": lemma, "features": feats,
               "gloss": lex[lemma]["gloss"]["en"] if lemma else "", "resolution": res}
        if guess: tok["guess"] = guess
        tokens.append(tok)
    seen, variants = set(), []
    for ref in w.findall("ref"):
        v = ref.get("v")
        if v and v not in seen:
            seen.add(v); variants.append({"text": v, "source": ref.get("source") or ""})
    d = {"id": pid, "order": order, "text": text, "gloss": w.get("gloss") or "",
         "tokens": tokens, "variants": variants}
    d.update(extra)
    return d, how

def build_phrases(words, lex, forms):
    idx = build_indexes(words, lex, forms)
    byv = {w.get("v"): w for w in words if w.get("speech") == "phrase"}
    texts = {w.get("v"): w for w in words if w.get("speech") == "text"}
    out, stats = [], Counter()
    for pid, v in SINGLE_PHRASES:
        d, how = build_phrase(pid, byv[v], 0, idx, lex, {"textId": pid, "line": 1})
        out.append(d); stats.update(how)
    mk = texts["Markirya"]
    for i, e in enumerate(mk.findall("element"), 1):
        d, how = build_phrase(f"markirya-{i:02d}", byv[e.get("v")], i, idx, lex,
                              {"textId": "markirya", "line": i,
                               "note": "Eldamo line (second Late Quenya draft, MC/221-2), editorially normalised."})
        out.append(d); stats.update(how)
    return out, stats

# ---------------------------------------------------------------- lessons
def feature_info(f, gram_names):
    """Skeleton entry for a feature; unknown features get a default so nothing is dropped."""
    for x in SKEL["features"]:
        if x["feature"] == f:
            return x
    guess = f.replace("-", " ")
    return {"feature": f, "entry": guess if guess in gram_names else None,
            "rank": 500, "core": True, "auto": True}

def used_features(phrases):
    return sorted({x for p in phrases for t in p["tokens"] for x in t["features"]})

def build_lessons(words, phrases):
    gram = {w.get("v"): w for w in words if w.get("speech") == "grammar"}
    lessons = {}
    def from_entry(lid, title, entry):
        paras = html_to_text(gram[entry].find("notes").text)
        lessons[lid] = {"id": lid, "title": title, "entry": entry, "generated": False,
                        "summary": summarize(paras), "body": paras,
                        "source": f"Eldamo grammar entry “{entry}” (CC BY 4.0, Paul Strack)"}
    for f in SKEL["foundations"]:
        from_entry(f["id"], f["entry"].capitalize(), f["entry"])
    for f in [feature_info(x, set(gram)) for x in used_features(phrases)]:
        lid = "feat-" + f["feature"]
        label = f.get("label") or f["feature"].replace("-", " ").capitalize()
        if f["entry"]:
            from_entry(lid, label, f["entry"])
        else:  # no Eldamo entry: describe from the data itself
            t, p = next((t, p) for p in phrases for t in p["tokens"] if f["feature"] in t["features"])
            summ = (f"Eldamo has no grammar entry for “{f['feature']}”. In the text it appears as "
                    f"“{t['text']}” ({t['gloss']}), from “{p['text']}” = “{p['gloss']}”.")
            lessons[lid] = {"id": lid, "title": label, "entry": None,
                            "generated": True, "summary": summ, "body": [summ],
                            "source": "Generated from Eldamo phrase data"}
    return lessons

# ------------------------------------------------------------- curriculum
def interleave(a, b):
    items = [((i + .5) / len(a), 0, x) for i, x in enumerate(a)] + \
            [((i + .5) / len(b), 1, x) for i, x in enumerate(b)]
    return [x for _, _, x in sorted(items, key=lambda t: (t[0], t[1]))]

def build_curriculum(phrases, gram_names):
    rank = {x: feature_info(x, gram_names) for x in used_features(phrases)}
    core_n = SKEL["markirya_core_lines"]
    by_id = {p["id"]: p for p in phrases}
    blocks = [("elen-sila", ["elen-sila"], True), ("aiya-earendil", ["aiya-earendil"], True),
              ("markirya-12", [f"markirya-{i:02d}" for i in range(1, core_n + 1)], True),
              ("markirya-full", [f"markirya-{i:02d}" for i in range(core_n + 1, 38)], False)]
    sessions = []
    def add_session(slots, milestone=None, stretch=False):
        sessions.append({"n": len(sessions) + 1, "slots": slots, "milestone": milestone, "stretch": stretch})
    for f in SKEL["foundations"]:
        add_session([dict(kind="lesson", lesson=f["id"], title=f["entry"].capitalize())])
    seen_f, seen_l = set(), set()
    for mid, pids, core in blocks:
        toks = [t for pid in pids for t in by_id[pid]["tokens"]]
        feats = sorted({x for t in toks for x in t["features"]} - seen_f, key=lambda x: rank[x]["rank"])
        lemmas = []
        for t in toks:
            if t["lemma"] and t["lemma"] not in seen_l and t["lemma"] not in lemmas:
                lemmas.append(t["lemma"])
        seen_f |= set(feats); seen_l |= set(lemmas)
        gram = []
        for x in feats:
            label = rank[x].get("label") or x.replace("-", " ").capitalize()
            gram.append(dict(kind="lesson", lesson="feat-" + x, feature=x, title=label))
            reps = SKEL["sessions_per_core_feature"] if rank[x]["core"] else SKEL["sessions_per_minor_feature"]
            for r in range(1, reps):
                gram.append(dict(kind="practice", feature=x, title=label + " practice"))
        w = SKEL["words_per_vocab_session"]
        vocab = [dict(kind="vocab", lemmas=lemmas[i:i + w], title=f"Words {i // w + 1}")
                 for i in range(0, len(lemmas), w)]
        content = interleave(gram, vocab) if gram and vocab else gram + vocab

        # Reading is a slot, attached onto the tail of this block's content sessions, not a
        # dedicated session of its own — the Reader tab is where reading actually lives now.
        step = SKEL["reading_lines_per_session"] if mid == "markirya-full" else len(pids)
        reading_chunks = []
        for i in range(0, len(pids), step):
            chunk = pids[i:i + step]
            last = i + step >= len(pids)
            reading_chunks.append({"slot": dict(kind="reading", phrases=chunk, title="Reading: " + mid),
                                    "milestone": mid if last else None, "stretch": not core})

        # Defensive fallback (not expected to fire with current content): if a block ever has more
        # reading chunks than content sessions to carry them, the overflow becomes its own session
        # rather than being silently dropped.
        attach, overflow = reading_chunks[:len(content)], reading_chunks[len(content):]
        attach_from = len(content) - len(attach)
        for idx, c in enumerate(content):
            slots, milestone, stretch = [c], None, False
            if idx >= attach_from:
                rc = attach[idx - attach_from]
                slots.append(rc["slot"]); milestone, stretch = rc["milestone"], rc["stretch"]
            add_session(slots, milestone=milestone, stretch=stretch)
        for rc in overflow:
            add_session([rc["slot"]], milestone=rc["milestone"], stretch=rc["stretch"])
    return sessions

# ------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("--out", default=str(ROOT / "composeApp/src/commonMain/composeResources/files"))
    a = ap.parse_args()
    version, words = load(a.xml)
    lex = build_lexicon(words)
    forms = build_forms(words, lex)
    phrases, tstats = build_phrases(words, lex, forms)
    lessons = build_lessons(words, phrases)
    sessions = build_curriculum(phrases, {w.get("v") for w in words if w.get("speech") == "grammar"})
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    def dump(name, obj):
        (out / name).write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    dump("lexicon.json", list(lex.values()))
    dump("forms.json", forms)
    dump("phrases.json", phrases)
    dump("lessons.json", list(lessons.values()))
    dump("curriculum.json", sessions)
    dump("meta.json", {"eldamo_version": version, "language": LANG,
                       "attribution": "Data © 2008–2026 Paul Strack, Eldamo (https://eldamo.org), CC BY 4.0.",
                       "glosses": ["en"]})
    problems = check(lex, forms, phrases, lessons, sessions)
    report(version, lex, forms, phrases, tstats, sessions, problems)
    sys.exit(1 if problems else 0)

def check(lex, forms, phrases, lessons, sessions):
    P = []
    for p in phrases:
        for t in p["tokens"]:
            if t["lemma"] and t["lemma"] not in lex:
                P.append(f"{p['id']}: lemma {t['lemma']} not in lexicon")
    for f in forms:
        if f["lemma"] not in lex: P.append(f"form of unknown lemma {f['lemma']}")
    if [s["n"] for s in sessions] != list(range(1, len(sessions) + 1)): P.append("session numbers not contiguous")
    intro = Counter(l for s in sessions for slot in s["slots"] if slot["kind"] == "vocab" for l in slot["lemmas"])
    P += [f"lemma introduced twice: {l}" for l, c in intro.items() if c > 1]
    for s in sessions:
        for slot in s["slots"]:
            if slot["kind"] != "lesson": continue
            if slot["lesson"] not in lessons: P.append(f"session {s['n']}: missing lesson")
            elif not lessons[slot["lesson"]]["summary"].strip(): P.append(f"empty lesson {slot['lesson']}")
            elif lessons[slot["lesson"]]["summary"].rstrip().endswith(":"):
                P.append(f"summary ends on a colon: {slot['lesson']}")
    # every resolved lemma used by a reading must be introduced no later than that reading
    pos = {}
    for s in sessions:
        for slot in s["slots"]:
            if slot["kind"] == "vocab":
                for l in slot["lemmas"]: pos[l] = s["n"]
    by_id = {p["id"]: p for p in phrases}
    for s in sessions:
        for slot in s["slots"]:
            if slot["kind"] != "reading": continue
            for pid in slot["phrases"]:
                for t in by_id[pid]["tokens"]:
                    if t["lemma"] and pos.get(t["lemma"], 10**9) > s["n"]:
                        P.append(f"session {s['n']}: reads {t['lemma']} before it is taught")
    return P

def report(version, lex, forms, phrases, tstats, sessions, problems):
    print(f"Eldamo {version}: {len(lex)} lexicon entries, {len(forms)} attested forms, {len(phrases)} phrases")
    print("token resolution:", dict(tstats))
    unres = [(p["id"], t["text"]) for p in phrases for t in p["tokens"] if not t["lemma"]]
    print("unresolved tokens:", unres or "none")
    amb = [(p["id"], t["text"]) for p in phrases for t in p["tokens"] if t["resolution"].endswith("ambiguous")]
    print("ambiguous (heuristic) tokens:", len(amb), amb[:8])
    print(f"curriculum: {len(sessions)} sessions;",
          dict(Counter(slot['kind'] for s in sessions for slot in s['slots'])))
    for s in sessions:
        if s.get("milestone"):
            print(f"  milestone {s['milestone']} at session {s['n']}")
    print("PROBLEMS:", problems or "none")

if __name__ == "__main__":
    _check_summarize()
    main()
