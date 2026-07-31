#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
#
# Regenerates the tutorial's voice narration from the texts in shared/.../tutorial/TutorialLesson.kt.
# Run whenever a narrated tutorial text changes — NarrationManifestTest fails the build until the
# clips match the words again. Commit the resulting MP3s + manifest.txt.
#
# Pipeline: a shared unit test dumps {"id","text"} JSON Lines to build/narration-texts.jsonl;
# Qwen3-TTS (Apache-2.0, the same engine and Aiden voice 500 uses, so the two apps sound like one
# suite) synthesizes each line on a GPU; ffmpeg encodes mono MP3s; each text's SHA-256 lands in
# manifest.txt for the drift gate.
#
# WHY THIS RUNS OVER SSH, unlike 500's version: the model is 1.7B, so CPU synthesis is impractical
# and a CUDA GPU is required — but the GPU box has no Android SDK, and installing one just to dump
# a few dozen strings would be silly. So the texts are dumped HERE (where the toolchain already
# lives), rendered THERE, and the clips come back. The render box needs only Python, torch and
# ffmpeg.
#
# One-time setup on the render box:
#   python3 -m venv ~/.venvs/qwen-tts
#   ~/.venvs/qwen-tts/bin/pip install -U qwen-tts soundfile
#   ffmpeg on PATH
#
# Build the venv on a Python whose development headers are installed (`python3-devel` /
# `python3-dev`). torch routes some ops through Triton, which JIT-compiles a small CUDA shim against
# Python.h on first use — so a headerless interpreter fails at the first synthesis, deep in a torch
# traceback, long after the install looked fine. If you are tempted to reach for an older Python to
# get wheels, check first that the newer one actually lacks them; being wrong about that is how this
# comment came to be written.
#
# Re-rolling: generation samples, so a take can occasionally come out mangled with nothing wrong in
# the text. Pass clip ids to regenerate just those lines with a fresh roll:
#   scripts/generate-narration.sh basics-step-4 bidding-step-2
# Partial re-rolls require the texts to be unchanged (the manifest hash is checked); after any text
# edit, run the full regeneration instead.
set -euo pipefail
cd "$(dirname "$0")/.."

GPU_HOST="${GPU_HOST:-jack@192.168.0.18}"
REMOTE_PYTHON="${REMOTE_PYTHON:-\$HOME/.venvs/qwen-tts/bin/python}"
VOICE="${VOICE:-Aiden}"
INSTRUCT="${INSTRUCT:-Clear and concise, explaining a new concept.}"
OUT=shared/src/commonMain/composeResources/files/narration
TEXTS=shared/build/narration-texts.jsonl
MANIFEST="$OUT/manifest.txt"

command -v rsync >/dev/null || { echo "rsync not on PATH"; exit 1; }
ssh -o BatchMode=yes -o ConnectTimeout=15 "$GPU_HOST" "test -x $REMOTE_PYTHON" 2>/dev/null || {
  echo "no qwen-tts venv at $REMOTE_PYTHON on $GPU_HOST (see the setup in this script's header)"
  exit 1
}

echo "Dumping narration texts…"
./gradlew -q :shared:testDebugUnitTest --tests '*NarrationManifestTest.dump*' >/dev/null

# Re-roll mode: keep only the requested ids, and require their manifest hashes to be current — a
# partial regen over changed text would leave the manifest describing words no clip says.
SEND="$TEXTS"
if [ "$#" -gt 0 ]; then
  [ -f "$MANIFEST" ] || { echo "no manifest — run a full generation first"; exit 1; }
  SEND=$(mktemp)
  trap 'rm -f "$SEND"' EXIT
  MANIFEST="$MANIFEST" TEXTS="$TEXTS" SEND="$SEND" python3 - "$@" <<'PYEOF'
import hashlib, json, os, sys
wanted = sys.argv[1:]
rows = {json.loads(l)["id"]: json.loads(l) for l in open(os.environ["TEXTS"], encoding="utf-8") if l.strip()}
recorded = {}
for line in open(os.environ["MANIFEST"], encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    clip_id, digest = line.split(" ", 1)
    recorded[clip_id] = digest.strip()
with open(os.environ["SEND"], "w", encoding="utf-8") as out:
    for clip_id in wanted:
        row = rows.get(clip_id) or sys.exit(f"unknown clip id: {clip_id}")
        want = hashlib.sha256(row["text"].encode()).hexdigest()
        if recorded.get(clip_id) != want:
            sys.exit(f"text for {clip_id} changed since the last full run — regenerate everything")
        out.write(json.dumps(row) + "\n")
PYEOF
  echo "Re-rolling: $*"
fi

REMOTE_DIR=$(ssh -o BatchMode=yes "$GPU_HOST" 'mktemp -d')
# shellcheck disable=SC2064  # expand REMOTE_DIR now, not at trap time
trap "ssh -o BatchMode=yes '$GPU_HOST' 'rm -rf $REMOTE_DIR' >/dev/null 2>&1 || true" EXIT

rsync -q "$SEND" "$GPU_HOST:$REMOTE_DIR/texts.jsonl"
rsync -q scripts/narration_render.py "$GPU_HOST:$REMOTE_DIR/render.py"

# Run from the work dir, not the login dir: a stray ~/foo.py that shadows a stdlib module the
# transformers import chain pulls in (email, json, …) breaks the render with a traceback that looks
# like a broken install. Cheap insurance on a box that isn't ours to keep tidy.
ssh -o BatchMode=yes "$GPU_HOST" \
  "cd $REMOTE_DIR && VOICE=$(printf %q "$VOICE") INSTRUCT=$(printf %q "$INSTRUCT") \
   $REMOTE_PYTHON $REMOTE_DIR/render.py $REMOTE_DIR/texts.jsonl $REMOTE_DIR/out"

mkdir -p "$OUT"
[ "$#" -eq 0 ] && rm -f "$OUT"/*.mp3   # full run: clips and manifest are rebuilt together
rsync -q "$GPU_HOST:$REMOTE_DIR/out/"*.mp3 "$OUT/"

# The manifest is written here, from the same texts the app compiles, so the hashes cannot drift
# from what NarrationManifestTest checks even if something went wrong in transit.
if [ "$#" -eq 0 ]; then
  TEXTS="$TEXTS" OUT="$OUT" VOICE="$VOICE" INSTRUCT="$INSTRUCT" python3 - <<'PYEOF'
import hashlib, json, os
rows = [json.loads(l) for l in open(os.environ["TEXTS"], encoding="utf-8") if l.strip()]
with open(os.path.join(os.environ["OUT"], "manifest.txt"), "w", encoding="utf-8") as out:
    out.write("# Generated by scripts/generate-narration.sh — do not edit.\n")
    out.write(f"# engine: qwen3-tts-1.7b-customvoice, voice: {os.environ['VOICE']}, "
              f"instruct: {os.environ['INSTRUCT']}\n")
    for row in rows:
        out.write(f"{row['id']} {hashlib.sha256(row['text'].encode()).hexdigest()}\n")
PYEOF
fi

echo "Generated $(ls "$OUT"/*.mp3 | wc -l) clips into $OUT ($(du -sh "$OUT" | cut -f1))."
