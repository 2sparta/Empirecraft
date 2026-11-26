"""Build the soil release over the installed Earth and TFC Integration JARs."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile

from build import R, REPO, pack

EARTH_BASE = "empirecraft-earth-0.1.2.jar"
TFC_BASE = "empirecraft-tfc-integration-1.0.9.jar"
EARTH_RELEASE = "empirecraft-earth-0.1.3.jar"
TFC_RELEASE = "empirecraft-tfc-integration-1.0.10.jar"
BASE_SHA256 = {
    EARTH_BASE: "b8ac737f8f0c4aa883201d670a687545c2dd8bb926634fe363d922b845e1c8a2",
    TFC_BASE: "5e6fc804e70e39c0686ede61bbd19475250021acc3c7ebce4db5f6487b0b6f44",
}

def files(path):
    with ZipFile(path) as jar:
        return {name: jar.read(name) for name in jar.namelist() if not name.endswith("/")}

def run(command):
    subprocess.run(list(map(str, command)), check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("sdk", "jdk", "libraries", "base-dir", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--native-probe", action="store_true")
    args = parser.parse_args()

    earth = args.base_dir / EARTH_BASE
    integration = args.base_dir / TFC_BASE
    for base in (earth, integration):
        if hashlib.sha256(base.read_bytes()).hexdigest() != BASE_SHA256[base.name]:
            parser.error(f"Unexpected baseline {base}")
    java, javac = args.jdk/"bin/java", args.jdk/"bin/javac"
    sdk = args.sdk
    mods = REPO/"mods"
    tfc, = mods.glob("TerraFirmaCraft-*.jar")
    calendar, = mods.glob("empirecraft-calendar-*.jar")
    libraries = sorted(args.libraries.rglob("*.jar"))
    asm = sorted((args.libraries/"org/ow2/asm").rglob("*.jar"))
    tooling = os.pathsep.join(map(str, [sdk/"tiny-remapper.jar", sdk/"mapping-io.jar", *asm]))
    tfc_sources = REPO/"tfc-integration/src/main/java/ua/empirecraft/tfc"
    earth_sources = R/"src/main/java/ua/empirecraft/earth"
    defaults = R/"src/main/resources/earth-defaults.json"

    with tempfile.TemporaryDirectory(prefix="earth-soil-release-") as temp:
        work = Path(temp)
        def remap(source, target, start, end, classpath):
            run([java, "-Xmx768m", "-cp", tooling, "dev.architectury.tinyremapper.Main",
                 source, target, sdk/"named-srg.tiny", start, end, *classpath, "--ignoreConflicts"])

        named_tfc = work/"tfc-api.jar"
        remap(tfc, named_tfc, "srg", "named", [sdk/"runtime-srg.jar", *libraries])
        cp = [sdk/"runtime-named.jar", sdk/"annotations.jar", named_tfc, earth, integration, calendar, *libraries]

        def compile(name, sources, extra=()):
            classes = work/name
            run([javac, "--release", "17", "-proc:none", "-encoding", "UTF-8",
                 "-cp", os.pathsep.join(map(str, [*extra, *cp])), "-d", classes, *sources])
            return classes

        classes = compile("build", [
            earth_sources/"EarthMod.java", earth_sources/"SoilRegionDefaults.java", earth_sources/"Geography.java",
            tfc_sources/"OneTimeTerrain.java", tfc_sources/"RegionalSoils.java", tfc_sources/"RegionalSoilPalette.java",
        ])
        tests = compile("tests", [R/"tests/RegionalSoilTest.java"], [classes])
        old = work/"old-defaults.json"
        old.write_bytes(files(earth)["earth-defaults.json"])
        run([java, "-cp", os.pathsep.join(map(str, [tests, classes, *cp])),
             "ua.empirecraft.earth.RegionalSoilTest", defaults, old])

        def mapped(name, directory):
            named, target = work/(name+"-named.jar"), work/(name+"-srg.jar")
            pack(named, {p.relative_to(directory).as_posix(): p.read_bytes() for p in directory.rglob("*.class")})
            remap(named, target, "named", "srg", [classes, *cp])
            return {path: data for path, data in files(target).items() if path.endswith(".class")}

        overlay = mapped("soil", classes)
        for path, name, prefix, version in [
            (earth, EARTH_RELEASE, "ua/empirecraft/earth/", "0.1.3"),
            (integration, TFC_RELEASE, "ua/empirecraft/tfc/", "1.0.10"),
        ]:
            entries = files(path)
            replacements = {key: value for key, value in overlay.items() if key.startswith(prefix)}
            changed_classes = {key.split("$", 1)[0].removesuffix(".class") for key in replacements}
            for key in list(entries):
                if key.endswith(".class") and key.split("$", 1)[0].removesuffix(".class") in changed_classes:
                    del entries[key]
            entries.update(replacements)
            source = R if path == earth else REPO/"tfc-integration"
            entries["META-INF/mods.toml"] = (source/"src/main/resources/META-INF/mods.toml").read_bytes()
            manifest = "META-INF/MANIFEST.MF"
            import re
            entries[manifest] = re.sub(rb"(?m)^Implementation-Version: [^\r\n]+",
                                       f"Implementation-Version: {version}".encode(), entries[manifest])
            if path == earth:
                entries["earth-defaults.json"] = defaults.read_bytes()
            artifact = args.output/name
            pack(artifact, entries)
            digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
            artifact.with_suffix(".jar.sha256").write_text(f"{digest}  {name}\n")
            print(f"BUILT {name} {digest}", flush=True)

        if args.native_probe:
            probe = compile("probe", [R/"tests/SoilMigrationProbe.java"], [classes])
            entries = mapped("probe", probe)
            entries["META-INF/mods.toml"] = b'''
modLoader="javafml"
loaderVersion="[47,)"
license="EUPL-1.2"
[[mods]]
modId="empirecraft_soil_test"
version="1"
'''
            pack(args.output/"soil-migration-TEST.jar", entries)

if __name__ == "__main__":
    main()
