# RTTC plan

## Next up (for local Claude Code)
1. Anton runs `./gradlew runClient` once and checks: "RTTC" is in the Mods list; Options → Resource Packs
   shows "Programmer Art" selected, directly above Default, and it can't be removed or moved; blocks use the
   old textures. (Build passes; this needs a human with a screen.)
2. **M2** (plan approved 2026-11-21, see "M2 plan" below): step 4, G2 biome tags (gemini-team), then the headless check and review. The M1 water
   colour still needs screenshots for Anton.

## Milestones
- [x] M0 Bootstrap: renamed template, CLAUDE.md, agents, docs
- [x] M0.5 Gemini wired up and smoke-tested (`agy`, lang-key smoke test passed and reverted)
- [x] M0.6 Two-tier Gemini path (`scripts/gemini-team.py`: Neon plans/reviews, parallel Hydrogen workers), smoke-tested and reverted
- [ ] **M1 The Look**
  - [x] Force vanilla's built-in `programmer_art` pack: always enabled, above vanilla, not removable
        in the pack screen. (`client/ForcedProgrammerArt` + `mixin/client/PackRepositoryMixin`; log confirms order
        `vanilla, programmer_art, mod_resources`; visual check of leaves/grass/pack screen still needs Anton.) Original task: Have `explorer` check how vanilla registers it (`BuiltInPackSource` /
        `ServerPacksSource`/`ClientPackSource`, `PackRepository`, `Options.resourcePacks`), then pick
        the cleanest hook (NeoForge `AddPackFindersEvent` with a required/fixed `PackSelectionConfig`
        vs. a small mixin). Record the choice in DECISIONS.
  - [→] Lime Alpha/Beta grass, foliage and vine colours, only in Classic worlds: **moved to M2**. The colours
        go in the `rttc:` biome JSONs, which only exist in Classic, so no client-side signal is needed.
  - [ ] Look at water colour (flat old blue?), and **ASK Anton** with screenshots.
- [ ] **M2 Classic world preset**: Beta-style terrain, 17 `rttc:` 1.2.5-era biomes, −64..320, no deepslate,
      carver caves only, curated structures. See "M2 plan" below. Steps: [x] 0 plan  [x] 1 Java  [x] 2 core data +
      spike  [x] 3 G1 biomes  [ ] 4 G2 tags  [ ] 5 review
- [ ] M3 Stone age (flint/stone tools, work stump, charcoal pit, bloomery). 30–60 min, light.
- [ ] M4 Steam: boilers, engines, shafts; first visible-item pipes
- [ ] M5 Rails and carts as main logistics; tunnel bore
- [ ] M6 Electric tier (voltage tiers, cable loss, overvoltage explosions, macerator), then the reactor puzzle
- [ ] Ongoing: programmer-art textures for post-1.14 vanilla blocks (list in art-bible.md, Gemini can compile it)
- [ ] Compat: JEI, Jade

## M2 plan (approved 2026-11-21)

### Context
M2 adds the "Classic" world type: Beta-style terrain on vanilla's −64..320 height, using only a 1.2.5-era set of 17
`rttc:` biomes, with lime colours set in biome JSON, no deepslate, carver caves only, and a curated set of structures.
The decisions are recorded in DECISIONS.md (2026-11-21). Answers from Anton during planning:
- Ocean ruins, shipwrecks, ruined portals and buried treasure are **excluded** too.
- Underground blobs are **dirt and gravel only**: no granite, diorite, andesite or tuff.

This also settles the M1 lime-grass item. The colours live in the `rttc:` biomes, which only exist in Classic worlds,
so no client-side "is this Classic" signal is needed.

### Architecture (Claude-owned)
We keep vanilla's `NoiseBasedChunkGenerator`, so carvers, surface rules, features, structures and the beardifier keep
working unchanged. We add two Java hooks plus data.

