#!/usr/bin/env python3
"""Generate Reader audio: synthesized (not recorded) pronunciation clips for every
phrase and word in the Reader, scoped to phrases.json content only (BACKLOG item 7).

Pipeline: Quenya spelling -> tools/quenya_phonetics.py -> eSpeak NG phoneme synthesis
-> AAC encoding -> composeApp/.../composeResources/files/audio/*.m4a + audio.json
manifest (lookup key -> filename), read by DataLoader like the other JSON files.

Usage: python tools/generate_audio.py [--out DIR]
Requires: espeak-ng, ffmpeg (both via `brew install` on macOS).
"""
import argparse, hashlib, json, re, shutil, subprocess, sys, tempfile, unicodedata
from pathlib import Path

from quenya_phonetics import to_phonemes

ROOT = Path(__file__).resolve().parent.parent
SUPERSCRIPTS = "¹²³⁴⁵⁶⁷⁸⁹⁰"


def skey(s):
    """Same strict key as build_data.py: case/punctuation-insensitive, k == c, accents kept."""
    s = unicodedata.normalize("NFC", s).lower().translate(str.maketrans("", "", SUPERSCRIPTS))
    s = re.sub(r"[’‘'`´\-?.,;:!\"“”()\[\]…]", "", s)
    return s.replace("k", "c")


def synth(text, out_m4a, bitrate="32k"):
    # AAC/.m4a, not Opus: Android's MediaPlayer decodes Opus fine (API 21+), but iOS's
    # AVAudioPlayer does not support raw/Ogg Opus without a .caf repackage or a third-
    # party decoder. AAC is natively supported by both with no extra container step.
    phonemes = to_phonemes(text)
    with tempfile.NamedTemporaryFile(suffix=".wav") as wav:
        subprocess.run(["espeak-ng", "-v", "en", "-w", wav.name, phonemes],
                        check=True, capture_output=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav.name,
                         "-c:a", "aac", "-b:a", bitrate, str(out_m4a)], check=True)


def check_tools():
    for tool in ("espeak-ng", "ffmpeg"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} not found on PATH (brew install {tool})")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "composeApp/src/commonMain/composeResources/files"))
    a = ap.parse_args()
    check_tools()

    files_dir = Path(a.out)
    audio_dir = files_dir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)
    for f in audio_dir.glob("*.m4a"):
        f.unlink()  # manifest is regenerated wholesale; don't accumulate stale clips

    phrases = json.loads((files_dir / "phrases.json").read_text(encoding="utf-8"))

    words, lines, skipped = {}, {}, []
    for p in phrases:
        for t in p["tokens"]:
            k = skey(t["text"])
            if k and k not in words:
                words[k] = t["text"]
        lines[p["id"]] = p["text"]

    manifest = {"words": {}, "lines": {}}
    n_ok = 0
    for k, text in words.items():
        name = hashlib.sha1(k.encode()).hexdigest()[:10] + ".m4a"
        try:
            synth(text, audio_dir / name)
        except Exception as e:
            skipped.append((text, str(e)))
            continue
        manifest["words"][k] = name
        n_ok += 1
    for pid, text in lines.items():
        name = "l_" + hashlib.sha1(pid.encode()).hexdigest()[:10] + ".m4a"
        try:
            synth(text, audio_dir / name)
        except Exception as e:
            skipped.append((pid, str(e)))
            continue
        manifest["lines"][pid] = name
        n_ok += 1

    (files_dir / "audio.json").write_text(
        json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    total_bytes = sum(f.stat().st_size for f in audio_dir.glob("*.m4a"))
    print(f"{len(words)} unique words + {len(lines)} lines -> {n_ok} clips synthesized, "
          f"{len(skipped)} skipped")
    print(f"audio/ total size: {total_bytes / 1024:.1f} KiB")
    if skipped:
        print("skipped:", skipped)
        sys.exit(1)


if __name__ == "__main__":
    main()
