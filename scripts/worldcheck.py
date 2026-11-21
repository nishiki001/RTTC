#!/usr/bin/env python3
"""Summarise generated terrain from a world's region files (no Minecraft needed).

Usage: scripts/worldcheck.py <world-dir> [--sea-level 63]

Reads every fully generated chunk in <world-dir>/region/*.mca and prints:
surface-height percentiles, ocean share, biome shares, structure starts, counts of blocks that must not appear in a Classic world
(deepslate, tuff, granite/diorite/andesite), water/lava found UNDER solid ground (flooded caves), and holes in
the bottom floor. Stdlib only: a minimal NBT reader for the 1.18+ chunk format.
"""
from __future__ import annotations

import argparse
import collections
import io
import math
import struct
import sys
import zlib
from pathlib import Path

CHECK = ["minecraft:deepslate", "minecraft:tuff", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
         "minecraft:calcite", "minecraft:amethyst_block", "minecraft:glow_lichen", "minecraft:copper_ore",
         "minecraft:deepslate_copper_ore", "minecraft:iron_ore", "minecraft:diamond_ore", "minecraft:mossy_cobblestone",
         "minecraft:bee_nest", "minecraft:oak_log", "minecraft:birch_log"]
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
FLUIDS = {"minecraft:water", "minecraft:lava"}
TERRAIN_PARTS = ("stone", "dirt", "grass_block", "sand", "gravel", "_ore", "clay", "mycelium", "podzol", "bedrock",
                 "snow_block", "ice", "obsidian")


def is_terrain(name: str) -> bool:
    """Natural ground only: trees, plants and snow layers don't count as surface or overhang."""
    return any(p in name for p in TERRAIN_PARTS)


SOFT = AIRS | FLUIDS | {"minecraft:snow", "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
                         "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant"}


# ---- minimal NBT ----
def _read(fmt: str, f):
    return struct.unpack(">" + fmt, f.read(struct.calcsize(fmt)))[0]


def _payload(t: int, f):
    if t == 1: return _read("b", f)
    if t == 2: return _read("h", f)
    if t == 3: return _read("i", f)
    if t == 4: return _read("q", f)
    if t == 5: return _read("f", f)
    if t == 6: return _read("d", f)
    if t == 7: return f.read(_read("i", f))
    if t == 8: return f.read(_read("H", f)).decode("utf-8", "replace")
    if t == 9:
        et, n = _read("b", f), _read("i", f)
        return [_payload(et, f) for _ in range(n)]
    if t == 10:
        out = {}
        while (ct := _read("b", f)) != 0:
            name = f.read(_read("H", f)).decode("utf-8", "replace")
            out[name] = _payload(ct, f)
        return out
    if t == 11:
        n = _read("i", f)
        return list(struct.unpack(f">{n}i", f.read(4 * n)))
    if t == 12:
        n = _read("i", f)
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(f"bad NBT tag {t}")


def read_nbt(data: bytes) -> dict:
    f = io.BytesIO(data)
    assert _read("b", f) == 10
    f.read(_read("H", f))
    return _payload(10, f)


def chunks(region: Path):
    raw = region.read_bytes()
    for i in range(1024):
        off = int.from_bytes(raw[i * 4:i * 4 + 3], "big") * 4096
        if off == 0:
            continue
        length = int.from_bytes(raw[off:off + 4], "big")
        comp = raw[off + 4]
        body = raw[off + 5:off + 4 + length]
        if comp == 2:
            yield read_nbt(zlib.decompress(body))
        elif comp == 1:
            import gzip
            yield read_nbt(gzip.decompress(body))


