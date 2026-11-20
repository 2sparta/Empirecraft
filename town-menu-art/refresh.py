#!/usr/bin/env python3
"""Patch only menu controls into the pinned current Feudal plugin. No client UI overlay."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
BASE_SHA='f2d04bad4929ef1ded7f1f9e01ba6c5d182150f1c0a15a734da612b8e43dcd4a'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):
    with zipfile.ZipFile(p) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def jar(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            i=zipfile.ZipInfo(name,(2026,11,20,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
    path.with_suffix('.jar.sha256').write_text(sha(path)+'  '+path.name+'\n')
def main():
    p=argparse.ArgumentParser();p.add_argument('--reference-repo',required=True,type=Path);p.add_argument('--jdk17',required=True,type=Path);p.add_argument('--base',required=True,type=Path);a=p.parse_args()
    assert sha(a.base)==BASE_SHA,'Changed Feudal baseline'
    b=ROOT/'build/refresh';b.mkdir(parents=True,exist_ok=True)
    libs=sorted((a.reference_repo/'server/libraries').rglob('*.jar'));plugins=sorted((a.reference_repo/'server/plugins').glob('*.jar'))
    forge=next(p for p in libs if p.name=='forge-1.20.1-47.4.23-universal.jar')
    api=b/'bukkit-api.jar';jar(api,{n:v for n,v in read(forge).items() if n.startswith('org/bukkit/') and not n.startswith('org/bukkit/craftbukkit/') and n.endswith('.class')})
    classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    names=['TownMenu','ProvisionController','ContractMenus','MenuSkin','MenuIcons']
    cp=[api,a.base,*plugins,*libs]
    cmd=['java','com.sun.tools.javac.Main','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*[REPO/f'feudal/src/main/java/ua/empirecraft/feudal/{n}.java' for n in names]]
    # Bukkit/Towny may be Java21, so use the current compiler with the Java17 platform.
    cmd[2:4]=['-source','17','-target','17','--system',a.jdk17]
    subprocess.run(list(map(str,cmd)),check=True)
    tests=b/'tests';tests.mkdir(exist_ok=True)
    compile_test=['java','com.sun.tools.javac.Main','-source','17','-target','17','--system',a.jdk17,'-proc:none','-cp',os.pathsep.join(map(str,[classes,*cp])),'-d',tests,ROOT/'tests/MenuSkinTests.java']
    subprocess.run(list(map(str,compile_test)),check=True)
    subprocess.run(['java','-ea','-cp',os.pathsep.join(map(str,[tests,classes,*cp])),'ua.empirecraft.feudal.MenuSkinTests'],check=True)
    old=read(a.base);new=dict(old)
    def controlled(n):return any(n==f'ua/empirecraft/feudal/{stem}.class' or n.startswith(f'ua/empirecraft/feudal/{stem}$') for stem in names)
    for name in list(new):
        if controlled(name):del new[name]
    patch={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    assert all(controlled(n) for n in patch)
    new.update(patch);new['plugin.yml']=(REPO/'feudal/src/main/resources/plugin.yml').read_bytes()
    assert b'version: 1.20.2\n' in new['plugin.yml']
    unchanged=[n for n in old if not controlled(n) and n!='plugin.yml'];assert all(new[n]==old[n] for n in unchanged)
    out=ROOT/'dist/empirecraft-feudal-1.20.2.jar';jar(out,new)
    (ROOT/'audit').mkdir(exist_ok=True)
    (ROOT/'audit/refresh-build.json').write_text(json.dumps(dict(baseline_sha256=BASE_SHA,output_sha256=sha(out),unchanged_entries=len(unchanged),patched_classes=sorted(patch)),indent=2)+'\n');print(out)
if __name__=='__main__':main()
