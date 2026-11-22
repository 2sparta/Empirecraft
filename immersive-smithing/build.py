"""Build the smithing migration against the installed Empirecraft versions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import tomllib
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

from content import ALIASES, FAMILIES, NUGGET, STEEL, SHEET, BLOCK
from content import canonical_mapping, encode, recipe, rewrite, smithing, substitute, update_pack_data

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
BASELINE = "3ebaf1fb938586e8f85540486ad05527a51d880a"
def write_jar(path, entries):
    with ZipFile(path, "w", compression=ZIP_DEFLATED) as archive:
        for key, data in sorted(entries.items()):
            info = ZipInfo(key, (2026, 11, 22, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, data)

def read_jar(path):
    with ZipFile(path) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith("/")}

def run(args):
    command = [str(arg) for arg in args]
    result = subprocess.run(command)
    if result.returncode:
        raise SystemExit(f"{Path(command[0]).name} failed with exit code {result.returncode}")

# Use the installed binaries for the runtime classes and resources outside this change.
VERSIONS = {
    "empirecraft-armory": ("1.2.2", "1.3.0"),
    "empirecraft-forging": ("1.1.6", "1.2.0"),
    "empirecraft-casting": ("1.3.2", "1.4.0"),
    "empirecraft-tfc-integration": ("1.0.7", "1.0.8"),
    "hamzabus_ottoman_mod": ("1.0.4-forge-empirecraft.1", "1.0.4-forge-empirecraft.2"),
    "empirecraft-interactions": ("1.2.0", "1.2.1"),
    "empirecraft-no-end": ("1.0.1", "1.0.2"),
    "empirecraft-content-policy": ("1.0.1", "1.0.2"),
    "siegeworks": ("forge-0.1.0-beta.11-1.20.1-empirecraft.2", "forge-0.1.0-beta.11-1.20.1-empirecraft.3"),
}

SERVER_ONLY = {"empirecraft-content-policy"}

DATA_OVERRIDES = (
    "TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar",
    "chineseweapons-forge-1.20.1-1.2.1.6-empirecraft.2.jar",
    "epic-knights-all-in-one-1_20_1-forge-empirecraft.1.jar",
)

def strip_dependency(data):
    return re.sub(rb'^\s*\[\[dependencies\.[^\]]+\]\]\s*\n\s*modId\s*=\s*"overgeared"\s*\n(?:[^\n]*\n){4}', b"\n", data, flags=re.M)

def release_metadata(data, path, old, new):
    if path.endswith("mods.toml"):
        pattern = rb'(?m)^(\s*version\s*=\s*")' + re.escape(old.encode()) + rb'(")'
    else:
        pattern = rb'(?m)^(Implementation-Version:\s*)' + re.escape(old.encode()) + rb'(\r?$)'
    return re.sub(pattern, lambda m: m[1] + new.encode() + m[2], data)

def base_path(name, build):
    side = "server/mods" if any(name.startswith(key + "-") for key in SERVER_ONLY) else "mods"
    jar = ROOT / side / name
    if not jar.exists():
        jar = build / name
        if not jar.exists():
            with jar.open("wb") as out:
                subprocess.run(["git", "show", f"{BASELINE}:{side}/{name}"], cwd=ROOT, stdout=out, check=True)
    return jar

def armory_resources(entries, immersive):
    root = "data/empirecraft_armory/"
    for name, raw in list(entries.items()):
        if not name.startswith(root + "recipes/parts/"):
            continue
        original = json.loads(raw)
        metal = name.split("/")[-1].removesuffix(".json").split("_", 1)[0]
        if name.split("/")[-1].startswith("black_steel_"):
            metal = "black_steel"
        source = original["key"]["I"]["item"]
        converted = json.loads(raw)
        del converted["key"]["T"]
        converted["pattern"] = [row.replace("T", " ") for row in converted["pattern"]]
        converted = recipe(converted, name)
        if converted["type"] != "immersive_smithing:smithing":
            # Mail/lamellar panels retain the required Epic Knights stock.
            count = sum(row.count("I") for row in original["pattern"])
            converted = smithing(FAMILIES[metal], 1, original["result"],
                                 [{"ingredient": {"item": source}, "count": count}])
        converted["anvil_pattern"] = "immersive_smithing:chestplate"
        converted["process_profile"] = "empirecraft_armory:armor_component"
        entries[name] = encode(converted)
    profile = json.loads(immersive["data/immersive_smithing/immersive_smithing/process_profiles/armor_plate.json"])
    profile.pop("patterns", None)
    for operation in profile["operations"]:
        operation["work"] = min(operation["work"], 3)
    entries[root+"immersive_smithing/process_profiles/armor_component.json"] = encode(profile)
    entries.pop(root + "recipes/armorer_table.json", None)

    def shaped(name, pattern, key, result, count=1):
        entries[root + f"recipes/{name}.json"] = encode({
            "type": "minecraft:crafting_shaped", "pattern": pattern,
            "key": key, "result": {"item": result, "count": count},
        })

    shaped("steel_ingot_from_nuggets", ["NNN"] * 3, {"N": {"item": NUGGET}}, STEEL)
    entries[root + "recipes/steel_nuggets.json"] = encode({
        "type": "minecraft:crafting_shapeless", "ingredients": [{"item": STEEL}],
        "result": {"item": NUGGET, "count": 9},
    })
    model = "assets/empirecraft_armory/models/item/steel_nugget.json"
    entries[model] = encode({"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/iron_nugget"}})
    for folder, value in (("ingots", STEEL), ("plates", SHEET), ("storage_blocks", BLOCK), ("nuggets", NUGGET)):
        entries[f"data/forge/tags/items/{folder}/steel.json"] = encode({"replace": True, "values": [value]})
    for name, item, family in (
        ("silver", "magistuarmory:silver_ingot", "forge:silver"),
        ("bronze", "createbigcannons:bronze_ingot", "forge:bronze"),
        ("black_steel", "tfc:metal/ingot/black_steel", "forge:black_steel"),
        ("high_carbon_black_steel", "tfc:metal/ingot/high_carbon_black_steel", "empirecraft_armory:high_carbon_black_steel"),
    ):
        entries[root + f"immersive_smithing/materials/{name}.json"] = encode({
            "family": family, "display_name": f"armory.origin.material.{name}",
            "sources": [{"item": item, "units": 9}], "requires_ignition": True,
            "melt_time_multiplier": 1.2, "sources_only": True,
        })
    for locale, strings in {
        "uk_ua": {
            "armory.anvil_assembly": "Складання обладунків",
            "armory.smelt_in_tfc": "Спочатку виплавте сировину в TFC.",
            "armory.help_body": "Вибери обладунок і переглянь вимоги. Викуй деталі в горні, на ковадлі й у кориті Immersive Smithing. Поклади деталі та кріплення в комірки ліворуч і збери кожен етап. Заготовку можна переносити між ковадлами.",
            "armory.process_hint": "Комірка для повернення деталей зі старого стола.",
            "armory.move_to_anvil": "Заберіть речі й перенесіть їх на ковадло кузні.",
            "armory.smithing_pattern": "Потрібна оправка в інвентарі: %s",
            "armory.work_saved": "Продовжіть складання на ковадлі кузні.",
            "armory.jei": "Викуйте деталі в Immersive Smithing. Shift + ПКМ порожніми руками на ковадлі відкриває чотири етапи складання.",
            "armory.origin.material.high_carbon_black_steel": "Насичена чорна сталь",
            "item.empirecraft_armory.steel_nugget": "Сталевий самородок",
        },
        "en_us": {
            "armory.anvil_assembly": "Armor Assembly",
            "armory.smelt_in_tfc": "Smelt the raw material in TerraFirmaCraft first.",
            "armory.help_body": "Select armor and inspect its requirements. Forge, shape and quench its components at the Immersive Smithing stations. Put the parts and fasteners in the supply slots and complete each stage. Workpieces can move between anvils.",
            "armory.process_hint": "Recover items left in this slot in the old workbench.",
            "armory.move_to_anvil": "Retrieve your items and move them to the Smith's Anvil.",
            "armory.smithing_pattern": "Carry this pattern: %s",
            "armory.work_saved": "Continue assembly at a Smith's Anvil.",
            "armory.jei": "Forge components in Immersive Smithing. Sneak + right-click the Smith's Anvil with empty hands to open four-stage assembly.",
            "armory.origin.material.high_carbon_black_steel": "High-carbon black steel",
            "item.empirecraft_armory.steel_nugget": "Steel Nugget",
        },
    }.items():
        path = f"assets/empirecraft_armory/lang/{locale}.json"
        lang = json.loads(entries[path])
        lang.update(strings)
        entries[path] = encode(lang)
        for entry in ("workshop", "workbench_ui", "armorer_workshop"):
            matching = [name for name in entries if name.endswith(f"/{locale}/entries/metals/{entry}.json")]
            for name in matching:
                # The handbook now includes this guide and the standalone mod has its smithing guide.
                entries.pop(name)


def change_data(entries):
    for name, raw in list(entries.items()):
        if not name.endswith(".json") or b"overgeared" not in raw or name.startswith("assets/"):
            continue
        if name.startswith("data/overgeared/"):
            del entries[name]
            continue
        data = json.loads(raw)
        if name == "empirecraft-tfc-policy.json":
            data = canonical_mapping(data)
        elif name == "metallurgy.json":
            data = rewrite(data)
            data["recipes"] = {key: recipe(value, key) for key, value in data["recipes"].items()}
        elif name in ("thermal-recipes.json", "content-recipes.json"):
            data = {key: recipe(value, key) for key, value in data.items()}
        elif isinstance(data, dict) and "/recipes/" in name:
            data = recipe(data, name)
        else:
            data = rewrite(data)
        if "/tags/" in name and isinstance(data, dict) and "values" in data:
            data["values"] = [value for value in data["values"]
                              if not (value if isinstance(value, str) else value["id"]).startswith(("overgeared:", "#overgeared:"))]
        if name in ("thermal.json", "food-policy.json") or data != json.loads(raw):
            entries[name] = encode(data)

def modernize_forging(entries):
    entries.pop("ua/empirecraft/forging/mixin/BowQualityMixin.class", None)
    for name in list(entries):
        if not name.endswith(".class"):
            continue
        if b"net/stirdrem/overgeared" in entries[name]:
            del entries[name]
    for name, raw in list(entries.items()):
        if name.startswith("empirecraft_forging/recipes/") and name.endswith(".json"):
            entries[name] = encode(recipe(json.loads(raw), name))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mod", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--libraries", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--build-dir", type=Path)
    parser.add_argument("--install", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    from contextlib import nullcontext
    build_context = nullcontext(args.build_dir) if args.build_dir else tempfile.TemporaryDirectory(prefix="empirecraft-smithing-build-")
    with build_context as directory:
        build = Path(directory)
        build.mkdir(parents=True, exist_ok=True)
        inventory = {key: base_path(f"{key}-{old}.jar", build) for key, (old, _) in VERSIONS.items()}
        smithing_mod = read_jar(args.mod)
        assert tomllib.loads(smithing_mod["META-INF/mods.toml"].decode())["mods"][0]["version"] == "1.3.1"
        libraries = sorted(args.libraries.rglob("*.jar"))
        asm = sorted((args.libraries / "org/ow2/asm").rglob("*.jar"))
        java, javac = args.jdk / "bin/java", args.jdk / "bin/javac"
        sdk = args.sdk
        remapper = os.pathsep.join(map(str, [sdk/"tiny-remapper.jar", sdk/"mapping-io.jar", *asm]))

        def remap(source, dest, start, end, classpath):
            run([java, "-Xmx768m", "-cp", remapper, "dev.architectury.tinyremapper.Main",
                 source, dest, sdk/"named-srg.tiny", start, end, *classpath, "--ignoreConflicts"])

        def compile_sources(key, sources, names=True, extra=()):
            classes = build / (key + "-classes")
            shutil.rmtree(classes, ignore_errors=True)
            classes.mkdir()
            common = [sdk/"runtime-named.jar", sdk/"client-named.jar"] if names else [sdk/"runtime-srg.jar", sdk/"client-original.jar"]
            cp = ([access] if names else []) + [*common, sdk/"annotations.jar", *extra, *libraries, *mods]
            run([javac, "--release", "17", "-proc:none", "-encoding", "UTF-8",
                 "-cp", os.pathsep.join(map(str, cp)), "-d", classes, *sources])
            entries = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")}
            if not names:
                return entries
            named, srg = build/(key+"-new-named.jar"), build/(key+"-new-srg.jar")
            write_jar(named, entries)
            srg.unlink(missing_ok=True)
            remap(named, srg, "named", "srg", cp)
            return {k: v for k, v in read_jar(srg).items() if k.endswith(".class")}

        access = build/"compile-access.jar"
        if not access.exists():
            cp = os.pathsep.join(map(str, asm))
            run([javac, "-proc:none", "-cp", cp, "-d", build, ROOT/"casting/tools/CompileAccess.java"])
            run([java, "-cp", str(build) + os.pathsep + cp, "CompileAccess",
                 sdk/"runtime-named.jar", sdk/"client-named.jar", access])

        mods = sorted(p for p in (ROOT/"mods").glob("*.jar") if p.name.startswith(("jei-", "create-", "medieval_boomsticks-", "epic-knights-", "SereneSeasons-", "PlayerAnimator", "TerraFirmaCraft-", "geckolib-","diegesis-")))
        mods = sorted(set(mods + [args.mod, *inventory.values()]))
        named = {}
        for key, path in (("smithing", args.mod), ("tfc", base_path(DATA_OVERRIDES[0], build)),
                          ("tfc-integration", inventory["empirecraft-tfc-integration"]),
                          ("casting", inventory["empirecraft-casting"])):
            target = build/(key+"-named.jar")
            if not target.exists():
                remap(path, target, "srg", "named", [sdk/"runtime-srg.jar", *libraries])
            named[key] = target

        source = ROOT/"armory/src/main"
        armory = read_jar(inventory["empirecraft-armory"])
        mixin = "ua/empirecraft/armory/mixin/PartForgingMixin.class"
        armory.pop(mixin)
        armory.update(compile_sources("armory", sorted((source/"java").rglob("*.java")), extra=named.values()))
        for path in (source/"resources").rglob("*"):
            if path.is_file():
                armory[path.relative_to(source/"resources").as_posix()] = path.read_bytes()
        armory_resources(armory, smithing_mod)

        source = ROOT/"forging-balance/src/main"
        forging = read_jar(inventory["empirecraft-forging"])
        modernize_forging(forging)
        forging.update(compile_sources("forging", sorted((source/"java").rglob("*.java")), names=False))
        for name in ("META-INF/mods.toml", "empirecraft_forging.mixins.json"):
            forging[name] = (source/"resources"/name).read_bytes()

        source = ROOT/"casting/src/main"
        casting = read_jar(inventory["empirecraft-casting"])
        for path in list(casting):
            if path.endswith(".class") and b"net/stirdrem/overgeared" in casting[path]:
                del casting[path]
        casting.update(compile_sources("casting", [source/"java/ua/empirecraft/casting/Thermal.java",
                                                   source/"java/ua/empirecraft/casting/client/RefiningJei.java"], extra=named.values()))
        for name in ("META-INF/mods.toml", "empirecraft_casting.mixins.json"):
            casting[name] = (source/"resources"/name).read_bytes()

        source = ROOT/"tfc-integration/src/main"
        tfc = read_jar(inventory["empirecraft-tfc-integration"])
        tfc.update(compile_sources("tfc-integration", [source/"java/ua/empirecraft/tfc/Integration.java"], extra=named.values()))
        tfc["META-INF/mods.toml"] = (source/"resources/META-INF/mods.toml").read_bytes()
        tfc["assets/empirecraft_tfc/models/item/stone_pickaxe_head.json"] = encode({
            "parent": "minecraft:item/generated", "textures": {"layer0": "empirecraft_tfc:item/stone_pickaxe_head"}})
        tfc["assets/empirecraft_tfc/models/item/golden_sword_blade_blank.json"] = encode({
            "parent": "minecraft:item/generated", "textures": {"layer0": "empirecraft_tfc:item/golden_sword_blade_blank"}})
        with ZipFile(base_path("overgeared-forge-1.20.1-1.6.36-empirecraft.1.jar", build)) as old:
            for name, texture in (("stone_pickaxe_head", "stone_pickaxe_head"), ("golden_sword_blade_blank", "golden_sword_blade")):
                tfc[f"assets/empirecraft_tfc/textures/item/{name}.png"] = old.read(f"assets/overgeared/textures/item/{texture}.png")
        for metal in ("steel", "copper", "golden"):
            for tool in ("axe", "pickaxe", "hoe", "shovel", "sword"):
                if (metal, tool) == ("steel", "sword"):
                    continue
                form = "sword_blade" if tool == "sword" else tool + "_head"
                head = f"empirecraft_tfc:golden_{form}_blank" if metal == "golden" else f"tfc:metal/{form}/{metal}"
                result = f"minecraft:golden_{tool}" if metal == "golden" else f"tfc:metal/{tool}/{metal}"
                tfc[f"data/empirecraft_tfc/recipes/smithing_finished/{metal}_{tool}.json"] = encode({
                    "type": "empirecraft_casting:quality_shaped", "pattern": ["H", "S"],
                    "key": {"H": {"item": head}, "S": {"tag": "forge:rods/wooden"}},
                    "result": {"item": result},
                })
        for locale, label in {"en_us": "Stone Pickaxe Head", "uk_ua": "Кам’яна головка кайла"}.items():
            name = f"assets/empirecraft_tfc/lang/{locale}.json"
            lang = json.loads(tfc[name])
            lang["item.empirecraft_tfc.stone_pickaxe_head"] = label
            lang["item.empirecraft_tfc.golden_sword_blade_blank"] = (
                "Золоте лезо меча" if locale == "uk_ua" else "Golden Sword Blade")
            tfc[name] = encode(lang)

        ottoman = read_jar(inventory["hamzabus_ottoman_mod"])
        ottoman.update(compile_sources("ottoman", sorted((ROOT/"ottoman/src").rglob("*.java")), extra=named.values()))
        key = "META-INF/mods.toml"
        ottoman[key] = strip_dependency(ottoman[key])
        revised = {"empirecraft-armory": armory, "empirecraft-forging": forging,
                   "empirecraft-casting": casting, "empirecraft-tfc-integration": tfc,
                   "hamzabus_ottoman_mod": ottoman}
        for key, (old, new) in VERSIONS.items():
            entries = revised[key] if key in revised else read_jar(inventory[key])
            change_data(entries)
            entries["META-INF/mods.toml"] = strip_dependency(entries["META-INF/mods.toml"])
            old_label, new_label = old.removeprefix("forge-"), new.removeprefix("forge-")
            for metadata in ("META-INF/mods.toml", "META-INF/MANIFEST.MF"):
                if metadata in entries:
                    entries[metadata] = release_metadata(entries[metadata], metadata, old_label, new_label)
            archive = args.output / f"{key}-{new}.jar"
            write_jar(archive, entries)
        overlay = args.output / "kubejs"
        owner = args.output / ".smithing-generated"
        if overlay.exists() and not owner.is_file():
            raise SystemExit(f"Use a fresh output directory for {overlay}")
        shutil.rmtree(overlay, ignore_errors=True)
        owner.touch()
        for filename in DATA_OVERRIDES:
            with ZipFile(base_path(filename, build)) as archive:
                raw = {name: archive.read(name) for name in archive.namelist()
                       if name.startswith("data/") and name.endswith(".json") and b"overgeared" in archive.read(name)}
            changed = dict(raw)
            change_data(changed)
            for name, value in changed.items():
                if value == raw[name]:
                    continue
                destination = overlay / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(value)
        shutil.copy2(args.mod, args.output / "immersive-smithing-1.3.1.jar")

    for archive in sorted(args.output.glob("*.jar")):
        checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        archive.with_suffix(".jar.sha256").write_text(f"{checksum}  {archive.name}\n")
        print(archive.name, checksum)
    if args.install:
        for folder in (ROOT/"mods", ROOT/"server/mods"):
            for key, (old, _) in VERSIONS.items():
                for suffix in (".jar", ".jar.sha256"):
                    (folder / (key+"-"+old+suffix)).unlink(missing_ok=True)
            for old in folder.glob("overgeared-forge-*.jar*"):
                old.unlink()
            for archive in args.output.glob("*.jar*"):
                if folder == ROOT/"mods" and any(archive.name.startswith(key+"-") for key in SERVER_ONLY):
                    continue
                shutil.copy2(archive, folder / archive.name)
        for side in (ROOT, ROOT/"server"):
            shutil.copytree(args.output/"kubejs", side/"kubejs", dirs_exist_ok=True)
        update_pack_data(ROOT)

if __name__ == "__main__":
    main()
