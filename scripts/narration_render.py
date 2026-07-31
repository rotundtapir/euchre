#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
"""Synthesize tutorial narration clips. Runs on the GPU box; driven by generate-narration.sh.

Reads JSON Lines of {"id": ..., "text": ...} and writes one mono MP3 per line. Kept as a file
rather than a heredoc inside the shell script so it can be linted, and so a failure points at a
real line number.
"""
import json
import os
import subprocess
import sys
import tempfile

import soundfile as sf
import torch
from qwen_tts import Qwen3TTSModel

MODEL = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"


def main() -> int:
    texts_path, out_dir = sys.argv[1], sys.argv[2]
    voice = os.environ.get("VOICE", "Aiden")
    instruct = os.environ.get("INSTRUCT", "Clear and concise, explaining a new concept.")

    if not torch.cuda.is_available():
        raise SystemExit("no CUDA device — this script is meant to run on the GPU box")

    lines = [json.loads(raw) for raw in open(texts_path, encoding="utf-8") if raw.strip()]
    print(f"Synthesizing {len(lines)} clips with {MODEL} (voice: {voice}) on "
          f"{torch.cuda.get_device_name(0)}…", flush=True)

    # float16, not bfloat16: the render box is a 2080 Ti (Turing), which has no *native* bf16.
    # Don't be talked out of this by torch.cuda.is_bf16_supported() returning True here — recent
    # torch counts emulation, which on this card is slower than fp16 for no quality gain.
    model = Qwen3TTSModel.from_pretrained(MODEL, device_map="cuda:0", dtype=torch.float16)

    os.makedirs(out_dir, exist_ok=True)
    with tempfile.TemporaryDirectory() as wav_dir:
        for i, line in enumerate(lines, 1):
            clip_id, text = line["id"], line["text"]
            wavs, sr = model.generate_custom_voice(
                text=text, language="English", speaker=voice, instruct=instruct,
            )
            wav_path = os.path.join(wav_dir, f"{clip_id}.wav")
            sf.write(wav_path, wavs[0], sr)
            # Mono 48k MP3: these are speech clips bundled into an app, so size matters more than
            # fidelity, and the app mixes them to a single channel anyway.
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
                 "-ac", "1", "-b:a", "48k", os.path.join(out_dir, f"{clip_id}.mp3")],
                check=True,
            )
            print(f"  [{i}/{len(lines)}] {clip_id}: {len(wavs[0]) / sr:.1f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
