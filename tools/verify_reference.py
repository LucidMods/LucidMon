from pathlib import Path
import hashlib
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "reference-build" / "SHA256SUMS.txt"

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

failed = False
for raw in MANIFEST.read_text(encoding="utf-8").splitlines():
    raw = raw.strip()
    if not raw or raw.startswith("#"):
        continue
    expected, rel = raw.split(maxsplit=1)
    path = ROOT / rel
    if not path.exists():
        print(f"MISSING: {rel}")
        failed = True
        continue
    actual = sha256(path)
    if actual != expected:
        print(f"MISMATCH: {rel}\n  expected {expected}\n  actual   {actual}")
        failed = True
    else:
        print(f"OK: {rel}")

sys.exit(1 if failed else 0)
