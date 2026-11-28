#!/usr/bin/env python3
"""Compile against named game APIs, then remap to SRG so the jar runs on Forge 1.20.1."""
import argparse
import hashlib
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
NAME = "empirecraft-provenance-1.0.0.jar"


def pack(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, raw in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 28, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, raw)
    path.with_suffix(path.suffix + ".sha256").write_text(
        hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name + "\n")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--sdk", type=Path, required=True)
    p.add_argument("--java-home", type=Path, required=True)
    a = p.parse_args()
    sdk = a.sdk.resolve()
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    java, javac = a.java_home / "bin/java", a.java_home / "bin/javac"
    asm = sorted((REPO / "server/libraries/org/ow2/asm").rglob("*.jar"))
    libs = sorted((REPO / "server/libraries").rglob("*.jar"))
    nested = build / "nested"
    nested.mkdir(exist_ok=True)
    for dep in (REPO / "mods").glob("*.jar"):
        with zipfile.ZipFile(dep) as z:
            for name in z.namelist():
                if name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                    target = nested / Path(name).name
                    if not target.exists():
                        target.write_bytes(z.read(name))
    cp = [sdk / "runtime-named.jar", sdk / "client-named.jar", *libs,
          *sorted(nested.glob("*.jar")), *sorted((REPO / "mods").glob("*.jar"))]
    classes = build / "classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    args = ["--release", "17", "-proc:none", "-encoding", "UTF-8",
            "-cp", os.pathsep.join(map(str, cp)), "-d", str(classes), *map(str, sources)]
    result = subprocess.run([str(javac), *args], capture_output=True, text=True)
    (build / "compile.log").write_text(result.stdout + result.stderr)
    if result.returncode:
        print(result.stdout + result.stderr)
        raise SystemExit(result.returncode)

    entries = {f.relative_to(classes).as_posix(): f.read_bytes() for f in classes.rglob("*.class")}
    named = build / "named.jar"
    pack(named, entries)
    srg = build / "srg.jar"
    srg.unlink(missing_ok=True)
    remapper = os.pathsep.join(map(str, [sdk / "tiny-remapper.jar", sdk / "mapping-io.jar", *asm]))
    subprocess.run([str(java), "-Xmx2G", "-cp", remapper, "dev.architectury.tinyremapper.Main",
                    str(named), str(srg), str(sdk / "named-srg.tiny"), "named", "srg",
                    *map(str, cp), "--ignoreConflicts"], check=True)
    with zipfile.ZipFile(srg) as z:
        entries = {n: z.read(n) for n in z.namelist() if n.endswith(".class")}
    res = ROOT / "src/main/resources"
    entries.update({f.relative_to(res).as_posix(): f.read_bytes() for f in res.rglob("*") if f.is_file()})
    entries["META-INF/LICENSE.txt"] = (ROOT / "LICENSE").read_bytes()
    entries["META-INF/MANIFEST.MF"] = (
        b"Manifest-Version: 1.0\r\nImplementation-Version: 1.0.0\r\n\r\n")
    target = build / "libs" / NAME
    pack(target, entries)
    print("Staged:", target)


if __name__ == "__main__":
    main()
