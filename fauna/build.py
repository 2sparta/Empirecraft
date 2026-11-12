#!/usr/bin/env python3
"""Offline named/SRG build using the pinned SDK produced by sails/build.py.

Normal CI can also use ./gradlew test build exportDefaults smokeJar.
This fallback compiles the same source against the actual installed Mohist/Forge API.
"""
import argparse,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent; REPO=ROOT.parent
VERSION='1.2.0'
def pack(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as jar:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;jar.writestr(info,raw)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--junit',type=Path,required=True);p.add_argument('--smoke-only',action='store_true',help='Rebuild only the test fixture against existing main classes');a=p.parse_args()
    sdk=a.sdk.resolve();junit=a.junit.resolve();java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac')
    libs=sorted((REPO/'server/libraries').rglob('*.jar')); calendar=next((REPO/'mods').glob('empirecraft-calendar-*.jar'))
    cp=[sdk/'runtime-named.jar',sdk/'client-named.jar',sdk/'annotations.jar',calendar,*libs]
    for path in [*cp[:4],sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',sdk/'named-srg.tiny',junit]:assert path.is_file(),path
    build=ROOT/'build';build.mkdir(exist_ok=True)
    main_classes=build/'classes/main'
    if a.smoke_only:assert main_classes.is_dir(),'Build the main addon first'
    for kind in (['smoke'] if a.smoke_only else ['main','test','smoke']):
        classes=build/'classes'/kind
        if classes.exists():shutil.rmtree(classes)
        classes.mkdir(parents=True)
        paths=[main_classes,junit,*cp] if kind!='main' else cp
        sources=sorted((ROOT/f'src/{kind}/java').rglob('*.java'))
        args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,paths)),'-d',str(classes),*map(str,sources)]
        argfile=build/f'javac-{kind}.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args)+'\n')
        subprocess.run([javac,'@'+str(argfile)],check=True)
        if kind=='test':
            reports=build/'test-results/test';reports.mkdir(parents=True,exist_ok=True)
            subprocess.run([java,'-cp',os.pathsep.join(map(str,[classes,main_classes,ROOT/'src/main/resources',junit,*cp])),'org.junit.platform.console.ConsoleLauncher','execute','--select-package','ua.empirecraft.fauna','--disable-banner','--details=summary','--reports-dir',reports],check=True)
            continue
        resources=ROOT/f'src/{kind}/resources';files={str(f.relative_to(classes)):f.read_bytes() for f in classes.rglob('*.class')}
        files.update({str(f.relative_to(resources)):f.read_bytes() for f in resources.rglob('*') if f.is_file()})
        if kind=='main':files['META-INF/MANIFEST.MF']=f'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft-fauna.mixins.json\r\nImplementation-Version: {VERSION}\r\n\r\n'.encode()
        named=build/f'{kind}-named.jar';remapped=build/f'{kind}-srg.jar';pack(named,files);remapped.unlink(missing_ok=True)
        subprocess.run([java,'-Xmx2G','-cp',os.pathsep.join(map(str,[*sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'),reverse=True),sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*libs])),'dev.architectury.tinyremapper.Main',named,remapped,sdk/'named-srg.tiny','named','srg',*cp,main_classes,'--ignoreConflicts'],check=True)
        with zipfile.ZipFile(remapped) as jar:files={n:jar.read(n) for n in jar.namelist() if not n.endswith('/')}
        pack(build/'libs'/('empirecraft-fauna-'+(VERSION if kind=='main' else 'smoke')+'.jar'),files)
    subprocess.run([java,'-cp',os.pathsep.join(map(str,[main_classes,*cp])),'ua.empirecraft.fauna.Settings',build/'defaults/empirecraft-fauna.json'],check=True)
if __name__=='__main__':main()
