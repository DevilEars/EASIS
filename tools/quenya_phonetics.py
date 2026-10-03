"""Quenya spelling -> eSpeak NG phoneme string, including stress placement.

Hand-derived from Tolkien's Appendix E (LotR) conventions — Eldamo's own phonetic-rule/
phoneme/phonetic-group data turned out to be historical sound-change apparatus (how Common
Eldarin became Quenya/Sindarin/etc.), not a synchronic "how is this spelled word pronounced"
table, and its one Quenya `phonetics` essay entry is an empty stub. See BACKLOG.md item 7.

Orthography rules encoded here:
  - c is always /k/; qu is /kw/.
  - ty, hy, ny, ly, ry are single palatalized consonants (approximated as consonant+j).
  - Acute accent (á é í ó ú) marks a LONG vowel, not a diphthong.
  - Diaeresis (ë ä ï ö ü) marks a vowel pronounced as its own syllable — plain short vowel,
    but never merges with a neighbouring vowel into a diphthong.
  - The six legal diphthongs are ai, oi, ui, au, eu, iu (second element always i or u).
  - Stress: never on the final syllable. Two syllables -> the first. Three or more -> the
    penultimate if it is "heavy" (long vowel, diphthong, or closed by >=2 consonants),
    otherwise the antepenultimate.
  - A trailing elision mark (’ or ') is dropped; the preceding spelling already reflects it.

eSpeak NG's `[[...]]` phoneme-input mode is used as a pure phoneme-to-waveform engine — it
does not need to "know" Quenya, every rule here is supplied by us.
"""
import re
import unicodedata

LONG = "́"       # combining acute: á é í ó ú
SEPARATE = "̈"   # combining diaeresis: ë ä ï ö ü

DIGRAPHS = {
    # Appendix E's actual palatalized/labialized digraphs. "ry" is NOT one of these —
    # e.g. in cirya the r and y are separate consonants (cir-ya), not a palatal r.
    "qu": "kw", "ty": "tj", "hy": "hj", "ny": "nj", "ly": "lj", "hw": "hw",
}
CONSONANTS = {
    "p": "p", "t": "t", "k": "k", "c": "k", "b": "b", "d": "d", "g": "g",
    "f": "f", "v": "v", "s": "s", "h": "h", "m": "m", "n": "n", "l": "l",
    "r": "r", "w": "w", "y": "j",
}
VOWELS = {"a": "a", "e": "e", "i": "i", "o": "o", "u": "u"}
DIPHTHONGS = {"ai", "oi", "ui", "au", "eu", "iu"}


def _segments(word):
    """[(kind, phoneme, heavy)] for 'V' (vowel nucleus) and [(kind, phoneme)] for 'C'."""
    s = unicodedata.normalize("NFD", word.lower())
    s = re.sub(r"[’'´`\-?.,;:!\"‘’“”()\[\]…]", "", s)
    out, i, n = [], 0, len(s)
    while i < n:
        ch = s[i]
        if ch in VOWELS:
            mark = s[i + 1] if i + 1 < n and s[i + 1] in (LONG, SEPARATE) else ""
            base = VOWELS[ch]
            consumed = 2 if mark else 1
            if mark == SEPARATE:
                out.append(("V", base, False))
                i += consumed
                continue
            # not diaeresis-marked: try to extend into a diphthong with the next vowel
            j = i + consumed
            nxt = s[j] if j < n else ""
            nxt_mark = s[j + 1] if j + 1 < n and s[j + 1] in (LONG, SEPARATE) else ""
            if not mark and nxt in VOWELS and not nxt_mark and (ch + nxt) in DIPHTHONGS:
                out.append(("V", base + VOWELS[nxt], True))
                i = j + 1
                continue
            out.append(("V", base + (":" if mark == LONG else ""), bool(mark)))
            i += consumed
        elif s[i:i + 2] in DIGRAPHS:
            out.append(("C", DIGRAPHS[s[i:i + 2]]))
            i += 2
        elif ch == "x":          # x = /ks/ (Appendix E): two consonant phonemes, one letter
            out.append(("C", "k"))
            out.append(("C", "s"))
            i += 1
        elif ch in CONSONANTS:
            out.append(("C", CONSONANTS[ch]))
            i += 1
        elif ch.isalpha():
            raise ValueError(f"unrecognised letter {ch!r} in {word!r}")
        else:
            i += 1  # stray mark (zero-width joiner etc.): skip
    return out


def _stress_index(segs):
    """Index into `segs` of the vowel segment that should carry primary stress."""
    nuclei = [i for i, seg in enumerate(segs) if seg[0] == "V"]
    if len(nuclei) <= 1:
        return nuclei[0] if nuclei else None
    if len(nuclei) == 2:
        return nuclei[0]

    def closing_consonants(nucleus_pos):
        """How many consonant segments sit between this nucleus and the next."""
        j, count = nucleus_pos + 1, 0
        while j < len(segs) and segs[j][0] == "C":
            count += 1
            j += 1
        return count

    penult = nuclei[-2]
    heavy = segs[penult][2] or closing_consonants(penult) >= 2
    return penult if heavy else nuclei[-3]


def to_phonemes(word):
    """Quenya spelling -> eSpeak NG `[[...]]` phoneme string with stress marked."""
    segs = _segments(word)
    stress = _stress_index(segs)
    out = []
    for i, seg in enumerate(segs):
        if i == stress:
            out.append("'" + seg[1])
        else:
            out.append(seg[1])
    return "[[" + "".join(out) + "]]"


if __name__ == "__main__":
    import sys
    for w in sys.argv[1:] or ["Eärendil", "andúnë", "cirya", "elen", "aiya", "ancalima"]:
        print(f"{w:16} {to_phonemes(w)}")
