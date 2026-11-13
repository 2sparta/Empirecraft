#!/usr/bin/env python3
"""Compile the layered armor mod against the exact pinned client and server API."""
import hashlib,json,os,shlex,shutil,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=REPO/'work/armor/build';SDK=Path(os.environ.get('EMPIRECRAFT_ARMOR_SDK',str(REPO/'work/armor/sdk')))
NAME='empirecraft-layered-armor-1.0.4.jar'
MIXIN_CONFIG='empirecraft_armor.mixins.json'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def archive(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
def main():
    BUILD.mkdir(parents=True,exist_ok=True);classes=BUILD/'mod-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    flags=shlex.split((SDK/'javac.args').read_text());sdkcp=flags[flags.index('-cp')+1].split(os.pathsep)
    cp=list(dict.fromkeys(map(str,[SDK/'runtime-named.jar',SDK/'client-named.jar',REPO/'mods/empirecraft-engagement-1.1.1.jar',next((REPO/'mods').glob('vs_sails-*.jar')),*sdkcp])))
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(cp),'-d',str(classes),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))]
    argfile=BUILD/'mod-javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args))
    subprocess.run([str(java),'com.sun.tools.javac.Main','@'+str(argfile)],check=True)
    archive(BUILD/'mod-named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
    (BUILD/'mod-srg.jar').unlink(missing_ok=True)
    subprocess.run([str(java),'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(BUILD/'mod-named.jar'),str(BUILD/'mod-srg.jar'),str(SDK/'named-srg.tiny'),'named','srg',*cp,'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(BUILD/'mod-srg.jar') as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    resources=ROOT/'src/main/resources';entries.update({p.relative_to(resources).as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    entries['META-INF/MANIFEST.MF']=('Manifest-Version: 1.0\nMixinConfigs: '+MIXIN_CONFIG+'\n\n').encode()
    entries['LICENSE']=(ROOT/'LICENSE').read_bytes()
    target=ROOT/'dist'/NAME;archive(target,entries);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+NAME+'\n')
    print('LAYERED_ARMOR_BUILD_PASS',NAME,sha(target))
if __name__=='__main__':main()
