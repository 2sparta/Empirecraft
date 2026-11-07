#!/usr/bin/env python3
"""Run policy, real NBT, Towny reflection and exact-JAR Mixin integration checks."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parent

def main():
    subprocess.run([sys.executable,str(ROOT/'tools/test_journeymap_version.py')],check=True)
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classpath-dir',type=Path,action='append',required=True)
    args=p.parse_args();subprocess.run([sys.executable,str(ROOT/'tools/test_skins.py')],check=True);jars=sorted({f.resolve() for directory in args.classpath_dir for f in directory.rglob('*.jar') if not f.name.startswith('townyrecruits-')})
    # Mohist retains older ASM versions too; Mixin's offline launcher must use its runtime ASM 9.7.
    jars.sort(key=lambda f:(0 if '/org/ow2/asm/' in f.as_posix() and '/9.7/' in f.as_posix() else 1 if f.name=='forge-1.20.1-47.4.23-server.jar' else 2,str(f)))
    jars.append(ROOT/'build/deps/journeymap-api.jar')
    classes=ROOT/'build/classes';tests=ROOT/'build/test-classes'
    if not classes.exists():raise SystemExit('Run build.py first.')
    if tests.exists():shutil.rmtree(tests)
    tests.mkdir(parents=True)
    java_home=os.environ.get('JAVA_HOME');java=str(Path(java_home)/'bin/java') if java_home else 'java'
    javac=[str(Path(java_home)/'bin/javac')] if java_home else ([shutil.which('javac')] if shutil.which('javac') else [java,'com.sun.tools.javac.Main'])
    cp=os.pathsep.join(map(str,[classes,ROOT/'src/main/resources',*jars]))
    sources=[*(ROOT/'src/test/java').rglob('*.java'),*(ROOT/'src/test/fixtures').rglob('*.java')]
    subprocess.run([*javac,'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(tests),*map(str,sources)],check=True)
    services=tests/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    for test in ['guardtest.AppearanceTest','guardtest.PolicyTest','guardtest.TacticalMathTest','guardtest.BattleMathTest','guardtest.MoralePolicyTest','guardtest.ArmyMapCodecTest','guardtest.JourneyMapArmyLayerTest','guardtest.ArmyMapRasterTest','guardtest.BannerAssignmentRuntimeTest','guardtest.ExchangeAuthorityRuntimeTest','guardtest.ExchangePacketAuthorityTest','ua.townyrecruits.ExchangeProjectionTest','guardtest.DesertionRuntimeTest','guardtest.TacticalCodecTest','guardtest.TacticalFireRuntimeTest','guardtest.TacticalAimRuntimeTest','guardtest.BombInventoryRuntimeTest','guardtest.TacticalPlanningIsolationTest','guardtest.RouteSlotsRuntimeTest','guardtest.TownyContractTest','ua.townyrecruits.ArmyDataTest','ua.townyrecruits.MilitaryDataTest','guardtest.BridgeMixinTest','guardtest.TacticalServerMixinTest','guardtest.TacticalClientMixinTest','guardtest.ClientAppearanceMixinTest','guardtest.ClientAppearanceRuntimeTest']:
        result=subprocess.run([java,'-ea','-cp',str(tests)+os.pathsep+cp,test],capture_output=True,text=True)
        (ROOT/'build'/(test+'.log')).write_text(result.stdout+result.stderr)
        print(result.stdout,end='');print(result.stderr,end='')
        if result.returncode:raise SystemExit(result.returncode)

if __name__=='__main__':main()
