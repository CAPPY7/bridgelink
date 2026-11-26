"""Fail if a finished feature has no real tests, or a named test is missing."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
text = (root / "docs/FEATURE_MATRIX.md").read_text()
readme = (root / "README.md").read_text()
rows = []
for line in text.splitlines():
    if not line.startswith("|"):
        continue
    cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
    if not cells or not cells[0].isdigit():
        continue
    rows.append(cells)

errors = []
feature_count = 33
ids = {int(row[0]) for row in rows}
missing = [str(number) for number in range(1, feature_count + 1) if number not in ids]
if missing:
    errors.append("missing feature rows: " + ", ".join(missing))
heading = re.search(r"Status of the (\d+) features", readme)
if not heading or int(heading.group(1)) != feature_count:
    errors.append(f"README must say Status of the {feature_count} features")
readme_ids = set()
for line in readme.splitlines():
    if not line.startswith("|"):
        continue
    cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
    if cells and cells[0].isdigit():
        readme_ids.add(int(cells[0]))
missing_readme = [str(number) for number in range(1, feature_count + 1) if number not in readme_ids]
if missing_readme:
    errors.append("README missing feature rows: " + ", ".join(missing_readme))

allowed = (
    "not started",
    "building",
    "built – not tested on a device",
    "built - not tested on a device",
    "final – owner tested",
    "final - owner tested",
    "removed",
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
        errors.append(f"feature {row[0]} status must be Not started, Building, Built – NOT TESTED on a device, Final – owner tested, or Removed")
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

proto_path = root / "android/app/src/test/java/app/bridgelink/android/protocol/ProtocolTest.java"
proto = proto_path.read_text()
start = proto.find("void everyFeatureMessageRoundTrips()")
if start < 0:
    errors.append("missing the round-trip test everyFeatureMessageRoundTrips")
    body = ""
else:
    body = proto[start:]
    nxt = body.find("\n    @Test")
    if nxt > 0:
        body = body[:nxt]
    if "Envelope.make" not in body or "Envelope.parse" not in body:
        errors.append("everyFeatureMessageRoundTrips does not make an envelope and read it back")
types_src = (root / "android/app/src/main/java/app/bridgelink/android/protocol/FeatureMessages.java").read_text()
types_match = re.search(r"TYPES = \{([^}]+)\}", types_src, re.S)
types = re.findall(r'"([a-z_]+)"', types_match.group(1) if types_match else "")
if not types:
    errors.append("FeatureMessages.TYPES is empty")
for kind in types:
    if f'"{kind}"' not in body:
        errors.append(f"message {kind} is not in the round-trip test")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"matrix ok {len(rows)} features")
