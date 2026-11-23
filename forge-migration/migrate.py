#!/usr/bin/env python3
"""Validate Empirecraft's Mohist data and prepare an isolated Forge server directory."""

import argparse
import base64
import binascii
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
from uuid import UUID
import zipfile

import yaml

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
FORGE = "1.20.1-47.4.23"
INSTALLER_SHA1 = "ed31ce02ac69176f34353235cb2508d5a0f1e088"
LOGIN_SHA256 = "230f18ee866a86df9d5f8343dd2ed6af4e026cd757c716b06298dd8075350a6b"
LOGIN = "serverlogin-1.0.3.jar"
COMPANION = "empirecraft-forge-migration-0.1.0.jar"
TOWNY = "empirecraft-towny-forge-0.5.0.jar"
IMPACT_JARS = {
    "empirecraft-claim-guard-1.0.2.jar": (
        "claim-guard", "claimguard", "0c25127aa9a5fbaafc7874e9b51d82e81b14ec4fd2d41aec45124dce712d8348",
    ),
    "siegeworks-forge-0.1.0-beta.11-1.20.1-empirecraft.3.jar": (
        "siegeworks", "siegeworks", "bfcbcc1ca4935385bab56be8bfad8380e9a968074f814a5acb1d3e573e5a3ab7",
    ),
}
BRIDGE_JARS = {
    "TownyRecruits.jar": ("towny-recruits", "ua/townyrecruits",
        ("TownyAccess", "Recruitment", "RecruitmentCommands"),
        "68dc998d62bfd778e33bde437ba2decc1d035ca7e810c02765afb6984fe846f1"),
    "empirecraft-towny-journeymap-1.14.1.jar": ("bridge", "ua/empirecraft/townymap",
        ("TownyAccess",),
        "71deb99fa5da9981959559fe825d08bbc4fa9bb7214749a2801cc6e983ab38c6"),
}
FORGE_LIB = Path("libraries/net/minecraftforge/forge") / FORGE
PLAYER = re.compile(r"[A-Za-z0-9_]{1,16}")
PLOT = re.compile(r"(-?\d+)_(-?\d+)_(\d+)\.data")
WORLD_MAPPINGS = {
    "world": (".", None),
    "world_europe": ("world", "minecraft:overworld"),
    "DIM1": ("DIM1", "minecraft:the_end"),
    "DIM-1": ("DIM-1", "minecraft:the_nether"),
}


class UniqueYaml(yaml.BaseLoader):
    """Read Bukkit YAML scalars as strings, including numeric player names."""

    def construct_mapping(self, node, deep=False):
        mapping = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str) or key in mapping:
                raise ValueError(f"Unsupported or duplicate YAML key at line {key_node.start_mark.line + 1}")
            mapping[key] = self.construct_object(value_node, deep=deep)
        return mapping


def read_yaml(file):
    try:
        return yaml.load(file.read_text(encoding="utf-8"), Loader=UniqueYaml)
    except yaml.YAMLError as error:
        line = getattr(error, "problem_mark", None)
        location = f" at line {line.line + 1}" if line else ""
        raise ValueError(f"Invalid YAML in {file.name}{location}") from None


def account_records(plugin):
    for filename in ("2fa.yml", "tg2fa.yml"):
        if read_yaml(plugin / filename):
            raise ValueError(f"{filename} has active data. The Forge login cannot enforce these second factors.")
    records = read_yaml(plugin / "passwords.yml")
    if not isinstance(records, dict) or not isinstance(records.get("passwords"), dict):
        raise ValueError("rlogin passwords.yml is missing its passwords section")

    users = {}
    blocked = set()
    skipped = 0
    for name, value in records["passwords"].items():
        if isinstance(value, dict):
            # Bukkit interprets periods in names as configuration path separators.
            if not name or not value or any(not key or not isinstance(item, str) for key, item in value.items()):
                raise ValueError("Unexpected nested rlogin record")
            skipped += len(value)
            if PLAYER.fullmatch(name):
                blocked.add(name.lower())
            continue
        if not PLAYER.fullmatch(name) or not isinstance(value, str):
            raise ValueError("rlogin contains a credential with an unsupported profile name")
        try:
            digest, salt = value.split(":")
            if len(base64.b64decode(digest, validate=True)) != 32 or len(base64.b64decode(salt, validate=True)) != 16:
                raise ValueError("Unexpected legacy credential length")
        except (ValueError, binascii.Error):
            raise ValueError("rlogin contains an invalid credential entry") from None
        key = name.lower()
        if key in users:
            raise ValueError("rlogin contains case-insensitive duplicate names")
        users[key] = {"lastKnownName": name, "salt": "rlogin:" + salt, "hash": digest, "iterations": 1}

    return {"formatVersion": 1, "users": users}, sorted(blocked - users.keys()), skipped


