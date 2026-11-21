# Decisions (one line each, newest last)

- 2026-11-21: Minecraft 1.21.1 + NeoForge 21.1.x + ModDevGradle. Frozen forever: new content only, no ports.
- 2026-11-21: Programmer Art is FORCED (not just default). One art style only.
- 2026-11-21: Keep modern combat, hunger, villages, ocean monuments.
- 2026-11-21: Custom "Classic" world preset with Beta-style terrain and modern biomes.
- 2026-11-21: Stages: stone age → steam → electric. Rails = main logistics; visible-item pipes locally.
  Energy: mechanical/steam → electric via generators, no RF/FE soup.
- 2026-11-21: mod_id `rttc`, name "RTTC" (never expanded), package `io.github.nishiki001.rttc`.
- 2026-11-21: Gemini (Antigravity CLI) does bulk, well-specified work; Claude does architecture and review.
- 2026-11-21: Gemini runs via `agy -p --mode accept-edits` (edits auto-approved, shell auto-denied), default model
  `gemini-4-neon-high`. Claude runs gradle/validation afterwards.
- 2026-11-21: License LGPL-3.0-only (addons/forks allowed; modified RTTC must stay open).
- 2026-11-21: Lime grass/foliage/vine colours only in the Classic preset, modern tints elsewhere.
- 2026-11-21: Programmer Art is forced via `AddPackFindersEvent` (no mixin): a `BuiltInPackSource` re-emits
  vanilla's `programmer_art` under the same id with `PackSelectionConfig(required, BOTTOM, fixed)`, replacing
  the vanilla entry (last source wins per id). It sits right above vanilla, so mods and user packs can still override it.
- 2026-11-21: Two-tier Gemini path `scripts/gemini-team.py`, built ourselves. agy 1.5.13 does have built-ins
  (Orchestrator (Neon) model, `/swarm` preview, `run_workflow` tool), but none enforce per-worker file
  ownership or give per-subtask headless results. Flow: Neon plans in plan mode via `--json-schema`
  (disjoint file ownership, validated), then up to 4 Hydrogen (`gemini-4-hydrogen-medium`) workers in parallel,
  each in its own detached git worktree (only owned files are copied back; anything else is logged and dropped),
  then Neon reviews and fixes small issues inside the owned files and returns {status, fixed, unresolved}.
  Log: `.gemini-tasks/<name>.log`. gemini-delegate uses it only for more than ~5 independent files;
  `gemini-task.sh` stays the default. Smoke-tested with 3 JSON files (~15 s per worker), build passed, reverted.
- 2026-11-21: Every commit gemini-delegate makes (single-agent or two-tier path; the scripts never commit
  themselves) carries `Co-Authored-By: Gemini <176961590+gemini-code-assist[bot]@users.noreply.github.com>`
  next to the Claude trailer, so GitHub shows the Gemini avatar. That is Google's official Gemini Code Assist
  app (owner `google`); the `antigravity-agent[bot]` account was rejected because its owner can't be verified.
  The bot is not literally Antigravity, so README states the project is developed with Claude Code and Gemini.
- 2026-11-21: Correction to the Programmer Art entry: `PackSelectionConfig(required, BOTTOM, fixed)` alone put the
  pack BELOW vanilla (`Pack.Position.insert` places fixed BOTTOM packs under non-fixed ones, and vanilla is
  BOTTOM but not fixed). Fixed with our first mixin, `mixin/client/PackRepositoryMixin`: after `rebuildSelected`
  (used by reload/setSelected) and after `addPack`/`removePack` it moves programmer_art to directly after vanilla.
  The pack stays required and fixed (can't be moved or disabled, never saved to options). Order top→bottom: user
  packs, Mod Resources, Programmer Art, Default. Mixins load from `rttc.mixins.json` (client section).
- 2026-11-21: Classic world (M2) decided, replacing "modern biomes" and the open world-height question:
  - Biomes: only a 1.2.5-era set, as our own `rttc:` biomes: plains, forest, birch forest, taiga, swamp, desert,
    jungle (no bamboo), windswept hills, snowy plains, snowy taiga, mushroom fields, river, frozen river, beach,
    ocean, frozen ocean, deep ocean.
  - Lime grass, foliage and water colours live in these biomes' JSON, not code. Since the `rttc:` biomes only
    exist in the Classic preset, this also settles "lime only in Classic" with no client-side signal.
  - Height: vanilla −64..320, with Beta-style noise scaled to use it.
  - No deepslate (stone down to bedrock), no geodes, glow lichen, aquifers or noise caves; carver caves only.
  - Vanilla copper ore is kept and reused as RTTC's copper.
  - Structures kept: villages, ocean monuments, desert and jungle temples, witch huts, mineshafts, strongholds,
    dungeons, igloos. Excluded: trial chambers, ancient cities, pillager outposts, woodland mansions, trail ruins.
- 2026-11-21: Classic world gen keeps vanilla's `NoiseBasedChunkGenerator` and adds two hooks: a density function type
  `rttc:beta_terrain` and a biome source `rttc:classic`. Both read the same `continents`/`river` functions, so coasts
  and rivers match. World-gen data is hand-written JSON, not datagen, so it can be diffed against vanilla's JSON.
- 2026-11-21: Classic aquifers: `aquifers_enabled: false` would flood every carved cave below sea level, so the
  aquifer stays on with its floodedness, spread and lava noises pinned to constants: no underground water or lava
  pockets, dry caves. If that fails the spike, fall back to a small mixin on the fluid picker.
- 2026-11-21: Classic world also excludes ocean ruins, shipwrecks, ruined portals and buried treasure; underground
  blobs are dirt and gravel only. `rttc:` biomes join kept structures' `has_structure/*` tags and `is_overworld`
  directly, and never the `minecraft:is_*` group tags (those pull in the excluded structures).
- 2026-11-21: Classic tuning comes from measured noise spread, not guesswork. NormalNoise σ is about 0.29 for our
  climate noises and 0.31 for continents. Climate stretch is ×3.5, with temperature +0.3. Beta's dry-tundra branch
  is cut from t<0.5 to t<0.3; otherwise ~25% of land is snowy plains. Continents thresholds: deep −0.43, ocean
  −0.16, beach −0.11, hills 0.40 (≈8/30/5/10 %). `base_y` 72.6 puts the terrain's sea-level crossing on the ocean
  threshold. River width is 0.022 (≈5% of land).
- 2026-11-21: `scripts/worldcheck.py` reads region files to check a generated world: surface heights, ocean share,
  biome and structure shares, forbidden blocks, flooded caves, floor holes. Headless runs use a throwaway
  `run/classic-spike` world with a world-local forceload datapack, because gradle doesn't forward stdin to
  `runServer`.
- 2026-11-21: M2 Classic is code-complete. The headless check on a 1296-chunk world found only `rttc:` biomes, 0
  deepslate/tuff/granite/diorite/andesite/geode/glow lichen, dry caves, a village and mineshafts, and none of the
  excluded structures. Kept in sync by hand (nothing enforces it): `river_width` in the terrain `shape` and in the
  biome `thresholds`, and the `ocean`/`beach` thresholds against `base_y`/`height_scale` (coast at
  72.6 − 0.16·60 = 63). Biome spawners are vanilla's (modern mobs included) until Anton decides. The grass and
  foliage colours are starting values.
