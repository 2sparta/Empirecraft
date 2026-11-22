"""Verify the shipped integration data, mod versions and matching client/server bytes."""
import hashlib
import json
from pathlib import Path
import subprocess
import tomllib
import zipfile

from build import ADDON, ARMORY_NEW, HERE, ROOT
from content import BASELINE, generate


def main():
    report = json.loads((HERE / "content.json").read_text())
    mods = ROOT / "mods"
    jewelry = mods / "immersive_jewelry_1.3.1.jar"
    tanning = mods / "immersive_tanning_1.3.0.jar"
    tfc = next(mods.glob("TerraFirmaCraft-*.jar"))
    data, overrides, expected = generate(mods, jewelry, tfc)
    assert all(report[key] == value for key, value in expected.items())
    candidates = [jewelry.name, tanning.name, ADDON, ARMORY_NEW,
                  "trmt-0.5.1b-1.20.1-forge-empirecraft.3.jar"]
    for name in candidates:
        client = (mods / name).read_bytes()
        for root in [mods, ROOT / "server/mods"]:
            assert (root / name).read_bytes() == client, name
            assert (root / (name + ".sha256")).read_text().split()[0] == hashlib.sha256(client).hexdigest()
        with zipfile.ZipFile(mods / name) as jar:
            assert jar.testzip() is None
    for path, content in overrides.items():
        for directory in ["kubejs", "server/kubejs"]:
            assert (ROOT / directory / path).read_bytes() == content, path
    with zipfile.ZipFile(mods / ADDON) as jar:
        assert all(jar.read(path) == content for path, content in data.items())
        assert jar.read("ua/empirecraft/immersivecrafts/mixin/LeatherStockMixin.class")
    # Preserve every unrelated Armory class and resource in the shipped JAR.
    import io
    raw = subprocess.check_output(
        ["git", "show", BASELINE + ":mods/empirecraft-armory-1.2.1.jar"], cwd=ROOT)
    assert hashlib.sha256(raw).hexdigest() == report["base_armory_sha256"]
    with zipfile.ZipFile(io.BytesIO(raw)) as before, zipfile.ZipFile(mods / ARMORY_NEW) as after:
        for path in before.namelist():
            if path.endswith("/") or "/AssemblyRecipe" in path or path in ("META-INF/mods.toml", "META-INF/MANIFEST.MF"):
                continue
            assert before.read(path) == after.read(path), path
        assert tomllib.loads(after.read("META-INF/mods.toml").decode())["mods"][0]["version"] == "1.2.2"
    for folder in [mods, ROOT / "server/mods"]:
        for pattern in ["empirecraft-armory-*.jar", "trmt-*.jar"]:
            assert len(list(folder.glob(pattern))) == 1
    boots = json.loads(overrides["data/thirtyyearswar/recipes/baroque_breeches_boots.json"])
    assert any(x["ingredient"].get("item") == "thirtyyearswar:baroque_breeches" for x in boots["auxiliary"])
    print(f"IMMERSIVE_ARTIFACT_PASS gems={len(report['gem_sources'])} garments={len(report['armor'])} sides=2")


if __name__ == "__main__":
    main()
