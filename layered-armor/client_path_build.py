#!/usr/bin/env python3
"""Build only the client resource finder against pinned game APIs; keep all other class bytes."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import build

ROOT=build.ROOT;REPO=build.REPO
def main():
    p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    out=ROOT/'build/client-path';out.mkdir(parents=True,exist_ok=True)
    baseline=REPO/'mods/empirecraft-layered-armor-1.0.3.jar'
    if not baseline.exists():baseline=next((REPO/'disabled-mods').rglob(baseline.name))
    with zipfile.ZipFile(baseline) as z:before={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    classes={n:b for n,b in before.items() if n.endswith('.class')};input=out/'baseline-srg.jar';build.archive(input,classes)
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    java=a.java_home/'bin/java';javac=a.java_home/'bin/javac';sdk=a.sdk.resolve()
    remapper=os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]))
    def remap(source,target,from_name,to_name,cp):
        target.unlink(missing_ok=True)
        subprocess.run([str(java),'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(source),str(target),str(sdk/'named-srg.tiny'),from_name,to_name,*map(str,cp),'--ignoreConflicts'],check=True)
    named=out/'baseline-named.jar';cp=[sdk/'runtime-named.jar',sdk/'client-named.jar',*libs]
    remap(input,named,'srg','named',[sdk/'runtime-srg.jar',*libs])
    folder=out/'classes';shutil.rmtree(folder,ignore_errors=True);folder.mkdir()
    subprocess.run([str(javac),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[named,*cp])),'-d',str(folder),str(ROOT/'src/main/java/ua/empirecraft/armor/client/ArmorLanguages.java')],check=True)
    patch=out/'patch-named.jar';build.archive(patch,{f.relative_to(folder).as_posix():f.read_bytes() for f in folder.rglob('*.class')})
    srg=out/'patch-srg.jar';remap(patch,srg,'named','srg',[named,*cp])
    with zipfile.ZipFile(srg) as z:after={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    assert set(after)=={'ua/empirecraft/armor/client/ArmorLanguages.class'}
    files=dict(before);files.update(after);files['META-INF/mods.toml']=(ROOT/'src/main/resources/META-INF/mods.toml').read_bytes()
    candidate=ROOT/'dist'/build.NAME;build.archive(candidate,files);candidate.with_suffix('.jar.sha256').write_text(build.sha(candidate)+'  '+candidate.name+'\n')
    changed=[n for n,b in classes.items() if files[n]!=b];assert changed==['ua/empirecraft/armor/client/ArmorLanguages.class']
    with zipfile.ZipFile(named) as z:test_named={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    test_named.update({n:b for n,b in files.items() if not n.endswith('.class')})
    test_named.update({f.relative_to(folder).as_posix():f.read_bytes() for f in folder.rglob('*.class')});build.archive(out/'candidate-named.jar',test_named)
    (ROOT/'audit').mkdir(exist_ok=True)
    (ROOT/'audit/client-path-build.json').write_text(json.dumps(dict(file=candidate.name,sha256=build.sha(candidate),baseline=baseline.name,baseline_sha256=build.sha(baseline),changed_classes=changed,unchanged_classes=len(classes)-1,server_classes_unchanged=True,
        sdk_sha256={n:build.sha(sdk/n) for n in ['runtime-named.jar','client-named.jar','named-srg.tiny']}),indent=2)+'\n')
    (out/'classpath.txt').write_text(os.pathsep.join(map(str,cp)))
    print('ARMOR_CLIENT_PATH_BUILD_PASS',candidate.name,build.sha(candidate),'unchanged_classes='+str(len(classes)-1))
if __name__=='__main__':main()
