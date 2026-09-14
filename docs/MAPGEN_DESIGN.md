# MapGen design baseline

`STANDARD` means no LucidMon terrain override.

`KANTO_ARCHIPELAGO` is the generator-enabled Kanto-inspired 6000x6000 archipelago profile. It keeps vanilla Overworld noise as the local-detail/cave/ore foundation, then LucidMon shapes the macro terrain, assigns the configured regional biomes, grades route corridors, and enforces the configured play-area ocean boundary.

The profile includes:

- south-central BCA large starting-city site
- far-north snowy mountain crown
- northwest taiga/windswept highlands
- central temperate city/route heartland
- eastern desert/savanna/badlands region
- southeast jungle/bamboo island chain
- southwest swamp/mangrove wetlands
- offshore mushroom island
- all vanilla ocean variants
- lush caves, dripstone caves and deep dark volumes
- southern volcanic island with crater/lava option and town site
- connected route graph
- Mt. Moon through-cave
- Rock Tunnel
- Seafoam-style islands
- Safari-style preserve
- Power Plant build site
- Victory Road and League plateau

The checked-in JSON5 template is the canonical profile specification.

## Activation

For a **new** dedicated-server world, both settings must agree:

```text
config/LucidMon/MapGen/LucidMon_mapgen.JSON5
mapType: "KANTO_ARCHIPELAGO"

server.properties
level-type=lucidmon:kanto_archipelago
```

LucidMon intentionally stops world loading if the config requests Kanto while the Overworld uses another generator, or if the Kanto preset is active while the config says `STANDARD`. This prevents accidental mixed-generation worlds.

When the Kanto world first loads, LucidMon writes `lucidmon-mapgen.lock` into the world root. The lock stores a fingerprint of the terrain/layout profile. Later layout changes will refuse to load that existing world instead of silently mixing incompatible chunks.

## Play-area boundary invariant

The configured `playableDiameter` is the hard maximum terrain envelope. For the default 6000x6000 profile centered at `0,0`, the playable square is X/Z `-3000..+3000`.

`oceanBufferBlocks` is reserved **inside** that boundary. With the default 256-block buffer, major dry-land terrain remains within roughly `-2744..+2744` on X/Z.

Anything outside the configured playable square is reshaped to ocean-floor terrain and uses the configured outside ocean biome. This rule is independent of `setWorldBorder`; disabling the Minecraft world border does not permit LucidMon land outside the configured play area.

`outsidePlayableArea.mode` currently accepts only `OCEAN_ONLY`. `outsidePlayableArea.biome` must be a vanilla Overworld ocean biome and defaults to `minecraft:deep_ocean`.

## Terrain model

The world preset still uses `minecraft:noise` with the vanilla Overworld noise settings. A LucidMon biome source supplies the regional biome layout. A mixin then reshapes generated columns to the Kanto macro silhouette after vanilla noise filling. This preserves the normal generation pipeline for caves, ores and many modded/vanilla features while giving LucidMon control over islands, mountains, ocean margins and prepared build areas.

The macro silhouette is deterministic across seeds. Seeded positional noise changes coastline roughness and local elevation so different seeds remain recognizable as the same regional profile without being block-identical.

## Routes and through-dungeons

Configured non-sea routes receive terrain grading and a surface path. Sea routes remain water connections rather than giant causeways. Mt. Moon, Rock Tunnel and Victory Road receive guaranteed carved corridors between their configured entrance/exit route nodes after vanilla cave carving.

## Human-verification requirement

Before marking a profile release-ready:

1. Generate a fresh disposable world with both Kanto activation settings.
2. Run `/lucidmon mapgen preflight` and confirm PASS.
3. Scan the full playable region.
4. Verify the ocean-only exterior and interior ocean buffer.
5. Audit all required vanilla Overworld biomes once the full audit command is implemented.
6. Verify route connectivity and all through-dungeon entrances/exits.
7. Inspect starting-city site, volcano, build zones and League approach.
8. Only after approval should the profile be advertised as supported.

The Kanto generator is intentionally still marked unstable until this runtime verification is complete.
