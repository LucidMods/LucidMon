# Contributing to LucidMon

Thanks for helping test or improve LucidMon.

## Before opening an issue

1. Reproduce the problem on the latest LucidMon unstable build if possible.
2. Confirm Minecraft is 1.21.1 and Java is 21.
3. Record exact Fabric Loader and dependency versions.
4. Keep the relevant `latest.log` / crash report.
5. For MapGen problems, include world seed, dimension and coordinates.
6. Do not post private server credentials, access tokens, IP allowlists, or player personal information.

## Pull requests

- Keep changes scoped to one subsystem where possible.
- Update `CHANGELOG.md` under an `Unreleased` section for user-visible changes.
- Add/adjust config comments whenever a config field changes.
- Preserve safe fallbacks for invalid enum/config values.
- Do not alter files in `reference-build/<version>/` after a baseline is recorded.
- Avoid adding copyrighted Pokémon art/audio/assets to the repository.

## Map profile rule

A future map profile should not become a normal selectable profile until its terrain, biome coverage, routes, structures and landmarks have been human reviewed in-game.
