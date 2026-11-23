"""Run the integration probe on a disposable Mohist instance."""
import argparse
import os
from pathlib import Path
import re
import shutil
from subprocess import run

from build import ROOT, HERE, VERSIONS, read_jar, write_jar

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--compiler", type=Path, required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    args = parser.parse_args()
    runtime = args.runtime
    assert (runtime/"SMITHING-TEST-ONLY").is_file()
    assert "level-name=smithing-test" in (runtime/"server.properties").read_text()
    assert not (runtime/"smithing-test").is_symlink()
    assert not (runtime/"world").exists()
    libraries = sorted((runtime/"libraries").rglob("*.jar"))
    asm = sorted((runtime/"libraries/org/ow2/asm").rglob("*.jar"))
    cp = [args.sdk/"runtime-named.jar", args.compiler/"armory-new-named.jar",
          args.compiler/"smithing-named.jar", *libraries]
    probe = runtime / "probe"
    probe.mkdir(exist_ok=True)
    java, javac = args.jdk/"bin/java", args.jdk/"bin/javac"
    run([javac, "--release", "17", "-proc:none", "-cp", os.pathsep.join(map(str, cp)),
         "-d", probe, HERE/"Probe.java"], check=True)
    jar = runtime/"probe-named.jar"
    remapped = runtime/"probe-srg.jar"
    remapped.unlink(missing_ok=True)
    write_jar(jar, {p.relative_to(probe).as_posix(): p.read_bytes() for p in probe.rglob("*.class")})
    run([java, "-Xmx640m", "-cp", os.pathsep.join(map(str, [args.sdk/"tiny-remapper.jar",
        args.sdk/"mapping-io.jar", *asm])), "dev.architectury.tinyremapper.Main", jar, remapped,
        args.sdk/"named-srg.tiny", "named", "srg", *cp, "--ignoreConflicts"], check=True)
    retired = {f"{name}-{old}.jar" for name, (old, _) in VERSIONS.items()}
    changed = {p.name: p for p in args.artifacts.glob("*.jar")}
    for p in (runtime/"mods").glob("*.jar"):
        p.unlink()
    for p in (ROOT/"server/mods").glob("*.jar"):
        if p.name not in retired and not p.name.startswith("overgeared-") and p.name not in changed:
            (runtime/"mods"/p.name).symlink_to(p)
    for name, updated in changed.items():
        (runtime/"mods"/name).symlink_to(updated)
    entries = read_jar(remapped)
    entries["META-INF/mods.toml"] = (b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n'
                                    b'[[mods]]\nmodId="empirecraft_smithing_test"\nversion="1"\n')
    write_jar(runtime/"mods/empirecraft-smithing-probe-TEST.jar", entries)
    shutil.rmtree(runtime/"kubejs", ignore_errors=True)
    shutil.copytree(ROOT/"server/kubejs", runtime/"kubejs")
    shutil.copytree(args.artifacts/"kubejs", runtime/"kubejs", dirs_exist_ok=True)
    logfile = runtime/"launch.log"
    with logfile.open("w") as output:
        result = run([java, "@launch.args"], cwd=runtime, stdout=output,
                     stderr=output, timeout=420, check=False)
    records = re.sub(r"\x1b\[[0-9;]*m", "", logfile.read_text(errors="replace")).splitlines()
    markers = [line for line in records if "IMMERSIVE_SMITHING_" in line]
    print("\n".join(markers or records[-60:]))
    assert result.returncode == 0 and any("IMMERSIVE_SMITHING_PASS" in line for line in markers), logfile

if __name__ == "__main__":
    main()
