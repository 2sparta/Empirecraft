"""Build matching client/server artifacts using the pack's Java 17 Forge SDK."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

from content import PACK, encode, generate


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
ARMORY_OLD = "empirecraft-armory-1.2.1.jar"
ARMORY_NEW = "empirecraft-armory-1.2.2.jar"
ADDON = "empirecraft-immersive-crafts-1.0.0.jar"


def write_jar(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(path, "w") as archive:
        for name, data in sorted(entries.items()):
            info = ZipInfo(name, (2026, 11, 21, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)


def read_jar(path):
    with ZipFile(path) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith("/")}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, cwd=None):
    result = subprocess.run([str(argument) for argument in command], cwd=cwd, check=False)
    if result.returncode:
        raise SystemExit(f"{Path(command[0]).name} exited with {result.returncode}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--libraries", type=Path, required=True)
    parser.add_argument("--armory-base", type=Path)
    parser.add_argument("--install", action="store_true")
    args = parser.parse_args()
    build = HERE / "build"
    build.mkdir(exist_ok=True)
    mods = ROOT / "mods"
    tanning = mods / "immersive_tanning_1.3.0.jar"
    jewelry = mods / "immersive_jewelry_1.3.1.jar"
    tfc = next(mods.glob("TerraFirmaCraft-*.jar"))
    base = args.armory_base or mods / ARMORY_OLD
    if not base.is_file() and args.armory_base is None:
        from content import BASELINE
        base = build / ARMORY_OLD
        base.write_bytes(subprocess.check_output(["git", "show", f"{BASELINE}:mods/{ARMORY_OLD}"], cwd=ROOT))
    asm = sorted((args.libraries / "org/ow2/asm").rglob("*.jar"))
    libraries = sorted(args.libraries.rglob("*.jar"))
    java = args.jdk / "bin/java"
    javac = args.jdk / "bin/javac"
    sdk = args.sdk
    remapper = os.pathsep.join(map(str, [sdk / "tiny-remapper.jar", sdk / "mapping-io.jar", *asm]))

    def remap(source, target, start, end, classpath):
        target.unlink(missing_ok=True)
        run([java, "-Xmx768m", "-cp", remapper, "dev.architectury.tinyremapper.Main",
             source, target, sdk / "named-srg.tiny", start, end, *classpath, "--ignoreConflicts"])

    def compile_overlay(name, sources, classpath):
        classes = build / (name + "-classes")
        shutil.rmtree(classes, ignore_errors=True)
        classes.mkdir()
        run([javac, "--release", "17", "-proc:none", "-encoding", "UTF-8",
             "-cp", os.pathsep.join(map(str, classpath)), "-d", classes, *sources])
        named = build / (name + "-named.jar")
        srg = build / (name + "-srg.jar")
        write_jar(named, {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")})
        remap(named, srg, "named", "srg", classpath)
        return {name: value for name, value in read_jar(srg).items() if name.endswith(".class")}

    common = [sdk / "runtime-named.jar", sdk / "client-named.jar", sdk / "annotations.jar", *libraries]
    named_armory = build / "armory-base-named.jar"
    pin = build / "armory-base.sha256"
    if not named_armory.exists() or not pin.is_file() or pin.read_text().strip() != digest(base):
        remap(base, named_armory, "srg", "named", [sdk / "runtime-srg.jar", *libraries])
        pin.write_text(digest(base) + "\n")
    addon = compile_overlay("crafts", sorted((HERE / "src").glob("*.java")), [*common, tanning])
    armory = read_jar(base)
    armory.update(compile_overlay("armory", [
        ROOT / "armory/src/main/java/ua/empirecraft/armory/AssemblyRecipe.java",
    ], [named_armory, *common]))
    for file in ("META-INF/mods.toml", "META-INF/MANIFEST.MF"):
        armory[file] = armory[file].replace(b"1.2.1", b"1.2.2")

    resources, overrides, report = generate(mods, jewelry, tfc)
    addon.update(resources)
    addon["pack.mcmeta"] = encode({"pack": {"pack_format": 15, "description": "Empirecraft immersive crafts"}})
    config = "empirecraft_immersive.mixins.json"
    addon[config] = encode({"required": True, "minVersion": "0.8", "package": "ua.empirecraft.immersivecrafts.mixin",
                           "compatibilityLevel": "JAVA_17", "mixins": ["LeatherStockMixin"],
                           "injectors": {"defaultRequire": 1}})
    addon["META-INF/MANIFEST.MF"] = f"Manifest-Version: 1.0\r\nMixinConfigs: {config}\r\n\r\n".encode()
    metadata = ('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n'
                f'[[mods]]\nmodId="{PACK}"\nversion="1.0.0"\n'
                'displayName="Empirecraft Immersive Crafts"\n')
    for dependency, version in (("tfc", "[3.2.30,3.3)"), ("immersive_tanning", "[1.3.0]"),
                                ("immersive_jewelry", "[1.3.1]")):
        metadata += (f'\n[[dependencies.{PACK}]]\nmodId="{dependency}"\nmandatory=true\n'
                     f'versionRange="{version}"\nordering="AFTER"\nside="BOTH"\n')
    addon["META-INF/mods.toml"] = metadata.encode()

    report["base_armory_sha256"] = digest(base)
    report["artifacts"] = {}
    for name, entries in ((ADDON, addon), (ARMORY_NEW, armory)):
        path = HERE / "dist" / name
        write_jar(path, entries)
        report["artifacts"][name] = digest(path)
        path.with_suffix(".jar.sha256").write_text(f"{digest(path)}  {name}\n")
    (HERE / "content.json").write_bytes(encode(report))
    if args.install:
        for directory in (ROOT, ROOT / "server"):
            for name in (ADDON, ARMORY_NEW):
                for suffix in ("", ".sha256"):
                    shutil.copy2(HERE / "dist" / (name + suffix), directory / "mods" / (name + suffix))
            for suffix in ("", ".sha256"):
                (directory / "mods" / (ARMORY_OLD + suffix)).unlink(missing_ok=True)
            for relative, data in overrides.items():
                destination = directory / "kubejs" / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(data)
    print(f"Built {len(report['armor'])} Epic Knights garments and {len(report['gem_sources'])} gem sources")


if __name__ == "__main__":
    main()
