# Changelog

All notable LucidMon changes should be recorded here. Versions remain `[UNSTABLE]` until the project reaches a human-verified release milestone.

## [Unreleased]

### Changed
- Full generated-world biome auditing, BCA structure auto-placement, and human-verification tooling remain follow-up work after terrain testing.

## [0.1.2-unstable.3] - 2026-09-14

### Added
- First generator-enabled `KANTO_ARCHIPELAGO` implementation using vanilla Overworld noise plus LucidMon macro terrain shaping.
- Registered `lucidmon:kanto` biome source and `lucidmon:kanto_archipelago` world preset.
- Deterministic Kanto-inspired main island, northern mountain mass, tropical islands, mushroom island, Seafoam-style islands, and southern volcano island.
- Seed-varied coastline/height detail while preserving the profile's macro layout.
- Hard ocean-only terrain outside the configured playable square and an interior ocean safety buffer.
- Config-driven biome-region assignment including all configured vanilla Overworld surface/ocean/cave biome families.
- Settlement/build-zone terrain flattening and generated route grading/surfacing.
- Generated Mt. Moon, Rock Tunnel, and Victory Road through-tunnels between configured route nodes.
- Volcano cone/crater shaping with optional crater lava.
- Dedicated-server activation safety: `mapType=KANTO_ARCHIPELAGO` must match `level-type=lucidmon:kanto_archipelago`.
- Per-world `lucidmon-mapgen.lock` profile fingerprint to prevent accidental layout/generator mixing after world creation.
- Optional configured world border application when the Kanto Overworld loads.

### Changed
- Added a hard `OCEAN_ONLY` MapGen policy outside the configured playable square, independent of the optional Minecraft world border.
- Added configurable outside-ocean biome validation with safe fallback to `minecraft:deep_ocean`.
- Added safe-land-envelope validation based on `(playableDiameter / 2) - oceanBufferBlocks`; out-of-bounds regions/landmarks/nodes are profile errors instead of being silently clamped.
- Moved the default north snow crown and southern volcano island fully inside the 6000x6000 profile's 256-block interior ocean buffer.
- MapGen status/preflight now reports boundary policy and live generator activation state.

### Testing status
- CI compilation/package verification is required before merging this version.
- Runtime world generation still requires server testing on a disposable world before the profile is considered human-verified.

## [0.1.1-unstable.2] - 2026-09-13

### Added
- Dedicated `config/LucidMon/MapGen/LucidMon_mapgen.JSON5` configuration.
- `STANDARD` and `KANTO_ARCHIPELAGO` MapGen enum with safe fallback to `STANDARD`.
- MapGen validation/preflight command framework.
- 6000x6000 Kanto-inspired profile schema.
- Required manifest for all 53 vanilla Minecraft 1.21.1 Overworld biomes.
- Macro ecological regions for snow, taiga/highlands, temperate, drylands, jungle islands, wetlands, mushroom fields, ocean variants and cave biomes.
- Southern volcanic island and town/build site configuration.
- BCA large starting-city anchor configuration.
- Named route graph with land, forest, wetland, mountain and sea connections.
- Mt. Moon entrance/exit dungeon specification with guaranteed through-path.
- Viridian Forest-style landmark, Rock Tunnel, Power Plant site, Safari preserve, Seafoam-style islands and Victory Road design entries.
- Human-verification/biome-audit settings for future map profiles.

### Important
- The absolute-coordinate Kanto terrain/chunk generator is **not active** in this version. `KANTO_ARCHIPELAGO` is a configuration/preflight profile only.

## [0.1.0-unstable.1] - 2026-09-11

### Added
- Automatic creation of `config/LucidMon/LucidMon_core.JSON5` when missing.
- Commented JSON5 defaults and safe per-field validation/fallbacks.
- `AI` / `PLAYER` Champion enum parsing (`AI` is the safe invalid-value fallback).
- Manual RGS gym/League placement and configured-coordinate inspection commands.
- Natural RGS gym/League structure-set suppression for manual-placement testing.
- LucidMon Map Reveal v3.
- Soulpack v9 with first-join ghost-backpack cleanup.
- Cobblemon Virtual Loot Fabric compatibility build for Cobblemon 1.8.
- Virtual Pasture recipe-book unlock.

### Compatibility fixes carried forward
- Removed the obsolete Cobblemon `ItemDropEntry.getComponents()` path used by the original Virtual Loot build.
- Uses the corrected test3 `PastureLootGenerator` bytecode without the invalid LocalVariableTable metadata from test2.

### Known incomplete systems
- Player Champion matchmaking and voucher ledger.
- Champion Challenge battle UI.
- AI Champion suppression in PLAYER mode.
- Player Champion win -> RCT Blue-equivalent progression.
- Rotation-aware manual gym placement.
- Automatic gym placement on server start.
