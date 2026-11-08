#!/usr/bin/env python3
"""Verify wind orientation, automatic resource loading and the exact installed flag Mixins."""
import os,shlex,subprocess
from pathlib import Path
import build

def run(args,log):
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True)
    log.write_text(result.stdout+result.stderr);print(result.stdout,end='');print(result.stderr,end='')
    if result.returncode:raise SystemExit(result.returncode)
def main():
    root=build.ROOT;repo=build.REPO;folder=build.BUILD/'test-classes';folder.mkdir(parents=True,exist_ok=True)
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac')
    flags=shlex.split((build.SDK/'javac.args').read_text());namedcp=os.pathsep.join([str(build.BUILD/'mod-classes'),flags[flags.index('-cp')+1]])
    run([javac,'--release','17','-proc:none','-encoding','UTF-8','-cp',namedcp,'-d',folder,root/'tests/FlagWindTest.java',root/'tests/TranslationPackTest.java'],build.BUILD/'test-compile.log')
    for name in ['FlagWindTest','TranslationPackTest']:
        extra=[root/'dist'/build.NAME] if name=='TranslationPackTest' else []
        run([java,'-ea','-cp',str(folder)+os.pathsep+namedcp,name,*extra],build.BUILD/(name+'.log'))
    jars=sorted(set((repo/'server').rglob('*.jar'))|set((build.SDK/'nested').glob('*.jar')))
    jars.sort(key=lambda p:(0 if '/org/ow2/asm/' in str(p) and '/9.7/' in str(p) else 1 if 'mixinextras' in p.name and ('0.4' in p.name or '0.5' in p.name) else 2 if p.name=='forge-1.20.1-47.4.23-server.jar' else 3,str(p)))
    cp=os.pathsep.join(map(str,[root/'dist'/build.NAME,*jars]))
    harness=repo/'seasonal-leaves/tests/guardtest'
    run([javac,'--release','17','-proc:none','-cp',cp,'-d',folder,harness/'OfflineMixinService.java',harness/'OfflineProperties.java',root/'tests/guardtest/PackMixinTest.java'],build.BUILD/'mixin-compile.log')
    services=folder/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    run([java,'-ea','-cp',str(folder)+os.pathsep+cp,'guardtest.PackMixinTest'],build.BUILD/'mixins.log')
if __name__=='__main__':main()
