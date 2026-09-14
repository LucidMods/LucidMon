# First GitHub setup

## Recommended repository settings

- Default branch: `main`
- Issues: enabled
- Discussions: optional
- Wiki: optional
- Releases: enabled

## Initial upload

The simplest path is to create an empty GitHub repository named `LucidMon`, then upload the **contents** of this package (not the outer ZIP itself) to the root of the repository.

For local Git users:

```bash
git init
git add .
git commit -m "Baseline v0.1.1-unstable.2"
git branch -M main
git remote add origin <YOUR_GITHUB_REPO_URL>
git push -u origin main
git tag v0.1.1-unstable.2
git push origin v0.1.1-unstable.2
```

Then create a GitHub Release from tag `v0.1.1-unstable.2` and attach the JAR from `reference-build/v0.1.1-unstable.2/`.

## Branch protection

Once other contributors are involved, protect `main` and require pull requests plus the GitHub Actions checks before merging.
