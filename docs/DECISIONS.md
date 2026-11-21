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
