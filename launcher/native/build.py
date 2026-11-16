"""Build against the pack's actual Minecraft/Forge SDK, then remap to SRG."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import tomllib

ROOT = Path(__file__).resolve().parent
def jar(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 15, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-repo", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    args = parser.parse_args()
    libs = sorted((args.reference_repo / "server/libraries").rglob("*.jar"))
    asm = sorted((args.reference_repo / "server/libraries/org/ow2/asm").rglob("*.jar"))
    cp = [args.sdk / "runtime-named.jar", args.sdk / "client-named.jar", args.sdk / "client-original.jar", *libs]
    # Use the shipped Quick Skin API without changing or bundling its proprietary code.
    quickskin = args.reference_repo / "mods/Quick Skin - Forge - 1.20.1-3.1.1.jar"
    quickskin_named = ROOT / "build/deps/quickskin-named.jar"
    quickskin_named.parent.mkdir(parents=True, exist_ok=True)
    quickskin_named.unlink(missing_ok=True)
    remapper = [args.sdk / "tiny-remapper.jar", args.sdk / "mapping-io.jar", *asm]
    subprocess.run([str(args.jdk / "bin/java"), "-cp", os.pathsep.join(map(str, remapper)),
        "dev.architectury.tinyremapper.Main", str(quickskin), str(quickskin_named),
        str(args.sdk / "named-srg.tiny"), "srg", "named", *map(str, cp), "--ignoreConflicts"], check=True)
    cp.append(quickskin_named)
    classes = ROOT / "build/classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    subprocess.run([str(args.jdk / "bin/javac"), "--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, cp)), "-d", str(classes), *map(str, (ROOT / "src/main/java").rglob("*.java"))], check=True)
    named = ROOT / "build/named.jar"
    jar(named, {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")})
    srg = ROOT / "build/srg.jar"
    srg.unlink(missing_ok=True)
    remapper = [args.sdk / "tiny-remapper.jar", args.sdk / "mapping-io.jar", *asm]
    subprocess.run([str(args.jdk / "bin/java"), "-cp", os.pathsep.join(map(str, remapper)), "dev.architectury.tinyremapper.Main", str(named), str(srg), str(args.sdk / "named-srg.tiny"), "named", "srg", *map(str, cp), "--ignoreConflicts"], check=True)
    with zipfile.ZipFile(srg) as z:
        files = {n: z.read(n) for n in z.namelist() if n.endswith(".class")}
    files.update({p.relative_to(ROOT / "src/main/resources").as_posix(): p.read_bytes() for p in (ROOT / "src/main/resources").rglob("*") if p.is_file()})
    files["META-INF/MANIFEST.MF"] = b"Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_launcher.mixins.json\r\n\r\n"
    metadata = tomllib.loads((ROOT / "src/main/resources/META-INF/mods.toml").read_text())
    version = metadata["mods"][0]["version"]
    target = ROOT / f"dist/empirecraft-launcher-client-{version}.jar"
    jar(target, files)
    target.with_suffix(".jar.sha256").write_text(hashlib.sha256(target.read_bytes()).hexdigest() + "  " + target.name + "\n")
    print(target)
if __name__ == "__main__":
    main()
