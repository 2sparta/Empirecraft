#!/usr/bin/env python3
"""Policy fixtures + real Mixin transformation/ASM verification of the pinned runtime."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-dir", type=Path, required=True, help="Runtime libraries, weapon mods, Moonlight and Towny JARs (may be nested).")
    parser.add_argument("--forge-runtime",action="store_true",help="Skip only the Mohist-specific Level.getWorld reflection contract on standard Forge.")
    args = parser.parse_args()
    jdk = os.environ.get("JAVA_HOME")
    java = str(Path(jdk) / "bin/java") if jdk else "java"
    javac = [str(Path(jdk) / "bin/javac")] if jdk else ([shutil.which("javac")] if shutil.which("javac") else [java, "com.sun.tools.javac.Main"])
    main_classes = ROOT / "build/classes"
    if not main_classes.exists(): raise SystemExit("Run build.py first.")
    build = ROOT / "build"
    policy = build / "policy-classes"
    integration = build / "test-classes"
    for directory in (policy, integration):
        if directory.exists(): shutil.rmtree(directory)
        directory.mkdir(parents=True)
    fixtures = sorted((ROOT / "src/test/fixtures").rglob("*.java"))
    subprocess.run([*javac, "--release", "17", "-proc:none", "-cp", str(main_classes), "-d", str(policy),
                    *map(str, fixtures), str(ROOT / "src/test/java/guardtest/TownyGateTest.java")], check=True)
    subprocess.run([java, "-ea", "-cp", os.pathsep.join(map(str, [policy, main_classes])), "guardtest.TownyGateTest"], check=True)
    jars = sorted(args.classpath_dir.resolve().rglob("*.jar"))
    jars.sort(key=lambda p: (0 if p.name.startswith("asm-") else 1 if p.name == "forge-1.20.1-47.4.23-server.jar" else 2, str(p)))
    # Do not include the built mod alongside its classes/resources: avoid duplicate configurations.
    jars = [p for p in jars if not p.name.startswith("empirecraft-claim-guard-")]
    classpath = os.pathsep.join(map(str, [main_classes, ROOT / "src/main/resources", *jars]))
    sources = [ROOT / "src/test/java/guardtest" / name for name in ("OfflineMixinService.java", "OfflineProperties.java", "MixinIntegrationTest.java")]
    subprocess.run([*javac, "--release", "17", "-proc:none", "-cp", classpath, "-d", str(integration), *map(str, sources)], check=True)
    services = integration / "META-INF/services"
    services.mkdir(parents=True,exist_ok=True)
    (services / "org.spongepowered.asm.service.IMixinService").write_text("guardtest.OfflineMixinService\n")
    (services / "org.spongepowered.asm.service.IGlobalPropertyService").write_text("guardtest.OfflineProperties\n")
    subprocess.run([java, "-ea", "-Dguard.forgeOnly=" + str(args.forge_runtime).lower(), "-cp", str(integration) + os.pathsep + classpath, "guardtest.MixinIntegrationTest"], check=True)


if __name__ == "__main__": main()
