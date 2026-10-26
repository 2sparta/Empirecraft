#!/usr/bin/env python3
"""Build the server-only seasonal leaf addon using the pinned Sails build toolchain."""
import argparse,hashlib,json,os,shlex,shutil,subprocess,sys,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
SHARED=Path(os.environ.get('EMPIRECRAFT_TOOLCHAIN',str(ROOT/'build/toolchain')))
VERSION='1.0.0'
def run(args):subprocess.run(list(map(str,args)),check=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def jar(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for n,data in sorted(entries.items()):
            info=zipfile.ZipInfo(n,(2026,10,26,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--server-home',type=Path,default=REPO/'server');ap.add_argument('--prepare-fixture',action='store_true');args=ap.parse_args()
    if not (SHARED/'javac.args').exists():run([sys.executable,ROOT/'toolchain.py',SHARED,args.server_home])
    build=ROOT/'build';build.mkdir(exist_ok=True)
    pinned=json.loads((ROOT/'build-inputs.json').read_text())
    for name,item in pinned.items():
        p=build/name;cached=args.server_home/'mods'/item['file']
        if not cached.exists():cached=REPO/'server/mods'/item['file']
        if not p.exists() or sha(p)!=item['sha256']:
            if cached.exists() and sha(cached)==item['sha256']:shutil.copyfile(cached,p)
            else:
                with urllib.request.urlopen(item['url'],timeout=90) as r:data=r.read()
                assert hashlib.sha256(data).hexdigest()==item['sha256'];p.write_bytes(data)
        assert sha(p)==item['sha256'] and p.stat().st_size==item['size']
    flags=shlex.split((SHARED/'javac.args').read_text());deps=flags[flags.index('-cp')+1].split(os.pathsep)
    asm=[p for p in deps if Path(p).name.startswith('asm')]
    remapper=os.pathsep.join(map(str,[SHARED/'tiny-remapper.jar',SHARED/'mapping-io.jar',*asm]))
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else 'java'
    compiler=[shutil.which('javac')] if shutil.which('javac') else [java,'com.sun.tools.javac.Main']
    runtime=SHARED/'runtime-srg.jar'
    if not zipfile.is_zipfile(runtime):
        runtime=build/'runtime-srg.jar'
        if not zipfile.is_zipfile(runtime):run([java,'-cp',remapper,'dev.architectury.tinyremapper.Main',SHARED/'runtime-named.jar',runtime,SHARED/'named-srg.tiny','named','srg','--ignoreConflicts'])
    modentries={}
    for n in pinned:
        with zipfile.ZipFile(build/n) as z:modentries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    jar(build/'mods-srg.jar',modentries)
    run([java,'-cp',remapper,'dev.architectury.tinyremapper.Main',build/'mods-srg.jar',build/'mods-named.jar',SHARED/'named-srg.tiny','srg','named',runtime,'--ignoreConflicts'])
    cp=os.pathsep.join([str(build/'patch-named.jar'),str(build/'mods-named.jar'),*deps])
    classes=build/('fixture-classes' if args.prepare_fixture else 'classes');shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    sources=sorted((ROOT/('tests' if args.prepare_fixture else 'src/main/java')).rglob('*.java'))
    if args.prepare_fixture:sources=[p for p in sources if p.name=='LeavesSmokeMod.java']
    values=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',classes,*sources]
    argfile=build/'javac.args';argfile.write_text('\n'.join('"'+str(x).replace('\\','\\\\').replace('"','\\"')+'"' for x in values))
    run([*compiler,'@'+str(argfile)])
    jar(build/'patch-named.jar',{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    (build/'patch-srg.jar').unlink(missing_ok=True)
    run([java,'-cp',remapper,'dev.architectury.tinyremapper.Main',build/'patch-named.jar',build/'patch-srg.jar',SHARED/'named-srg.tiny','named','srg',build/'mods-named.jar',SHARED/'runtime-named.jar',SHARED/'client-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(build/'patch-srg.jar') as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    if args.prepare_fixture:
        entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_leaves_test"\nversion="1.0.0"\ndisplayName="TEST ONLY seasonal leaves fixture"\n'
        entries['pack.mcmeta']=b'{"pack":{"pack_format":15,"description":"TEST ONLY leaf fixture"}}'
        entries['data/empirecraft_seasonal_leaves/tags/blocks/additional_leaves.json']=b'{"replace":false,"values":["empirecraft_leaves_test:test_leaves"]}'
        output=build/'empirecraft-leaves-test.jar'
    else:
        resources=ROOT/'src/main/resources';entries.update({str(p.relative_to(resources)):p.read_bytes() for p in resources.rglob('*') if p.is_file()}); entries['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
        output=build/f'empirecraft-seasonal-leaves-{VERSION}.jar'
    jar(output,entries);print(output.name,sha(output))
    if not args.prepare_fixture:
        for folder in [REPO/'server/mods']:
            folder.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(output,folder/output.name);(folder/(output.name+'.sha256')).write_text(sha(output)+'  '+output.name+'\n')
if __name__=='__main__':main()
