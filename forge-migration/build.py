#!/usr/bin/env python3
"""Build the server-side Towny and login mods against Empirecraft's SRG 1.20.1 libraries."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

from migrate import COMPANION, FORGE, REPO, ROOT, TOWNY


def archive(target, classes, package, resources, manifest):
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.writestr(zipfile.ZipInfo("META-INF/MANIFEST.MF"), manifest)
        for directory, root in ((classes / f"ua/empirecraft/{package}", classes), (resources, resources)):
            for file in sorted(directory.rglob("*")):
                if file.is_file():
                    info = zipfile.ZipInfo(file.relative_to(root).as_posix())
                    jar.writestr(info, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--libraries", type=Path, default=REPO / "server/libraries")
    parser.add_argument("--output", type=Path, default=REPO / "work/forge-migration")
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
        archive(temp / COMPANION, classes, "forgemigration", ROOT / "src/main/resources",
                "Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_forge_migration.mixins.json\r\n\r\n")
        archive(temp / TOWNY, classes, "townyforge", ROOT / "src/towny/resources", "Manifest-Version: 1.0\r\n\r\n")
        for name in (COMPANION, TOWNY):
            (temp / name).replace(output / name)
            print(output / name)


if __name__ == "__main__":
    main()