def properties(file, *, optional=False):
    if optional and not file.is_file():
        return {}
    data = {}
    for line in file.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.startswith(("#", "!")):
            continue
        key, delimiter, value = line.partition("=")
        if not key or not delimiter or key in data:
            raise ValueError(f"Invalid property in {file.name}")
        data[key] = value
    return data


def indexed_records(directory, *, prefer_name=False):
    records = {}
    for file in sorted(directory.glob("*.txt")):
        values = properties(file)
        name = values.get("name", file.stem) if prefer_name else file.stem
        if not name or name in records:
            raise ValueError(f"Invalid or duplicate Towny object in {directory.name}")
        records[name] = values
    return records


def check_region(world, x, z):
    region = world / "region" / f"r.{x // 32}.{z // 32}.mca"
    try:
        with region.open("rb") as file:
            file.seek(4 * (x % 32 + 32 * (z % 32)))
            entry = int.from_bytes(file.read(4), "big")
            offset, sectors = entry >> 8, entry & 255
            if offset >= 2 and sectors and (offset + sectors) * 4096 <= os.fstat(file.fileno()).st_size:
                return
    except FileNotFoundError:
        pass
    raise ValueError(f"Towny claim ({x}, {z}) is missing its chunk in {world.name}")


def flatten_sections(data, prefix="", *, include):
    result = {}
    for key, value in data.items():
        path = prefix + key
        if isinstance(value, dict):
            result.update(flatten_sections(value, path + ".", include=include))
        elif isinstance(value, include):
            result[path] = value
    return result


def towny_balances(server, towns, nations, economy):
    if economy.get("using_economy") != "true":
        return {}
    directory = server / "plugins/Essentials/userdata"
    if not directory.is_dir():
        raise ValueError("Towny uses EssentialsX Economy. Supply its userdata directory for the import.")
    town_prefix, nation_prefix = economy["town_prefix"], economy["nation_prefix"]
    organizations = {
        (prefix + name).casefold(): f"{kind}:{record['uuid']}"
        for kind, prefix, records in (("town", town_prefix, towns), ("nation", nation_prefix, nations))
        for name, record in records.items()
    }
    balances = {}
    for file in sorted(directory.glob("*.yml")):
        record = read_yaml(file)
        if not isinstance(record, dict) or "money" not in record:
            continue
        name = record.get("last-account-name", "")
        account = organizations.get(name.casefold())
        if account is None:
            if not PLAYER.fullmatch(name):
                continue
            account = f"resident:{UUID(file.stem)}"
        try:
            money = Decimal(record["money"])
            if not money.is_finite() or len(money.as_tuple().digits) > 32 or abs(money.adjusted()) > 32:
                raise InvalidOperation
        except (InvalidOperation, TypeError):
            raise ValueError("Invalid EssentialsX balance") from None
        if account in balances:
            raise ValueError("Duplicate EssentialsX account in Towny import")
        balances[account] = format(money, "f")
    return balances


