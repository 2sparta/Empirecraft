#!/usr/bin/env python3
"""Extend the installed/pinned TRMT compat JAR with chunk-persistent original soils."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path
from soil_assets import generate
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
VERSION='0.5.1b-1.20.1-forge-empirecraft.3'

def jar(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            i=zipfile.ZipInfo(name,(2026,11,20,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
    path.with_suffix('.jar.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def read(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def main():
    p=argparse.ArgumentParser();p.add_argument('--base',required=True,type=Path);p.add_argument('--reference-repo',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);p.add_argument('--test',action='store_true');a=p.parse_args()
    pinned=json.loads((ROOT/'remembered-input.json').read_text())
    assert hashlib.sha256(a.base.read_bytes()).hexdigest()==pinned['sha256'],'Changed TRMT baseline'
    b=ROOT/'build/remembered';b.mkdir(parents=True,exist_ok=True)
    libs=sorted((a.reference_repo/'server/libraries').rglob('*.jar'));asm=sorted((a.reference_repo/'server/libraries/org/ow2/asm').rglob('*.jar'))
    java=a.jdk/'bin/java';javac=a.jdk/'bin/javac'
    def run(*cmd):subprocess.run(list(map(str,cmd)),check=True)
    cp=[a.sdk/'runtime-named.jar',a.sdk/'client-named.jar',a.sdk/'client-original.jar',*libs]
    remapcp=os.pathsep.join(map(str,[a.sdk/'tiny-remapper.jar',a.sdk/'mapping-io.jar',*asm]))
    def remap(source,target,frm,to):
        target.unlink(missing_ok=True)
        run(java,'-cp',remapcp,'dev.architectury.tinyremapper.Main',source,target,a.sdk/'named-srg.tiny',frm,to,*cp,'--ignoreConflicts')
    remap(a.base,b/'base-named.jar','srg','named');cp.insert(2,b/'base-named.jar')
    classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    run(javac,'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'remembered').rglob('*.java')))
    jar(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    remap(b/'named.jar',b/'srg.jar','named','srg')
    tools=b/'tools';tools.mkdir(exist_ok=True)
    run(javac,'--release','17','-cp',os.pathsep.join(map(str,asm)),'-d',tools,ROOT/'tools/PatchRemembered.java')
    files=read(a.base)
    for cls in ('ServerPlayerEntityMixin','MobEntityMixin'):
        name='milkucha/trmt/mixin/'+cls+'.class';(b/'in.class').write_bytes(files[name]);run(java,'-cp',os.pathsep.join(map(str,[tools,*asm])),'PatchRemembered',b/'in.class',b/'out.class');files[name]=(b/'out.class').read_bytes()
    # Remove old aliasing: native adjacency must never flatten a regional soil.
    raw=b/'raw';raw.mkdir(exist_ok=True)
    run(javac,'--release','17','-proc:none','-cp',os.pathsep.join(map(str,libs)),'-d',raw,ROOT/'identity/ua/empirecraft/trmt/Surfaces.java')
    files.update({p.relative_to(raw).as_posix():p.read_bytes() for p in raw.rglob('*.class')})
    files.update(read(b/'srg.jar'));files.update(generate(REPO/'mods'))
    meta=files['META-INF/mods.toml'].decode().replace('0.5.1b-1.20.1-forge-empirecraft.1',VERSION)
    for mod in ('tfc','empirecraft_tfc'):
        meta+=f'\n[[dependencies.trmt]]\nmodId="{mod}"\nmandatory=true\nversionRange="[0,)"\nordering="AFTER"\nside="BOTH"\n'
    files['META-INF/mods.toml']=meta.encode()
    out=ROOT/'dist'/f'trmt-{VERSION}.jar';jar(out,files);print(out)
    if a.test:
        tests=b/'tests';shutil.rmtree(tests,ignore_errors=True);tests.mkdir()
        run(javac,'--release','17','-proc:none','-cp',os.pathsep.join(map(str,[b/'named.jar',*cp])),'-d',tests,*sorted((ROOT/'tests/remembered').rglob('*.java')))
        jar(b/'test-named.jar',{p.relative_to(tests).as_posix():p.read_bytes() for p in tests.rglob('*.class')});remap(b/'test-named.jar',b/'test-srg.jar','named','srg')
        probe=read(b/'test-srg.jar');probe['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_trmt_test"\nversion="2"\ndisplayName="TEST ONLY Soil memory"\n'
        jar(b/'empirecraft-trmt-test.jar',probe)
if __name__=='__main__':main()
