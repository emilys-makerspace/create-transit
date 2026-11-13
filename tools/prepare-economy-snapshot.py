#!/usr/bin/env python3
"""Prepare the fingerprinted Economy source in an isolated directory; never edit its source project."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--destination", type=Path, default=ROOT / "build/upstream/economy")
    args = parser.parse_args()
    source = args.source.resolve()
    destination = args.destination.resolve()
    if destination == source or destination.is_relative_to(source) or source.is_relative_to(destination):
        raise ValueError("Snapshot destination must be separate from the source project")
    if destination.exists():
        raise ValueError("Choose a new empty destination; existing snapshots are never overwritten")
    expected = json.loads((ROOT / "config/economy-source-snapshot.json").read_text())["sourceTreeSha256"]
    destination.mkdir(parents=True)
    shutil.copytree(source / "src/main", destination / "src/main")
    digest = hashlib.sha256()
    for file in sorted((destination / "src/main").rglob("*")):
        if file.is_file():
            digest.update(str(file.relative_to(destination)).encode())
            digest.update(b"\0")
            digest.update(file.read_bytes())
            digest.update(b"\0")
    if digest.hexdigest() != expected:
        raise ValueError("Source differs from the pinned snapshot; review source and dependency lock updates first")
    for filename in ["settings.gradle", "gradle.properties"]:
        shutil.copy2(source / filename, destination / filename)
    shutil.copytree(source / "gradle", destination / "gradle")
    shutil.copy2(ROOT / "config/economy-snapshot.gradle", destination / "build.gradle")
    print(f"Prepared isolated source snapshot: {destination}")
    print(f"Build with ./gradlew -p '{destination}' jar, then import its build/libs/economy-0.6.0-alpha.jar")


if __name__ == "__main__":
    main()
