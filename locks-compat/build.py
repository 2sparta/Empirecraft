#!/usr/bin/env python3
"""Build against an installed Mohist 71a84ff7 runtime, without downloading dependencies."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
CORE_SHA256 = "ade8962d51108f70a9facb8a7680990e50768cab83a2559d8bb64889f043471c"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-home", type=Path, default=REPO / "server")
    parser.add_argument("--smoke-plugins", action="store_true", help="Also build test-only Bukkit fixtures into build/; they modify locks_test and stop the test server.")
    parser.add_argument("--libraries-only", action="store_true", help="Compile the Forge-only addon using installed libraries when the Mohist bootstrap is absent.")
    args = parser.parse_args()
    runtime = args.server_home.resolve()
    core = runtime / "server.jar"
    if not args.libraries_only and (not core.is_file() or hashlib.sha256(core.read_bytes()).hexdigest() != CORE_SHA256):
        raise SystemExit("Expected server.jar from Mohist 71a84ff7; see README.md.")
    game_jars = list((runtime / "libraries").rglob("server-1.20.1-20230612.114412-srg.jar"))
    if not game_jars:
        raise SystemExit("Run Mohist once to install its runtime libraries before building.")
    locks = REPO / "server/mods/locks-steel-only-1.20.1-1.2.4-empirecraft.1.jar"
    if not locks.is_file():
        raise SystemExit("The installed Empirecraft LocksSteelOnly JAR is required in server/mods/.")
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    if not java:
        raise SystemExit("JDK 17+ is required.")
    javac = str(Path(java_home) / "bin/javac") if java_home and (Path(java_home) / "bin/javac").is_file() else shutil.which("javac")
    compiler = [javac] if javac else [java, "com.sun.tools.javac.Main"]
    build = ROOT / "build"
    classes = build / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    patched_game = list((runtime / "libraries").rglob("forge-1.20.1-47.4.23-server.jar"))
    classpath = os.pathsep.join(str(p) for p in [core, *patched_game, *game_jars, locks, *sorted((runtime / "libraries").rglob("*.jar"))])
    compile_args = ["--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes), *map(str, sorted((ROOT / "src/main/java").rglob("*.java")))]
    argfile = build / "javac.args"
    argfile.write_text("\n".join('"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"' for s in compile_args) + "\n", encoding="utf-8")
    subprocess.run([*compiler, "@" + str(argfile)], check=True)
    target = REPO / "server/mods/empirecraft-locks-compat-1.0.1.jar"
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        files = {str(p.relative_to(classes)).replace(os.sep, "/"): p.read_bytes() for p in classes.rglob("*.class")}
        files.update({str(p.relative_to(ROOT / "src/main/resources")).replace(os.sep, "/"): p.read_bytes() for p in (ROOT / "src/main/resources").rglob("*") if p.is_file()})
        files["META-INF/MANIFEST.MF"] = b"Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_locks_compat.mixins.json\r\nImplementation-Version: 1.0.1\r\n\r\n"
        files["META-INF/LICENSE.txt"] = (ROOT / "LICENSE").read_bytes()
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 22, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            jar.writestr(entry, data)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix(".jar.sha256").write_text(digest + "  " + target.name + "\n")
    print(target)
    print("SHA-256:", digest)
    if args.smoke_plugins:
        tests = build / "test-classes"
        tests.mkdir(exist_ok=True)
        test_sources = sorted((ROOT / "src/test/java").glob("*.java"))
        test_args = ["--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", str(tests), *map(str, test_sources)]
        test_argfile = build / "javac-test.args"
        test_argfile.write_text("\n".join('"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"' for s in test_args) + "\n", encoding="utf-8")
        subprocess.run([*compiler, "@" + str(test_argfile)], check=True)
        for source in test_sources:
            name = source.stem
            fixture = build / (name + ".jar")
            with zipfile.ZipFile(fixture, "w", compression=zipfile.ZIP_DEFLATED) as jar:
                jar.write(tests / (name + ".class"), name + ".class")
                jar.writestr("plugin.yml", "name: " + name + "\nversion: 1.0\nmain: " + name + "\napi-version: 1.20\n")
            print("Test-only fixture:", fixture)


if __name__ == "__main__":
    main()
