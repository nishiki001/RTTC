# RTTC plan

## Next up (for local Claude Code)
1. Anton runs `./gradlew runClient` once and checks: "RTTC" is in the Mods list; Options → Resource Packs
   shows "Programmer Art" selected, directly above Default, and it can't be removed or moved; blocks use the
   old textures. (Build passes; this needs a human with a screen.)
2. Continue **M1**: water colour (screenshots for Anton). The grass-colour task depends on the Classic
   preset, so it probably lands with M2.

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
  - [ ] Lime Alpha/Beta grass, foliage and vine colours via colour handlers or biome overrides, **only in
        the Classic preset** (Anton, 2026-11-21). Needs a client-side "is this a Classic world" signal;
        may be easiest to land together with M2's preset.
  - [ ] Look at water colour (flat old blue?), and **ASK Anton** with screenshots.
- [ ] M2 Classic world preset: Beta-1.7.3-style 3D noise ChunkGenerator, modern biomes, carver caves,
      vanilla structures still generating. **ASK Anton about world height first** (~0–192 proposed).
- [ ] M3 Stone age (flint/stone tools, work stump, charcoal pit, bloomery). 30–60 min, light.
- [ ] M4 Steam: boilers, engines, shafts; first visible-item pipes
- [ ] M5 Rails and carts as main logistics; tunnel bore
- [ ] M6 Electric tier (voltage tiers, cable loss, overvoltage explosions, macerator), then the reactor puzzle
- [ ] Ongoing: programmer-art textures for post-1.14 vanilla blocks (list in art-bible.md, Gemini can compile it)
- [ ] Compat: JEI, Jade
