# Current rollback baseline

## v0.1.1-unstable.2

This repository package was created around the exact LucidMon preflight build used immediately before implementation of the real Kanto Archipelago chunk generator.

### Reference artifacts

- `reference-build/v0.1.1-unstable.2/LucidMon-0.1.1-unstable.2-mapgen-preflight.jar`
- `reference-build/v0.1.1-unstable.2/LucidMon-Server-PreMapGen-Configs-v0.1.1-unstable.2.zip`

The files in `reference-build/` are intentionally retained as rollback artifacts. Do not silently replace them. Future releases should get a new versioned directory.

### Known working/integrated areas

- Core JSON5 creation/validation
- AI / PLAYER config enum parsing with safe `AI` fallback
- MapGen config creation/validation and preflight command surface
- Map Reveal v3 resources
- Soulpack v9 resources
- Virtual Pasture Cobblemon 1.8 compatibility build and recipe-book unlock
- Manual RGS gym placement scaffold / natural gym structure-set suppression

### Not yet complete in this baseline

- Real KANTO_ARCHIPELAGO custom terrain generator
- Player Champion voucher ledger and persistent reservation ordering
- Champion Challenge battle UI and arena flow
- AI Champion suppression in PLAYER mode
- Player-Champion win -> RCT Blue-equivalent progression
- Rotation-aware gym placement
- Full migration of patched Virtual Loot Java sources into LucidMon's normal source tree
