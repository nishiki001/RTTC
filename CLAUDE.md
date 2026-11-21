# RTTC

Casual old-Minecraft tech mod: a "Tekkit Classic revival" inspired by IC2 Classic, BuildCraft and Railcraft.
It's NOT a copy of them and NOT a realism/GregTech/TFC mod. The name RTTC is never expanded.

## Hard rules
- **Minecraft 1.21.1, NeoForge 21.1.x, ModDevGradle, Java 21. Frozen forever.** Never suggest porting.
- **One art style: Programmer Art.** Vanilla's built-in pack is forced on. Every texture we add follows
  `docs/art-bible.md`. Never produce modern-style textures.
- Keep it casual: no long processing chains, no realism grind.
- Read `docs/PLAN.md` at the start of a session; update it and `docs/DECISIONS.md` when things change.

## Layout (`io.github.nishiki001.rttc`)
- `RTTC` main class (`MODID`, `LOGGER`, `RTTC.id(path)`).
- `registry/`: `ModRegistries` holds all DeferredRegisters. Content goes in `ModBlocks`, `ModItems`, etc.
- `client/`: client-only code (`RTTCClient` is a `dist = CLIENT` @Mod). Common code never imports from here.
- `data/`: datagen providers. Prefer datagen over hand-written JSON (`./gradlew runData`, output in `src/generated`).
- `world/`, `content/<stage>/` as they appear. Mixins in `mixin/` (add `rttc.mixins.json` when first needed).

## Commands
- `./gradlew build`: compile and test (CI runs this on push).
- `./gradlew runClient` / `runServer` / `runData`.

## Working efficiently
- Use plan mode per milestone; keep plans in `docs/PLAN.md`, not in chat.
- Delegate: `build-runner` for any gradle run (never read raw build logs yourself), `explorer` for searching
  the repo or the decompiled MC/NeoForge sources, `reviewer` before committing a milestone, `gemini-delegate`
  for bulk well-specified work (JSON, lang, datagen, repetitive registrations, tags).
- Keep for yourself: architecture, mixins, world gen, energy/network systems, rendering hooks, and reviewing
  Gemini's output.
- Don't re-read large files you already summarised. Never dump build/run logs or other bulk output from
  `build/`, `run/` or `.gradle/` into context. Reading the decompiled sources jar under
  `build/moddev/artifacts/` (or `~/.gradle/caches/`) is fine: list it, then read only the classes you need.
- Commit after each working step with a clear message.
