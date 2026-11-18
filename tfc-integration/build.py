#!/usr/bin/env python3
"""Reproducible staged build. Installation is a separate, explicit operation."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path
import resources

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
VERSION='1.0.5'

def pack(path, entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100644<<16;z.writestr(info,raw)
    path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')

def compile(sources, classes, cp, javac):
    classes.mkdir(parents=True,exist_ok=True)
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sources)]
    argfile=classes.parent/(classes.name+'.args')
    argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args)+'\n')
    subprocess.run([str(javac),'@'+str(argfile)],check=True)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tfc',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True)
    p.add_argument('--java-home',type=Path,required=True);p.add_argument('--fixture',action='store_true')
    a=p.parse_args();build=ROOT/'build';build.mkdir(exist_ok=True)
    java=a.java_home/'bin/java';javac=a.java_home/'bin/javac'
    decisions=json.loads((ROOT/'decisions.json').read_text())
    assert hashlib.sha256(a.tfc.read_bytes()).hexdigest()==decisions['upstream_sha256']
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    compile(sorted((ROOT/'tools').glob('*.java')),build/'patcher',asm,javac)
    patched=build/'tfc-native.jar'
    subprocess.run([str(java),'-cp',os.pathsep.join(map(str,[build/'patcher',*asm])),'NativePatch',str(a.tfc),str(patched),str(build/'native-methods.txt')],check=True)
    files,report=resources.transform(patched,decisions)
    tfc_output=build/'libs/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.1.jar'
    pack(tfc_output,files)
    (ROOT/'audit').mkdir(exist_ok=True)
    (ROOT/'audit/resource-changes.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    # Compile against named game APIs and class-only remaps of the exact installed dependencies.
    sdk=a.sdk.resolve();named=build/'named';named.mkdir(exist_ok=True)
    remapper=os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]))
    deps=[a.tfc]
    patterns=['create-1.*.jar','naturalist-*.jar','DragNs_*.jar','legendarysurvivaloverhaul-*.jar','SereneSeasons-*.jar']
    deps += [next((REPO/'mods').glob(pattern)) for pattern in patterns]
    mapped=[]
    for dep in deps:
        dest=named/dep.name
        pin=dest.with_suffix('.input-sha256');digest=hashlib.sha256(dep.read_bytes()).hexdigest()
        if not dest.exists() or not pin.exists() or pin.read_text()!=digest:
            source=named/'input.jar'
            with zipfile.ZipFile(dep) as z:pack(source,{n:z.read(n) for n in z.namelist() if n.endswith('.class')})
            dest.unlink(missing_ok=True)
            subprocess.run([str(java),'-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(source),str(dest),str(sdk/'named-srg.tiny'),'srg','named',str(sdk/'runtime-srg.jar'),'--ignoreConflicts'],check=True)
            pin.write_text(digest)
        mapped.append(dest)
    access=build/'compile-access.jar'
    subprocess.run([str(java),'-cp',os.pathsep.join(map(str,[build/'patcher',*asm])),'CompileAccess',str(sdk/'runtime-named.jar'),str(access)],check=True)
    nested=build/'nested';nested.mkdir(exist_ok=True)
    for dep in (REPO/'mods').glob('*.jar'):
        with zipfile.ZipFile(dep) as z:
            for name in z.namelist():
                if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    target=nested/Path(name).name
                    if not target.exists():target.write_bytes(z.read(name))
    cp=[access,sdk/'runtime-named.jar',sdk/'client-named.jar',*mapped,*libs,*sorted(nested.glob('*.jar')),*sorted((REPO/'mods').glob('*.jar'))]
    if a.fixture:cp+=sorted((REPO/'server/mods').glob('empirecraft-world-rules-*.jar'))
    kind='fixture' if a.fixture else 'main';classes=build/(kind+'-classes')
    if classes.exists():shutil.rmtree(classes)
    sources=sorted((ROOT/('src/test/java' if a.fixture else 'src/main/java')).rglob('*.java'))
    compile(sources,classes,[build/'main-classes',*cp],javac)
    named_jar=build/(kind+'-named.jar');srg=build/(kind+'-srg.jar')
    entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    pack(named_jar,entries);srg.unlink(missing_ok=True)
    subprocess.run([str(java),'-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(named_jar),str(srg),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    if a.fixture:
        entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="EUPL-1.2"\n[[mods]]\nmodId="empirecraft_tfc_test"\nversion="1.0.0"\ndisplayName="TEST ONLY TFC integration"\n'
    else:
        res=ROOT/'src/main/resources'
        entries.update({f.relative_to(res).as_posix():f.read_bytes() for f in res.rglob('*') if f.is_file()})
        entries.update(resources.generate(decisions,a.tfc))
        entries['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
        entries['META-INF/MANIFEST.MF']=f'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_tfc.mixins.json\r\nImplementation-Version: {VERSION}\r\n\r\n'.encode()
    target=build/'libs'/('empirecraft-tfc-test.jar' if a.fixture else 'empirecraft-tfc-integration-'+VERSION+'.jar')
    pack(target,entries)
    print('Staged:',target)

if __name__=='__main__':main()
