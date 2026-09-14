# LucidMon

LucidMon is an **unstable Fabric 1.21.1 Cobblemon tournament framework** being developed as a single modular mod instead of a collection of private datapacks and patch JARs.

> Current baseline: **v0.1.1-unstable.2**  
> Java: **21**  
> Fabric Loader tested: **0.19.5**  
> Cobblemon tested: **1.8.0**

## Current modules

- Core commented JSON5 configuration and safe fallback validation
- Manual Radical Gyms & Structures placement scaffold
- Map Reveal utilities
- Soulpack backpack recovery
- Cobblemon 1.8-compatible Virtual Pasture fork
- MapGen profile/configuration preflight framework
- Planned AI / PLAYER Champion framework

## Important status

The `KANTO_ARCHIPELAGO` configuration schema, biome manifest, route graph and landmark design are present in the current baseline, but **the absolute-coordinate Kanto terrain/chunk generator is not yet enabled in v0.1.1-unstable.2**. Do not use this version to create the final Kanto world.

`STANDARD` MapGen mode intentionally leaves normal Minecraft/modded terrain generation alone.

## Server config locations

LucidMon creates its configuration files if they do not exist:

```text
config/
└── LucidMon/
    ├── LucidMon_core.JSON5
    └── MapGen/
        └── LucidMon_mapgen.JSON5
```

Example configs are committed under [`config-examples/`](config-examples/).

## Rollback baseline

The exact tested binary/config package for the current baseline is committed under:

```text
reference-build/v0.1.1-unstable.2/
```

See [`BASELINE.md`](BASELINE.md) and [`CHANGELOG.md`](CHANGELOG.md) before changing production/test servers.

## Building

The repository contains a Gradle/Loom project definition and GitHub Actions build workflow. A temporary binary vendor JAR for the MIT-licensed patched Virtual Loot implementation is included in `vendor/` so the current integrated functionality can be reconstructed while that fork is migrated into the normal LucidMon source tree.

With Gradle 8.x and Java 21:

```bash
gradle build
```

GitHub Actions also attempts the build automatically on pushes and pull requests.

## Dependencies tested for the current baseline

| Dependency | Tested version | Relation |
|---|---|---|
| Fabric Loader | 0.19.5 | Required |
| Fabric API | 0.116.8+1.21.1 | Required |
| Architectury API | 13.0.11 | Required |
| Cobblemon | 1.8.0+1.21.1 | Required |
| RCT API | 0.16.0-beta | Required |
| RCT Mod | 0.19.0-beta | Required |
| Radical Gyms & Structures | 1.21 | Required |
| CobbleFurnies | 1.2 | Required |
| Sophisticated Backpacks | 1.21.1-3.23.4.3.106 | Required |
| Sophisticated Core | 1.21.1-1.2.9.21.168 | Required |
| Cobbreeding | 2.3.0 | Optional |
| Cobbleworkers | 2.0.5+1.8.0 | Optional |
| Waystones | 21.1.42 | Optional |
| Cobblemon: Virtual Loot | adapted 0.3 fork | Embedded |

## Reporting bugs

Use GitHub Issues. Please include the LucidMon version, Minecraft/Fabric versions, relevant dependency versions, reproduction steps, logs, and world seed/coordinates for MapGen issues.

## Licensing and attribution

LucidMon is MIT licensed. Portions of the Virtual Pasture implementation are derived from **Cobblemon: Virtual Loot** by LunazStudios under the MIT License. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

LucidMon is not affiliated with Mojang, Microsoft, The Pokémon Company, Nintendo, Game Freak, Cobblemon, or the developers of its dependency projects.