def towny_snapshot(server):
    directory = server / "plugins/Towny/data"
    settings = read_yaml(directory.parent / "settings/database.yml")["database"]
    if any(settings.get(key) != "flatfile" for key in ("database_load", "database_save")):
        raise ValueError("This import requires Towny's flat-file database")
    worlds = indexed_records(directory / "worlds", prefer_name=True)
    towns = indexed_records(directory / "towns", prefer_name=True)
    nations = indexed_records(directory / "nations", prefer_name=True)
    residents = indexed_records(directory / "residents")
    dimensions = {}
    for name, record in worlds.items():
        if name not in WORLD_MAPPINGS:
            continue
        folder, dimension = WORLD_MAPPINGS[name]
        uid = server / folder / "uid.dat"
        if not uid.is_file() or UUID(bytes=uid.read_bytes()) != UUID(record["uuid"]):
            raise ValueError(f"The Towny world UUID does not match {uid}")
        if dimension:
            dimensions[name] = dimension
    if dimensions.get("world_europe") != "minecraft:overworld":
        raise ValueError("The world folder could not be verified as world_europe")

    claims = []
    deleted = 0
    for file in sorted((directory / "townblocks").rglob("*.data")):
        relative = file.relative_to(directory / "townblocks")
        if len(relative.parts) > 2 and relative.parts[1] == "deleted":
            deleted += 1
            continue
        name = relative.parts[0]
        match = PLOT.fullmatch(file.name)
        if len(relative.parts) != 2 or not match:
            raise ValueError(f"Unsupported active Towny claim layout in {name}")
        x, z, width = map(int, match.groups())
        if width != 16 or name not in dimensions:
            raise ValueError(f"Towny claim has an unresolved dimension or grid size in {name}")
        record = properties(file)
        if record.get("town") not in towns:
            raise ValueError(f"Towny claim points to a missing town in {name}")
        check_region(server / WORLD_MAPPINGS[name][0], x, z)
        claims.append({"world": name, "x": x, "z": z, "data": record})

    for record in towns.values():
        if record.get("mayor") not in residents or record.get("nation") and record["nation"] not in nations:
            raise ValueError("A Towny mayor or nation is missing")
        home = record.get("homeBlock", "")
        if home:
            if len(home.split(",")) != 3:
                raise ValueError("Invalid Towny home block")
            world, x, z = home.split(",")
            if not any(c["world"] == world and c["x"] == int(x) and c["z"] == int(z)
                       and towns[c["data"]["town"]] is record for c in claims):
                raise ValueError("A Towny home block is missing or belongs to another town")
    for record in nations.values():
        if record.get("capital") not in towns:
            raise ValueError("A Towny capital is missing")
    for record in residents.values():
        UUID(record["uuid"])
        if record.get("town") and record["town"] not in towns:
            raise ValueError("A Towny resident refers to a missing town")
    authorities = (directory.parent / "settings/config.yml", directory.parent / "settings/townyperms.yml")
    config = read_yaml(authorities[0])
    protection = config["protection"]
    permissions = read_yaml(authorities[1])
    sections = ("claiming", "economy", "nation", "town", "spawning", "bank")
    settings = flatten_sections({key: config.get(key, {}) for key in sections}, include=str)
    settings.update(flatten_sections({"day_timer": config.get("plugin", {}).get("day_timer", {})}, include=str))
    for plot in config.get("townblocktypes", {}).get("types", []):
        name = plot["name"].lower()
        if name == "jail":
            continue
        for field in ("cost", "tax", "itemUseIds", "switchIds", "allowedBlocks"):
            settings[f"plots.{name}.{field}"] = plot.get(field, "")
    for kind, fields in (("town", ("numResidents", "townOutpostLimit", "upkeepModifier")),
                         ("nation", ("numResidents", "nationBonusOutpostLimit", "upkeepModifier"))):
        for index, level in enumerate(config["levels"][kind + "_level"]):
            for key in fields:
                if key in level:
                    settings[f"levels.{kind}.{index}.{key}"] = level[key]
    return {
        "formatVersion": 1, "dimensions": dimensions, "worlds": worlds, "towns": towns,
        "nations": nations, "residents": residents, "claims": claims,
        "itemUse": protection["item_use_ids"].split(","),
        "switches": protection["switch_ids"].split(","),
        "settings": settings,
        "permissions": flatten_sections(permissions, include=list),
        "balances": towny_balances(server, towns, nations, config["economy"]),
        "invitations": [],
    }, deleted, authorities


def digest(file, algorithm):
    with file.open("rb") as source:
        return hashlib.file_digest(source, algorithm).hexdigest()


def save_json(file, data, *, private=False):
    file.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor = os.open(file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600 if private else 0o644)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")


