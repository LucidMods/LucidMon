# Release / rollback process

1. Update `gradle.properties` version.
2. Update `CHANGELOG.md`.
3. Run repository integrity checks.
4. Build with Java 21.
5. Test on a fresh server/world appropriate to the change.
6. Save the tested JAR/config bundle under a new `reference-build/<version>/` directory.
7. Commit and tag the exact source state: `v<version>`.
8. Create a GitHub Release from that tag and attach the tested JAR.
9. Publish the same tested JAR to Modrinth.

Never retag or overwrite an existing baseline release. New fixes receive a new unstable version.
