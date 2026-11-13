#!/usr/bin/env python3
"""Import pinned local mod artifacts without modifying or redistributing the originals."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import re
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(create, economy):
    outputs = {}
    originals = []
    for source, mod_id, version in [(create, "create", "6.0.10"), (economy, "economy", "0.6.0-alpha")]:
        content = source.read_bytes()
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            metadata = archive.read("META-INF/neoforge.mods.toml").decode()
            mods = tomllib.loads(metadata)["mods"]
            declared = next(mod["version"] for mod in mods if mod["modId"] == mod_id)
            correction = None
            if declared != version:
                # The known local 0.6.0-alpha build has a stale descriptor. Check its actual API too.
                if mod_id != "economy" or declared != "0.4.0-alpha" or source.name != "economy-0.6.0-alpha.jar":
                    raise ValueError(f"{source}: expected {version}, found {declared}")
                api = archive.read("ca/emilybelnavis/mcmods/economy/api/BankingIntegration.class")
                if not all(symbol in api for symbol in [b"debitAccount", b"allowOverLimit", b"DebitKind"]):
                    # Parameter names need not survive compilation; the two debit/credit overloads do.
                    if not (b"debitAccount" in api and b"DebitKind" in api and api.count(b"Proof;Z)") >= 1):
                        raise ValueError("Economy artifact lacks the required v3 override signatures")
                replacement = re.sub(r'(?m)^version="0\.4\.0-alpha"$', 'version="0.6.0-alpha"', metadata)
                if replacement == metadata:
                    raise ValueError("Unexpected Economy descriptor layout")
                patched = io.BytesIO()
                with zipfile.ZipFile(patched, "w") as target:
                    for entry in archive.infolist():
                        if re.match(r"META-INF/[^/]+\.(SF|RSA|DSA)$", entry.filename):
                            raise ValueError("Refusing to patch a signed artifact")
                        target.writestr(copy.copy(entry), replacement.encode() if entry.filename == "META-INF/neoforge.mods.toml"
                                        else archive.read(entry.filename))
                content = patched.getvalue()
                correction = "Development copy only: corrected stale mod descriptor 0.4.0-alpha to 0.6.0-alpha; class files unchanged."
                print(correction)
            outputs[f"{mod_id}-{'1.21.1-' if mod_id == 'create' else ''}{version}.jar"] = content
            originals.append({"filename": source.name, "sha256": digest(source.read_bytes()),
                              "declaredVersion": declared, "developmentCorrection": correction})
            nested = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
            for library in nested:
                name = Path(library["path"]).name
                data = archive.read(library["path"])
                if name in outputs and outputs[name] != data:
                    raise ValueError(f"Conflicting bundled library: {name}")
                outputs[name] = data
    return outputs, originals


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--create", required=True, type=Path)
    parser.add_argument("--economy", required=True, type=Path)
    args = parser.parse_args()
    outputs, originals = prepare(args.create, args.economy)
    lock = {"originals": originals, "artifacts": [
        {"filename": name, "sha256": digest(data)} for name, data in sorted(outputs.items())]}
    lock_path = ROOT / "config/dev-mods.lock.json"
    if lock_path.exists() and json.loads(lock_path.read_text()) != lock:
        raise ValueError("Inputs differ from the pinned dependency lock; review a deliberate lock update first")
    library_dir = ROOT / "libs"
    library_dir.mkdir(exist_ok=True)
    unexpected = {p.name for p in library_dir.glob("*.jar")} - outputs.keys()
    if unexpected:
        raise ValueError(f"Unexpected jars in libs: {sorted(unexpected)}")
    for name, data in outputs.items():
        (library_dir / name).write_bytes(data)
    lock_path.parent.mkdir(exist_ok=True)
    lock_path.write_text(json.dumps(lock, indent=2) + "\n")
    print(f"Imported {len(outputs)} pinned artifacts into {library_dir}")


if __name__ == "__main__":
    main()