def copy(source, target, names):
    for name in names:
        path = source / name
        if path.is_dir():
            shutil.copytree(path, target / name, ignore=shutil.ignore_patterns("session.lock"))
        elif path.is_file():
            shutil.copy2(path, target / name)


def java():
    home = os.environ.get("JAVA_HOME")
    return str(Path(home) / "bin/java") if home else "java"


def migrate_player_dimensions(server, staging, companion):
    playerdata = staging / "world/playerdata"
    if not playerdata.is_dir():
        return 0
    libraries = sorted((server / "libraries").rglob("*.jar"))
    srg = server / "libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
    if not srg.is_file():
        raise ValueError("The Minecraft SRG JAR required to migrate player NBT is missing")
    result = subprocess.run([
        java(), "-cp", os.pathsep.join(map(str, (companion, srg, *libraries))),
        "ua.empirecraft.forgemigration.PlayerDimensions", str(playerdata),
    ], stdout=subprocess.PIPE, text=True, check=True)
    return int(result.stdout.strip().splitlines()[-1])


def archive_feudal(server, staging, companion, report):
    source = server / "plugins/EmpirecraftFeudal/feudal.mv.db"
    if not source.is_file():
        return
    candidates = sorted((server / "plugins").glob("empirecraft-feudal-*.jar"))
    if len(candidates) != 1:
        raise ValueError("Expected one installed EmpirecraftFeudal JAR to read its database")
    world = staging / "world/empirecraft"
    world.mkdir(exist_ok=True)
    backup = world / "feudal-archive"
    backup.mkdir(mode=0o700)
    target = backup / source.name
    shutil.copyfile(source, target)
    target.chmod(0o600)
    gson = sorted((server / "libraries/com/google/code/gson").rglob("*.jar"))
    data = world / "feudal-map.json"
    subprocess.run([java(), "-cp", os.pathsep.join(map(str, (companion, *candidates, *gson))),
                    "ua.empirecraft.forgemigration.FeudalMapImport", str(backup / "feudal"), str(data)], check=True)
    data.chmod(0o600)
    view = json.loads(data.read_text(encoding="utf-8"))
    report["feudalMapEstates"] = len(view["estates"])
    report["feudalMapPoints"] = len(view["points"])


def check_forge(runtime):
    folder = runtime / FORGE_LIB
    args_file = folder / "unix_args.txt"
    if not args_file.is_file():
        raise ValueError("Missing the standard clean Forge " + FORGE + " server launch arguments")
    arguments = shlex.split(args_file.read_text(encoding="utf-8"), comments=True)
    if not any(pair == ("--launchTarget", "forgeserver") for pair in zip(arguments, arguments[1:])):
        raise ValueError("The supplied launch arguments do not start dedicated Forge")
    for jar in (folder / f"forge-{FORGE}-universal.jar", folder / f"forge-{FORGE}-server.jar"):
        with zipfile.ZipFile(jar) as archive:
            if any(name.startswith(("com/mohistmc/", "org/bukkit/")) for name in archive.namelist()):
                raise ValueError("The supplied Forge runtime contains Mohist/Bukkit classes")
    if not (runtime / "run.sh").is_file() or not (runtime / "user_jvm_args.txt").is_file():
        raise ValueError("The supplied Forge runtime is missing its standard launcher files")


