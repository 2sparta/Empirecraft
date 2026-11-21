#!/usr/bin/env python3
"""Check the staged Towny import and run the actual Server Login storage with applied mixins."""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from migrate import COMPANION, LOGIN, LOGIN_SHA256, REPO, ROOT, TOWNY, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", type=Path)
    args = parser.parse_args()
    stage = args.stage.resolve()
    mods = stage / "mods"
    if digest(mods / LOGIN, "sha256") != LOGIN_SHA256:
        parser.error("Unexpected Server Login build")
    assert not (stage / "plugins").exists()
    report = json.loads((stage / "migration-report.json").read_text())
    database = stage / "world/serverlogin/users.json"
    assert len(json.loads(database.read_text())["users"]) == report["accounts"]
    if os.name == "posix":
        assert database.stat().st_mode & 0o077 == 0
    libraries = REPO / "server/libraries"
    jars = [
        *sorted((libraries / "org/spongepowered").rglob("*.jar")),
        *sorted((libraries / "org/ow2/asm").rglob("*9.8.jar")),
        *sorted((libraries / "com/google/guava").rglob("*.jar")),
        *sorted((libraries / "com/google/code/gson").rglob("*.jar")),
        *sorted((libraries / "com/mojang/logging").rglob("*.jar")),
        *sorted((libraries / "org/slf4j").rglob("*2.0.6.jar")),
        *sorted((libraries / "org/apache/logging").rglob("*2.20.0.jar")),
        mods / LOGIN,
        mods / COMPANION,
        mods / TOWNY,
    ]
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    if not javac:
        parser.error("Set JAVA_HOME to an installed JDK 17 or 21")
    fixtures = REPO / "claim-guard/src/test/java/guardtest"
    with tempfile.TemporaryDirectory(dir=stage.parent, prefix=".forge-test-") as temp:
        temp = Path(temp)
        classes = temp / "classes"
        services = classes / "META-INF/services"
        services.mkdir(parents=True)
        (services / "org.spongepowered.asm.service.IMixinService").write_text("guardtest.OfflineMixinService\n")
        (services / "org.spongepowered.asm.service.IGlobalPropertyService").write_text("guardtest.OfflineProperties\n")
        classpath = os.pathsep.join(map(str, jars))
        subprocess.run([
            javac, "--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes),
            str(fixtures / "OfflineMixinService.java"), str(fixtures / "OfflineProperties.java"),
            str(ROOT / "tests/MigrationTest.java"), str(ROOT / "tests/TownyTest.java"),
        ], check=True)
        subprocess.run([
            java, "-cp", str(classes) + os.pathsep + classpath,
            "ua.empirecraft.forgemigration.MigrationTest", str(temp / "world"),
        ], cwd=temp, check=True)
        subprocess.run([
            java, "-cp", str(classes) + os.pathsep + classpath,
            "ua.empirecraft.townyforge.TownyTest", str(stage / "world"), str(temp / "towny-world"),
        ], cwd=temp, check=True)


if __name__ == "__main__":
    main()
