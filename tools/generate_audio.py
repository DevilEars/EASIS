#!/usr/bin/env python3
"""Generate Reader audio clips with local Piper, then AAC.

Pipeline: Quenya spelling -> tools/quenya_phonetics.py -> tools/piper_utterance.py
-> piper-tts -> AAC -> composeApp/.../composeResources/files/audio/*.m4a + audio.json.

Usage, from the shared virtual environment:
    source /Users/devilliers.neethling/code/persoonlik/Quenya/claudeslop/.venv-piper/bin/activate
    python tools/generate_audio.py --dry-utterance
    python tools/generate_audio.py [--out DIR]

Requires: that virtual environment (piper-tts installed once), ffmpeg.
Does not shell out to espeak-ng.
Voice: en_GB-cori-high unless QUENYA_PIPER_VOICE is set.
Pace: length scale 1.15 unless QUENYA_PIPER_LENGTH is set.
Voice files: claudeslop/.piper-voices unless PIPER_VOICES_DIR is set.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata
import wave
from pathlib import Path

from piper_utterance import line_utterance, word_utterance

ROOT = Path(__file__).resolve().parent.parent
# Worktrees are siblings under claudeslop/. The venv and the ONNX files live
# beside those trees, so a second worktree does not install or download again.
SHARED_ROOT = ROOT.parent
SHARED_VENV = SHARED_ROOT / ".venv-piper"
# Clip cache stays in this worktree. A shared cache would let one tree skip
# synthesis because the other tree had already recorded the filename.
CACHE_DIR = Path(__file__).resolve().parent / ".piper"
CACHE_PATH = CACHE_DIR / "cache.json"
DEFAULT_VOICE = "en_GB-cori-high"
DEFAULT_LENGTH = 1.15
SUPERSCRIPTS = "¹²³⁴⁵⁶⁷⁸⁹⁰"
LISTEN_WORDS = ("cirya", "Eärendil", "andúnë", "ancalima")
LISTEN_LINE = "aiya Eärendil elenion ancalima"
PROBE_WORDS = ("cirya", "Eärendil", "andúnë", "ancalima")
PROBE_EXTRA = " ,.?!"


def skey(s):
    """Same strict key as build_data.py: case/punctuation-insensitive, k == c, accents kept."""
    s = unicodedata.normalize("NFC", s).lower().translate(str.maketrans("", "", SUPERSCRIPTS))
    s = re.sub(r"[’‘'`´\-?.,;:!\"“”()\[\]…]", "", s)
    return s.replace("k", "c")


def listen_utterances():
    rows = [(word, word_utterance(word)) for word in LISTEN_WORDS]
    rows.append((LISTEN_LINE, line_utterance(LISTEN_LINE)))
    return rows


def print_listen_utterances():
    for label, utterance in listen_utterances():
        print(f"{label}  {utterance}")


def voice_name():
    return os.environ.get("QUENYA_PIPER_VOICE", DEFAULT_VOICE).strip() or DEFAULT_VOICE


def length_scale():
    raw = os.environ.get("QUENYA_PIPER_LENGTH", str(DEFAULT_LENGTH))
    try:
        scale = float(raw)
    except ValueError:
        sys.exit(f"QUENYA_PIPER_LENGTH must be a number, got {raw!r}")
    if scale <= 0:
        sys.exit(f"QUENYA_PIPER_LENGTH must be greater than 0, got {scale}")
    return scale


def voices_dir():
    raw = os.environ.get("PIPER_VOICES_DIR", "").strip()
    if raw:
        return Path(raw).expanduser()
    return SHARED_ROOT / ".piper-voices"


def require_venv():
    if Path(sys.prefix).resolve() != SHARED_VENV.resolve():
        activate = SHARED_VENV / "bin" / "activate"
        sys.exit(
            "Python must be the shared virtual environment.\n"
            f"  source {activate}"
        )


def require_ffmpeg():
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg not found on PATH (brew install ffmpeg)")


def require_piper():
    try:
        from piper import PiperVoice, SynthesisConfig
    except ImportError:
        activate = SHARED_VENV / "bin" / "activate"
        sys.exit(
            "piper-tts is not installed in the shared virtual environment.\n"
            f"  source {activate}\n"
            "  pip install piper-tts"
        )
    return PiperVoice, SynthesisConfig


def inventory(files_dir):
    phrases = json.loads((files_dir / "phrases.json").read_text(encoding="utf-8"))
    words, lines = {}, {}
    for phrase in phrases:
        for token in phrase["tokens"]:
            key = skey(token["text"])
            if key and key not in words:
                words[key] = token["text"]
        lines[phrase["id"]] = phrase["text"]
    return words, lines


def plan_clips(words, lines):
    """Return (clips, failures). A clip is filename, manifest bucket, key, label, utterance."""
    clips, failures = [], []
    for key, text in words.items():
        name = hashlib.sha1(key.encode()).hexdigest()[:10] + ".m4a"
        try:
            clips.append((name, "words", key, text, word_utterance(text)))
        except ValueError as exc:
            failures.append(f"{text}: {exc}")
    for pid, text in lines.items():
        name = "l_" + hashlib.sha1(pid.encode()).hexdigest()[:10] + ".m4a"
        try:
            clips.append((name, "lines", pid, text, line_utterance(text)))
        except ValueError as exc:
            failures.append(f"{text}: {exc}")
    return clips, failures


def ensure_voice(name):
    directory = voices_dir()
    model = directory / f"{name}.onnx"
    config = directory / f"{name}.onnx.json"
    if model.is_file() and model.stat().st_size > 0 and config.is_file() and config.stat().st_size > 0:
        return model
    directory.mkdir(parents=True, exist_ok=True)
    cmd = [sys.executable, "-m", "piper.download_voices", name, "--data-dir", str(directory)]
    result = subprocess.run(cmd)
    if result.returncode != 0 or not model.is_file() or not config.is_file():
        sys.exit("voice download failed. Run: " + " ".join(cmd))
    return model


def missing_characters(utterance, id_map):
    return [ch for ch in utterance[2:-2] if ch not in id_map]


def probe(voice, synthesis_config, id_map):
    for spelling in PROBE_WORDS:
        utterance = word_utterance(spelling)
        missing = missing_characters(utterance, id_map)
        if missing:
            chars = " ".join(repr(ch) for ch in missing)
            sys.exit(f"voice phoneme map is missing {chars} in {spelling}")
    for ch in PROBE_EXTRA:
        if ch not in id_map:
            sys.exit(f"voice phoneme map is missing {ch!r}")
    cirya = word_utterance("cirya")
    fd, wav_path = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    try:
        with wave.open(wav_path, "wb") as wav:
            voice.synthesize_wav(cirya, wav, syn_config=synthesis_config)
        subprocess.run(
            ["ffmpeg", "-v", "error", "-i", wav_path, "-f", "null", "-"],
            check=True,
        )
    finally:
        Path(wav_path).unlink(missing_ok=True)


def load_cache():
    if not CACHE_PATH.is_file():
        return {}
    return json.loads(CACHE_PATH.read_text(encoding="utf-8"))


def save_cache(cache):
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    tmp = CACHE_PATH.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, CACHE_PATH)


def cache_hit(cache, name, voice, scale, utterance, audio_dir):
    record = cache.get(name)
    if not record:
        return False
    return (
        record.get("voice") == voice
        and record.get("length_scale") == scale
        and record.get("utterance") == utterance
        and (audio_dir / name).is_file()
    )


def synthesize_clip(voice, synthesis_config, utterance, target):
    """Encode beside the library and replace the clip only after ffmpeg can read it."""
    fd, wav_path = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    partial = target.with_name(target.stem + ".partial.m4a")
    try:
        with wave.open(wav_path, "wb") as wav:
            voice.synthesize_wav(utterance, wav, syn_config=synthesis_config)
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
             "-c:a", "aac", "-b:a", "32k", str(partial)],
            check=True,
        )
        subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(partial), "-f", "null", "-"],
            check=True,
        )
        os.replace(partial, target)
    finally:
        Path(wav_path).unlink(missing_ok=True)
        partial.unlink(missing_ok=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "composeApp/src/commonMain/composeResources/files"))
    ap.add_argument("--dry-utterance", action="store_true",
                    help="print the listen-list utterances and exit")
    args = ap.parse_args()
    require_venv()
    if args.dry_utterance:
        print_listen_utterances()
        return

    require_ffmpeg()
    PiperVoice, SynthesisConfig = require_piper()
    scale = length_scale()
    name = voice_name()

    files_dir = Path(args.out)
    words, lines = inventory(files_dir)
    clips, failures = plan_clips(words, lines)
    if failures:
        print("phonetics rejected:", ", ".join(failures), file=sys.stderr)
        sys.exit(1)

    model = ensure_voice(name)
    voice = PiperVoice.load(model)
    synthesis_config = SynthesisConfig(length_scale=scale)
    id_map = voice.config.phoneme_id_map
    for _filename, _bucket, _key, label, utterance in clips:
        missing = missing_characters(utterance, id_map)
        if missing:
            chars = " ".join(repr(ch) for ch in dict.fromkeys(missing))
            sys.exit(f"voice phoneme map is missing {chars} in {label}")
    probe(voice, synthesis_config, id_map)

    audio_dir = files_dir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)
    cache = load_cache()
    manifest = {"words": {}, "lines": {}}
    for filename, bucket, key, label, utterance in clips:
        target = audio_dir / filename
        if not cache_hit(cache, filename, name, scale, utterance, audio_dir):
            try:
                synthesize_clip(voice, synthesis_config, utterance, target)
            except Exception as exc:
                print(f"failed {label}: {exc}", file=sys.stderr)
                sys.exit(1)
            cache[filename] = {"voice": name, "length_scale": scale, "utterance": utterance}
            save_cache(cache)
        manifest[bucket][key] = filename

    missing_files = [filename for filename, _bucket, _key, _label, _utt in clips
                     if not (audio_dir / filename).is_file()]
    if missing_files:
        print("clips missing, manifest not written:", ", ".join(missing_files), file=sys.stderr)
        sys.exit(1)

    (files_dir / "audio.json").write_text(
        json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    total_bytes = sum(f.stat().st_size for f in audio_dir.glob("*.m4a"))
    print(f"{len(words)} unique words + {len(lines)} lines -> {len(clips)} clips, "
          f"audio/ {total_bytes / 1024:.1f} KiB")
    print("listen:")
    by_label = {label: filename for filename, _bucket, _key, label, _utt in clips}
    for label, _utt in listen_utterances():
        filename = by_label.get(label)
        path = audio_dir / filename if filename else "(not in this library)"
        print(f"  {label}  {path}")


if __name__ == "__main__":
    main()
