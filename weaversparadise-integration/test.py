#!/usr/bin/env python3
"""Verify the patched Weavers' Paradise jar and the recipes that consume its cloth."""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
JAR = REPO / "mods/weaversparadise-1.6.4.1-empirecraft.1.jar"
SERVER_JAR = REPO / "server/mods/weaversparadise-1.6.4.1-empirecraft.1.jar"
CURIOS = REPO / "mods/curios-forge-5.14.1+1.20.1.jar"
CURIOS_SERVER = REPO / "server/mods/curios-forge-5.14.1+1.20.1.jar"
VORTY = REPO / "mods/vortylib-forge-1.20.1-1.2.5.2.jar"
VORTY_SERVER = REPO / "server/mods/vortylib-forge-1.20.1-1.2.5.2.jar"
RECIPE_DIR = REPO / "tfc-integration/src/main/resources/data/empirecraft_tfc/recipes/crafting"

failures = []


def check(condition, message):
    print(("PASS " if condition else "FAIL ") + message)
    if not condition:
        failures.append(message)


def main():
    audit = json.loads((ROOT / "audit/removed.json").read_text())
    cut = set(audit["removed_items"])

    check(JAR.read_bytes() == SERVER_JAR.read_bytes(), "patched jar identical on client and server")
    check(CURIOS.is_file() and CURIOS.read_bytes() == CURIOS_SERVER.read_bytes(),
          "curios present and identical on both sides")
    check(VORTY.is_file() and VORTY.read_bytes() == VORTY_SERVER.read_bytes(),
          "vortylib present and identical on both sides")
    if VORTY.is_file():
        with zipfile.ZipFile(VORTY) as lib:
            check(any("AdvancedItemOptions" in n for n in lib.namelist()),
                  "vortylib provides AdvancedItemOptions the mod constructs at load")

    with zipfile.ZipFile(JAR) as jar:
        check(jar.testzip() is None, "patched jar archive is intact")
        entries = set(jar.namelist())
        check("META-INF/empirecraft-weaversparadise.json" in entries, "audit manifest inside jar")

        recipes = {}
        for name in entries:
            if name.startswith("data/weaversparadise/recipes/") and name.endswith(".json"):
                recipes[name] = json.loads(jar.read(name))["result"]["item"].split(":")[-1] \
                    if isinstance(json.loads(jar.read(name)).get("result"), dict) else None
        produced = {v for v in recipes.values() if v}
        check(not (produced & cut), "no shipped recipe produces a removed item")
        for keep in ("wool_cloth", "silk_cloth", "cotton_cloth", "player_plushie", "raw_cotton"):
            check(keep in produced, f"kept recipe still produces {keep}")

        check("data/weaversparadise/loot_tables/blocks/spinning_jenny.json" not in entries,
              "spinning jenny block loot removed")
        check("data/weaversparadise/loot_modifiers/armor_lootbox_modifier.json" not in entries,
              "armour loot-box modifier removed")
        global_loot = json.loads(jar.read("data/forge/loot_modifiers/global_loot_modifiers.json"))
        check(all("armor_lootbox" not in e for e in global_loot["entries"]),
              "global loot modifier list no longer references the loot box")

        check("assets/weaversparadise/textures/block/spinning_jenny_texture.png" in entries,
              "textures kept so existing worlds still render cut items")

    sdk = {}
    with zipfile.ZipFile(REPO / "mods/vs_sails-1.20.1-0.3.2-empirecraft.19.4-forge.jar") as sails:
        sdk["vs_sails:rope"] = any("vs_sails" in n for n in sails.namelist())
    check(sdk["vs_sails:rope"], "vs_sails:rope exists for the sail recipe")

    for recipe in ("wool_cloth_split.json", "silk_cloth_split.json", "white_sail_from_cotton_cloth.json"):
        path = RECIPE_DIR / recipe
        check(path.is_file(), f"recipe present: {recipe}")
        if path.is_file():
            data = json.loads(path.read_text())
            items = {i["item"] for i in data["ingredients"]}
            with zipfile.ZipFile(JAR) as jar:
                lang = json.loads(jar.read("assets/weaversparadise/lang/en_us.json"))
            for item in items:
                if item.startswith("weaversparadise:"):
                    key = "item." + item.replace(":", ".")
                    block_key = "block." + item.replace(":", ".")
                    check(key in lang or block_key in lang, f"{item} is a real Weavers' Paradise item")

    installed = REPO / "mods/empirecraft-tfc-integration-1.0.11.jar"
    check(installed.is_file(), "built tfc-integration 1.0.11 jar is installed in mods/")
    if installed.is_file():
        with zipfile.ZipFile(installed) as jar:
            names = set(jar.namelist())
            toml = jar.read("META-INF/mods.toml").decode()
        for recipe in ("crafting/wool_cloth_split.json", "crafting/silk_cloth_split.json",
                       "crafting/white_sail_from_cotton_cloth.json",
                       "crafting/murex_gland.json", "pot/murex_purple_dye.json",
                       "pot/kermes_red_dye.json"):
            check(f"data/empirecraft_tfc/recipes/{recipe}" in names,
                  f"installed jar ships {recipe}")
        check(sum("paleolithic_pigments" in n for n in names) > 90,
              "installed jar ships the pigments datapack")
        check('version="1.0.11"' in toml, "installed jar declares version 1.0.11")

    print()
    print(f"{'FAILED' if failures else 'ALL PASSED'} ({len(failures)} failure(s))")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
