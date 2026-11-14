#!/usr/bin/env python3
"""Test the exact runtime repair in a copied generated world, never a production world."""
import argparse,json,os,shutil,subprocess,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO
def main():
    p=argparse.ArgumentParser();p.add_argument('--fixture',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--work',type=Path,default=REPO.parent/'work/runtime-fixes-native');a=p.parse_args()
    fixture=a.fixture.resolve();work=a.work.resolve();assert str(fixture).startswith('/workspace/work/') and (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file()
    assert str(work).startswith('/workspace/work/');work.mkdir(parents=True,exist_ok=True);runtime=work/'runtime';assert not runtime.exists()
    shutil.copytree(fixture,runtime,symlinks=True)
    for old in (runtime/'mods').glob('empirecraft-handbook-probe-TEST-ONLY.jar'):old.unlink()
    for old in (runtime/'mods').glob('empirecraft-pack-fixes-*.jar'):old.unlink()
    candidate=ROOT/'dist'/build.NAME;installed=runtime/'mods'/candidate.name;shutil.copyfile(candidate,installed)
    tested_sha256=build.sha(installed)
    classes=work/'classes';classes.mkdir();libs=sorted((REPO/'server/libraries').rglob('*.jar'));java=a.java_home/'bin/java'
    cp=[a.sdk/'runtime-named.jar',*libs]
    subprocess.run([str(a.java_home/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'tests/RuntimeNativeProbe.java')],check=True)
    named=work/'probe-named.jar';srg=work/'probe-srg.jar';build.archive(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[a.sdk/'tiny-remapper.jar',a.sdk/'mapping-io.jar',*asm]
    subprocess.run([str(java),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(named),str(srg),str(a.sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(srg,'a') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_runtime_probe"\nversion="1.0.0"\n')
    shutil.copyfile(srg,runtime/'mods/empirecraft-runtime-probe-TEST-ONLY.jar')
    log=work/'native.log'
    with log.open('w') as out:run=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=out,stderr=subprocess.STDOUT,timeout=600)
    raw=log.read_text(errors='replace');markers=[line for line in raw.splitlines() if 'RUNTIME_NATIVE_' in line];print('\n'.join(markers))
    assert run.returncode==0 and any('RUNTIME_NATIVE_PASS' in line for line in markers) and 'RUNTIME_NATIVE_FAIL' not in raw,str(log)
    assert build.sha(installed)==tested_sha256,'Test runtime JAR changed during the run'
    report={'status':'passed','file':candidate.name,'sha256':tested_sha256,'markers':markers,'native_chunk_serialization':True,'native_missing_attribute_mounts':True,'bukkit_jump_strength_getter':True,'runtime':'Mohist / Forge 47.4.23 / Java 21','production_world_opened':False,'graphical_client_tested':False}
    (ROOT/'audit/runtime-native.json').write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