def unpack(palette: list, data: list | None, count: int, min_bits: int) -> list:
    if len(palette) == 1 or not data:
        return [palette[0]] * count
    bits = max(min_bits, math.ceil(math.log2(len(palette))))
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for long in data:
        long &= (1 << 64) - 1
        for k in range(per):
            out.append(palette[(long >> (k * bits)) & mask])
            if len(out) == count:
                return out
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("world")
    ap.add_argument("--sea-level", type=int, default=63)
    a = ap.parse_args()
    regions = sorted(Path(a.world, "region").glob("*.mca"))
    if not regions:
        print("no region files", file=sys.stderr)
        return 1

    counts = collections.Counter()
    biomes = collections.Counter()
    structures = collections.Counter()
    heights = []
    ocean_cols = flooded = lava_high = floor_holes = n_chunks = 0
    cliff_pairs = pairs = overhang_cols = 0
    agree = disagree = 0
    for region in regions:
        for ch in chunks(region):
            if ch.get("Status") != "minecraft:full":
                continue
            n_chunks += 1
            for sid, start in ch.get("structures", {}).get("starts", {}).items():
                if start.get("id", "INVALID") != "INVALID":
                    structures[sid] += 1
            cols: dict[tuple[int, int], dict[int, str]] = collections.defaultdict(dict)
            sec_biomes: dict[int, list] = {}
            for sec in ch.get("sections", []):
                bs = sec.get("block_states")
                if not bs:
                    continue
                sy = sec["Y"]
                names = [p["Name"] for p in bs["palette"]]
                blocks = unpack(names, bs.get("data"), 4096, 4)
                for idx, name in enumerate(blocks):
                    y = sy * 16 + (idx >> 8)
                    if name in CHECK:
                        counts[name] += 1
                    if name not in AIRS:
                        cols[(idx & 15, (idx >> 4) & 15)][y] = name
                bi = sec.get("biomes")
                if bi:
                    sb = unpack(bi["palette"], bi.get("data"), 64, 1)
                    sec_biomes[sy] = sb
                    for b in sb:
                        biomes[b] += 64
            tops = {}
            for (cx, cz), col in cols.items():
                solid = [y for y, nm in col.items() if is_terrain(nm)]
                if not solid:
                    continue
                top = max(solid)
                tops[(cx, cz)] = top
                # overhang: solid block above an air gap of >= 2, all above y 70 (few caves up there)
                ys = sorted(y for y in solid if y >= 70)
                if any(b - a >= 3 for a, b in zip(ys, ys[1:])):
                    overhang_cols += 1
                heights.append(top)
                is_ocean = col.get(a.sea_level - 1) == "minecraft:water" and top < a.sea_level
                ocean_cols += is_ocean
                sb = sec_biomes.get((a.sea_level - 4) >> 4)
                if sb:
                    bname = sb[(((a.sea_level - 4) & 15) >> 2) * 16 + (cz >> 2) * 4 + (cx >> 2)]
                    # rivers and beaches sit at sea level either way, so they are not counted
                    if "ocean" in bname:
                        agree += is_ocean
                        disagree += not is_ocean
                    elif "river" not in bname and "beach" not in bname:
                        agree += not is_ocean
                        disagree += is_ocean
                if not is_ocean:  # land columns only: seafloor caves are meant to hold sea water
                    for y, nm in col.items():
                        if nm in FLUIDS and y < top - 4:
                            if nm == "minecraft:water":
                                flooded += 1
                            elif y > -50:
                                lava_high += 1
                for y in range(-64, -58):
                    if col.get(y) is None:  # air; carver lava at the bottom is fine
                        floor_holes += 1
            for (cx, cz), t in tops.items():
                for nx, nz in ((cx + 1, cz), (cx, cz + 1)):
                    if (nx, nz) in tops:
                        pairs += 1
                        cliff_pairs += abs(tops[(nx, nz)] - t) >= 5

    heights.sort()
    pct = lambda p: heights[min(len(heights) - 1, int(p / 100 * len(heights)))]
    print(f"chunks: {n_chunks}  columns: {len(heights)}")
    print(f"surface y  p1 {pct(1)}  p10 {pct(10)}  p50 {pct(50)}  p90 {pct(90)}  p99 {pct(99)}  max {heights[-1]}")
    print(f"ocean columns: {100 * ocean_cols / max(1, len(heights)):.1f}%")
    print(f"under land: water {flooded} (flooded caves; springs/lakes give a few per chunk), lava above y-50 {lava_high}")
    print(f"floor air holes (y -64..-59): {floor_holes}")
    print(f"cliffs (neighbour step >= 5): {100 * cliff_pairs / max(1, pairs):.2f}%   "
          f"overhang columns (gap above y70): {100 * overhang_cols / max(1, len(heights)):.2f}%")
    print(f"biome/terrain agreement (ocean biome = water, land biome = land): "
          f"{100 * agree / max(1, agree + disagree):.1f}%")
    print("blocks:", ", ".join(f"{k.split(':')[1]}={counts[k]}" for k in CHECK))
    total = sum(biomes.values()) or 1
    print("structure starts:", ", ".join(f"{k}={v}" for k, v in structures.most_common()) or "none")
    print("biomes:", ", ".join(f"{b}={100 * v / total:.1f}%" for b, v in biomes.most_common()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
