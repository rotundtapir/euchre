#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
"""Judge a filmed animation by failure signature rather than by appearance.

Reads the PNG sequence `TutorialFrameCapture` leaves on the device (pull it first) and reports
three mechanical faults. Each is a yes/no question about pixels, so the answer is the same on
every run of a pinned seed, and none of them asks anyone whether the motion "looks nice":

  DEAD      nothing moved across a stretch that should have been animating — the frames either
            side of the window are the same picture. This is what "the deal doesn't animate"
            looks like from the outside.
  BLINK     a region of the screen was there in one frame and gone in the next with no ramp:
            the frames around it are quiet, so the change was a cut, not a fade or a slide.
  TELEPORT  the thing that is moving jumped further between two frames than it did in the frames
            around it — content relocating rather than travelling.

Usage:  frame-signatures.py <frame-dir> [--quiet-frac 0.002] [--report-dir DIR]

Exit status is 1 when anything was flagged, so this can gate a build later if it earns it.
"""
from __future__ import annotations

import argparse
import pathlib
import sys

import numpy as np
from PIL import Image

# A pixel counts as changed when any channel moves by more than this (0-255). Well above the
# emulator's software-renderer dither, well below a card appearing.
PIXEL_DELTA = 24

# Fraction of the screen that must change for a frame pair to count as "something happened".
BUSY_FRAC = 0.004

# A cut is suspicious when the step is this many times busier than its neighbours.
CUT_RATIO = 6.0

# A jump is suspicious when the changed region's centre moves this many times its usual step.
JUMP_RATIO = 4.0


def load(dir_: pathlib.Path) -> tuple[list[str], np.ndarray]:
    paths = sorted(dir_.glob("frame_*.png"))
    if len(paths) < 3:
        sys.exit(f"need at least 3 frames in {dir_}, found {len(paths)}")
    # Greyscale at half size: enough to see a card move, a quarter of the memory.
    frames = np.stack([
        np.asarray(Image.open(p).convert("L").reduce(2), dtype=np.int16) for p in paths
    ])
    return [p.name for p in paths], frames


def changes(frames: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Per-step changed-pixel fraction, and the centre of what changed."""
    masks = np.abs(np.diff(frames, axis=0)) > PIXEL_DELTA
    fracs = masks.mean(axis=(1, 2))
    centres = np.full((len(masks), 2), np.nan)
    for i, mask in enumerate(masks):
        ys, xs = np.nonzero(mask)
        if len(ys):
            centres[i] = (ys.mean(), xs.mean())
    return fracs, centres, masks


def find_dead(names, fracs, quiet_frac) -> list[str]:
    """A run of frames where nothing at all changed."""
    quiet = fracs < quiet_frac
    findings, start = [], None
    for i, is_quiet in enumerate([*quiet, False]):
        if is_quiet and start is None:
            start = i
        elif not is_quiet and start is not None:
            if i - start >= 6:  # ~0.3s of stillness at a 50ms interval
                findings.append(
                    f"DEAD    {names[start]} → {names[i]}: {i - start} frames "
                    f"({(i - start) * 50}ms) with under {quiet_frac:.1%} of pixels changing"
                )
            start = None
    return findings


def find_cuts(names, fracs) -> list[str]:
    findings = []
    for i in range(1, len(fracs) - 1):
        neighbours = max(fracs[i - 1], fracs[i + 1], 1e-9)
        if fracs[i] > BUSY_FRAC and fracs[i] / neighbours > CUT_RATIO:
            findings.append(
                f"BLINK   {names[i]} → {names[i + 1]}: {fracs[i]:.1%} of the screen changed in "
                f"one step, against {fracs[i - 1]:.2%}/{fracs[i + 1]:.2%} either side — a cut, "
                f"not a fade"
            )
    return findings


def find_jumps(names, fracs, centres) -> list[str]:
    steps = np.linalg.norm(np.diff(centres, axis=0), axis=1)
    busy = fracs[:-1] > BUSY_FRAC
    typical = np.nanmedian(steps[busy]) if busy.any() else np.nan
    if not np.isfinite(typical) or typical <= 0:
        return []
    findings = []
    for i, step in enumerate(steps):
        if np.isfinite(step) and busy[i] and step > typical * JUMP_RATIO:
            findings.append(
                f"TELEPORT {names[i + 1]}: the moving region's centre shifted {step:.0f}px, "
                f"against a typical {typical:.0f}px — content relocated rather than travelled"
            )
    return findings


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("frames", type=pathlib.Path)
    ap.add_argument("--quiet-frac", type=float, default=0.002)
    ap.add_argument("--report-dir", type=pathlib.Path)
    args = ap.parse_args()

    names, frames = load(args.frames)
    fracs, centres, _ = changes(frames)
    findings = find_dead(names, fracs, args.quiet_frac) + find_cuts(names, fracs) + \
        find_jumps(names, fracs, centres)

    print(f"{len(names)} frames from {args.frames}")
    print("per-step change (% of pixels):")
    print("  " + " ".join(f"{f * 100:.1f}" for f in fracs))
    print()
    for f in findings:
        print(f)
    if not findings:
        print("no failure signatures")

    if args.report_dir:
        # Save the frames worth a human (or an agent) actually looking at.
        args.report_dir.mkdir(parents=True, exist_ok=True)
        flagged = {n.split()[1].rstrip(":") for n in findings if len(n.split()) > 1}
        for name in sorted(flagged):
            src = args.frames / name.split("→")[0].strip()
            if src.exists():
                Image.open(src).save(args.report_dir / src.name)
        print(f"\nflagged frames copied to {args.report_dir}")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
