"""Fail if a finished feature has no real tests, or a named test is missing."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
text = (root / "docs/FEATURE_MATRIX.md").read_text()
rows = []
for line in text.splitlines():
    if not line.startswith("|"):
        continue
    cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
    if not cells or not cells[0].isdigit():
        continue
    rows.append(cells)

errors = []
ids = {int(row[0]) for row in rows}
missing = [str(number) for number in range(1, 26) if number not in ids]
if missing:
    errors.append("missing feature rows: " + ", ".join(missing))

allowed = (
    "not started",
    "building",
    "built – self-tested",
    "built - self-tested",
    "final – owner tested",
    "final - owner tested",
)

sources = []
for path in root.rglob("*"):
    if path.suffix in {".java", ".swift", ".kt"} and "build" not in path.parts:
        try:
            sources.append(path.read_text())
        except OSError:
            pass
blob = "\n".join(sources)

for row in rows:
    if len(row) < 7:
        errors.append(f"row {row[0]} needs 7 columns")
        continue
    status = row[2].strip().lower()
    if status not in allowed:
        errors.append(f"feature {row[0]} status must be Not started, Building, Built – self-tested, or Final – owner tested")
    named = []
    for cell in row[3:6]:
        if cell.lower() in {"", "—", "-", "none", "n/a"}:
            continue
        named.append(cell.split()[0])
    manual = row[6]
    if status.startswith("built") or status.startswith("final"):
        if len(named) < 3 or manual.lower() in {"", "—", "-", "none", "n/a"}:
            errors.append(f"feature {row[0]} says finished without unit, integration, and manual ids")
    for item in named:
        name = item.split(".")[-1]
        if name not in blob:
            errors.append(f"feature {row[0]} lists missing test {name}")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"matrix ok {len(rows)} features")
