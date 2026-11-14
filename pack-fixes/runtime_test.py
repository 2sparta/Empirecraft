#!/usr/bin/env python3
"""Verify traversal equivalence, offline exact Mixin targets, and Ponder content policy."""
import argparse,json,os,re,subprocess,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO
def main():
    p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    work=ROOT/'build/runtime-tests';classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));java=a.java_home/'bin/java';javac=a.java_home/'bin/javac'
    cp=[ROOT/'build/runtime/additions-named.jar',a.sdk/'runtime-named.jar',a.sdk/'client-named.jar',ROOT/'dist'/build.NAME,*libs]
    subprocess.run([str(javac),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'tests/PaletteEntriesTest.java'),str(ROOT/'tests/PonderSafetyTest.java'),str(ROOT/'tests/JeiInvocationsTest.java'),str(ROOT/'tests/TickStallReporterTest.java')],check=True)
    markers=[]
    for name,extra in [('PaletteEntriesTest',[]),('PonderSafetyTest',[ROOT/'audit/ponder-safety.json']),('JeiInvocationsTest',[]),('TickStallReporterTest',[])]:
        run=subprocess.run([str(java),'-ea','-cp',os.pathsep.join(map(str,[classes,*cp])),name,*map(str,extra)],capture_output=True,text=True);(work/(name+'.log')).write_text(run.stdout+run.stderr)
        if run.returncode:print((run.stdout+run.stderr)[-5000:]);raise SystemExit(name+' failed')
        selected=[line for line in run.stdout.splitlines() if '_PASS' in line];print('\n'.join(selected));markers.extend(selected)
    jars=sorted(set((REPO/'server').rglob('*.jar'))|set((REPO/'tfc-integration/build/nested').glob('*.jar')))
    jars.sort(key=lambda p:(0 if '/org/ow2/asm/' in str(p) and '/9.7/' in str(p) else 1 if 'mixinextras' in p.name and ('0.4' in p.name or '0.5' in p.name) else 2 if p.name=='forge-1.20.1-47.4.23-server.jar' else 3,str(p)))
    mixinextras=work/'MixinExtras-0.4.1.jar'
    with zipfile.ZipFile(REPO/'tfc-integration/build/nested/mixinextras-forge-0.4.1.jar') as z:mixinextras.write_bytes(z.read('META-INF/jars/MixinExtras-0.4.1.jar'))
    srgcp=[ROOT/'dist'/build.NAME,mixinextras,*jars];harness=REPO/'seasonal-leaves/tests/guardtest'
    subprocess.run([str(javac),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,srgcp)),'-d',str(classes),str(harness/'OfflineMixinService.java'),str(harness/'OfflineProperties.java'),str(ROOT/'tests/guardtest/RuntimeMixinTest.java'),str(ROOT/'tests/guardtest/PackMixinTest.java')],check=True)
    services=classes/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n');(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    for name in ['guardtest.RuntimeMixinTest','guardtest.PackMixinTest']:
        run=subprocess.run([str(java),'-ea','-cp',os.pathsep.join(map(str,[classes,*srgcp])),name],capture_output=True,text=True);(work/(name+'.log')).write_text(run.stdout+run.stderr)
        if run.returncode:print((run.stdout+run.stderr)[-7000:]);raise SystemExit(name+' failed')
        selected=[line for line in run.stdout.splitlines() if 'MIXIN_PASS' in line];print('\n'.join(selected));markers+=selected
    report={'status':'passed','file':build.NAME,'sha256':build.sha(ROOT/'dist'/build.NAME),'markers':markers,'palette_equivalence_and_sparse_fallback':True,'palette_benchmark_fixture':{'entries':64,'initial_capacity':8192,'iterations_per_round':12000,'rounds':7},'actual_mixin_targets':9,'graphical_client_tested':False}
    (ROOT/'audit/runtime-tests.json').write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
