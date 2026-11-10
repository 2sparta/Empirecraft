#!/usr/bin/env python3
"""Transform actual Minecraft client/server classes with all production armor Mixins."""
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
    flags=shlex.split((build.SDK/'javac.args').read_text())
    namedcp=os.pathsep.join(map(str,[build.BUILD/'mod-classes',build.SDK/'runtime-named.jar',build.SDK/'client-named.jar']))+os.pathsep+flags[flags.index('-cp')+1]
    run([javac,'--release','17','-proc:none','-encoding','UTF-8','-cp',namedcp,'-d',folder,root/'tests/ArmorLanguageTest.java'],build.BUILD/'language-compile.log')
    run([java,'-ea','-cp',str(folder)+os.pathsep+namedcp,'ArmorLanguageTest',root/'dist'/build.NAME],build.BUILD/'language.log')
    api=build.BUILD/'client-api-srg.jar'
    inputs=[build.SDK/'client-named.jar',build.SDK/'named-srg.tiny',build.SDK/'runtime-named.jar']
    import hashlib,json
    identity={path.name:hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}
    stamp=build.BUILD/'client-api-inputs.json'
    if not api.exists() or not stamp.exists() or json.loads(stamp.read_text())!=identity:
        asm=sorted((repo/'server/libraries/org/ow2/asm').rglob('*.jar'))
        cp=os.pathsep.join(map(str,[build.SDK/'tiny-remapper.jar',build.SDK/'mapping-io.jar',*asm]))
        run([java,'-Xmx1G','-cp',cp,'dev.architectury.tinyremapper.Main',inputs[0],api,inputs[1],'named','srg',inputs[2],'--ignoreConflicts'],build.BUILD/'client-api.log')
        stamp.write_text(json.dumps(identity,indent=2)+'\n')
    jars=sorted(set((repo/'server').rglob('*.jar'))|set((build.SDK/'nested').glob('*.jar')))
    jars.sort(key=lambda p:(0 if '/org/ow2/asm/' in str(p) and '/9.7/' in str(p) else 1 if 'mixinextras' in p.name and ('0.4' in p.name or '0.5' in p.name) else 2 if p.name=='forge-1.20.1-47.4.23-server.jar' else 3,str(p)))
    cp=os.pathsep.join(map(str,[root/'dist'/build.NAME,api,*jars]))
    harness=repo/'seasonal-leaves/tests/guardtest'
    run([javac,'--release','17','-proc:none','-cp',cp,'-d',folder,harness/'OfflineMixinService.java',harness/'OfflineProperties.java',root/'tests/guardtest/ArmorMixinTest.java'],build.BUILD/'mixin-compile.log')
    services=folder/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    run([java,'-ea','-cp',str(folder)+os.pathsep+cp,'guardtest.ArmorMixinTest'],build.BUILD/'mixins.log')
if __name__=='__main__':main()