def stage(server, dest, snapshot, accounts, blocked, report, authorities, login, companion, towny, installer, forge_runtime,
          *, fresh_world=False):
    if dest.exists() or dest.is_relative_to(server) or server.is_relative_to(dest):
        raise ValueError("Staging requires a new directory outside the Mohist server")
    if digest(login, "sha256") != LOGIN_SHA256:
        raise ValueError("Server Login does not match the pinned 1.0.3 release")
    if installer and digest(installer, "sha1") != INSTALLER_SHA1:
        raise ValueError("Installer does not match official Forge " + FORGE)
    if not companion.is_file() or not towny.is_file():
        raise ValueError("Build the Empirecraft Towny and login migration mods first")
    impact_jars = impact_overlays(server / "mods", towny.parent)
    bridges = bridge_overlays(server / "mods", towny.parent)

    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=dest.parent, prefix=".empirecraft-forge-") as temp:
        staging = Path(temp) / "stage"
        staging.mkdir(mode=0o700)
        copy(server, staging, (
            "config", "defaultconfigs", "kubejs", "mods", "schematics", "immersive_paintings",
            "eula.txt", "icon.png", "banned-ips.json", "banned-players.json", "whitelist.json",
        ))
        if fresh_world:
            (staging / "world").mkdir()
            copy(server / "world", staging / "world", ("datapacks", "serverconfig"))
        else:
            copy(server, staging, ("world", "journeymap"))
            for vanilla in ("DIM-1", "DIM1"):
                target = staging / "world" / vanilla
                if target.exists():
                    raise ValueError(f"Both root and nested copies of {vanilla} exist. Resolve the duplicate before import.")
                if (server / vanilla).is_dir():
                    target.mkdir()
                    copy(server / vanilla, target, ("region", "entities", "poi", "data", "forcedchunks.dat"))
        shutil.copy2(login, staging / "mods" / LOGIN)
        shutil.copy2(companion, staging / "mods" / COMPANION)
        shutil.copy2(towny, staging / "mods" / TOWNY)
        for source in impact_jars:
            shutil.copy2(source, staging / "mods" / source.name)
        report["nativeImpactGuards"] = [source.name for source in impact_jars]
        for source in bridges:
            shutil.copy2(source, staging / "mods" / source.name)
        report["nativeBridges"] = [source.name for source in bridges]

        if not fresh_world:
            report["playerDimensionFilesUpdated"] = migrate_player_dimensions(server, staging, companion)
            archive_feudal(server, staging, companion, report)
        config = properties(server / "server.properties")
        if config.get("online-mode") != "false" or config.get("level-name") != "world":
            raise ValueError("The Forge staging profile expects the existing offline world layout")
        config.update({
            "server-ip": "127.0.0.1", "server-port": "25629", "online-mode": "false",
            "white-list": "true", "enforce-whitelist": "true", "enable-rcon": "false", "enable-query": "false",
        })
        (staging / "server.properties").write_text(
            "".join(f"{key}={value}\n" for key, value in sorted(config.items())), encoding="utf-8"
        )
        login_config = staging / "config/serverlogin-common.toml"
        login_config.write_text(
            '[general]\nlanguage = "ru_ru"\n\n[gate]\n'
            'allowedCommands = ["login", "l", "register", "reg"]\n'
            'allowedPayloadChannels = ["minecraft:brand", "forge", "fml", "voicechat:request_secret"]\n',
            encoding="utf-8",
        )
        for authority in authorities:
            archive = staging / "migration-reference" / authority.name
            archive.parent.mkdir(exist_ok=True)
            shutil.copy2(authority, archive)
        if not fresh_world:
            save_json(staging / "world/empirecraft/towny-import.json", snapshot, private=True)
        save_json(staging / "world/serverlogin/users.json", accounts, private=True)
        save_json(staging / "world/serverlogin/reserved-names.json", blocked, private=True)
        if installer:
            subprocess.run([java(), "-jar", str(installer), "--installServer", str(staging)],
                           cwd=staging, check=True)
            check_forge(staging)
        elif forge_runtime:
            check_forge(forge_runtime)
            copy(forge_runtime, staging, ("libraries", "run.sh", "run.bat", "user_jvm_args.txt"))
        report["cleanForgeInstalled"] = bool(installer or forge_runtime)
        save_json(staging / "migration-report.json", report)
        staging.rename(dest)


def impact_overlays(mods, artifacts):
    overlays = []
    for filename, (_, package, expected) in IMPACT_JARS.items():
        base = mods / filename
        if not base.is_file():
            continue
        overlay = artifacts / "impact-guards" / filename
        if digest(base, "sha256") != expected or not overlay.is_file():
            raise ValueError(f"Build the matching native Towny guard for {filename} with build.py --pack-mods")
        bridge = f"ua/empirecraft/{package}/TownyGate"
        with zipfile.ZipFile(base) as original, zipfile.ZipFile(overlay) as patched:
            untouched = lambda name: not name.startswith((bridge + ".", bridge + "$"))
            expected_names = {name for name in original.namelist() if untouched(name)}
            actual_names = {name for name in patched.namelist() if untouched(name)}
            if (actual_names != expected_names
                    or bridge + "$NativeTowny.class" not in patched.namelist()
                    or any(original.read(name) != patched.read(name) for name in expected_names)):
                raise ValueError(f"Invalid native Towny guard overlay for {filename}")
        overlays.append(overlay)
    return overlays


