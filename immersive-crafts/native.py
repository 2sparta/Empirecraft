"""Run the full installed pack against a disposable Mohist world."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

from build import HERE, ROOT, encode, run, write_jar


JDK = Path("/workspace/work/tfc/jdk/bin")
SDK = Path("/workspace/work/tfc/toolchain")
LIBRARIES = Path("/workspace/Empirecraft/server/libraries")


def main():
    build = HERE / "build"
    runtime = Path("/workspace/work/knockedback-native/runtime")
    assert (runtime / "TFC-INTEGRATION-TEST-ONLY").is_file()
    assert "level-name=test" in (runtime / "server.properties").read_text()
    assert not (runtime / "world").exists()
    classes = build / "probe-classes"
    classes.mkdir(exist_ok=True)
    mods = sorted((ROOT / "server/mods").glob("*.jar"))
    asm = sorted((LIBRARIES / "org/ow2/asm").rglob("*.jar"))
    cp = [SDK / "runtime-named.jar", SDK / "annotations.jar", *sorted(LIBRARIES.rglob("*.jar")),
          *(p for p in mods if p.name.startswith(("immersive_tanning", "immersive_jewelry", "trmt-")))]
    run([JDK / "javac", "--release", "17", "-proc:none", "-cp", os.pathsep.join(map(str, cp)),
         "-d", classes, HERE / "Probe.java"])
    named = build / "probe-named.jar"
    srg = build / "probe-srg.jar"
    srg.unlink(missing_ok=True)
    write_jar(named, {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")})
    remapper = os.pathsep.join(map(str, [SDK / "tiny-remapper.jar", SDK / "mapping-io.jar", *asm]))
    run([JDK / "java", "-Xmx768m", "-cp", remapper, "dev.architectury.tinyremapper.Main",
         named, srg, SDK / "named-srg.tiny", "named", "srg", *cp, "--ignoreConflicts"])

    from build import read_jar
    probe = read_jar(srg)
    probe["META-INF/mods.toml"] = (b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n'
                                   b'[[mods]]\nmodId="empirecraft_immersive_probe"\nversion="1"\n')
    for p in (runtime / "mods").glob("*.jar"):
        p.unlink()
    for p in mods:
        (runtime / "mods" / p.name).symlink_to(p)
    write_jar(runtime / "mods/immersive-crafts-probe-TEST-ONLY.jar", probe)
    for base in ["kubejs"]:
        for p in (ROOT / "server" / base).rglob("*"):
            if not p.is_file():
                continue
            target = runtime / base / p.relative_to(ROOT / "server" / base)
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.is_symlink():
                target.unlink()
            shutil.copy2(p, target)
    test_world = runtime / "test"
    assert not test_world.is_symlink()
    if test_world.is_dir():
        shutil.rmtree(test_world)
    (runtime / "logs").mkdir(exist_ok=True)
    log = build / "native.log"
    with log.open("w") as output:
        result = subprocess.run(["/usr/bin/java", "@launch.args"], cwd=runtime,
                                stdout=output, stderr=subprocess.STDOUT, timeout=600)
    text = re.sub(r"\x1b\[[0-9;]*m", "", log.read_text(errors="replace"))
    lines = [line.strip() for line in text.splitlines() if "IMMERSIVE_CRAFTS_" in line]
    if result.returncode or not any("IMMERSIVE_CRAFTS_PASS" in line for line in lines):
        print("\n".join(text.splitlines()[-90:]))
        raise SystemExit("Native immersive test failed")
    evidence = {"status": "passed", "production_world_opened": False, "markers": lines,
                "artifacts": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in mods
                              if re.search(r"immersive|trmt-|empirecraft-armory", p.name)}}
    (HERE / "native.json").write_bytes(encode(evidence))
    print("\n".join(lines))


if __name__ == "__main__":
    main()
