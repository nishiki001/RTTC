# RTTC plan

## Next up (for local Claude Code)
1. Anton runs `./gradlew runClient` once to check that "RTTC" shows in the Mods list (build passes).
2. **ASK Anton** about the license (currently All Rights Reserved, which blocks forks and addons;
   MIT/LGPL allows them).
3. Start **M1**.

## Milestones
- [x] M0 Bootstrap: renamed template, CLAUDE.md, agents, docs
- [x] M0.5 Gemini wired up and smoke-tested (`agy`, lang-key smoke test passed and reverted)
- [ ] **M1 The Look**
  - [ ] Force vanilla's built-in `programmer_art` pack: always enabled, above vanilla, not removable
        in the pack screen. Have `explorer` check how vanilla registers it (`BuiltInPackSource` /
        `ServerPacksSource`/`ClientPackSource`, `PackRepository`, `Options.resourcePacks`), then pick
        the cleanest hook (NeoForge `AddPackFindersEvent` with a required/fixed `PackSelectionConfig`
        vs. a small mixin). Record the choice in DECISIONS.
  - [ ] Lime Alpha/Beta grass, foliage and vine colours via colour handlers or biome overrides. Decide:
        everywhere, or only in the Classic preset? **ASK Anton.**
  - [ ] Look at water colour (flat old blue?), and **ASK Anton** with screenshots.
- [ ] M2 Classic world preset: Beta-1.7.3-style 3D noise ChunkGenerator, modern biomes, carver caves,
      vanilla structures still generating. **ASK Anton about world height first** (~0–192 proposed).
- [ ] M3 Stone age (flint/stone tools, work stump, charcoal pit, bloomery). 30–60 min, light.
- [ ] M4 Steam: boilers, engines, shafts; first visible-item pipes
- [ ] M5 Rails and carts as main logistics; tunnel bore
- [ ] M6 Electric tier (voltage tiers, cable loss, overvoltage explosions, macerator), then the reactor puzzle
- [ ] Ongoing: programmer-art textures for post-1.14 vanilla blocks (list in art-bible.md, Gemini can compile it)
- [ ] Compat: JEI, Jade