1. **Density function type `rttc:beta_terrain`** (`world/ClassicTerrain.java`, `DensityFunction.SimpleFunction`
   with a `mapAll` that re-wires its noises).
   - Beta 1.7.3's shape: lerp between min-limit and max-limit octave noise, chosen by the main noise, minus a vertical
     falloff around a base height. It is scaled to the tall world by codec params `base_y` and `vertical_scale`
     (start: sea level 63, about 2× Beta's relief, tuned in-game), plus a bottom slide so the floor is solid down to
     −64.
   - Noises are `NoiseHolder`s that point at our own `worldgen/noise/classic/*.json`, so they are seeded per world by
     `RandomState`. Inputs are DF arguments: `continents` (2D base height) and `river` (river channels pull the
     terrain down to about sea level − 3).
   - It is wrapped in `minecraft:interpolated` in the JSON, so it is evaluated at 4×8×4 cell corners, the same grid
     Beta used.
   - This is "Beta-style", not seed-exact Beta. Vanilla noise classes are used, not a port of Beta's code.
2. **Biome source type `rttc:classic`** (`world/ClassicBiomeSource.java`).
   - It reads the router climate values through the sampler (quart coords):
     | Router field | Meaning here |
     |---|---|
     | `continentalness` | the same `continents` DF the terrain uses |
     | `erosion` | the river DF |
     | `temperature`, `vegetation` | Beta-like climate noises |
     | `weirdness` | a rare mushroom-island noise |
   - Selection: deep ocean → ocean/frozen ocean → beach → river/frozen river → hills (windswept hills) → a
     temperature × humidity table (snowy plains/taiga, taiga, plains, forest, birch forest, swamp, jungle, desert).
   - Mushroom fields appear where the mushroom noise peaks, and the `continents` JSON adds a matching island bump.
   - The thresholds and the role → biome map are codec fields in the dimension JSON, so they can be tuned without code.
   - Because the terrain and the biomes read the same `continents`/`river` functions, coasts and rivers line up.
3. **Registration:** `registry/ModWorldgen` has DeferredRegisters on `Registries.DENSITY_FUNCTION_TYPE` and
   `Registries.BIOME_SOURCE`, registered from `ModRegistries.register`.
4. **Core data, hand-written by me** under `src/main/resources/data/`:
   - **`rttc/worldgen/noise_settings/classic.json`**:
     - `noise` −64/384, size 1/2; stone/water; sea level 63; `ore_veins_enabled: false` (removes the tuff and
       deepslate-iron veins).
     - The router points `final_density` and the climate fields at the functions above.
     - The surface rule is a copy of vanilla's minus the `deepslate` gradient, so it's stone to the bedrock floor.
       It adds Beta sand and gravel beaches near sea level.
   - **Aquifers:** a plain `aquifers_enabled: false` would flood every carved cave below y 63 with water, because the
     disabled aquifer fills air below sea level. That would be most of the world. Plan: keep the aquifer code on but
     pin `fluid_level_floodedness`, `fluid_level_spread` and `lava` to constants, so underground water and lava
     pockets never form, caves stay dry and oceans still fill at the surface. Step 2 checks this first. Fallback: a
     small mixin on the fluid picker, limited to our settings.
   - `rttc/worldgen/density_function/classic/{continents,river,temperature,vegetation,mushroom}.json`, plus
     `rttc/worldgen/noise/classic/*.json`.
   - `rttc/worldgen/world_preset/classic.json`: our overworld plus vanilla's nether and end.
     `minecraft/tags/worldgen/world_preset/normal.json` (`replace: false`) adds it to the world-type list.
     Lang key `generator.rttc.classic` = "Classic".
   - These are hand-written JSON rather than datagen: world-gen data is easiest to diff against vanilla's JSON. I'll
     note this in DECISIONS.

### Gemini-team tasks (more than 5 independent files each, so `scripts/gemini-team.py`)
- **G1, biome JSONs:** `data/rttc/worldgen/biome/<id>.json` × 17. Each is based on its vanilla counterpart's JSON
  (spawns, climate, features), but:
  - drop geodes, glow lichen, tuff/granite/diorite/andesite blobs, dripstone, sculk, bamboo (jungle), and any
    1.13+ vegetation the spec lists;
  - keep ores (incl. `ore_copper`), dirt/gravel blobs, `monster_room`/`monster_room_deep` (dungeons), lakes and
    springs;
  - carvers: `cave`, `cave_extra_underground`, `canyon`;
  - colours: the spec gives a starting lime table of `grass_color`/`foliage_color` by biome group, and swamp keeps
    `grass_color_modifier: swamp`. `water_color` stays vanilla's 4159204 except swamp, until Anton picks the old
    water from screenshots (the M1 item).
- **G2, tags (`replace: false`):**
  - `has_structure/` for the kept structures:
    | Tag | Biomes |
    |---|---|
    | `village_plains` | plains |
    | `village_desert` | desert |
    | `village_snowy` | snowy plains |
    | `village_taiga` | taiga |
    | `ocean_monument` | deep ocean |
    | `desert_pyramid` | desert |
    | `jungle_temple` | jungle |
    | `swamp_hut` | swamp |
    | `igloo` | snowy plains, snowy taiga |
    | `mineshaft` | all 17 |
  - `is_overworld` (all 17; strongholds use it) and `stronghold_biased_to` (land biomes).
  - Behaviour and effect tags mirror each vanilla counterpart's memberships (e.g. `water_on_map_outlines`,
    `plays_underwater_music`, `without_patrol_spawns`, `more_frequent_drowned_spawns`).
  - NeoForge `c:` convention tags are fine to add.
  - **Never** add our biomes to `minecraft:is_*` group tags (`is_ocean`, `is_beach`, `is_forest`, `is_mountain`,
    …) or to the excluded structures' tags. Those are what pull in outposts, shipwrecks, treasure, ocean ruins and
    ruined portals. The acceptance check greps for this.
