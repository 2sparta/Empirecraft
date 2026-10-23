#!/usr/bin/env python3
"""Reproducible SRG build against the installed EmpireCraft Mohist runtime."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
VERSION = "1.0.0"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-home", type=Path, default=REPO / "server")
    parser.add_argument("--classpath-dir", type=Path, help="Use a flat copy of the same runtime dependency JARs.")
    args = parser.parse_args()
    runtime = args.server_home.resolve()
    if args.classpath_dir:
        jars = sorted(args.classpath_dir.resolve().rglob("*.jar"))
    else:
        jars = sorted((runtime / "libraries").rglob("*.jar"))
        jars += sorted((runtime / "mods").glob("*.jar"))
        if (runtime / "server.jar").exists(): jars.append(runtime / "server.jar")
    required = ["forge-1.20.1-47.4.23-server.jar", "server-1.20.1-20230612.114412-srg.jar",
                "supplementaries-1.20-3.1.44-forge.jar", "siege-machines-1.20.1-forge-1.39 (2).jar"]
    for name in required:
        if not any(p.name == name for p in jars): raise SystemExit("Missing runtime dependency: " + name)
    # The patched Minecraft classes must precede the unpatched SRG game JAR.
    jars.sort(key=lambda p: (0 if p.name == required[0] else 1, str(p)))
    jars = [p for p in jars if not p.name.startswith("empirecraft-claim-guard-")]
    build = ROOT / "build"
    classes = build / "classes"
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir(parents=True)
    jdk = os.environ.get("JAVA_HOME")
    compiler = [str(Path(jdk) / "bin/javac")] if jdk else ([shutil.which("javac")] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"])
    arguments = ["--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, jars)),
                 "-d", str(classes), *map(str, sorted((ROOT / "src/main/java").rglob("*.java")))]
    argfile = build / "javac.args"
    argfile.write_text("\n".join('"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"' for s in arguments) + "\n")
    subprocess.run([*compiler, "@" + str(argfile)], check=True)
    target = REPO / "server/mods" / f"empirecraft-claim-guard-{VERSION}.jar"
    target.parent.mkdir(parents=True, exist_ok=True)
    files = {str(p.relative_to(classes)): p.read_bytes() for p in classes.rglob("*.class")}
    resources = ROOT / "src/main/resources"
    files.update({str(p.relative_to(resources)): p.read_bytes() for p in resources.rglob("*") if p.is_file()})
    files["META-INF/MANIFEST.MF"] = ("Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_claim_guard.mixins.json\r\nImplementation-Version: " + VERSION + "\r\n\r\n").encode()
    files["META-INF/LICENSE.txt"] = (ROOT / "LICENSE").read_bytes()
    with zipfile.ZipFile(target, "w") as jar:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name.replace(os.sep, "/"), (2026, 10, 23, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            jar.writestr(entry, data)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix(".jar.sha256").write_text(digest + "  " + target.name + "\n")
    print(target)
    print("SHA-256:", digest)


if __name__ == "__main__": main()