def bridge_overlays(mods, artifacts):
    result = []
    for filename, (_, package, classes, expected) in BRIDGE_JARS.items():
        base = mods / filename
        if not base.is_file():
            continue
        overlay = artifacts / "native-bridges" / filename
        if digest(base, "sha256") != expected or not overlay.is_file():
            raise ValueError(f"Build the matching Forge integration for {filename} with build.py --pack-mods")
        prefixes = tuple(prefix for name in classes for prefix in (f"{package}/{name}.", f"{package}/{name}$"))
        with zipfile.ZipFile(base) as original, zipfile.ZipFile(overlay) as patched:
            untouched = {name for name in original.namelist() if not name.startswith(prefixes)}
            if (untouched != {name for name in patched.namelist() if not name.startswith(prefixes)}
                    or f"{package}/TownyAccess$ForgeData.class" not in patched.namelist()
                    or any(original.read(name) != patched.read(name) for name in untouched)):
                raise ValueError(f"Invalid Forge integration overlay for {filename}")
        result.append(overlay)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, default=REPO / "server")
    parser.add_argument("--stage", type=Path, help="New isolated directory. Omit for a read-only audit.")
    parser.add_argument("--fresh-world", action="store_true", help="Start with an empty world and empty gameplay and login data")
    parser.add_argument("--login-jar", type=Path)
    parser.add_argument("--companion-jar", type=Path)
    parser.add_argument("--towny-jar", type=Path, help="Defaults to the Towny JAR beside --companion-jar")
    runtime = parser.add_mutually_exclusive_group()
    runtime.add_argument("--installer", type=Path, help="Optional verified clean Forge installer")
    runtime.add_argument("--forge-runtime", type=Path, help="Copy a locally installed clean Forge server runtime")
    args = parser.parse_args()
    if args.fresh_world and not args.stage:
        parser.error("--fresh-world requires --stage")
    if args.stage and (not args.login_jar or not args.companion_jar):
        parser.error("--stage requires --login-jar and --companion-jar")
    try:
        server = args.server.resolve()
        if args.fresh_world:
            accounts, blocked, authorities, snapshot = {"formatVersion": 1, "users": {}}, [], [], None
            report = {"forge": FORGE, "freshWorld": True, "activeClaims": 0, "towns": 0, "nations": 0,
                      "residents": 0, "accounts": 0, "economyAccounts": 0}
        else:
            accounts, blocked, skipped = account_records(server / "plugins/rlogin")
            snapshot, deleted, authorities = towny_snapshot(server)
            report = {
                "forge": FORGE, "activeClaims": len(snapshot["claims"]), "deletedClaimsExcluded": deleted,
                "towns": len(snapshot["towns"]), "nations": len(snapshot["nations"]),
                "residents": len(snapshot["residents"]), "accounts": len(accounts["users"]),
                "unusableDottedRecords": skipped, "reservedNames": len(blocked),
                "dimensions": snapshot["dimensions"], "economyAccounts": len(snapshot["balances"]),
            }
        if args.stage:
            stage(server, args.stage.resolve(), snapshot, accounts, blocked, report, authorities,
                  args.login_jar.resolve(), args.companion_jar.resolve(),
                  (args.towny_jar or args.companion_jar.with_name(TOWNY)).resolve(),
                  args.installer.resolve() if args.installer else None,
                  args.forge_runtime.resolve() if args.forge_runtime else None, fresh_world=args.fresh_world)
        print(json.dumps(report, ensure_ascii=False))
    except subprocess.CalledProcessError as error:
        print(f"Migration stopped: a Java command failed with code {error.returncode}", file=sys.stderr)
        raise SystemExit(1) from None
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        print(f"Migration stopped: {error}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
