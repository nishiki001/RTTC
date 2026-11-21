#!/usr/bin/env python3
"""Procedural, original Alpha-spirit foliage textures for RTTC (see docs/art-bible.md).

Writes greyscale 16x16 textures (the game tints them per biome) into
src/main/resources/assets/minecraft/textures/block/: grass_block_top and grass_block_side_overlay.
Leaves use Programmer Art's own textures (Anton, 2026-11-21); the leaf generator below is kept but not written.

Nothing is copied from Mojang. The only vanilla input is the *shape* of the green fringe baked into
vanilla's grass_block_side (read from the game jar at run time, never stored): our overlay must cover
every one of those pixels, or untinted modern green would show through.

Style: flat per-pixel noise on a small palette (5-6 greys), bright overall so the biome tint has headroom
(texture x tint x light), a light top-left bias, no outlines. Leaves get clustered holes (cutout).
Deterministic: same seed, same output. Usage: scripts/art/alpha_foliage.py [--out DIR] [--client-jar JAR]
"""
from __future__ import annotations

import argparse
import io
import random
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/minecraft/textures/block"
JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.267-client-extra-aka-minecraft-resources.jar"
N = 16


def value_noise(rng: random.Random, cell: int) -> list[list[float]]:
    """Tileable blocky value noise in 0..1 (cell = block size in pixels)."""
    g = N // cell
    grid = [[rng.random() for _ in range(g)] for _ in range(g)]
    return [[grid[(y // cell) % g][(x // cell) % g] for x in range(N)] for y in range(N)]


def quantise(v: float, palette: list[int]) -> int:
    i = min(len(palette) - 1, max(0, int(v * len(palette))))
    return palette[i]


def shade_field(seed: int, palette: list[int], fine: float, coarse: float, light: float) -> list[list[int]]:
    """Per-pixel noise + 2x2 clumps + a faint top-left light, quantised to the palette."""
    rng = random.Random(seed)
    clumps = value_noise(rng, 2)
    out = []
    for y in range(N):
        row = []
        for x in range(N):
            v = (1 - fine - coarse) * 0.5 + fine * rng.random() + coarse * clumps[y][x]
            v += light * (1 - (x + y) / (2 * (N - 1)) - 0.5)  # top-left a touch brighter
            row.append(quantise(max(0.0, min(0.999, v)), palette))
        out.append(row)
    return out


def holes(seed: int, fraction: float) -> set[tuple[int, int]]:
    """Clustered, tileable hole mask covering about `fraction` of the pixels."""
    rng = random.Random(seed * 7919)
    target = int(fraction * N * N)
    cut: set[tuple[int, int]] = set()
    while len(cut) < target:
        x, y = rng.randrange(N), rng.randrange(N)
        for _ in range(rng.choice((1, 1, 2, 3))):  # small clusters, wrap around edges
            cut.add((x % N, y % N))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1))
    return cut


def image(field, cut=frozenset(), alpha_mask=None) -> Image.Image:
    im = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            if (x, y) in cut or (alpha_mask is not None and not alpha_mask[y][x]):
                im.putpixel((x, y), (0, 0, 0, 0))
            else:
                g = field[y][x]
                im.putpixel((x, y), (g, g, g, 255))
    return im


def vanilla_fringe_mask(jar: Path) -> list[list[bool]]:
    """Pixels of vanilla grass_block_side that are green (the baked fringe our overlay must cover)."""
    with zipfile.ZipFile(jar) as z:
        side = Image.open(io.BytesIO(z.read("assets/minecraft/textures/block/grass_block_side.png"))).convert("RGB")
    return [[(lambda p: p[1] > p[0] + 8 and p[1] > p[2] + 8)(side.getpixel((x, y))) for x in range(N)] for y in range(N)]


def grass_side_overlay(seed: int, palette: list[int], must_cover: list[list[bool]]) -> Image.Image:
    rng = random.Random(seed)
    field = shade_field(seed, palette, fine=0.55, coarse=0.25, light=0.10)
    # ragged fringe: 3-5 px deep per column, never shallower than the vanilla fringe in that column
    mask = [[False] * N for _ in range(N)]
    for x in range(N):
        depth = rng.choice((3, 3, 4, 4, 5))
        if rng.random() < 0.25:
            depth += 1  # occasional drip
        deepest = max((y for y in range(N) if must_cover[y][x]), default=-1)
        for y in range(max(depth, deepest + 1)):
            mask[y][x] = True
    for y in range(N):
        for x in range(N):
            mask[y][x] = mask[y][x] or must_cover[y][x]
    return image(field, alpha_mask=mask)


def build(out: Path, jar: Path) -> dict[str, Image.Image]:
    # Palettes: bright greys; leaves a little darker than grass so canopies read against the ground.
    grass = [170, 186, 198, 210, 224, 238]
    # Leaves sit at 0.8 side light and show darker inner leaves through the holes, so their brightest third has
    # to be near-white for the reference green to be reachable (calibrated against docs/reference/alpha.png).
    leaf = [140, 180, 210, 235, 250, 255]
    spruce = [96, 124, 150, 172, 194]
    grass_only = {"grass_block_top", "grass_block_side_overlay"}
    tex = {
        "grass_block_top": image(shade_field(101, grass, fine=0.60, coarse=0.30, light=0.10)),
        "grass_block_side_overlay": grass_side_overlay(102, grass, vanilla_fringe_mask(jar)),
        "oak_leaves": image(shade_field(201, leaf, fine=0.70, coarse=0.25, light=0.12), holes(201, 0.46)),
        "birch_leaves": image(shade_field(202, leaf, fine=0.70, coarse=0.20, light=0.12), holes(202, 0.44)),
        "spruce_leaves": image(shade_field(203, spruce, fine=0.75, coarse=0.20, light=0.10), holes(203, 0.40)),
        "jungle_leaves": image(shade_field(204, leaf, fine=0.65, coarse=0.30, light=0.12), holes(204, 0.36)),
    }
    tex = {k: v for k, v in tex.items() if k in grass_only}
    out.mkdir(parents=True, exist_ok=True)
    for name, im in tex.items():
        im.save(out / f"{name}.png", optimize=True)
    return tex


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=OUT)
    ap.add_argument("--client-jar", type=Path, default=JAR)
    a = ap.parse_args()
    for name, im in build(a.out, a.client_jar).items():
        px = [p for p in im.getdata() if p[3]]
        print(f"{name:26} opaque={len(px):3}/256 greys={len({p[0] for p in px})} "
              f"mean={sum(p[0] for p in px) / len(px):.0f}")


if __name__ == "__main__":
    main()
