# MapGen design baseline

`STANDARD` means no LucidMon terrain override.

`KANTO_ARCHIPELAGO` is a planned 6000x6000 Kanto-inspired archipelago profile with:

- south-central BCA large starting city
- far-north snowy mountain crown
- northwest taiga/windswept highlands
- central temperate city/route heartland
- eastern desert/savanna/badlands region
- southeast jungle/bamboo island chain
- southwest swamp/mangrove wetlands
- offshore mushroom island
- all vanilla ocean variants
- lush caves, dripstone caves and deep dark
- southern volcanic island with crater and town site
- connected route graph
- Mt. Moon through-cave
- Rock Tunnel
- Seafoam-style islands
- Safari-style preserve
- Power Plant build site
- Victory Road and League plateau

The checked-in JSON5 template is the canonical current specification.

## Play-area boundary invariant

The configured `playableDiameter` is the hard maximum terrain envelope for LucidMon MapGen. For the default 6000x6000 profile centered at `0,0`, the playable square is X/Z `-3000..+3000`.

`oceanBufferBlocks` is reserved **inside** that boundary. With the default 256-block buffer, major dry-land terrain must remain within a safe envelope of roughly `-2744..+2744` on X/Z.

Anything outside the configured playable square is always generated as ocean-only terrain. This rule is independent of `setWorldBorder`; disabling the Minecraft world border does not permit LucidMon land generation outside the configured play area.

`outsidePlayableArea.mode` currently accepts only `OCEAN_ONLY`. `outsidePlayableArea.biome` must be one of the vanilla Overworld ocean biomes and defaults to `minecraft:deep_ocean`.

MapGen preflight treats configured macro regions, major landmarks, route nodes, and build zones that exceed the safe land envelope as profile errors rather than silently moving or clipping them.

## Human-verification requirement

Before marking a profile release-ready:

1. Generate a fresh world.
2. Scan the playable region.
3. Audit all required vanilla Overworld biomes.
4. Verify route connectivity.
5. Verify both entrances/exits for through-dungeons.
6. Inspect starting city, volcano, build zones and League approach.
7. Only after approval should the profile be advertised as supported.