- Each run goes through gemini-delegate: build, Claude reviews the diff, then commit with both co-author trailers.

### Steps (commit after each)
0. Copy this plan into PLAN.md; mark the M1 lime item as "moved to M2"; add a DECISIONS line (hand JSON for world
   gen; aquifer approach).
1. Java: `ModWorldgen`, `ClassicTerrain`, `ClassicBiomeSource`. Build.
2. Core data, with the biome map pointing at **vanilla** biomes for now so terrain can be tested before G1. Spike:
   create a world, check caves below sea level are dry, oceans are filled, the floor is solid, there's no deepslate
   and the relief looks right. Tune `vertical_scale`/thresholds.
3. G1 biome JSONs (gemini-team), then switch the biome map to the `rttc:` biomes.
4. G2 tags (gemini-team).
5. Reviewer on the M2 diff, then PLAN/DECISIONS update and commit.

### Verification
- `./gradlew build` (build-runner) after each step.
- **Headless:** `runServer` with `level-type=rttc\:classic` in `run/server.properties`. The log must show no
  world-gen/codec errors and the spawn chunks must generate. Then stop it.
- `runClient` log: "Classic" registers, there are no datapack errors, and the pack order is unchanged.
- **For Anton in-game** (I can't see the screen):
  - "Classic" is in the world-type list.
  - Lime grass and leaves; Beta-style hills and cliffs, beaches and rivers.
  - Stone down to bedrock; dry carver caves and ravines, no noise caves.
  - `/locate structure` finds the villages, monument, temples, swamp hut, igloo, stronghold and mineshaft, and
    *fails* for trial chambers, ancient city, outpost, mansion, trail ruins, shipwreck, ocean ruins, ruined portal
    and buried treasure.
  - `/locate biome` only finds `rttc:` biomes.
  - The normal world type is unchanged (vanilla tags were only extended).
