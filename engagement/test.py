#!/usr/bin/env python3
"""Execute real policies/NBT plus exact installed-JAR Mixin/ASM regression checks."""
import os,shlex,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def run(args,log):
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True);log.write_text(result.stdout+result.stderr);print(result.stdout,end='');print(result.stderr,end='')
    if result.returncode:raise SystemExit(result.returncode)
def main():
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac');build=ROOT/'build';tests=build/'test-classes';shutil.rmtree(tests,ignore_errors=True);tests.mkdir()
    args=shlex.split((build/'javac.args').read_text());namedcp=str(build/'classes')+os.pathsep+args[args.index('-cp')+1]
    sources=list((ROOT/'src/test/java/ua').rglob('*.java'));run([javac,'--release','17','-proc:none','-cp',namedcp,'-d',tests,*sources],build/'test-compile.log')
    for name in ['ua.empirecraft.engagement.CombatPolicyTest','ua.empirecraft.engagement.CombatLedgerTest','ua.empirecraft.engagement.RamMotionTest']:run([java,'-ea','-cp',str(tests)+os.pathsep+namedcp,name],build/(name+'.log'))
    jars=sorted(set((REPO/'server').rglob('*.jar'))|set((REPO/'sails/build/nested').glob('*.jar'))|set((REPO/'ship-cannons/build/nested').glob('*.jar')))
    jars.sort(key=lambda p:(0 if '/org/ow2/asm/' in str(p) and '/9.7/' in str(p) else 1 if 'mixinextras' in p.name and ('0.4' in p.name or '0.5' in p.name) else 2 if p.name=='forge-1.20.1-47.4.23-server.jar' else 3,str(p)))
    cp=os.pathsep.join(map(str,[ROOT/'dist/empirecraft-engagement-1.1.1.jar',ROOT/'src/main/resources',*jars]))
    run([javac,'--release','17','-proc:none','-cp',cp,'-d',tests,*sorted((ROOT/'src/test/java/guardtest').glob('*.java'))],build/'mixin-compile.log')
    services=tests/'META-INF/services';services.mkdir(parents=True,exist_ok=True);(services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n');(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    run([java,'-ea','-cp',str(tests)+os.pathsep+cp,'guardtest.EngagementMixinTest'],build/'mixins.log')
if __name__=='__main__':main()
