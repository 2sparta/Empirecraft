"""Check the installed migration without starting Minecraft."""
import hashlib
import json
from pathlib import Path
import tomllib
from zipfile import ZipFile

from content import STEEL, NUGGET

ROOT = Path(__file__).resolve().parent.parent

def main():
    installed = [ROOT/"mods", ROOT/"server/mods"]
    for side in installed:
        assert not list(side.glob("overgeared-*.jar")), side
        for path in side.glob("*.jar"):
            with ZipFile(path) as jar:
                if "META-INF/mods.toml" in jar.namelist():
                    dependencies = tomllib.loads(jar.read("META-INF/mods.toml").decode()).get("dependencies", {})
                    assert not any(dependency["mandatory"] and dependency["modId"] == "overgeared"
                                   for rows in dependencies.values() for dependency in rows), path
    smithing = "immersive-smithing-1.3.1.jar"
    assert hashlib.sha256((installed[0]/smithing).read_bytes()).hexdigest() == (
        "766bec4e5706dcf78d70d7f2a2f4432cdb954ed52e9878a7d0406f8cd0c06cf1")
    for prefix in ("immersive-smithing-", "empirecraft-armory-", "empirecraft-casting-",
                   "empirecraft-forging-", "empirecraft-tfc-integration-", "hamzabus_ottoman_mod-"):
        client, = installed[0].glob(prefix + "*.jar")
        server, = installed[1].glob(prefix + "*.jar")
        assert client.name == server.name and client.read_bytes() == server.read_bytes(), prefix

    with ZipFile(next(installed[0].glob("empirecraft-armory-*.jar"))) as jar:
        recipes = [name for name in jar.namelist() if name.startswith("data/empirecraft_armory/recipes/parts/")]
        assert len(recipes) == 140
        for name in recipes:
            data = json.loads(jar.read(name))
            assert data["type"] == "immersive_smithing:smithing" and data["metal_units"] > 0, name
            assert data["process_profile"] == "empirecraft_armory:armor_component", name
        assert "data/empirecraft_armory/recipes/armorer_table.json" not in jar.namelist()
        assert json.loads(jar.read("data/forge/tags/items/ingots/steel.json"))["values"] == [STEEL]
        assert json.loads(jar.read("data/forge/tags/items/nuggets/steel.json"))["values"] == [NUGGET]
    with ZipFile(next(installed[0].glob("empirecraft-tfc-integration-*.jar"))) as jar:
        policy = json.loads(jar.read("empirecraft-tfc-policy.json"))
        assert policy["aliases"].get("overgeared:steel_ingot") == STEEL and STEEL not in policy["aliases"]
    assert not any("net.stirdrem" in path.read_text()
                   for path in (ROOT/"server/kubejs/startup_scripts").glob("*.js"))
    print("PASS: retired dependency, client/server bytes, 140 native armor components and canonical steel")

if __name__ == "__main__":
    main()
