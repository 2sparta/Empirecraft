#!/usr/bin/env python3
"""Build the server-side Towny and login mods against Empirecraft's SRG 1.20.1 libraries."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

from migrate import COMPANION, FORGE, IMPACT_JARS, REPO, ROOT, TOWNY, digest


def archive(target, classes, package, resources, manifest):
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.writestr(zipfile.ZipInfo("META-INF/MANIFEST.MF"), manifest)
        for directory, root in ((classes / f"ua/empirecraft/{package}", classes), (resources, resources)):
            for file in sorted(directory.rglob("*")):
                if file.is_file():
                    info = zipfile.ZipInfo(file.relative_to(root).as_posix())
                    jar.writestr(info, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)


def build_impact_guards(mods, compiler, work, output):
    classes = work / "guard-classes"
    output.mkdir(exist_ok=True)
    for name, (project, package, expected) in IMPACT_JARS.items():
        jar = mods / name
        if not jar.is_file() or digest(jar, "sha256") != expected:
            raise ValueError(f"Expected the current pack artifact {name} in {mods}")
        path = Path("ua/empirecraft") / package
        java = REPO / project / "src/main/java" / path / "TownyGate.java"
        subprocess.run([*compiler, "--release", "17", "-encoding", "UTF-8",
                        "-d", str(classes), str(java)], check=True)
        replacements = {file.relative_to(classes).as_posix(): file.read_bytes()
                        for file in (classes / path).glob("TownyGate*.class")}
        target = work / name
        with zipfile.ZipFile(jar) as baseline, zipfile.ZipFile(target, "w") as overlay:
            for entry in baseline.infolist():
                if entry.filename in replacements:
                    overlay.writestr(entry, replacements.pop(entry.filename))
                else:
                    overlay.writestr(entry, baseline.read(entry))
            for filename, contents in sorted(replacements.items()):
                overlay.writestr(filename, contents, compress_type=zipfile.ZIP_DEFLATED)
        target.replace(output / name)
        print(output / name)


def build_probe(compiler, classpath, classes, target):
    subprocess.run([*compiler, "--release", "17", "-encoding", "UTF-8", "-proc:none",
                    "-cp", classpath, "-d", str(classes), str(ROOT / "tests/ForgeImpactProbe.java")], check=True)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.write(classes / "guardtest/ForgeImpactProbe.class", "guardtest/ForgeImpactProbe.class")
        jar.writestr("META-INF/mods.toml", '''
modLoader="javafml"
loaderVersion="[47,)"
license="All rights reserved"
[[mods]]
modId="empirecraft_towny_probe"
version="1"
''')
    print(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--libraries", type=Path, default=REPO / "server/libraries")
    parser.add_argument("--output", type=Path, default=REPO / "work/forge-migration")
    parser.add_argument("--pack-mods", type=Path, help="Build impact guards over this server mod directory")
    parser.add_argument("--test", action="store_true", help="Check travel rules with a synthetic Towny snapshot")
    parser.add_argument("--native-probe", action="store_true", help="Build the disposable server impact test")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    jars = sorted(args.libraries.resolve().rglob("*.jar"))
    order = [f"forge-{FORGE}-server.jar", "server-1.20.1-20230612.114412-srg.jar"]
    for name in order:
        if not any(jar.name == name for jar in jars):
            parser.error(f"Required SRG artifact missing: {name}")
    jars.sort(key=lambda jar: (order.index(jar.name) if jar.name in order else len(order), str(jar)))
    java_home = os.environ.get("JAVA_HOME")
    compiler = [str(Path(java_home) / "bin/javac")] if java_home else (
        [shutil.which("javac")] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    )
    with tempfile.TemporaryDirectory(dir=output) as temp:
        temp = Path(temp)
        classes = temp / "classes"
        classes.mkdir()
        result = subprocess.run([
            *compiler, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-classpath",
            os.pathsep.join(map(str, jars)), "-d", str(classes),
            *map(str, sorted((ROOT / "src/main/java").rglob("*.java"))),
        ])
        if result.returncode:
            raise SystemExit("JDK compilation failed. Set JAVA_HOME to an installed JDK 17 or 21.")
        classpath = os.pathsep.join(map(str, [classes, *jars]))
        if args.test:
            subprocess.run([*compiler, "--release", "17", "-encoding", "UTF-8", "-proc:none",
                            "-cp", classpath, "-d", str(classes), str(ROOT / "tests/TownyTravelTest.java")], check=True)
            runtime = str(Path(java_home) / "bin/java") if java_home else "java"
            subprocess.run([runtime, "-cp", classpath, "ua.empirecraft.townyforge.TownyTravelTest"], check=True)
            (classes / "ua/empirecraft/townyforge/TownyTravelTest.class").unlink()
        archive(temp / COMPANION, classes, "forgemigration", ROOT / "src/main/resources",
                "Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_forge_migration.mixins.json\r\n\r\n")
        archive(temp / TOWNY, classes, "townyforge", ROOT / "src/towny/resources",
                "Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_towny_forge.mixins.json\r\n\r\n")
        for name in (COMPANION, TOWNY):
            (temp / name).replace(output / name)
            print(output / name)
        if args.pack_mods:
            build_impact_guards(args.pack_mods.resolve(), compiler, temp, output / "impact-guards")
        if args.native_probe:
            build_probe(compiler, classpath, classes, output / "towny-impact-probe-TEST.jar")


if __name__ == "__main__":
    main()
