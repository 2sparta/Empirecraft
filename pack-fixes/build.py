#!/usr/bin/env python3
"""Compile the standalone Forge compatibility mod and embed the reviewed translation overlay."""
import hashlib,json,os,shlex,shutil,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';SDK=REPO/'sails/build'
NAME='empirecraft-pack-fixes-1.0.0.jar'
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
    cp=list(dict.fromkeys(map(str,[SDK/'runtime-named.jar',SDK/'client-named.jar',REPO/'mods/empirecraft-engagement-1.1.0.jar',REPO/'mods/vs_sails-1.20.1-0.3.2-empirecraft.18-forge.jar',*sdkcp])))
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(cp),'-d',str(classes),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))]
    argfile=BUILD/'mod-javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args))
    subprocess.run([str(java),'com.sun.tools.javac.Main','@'+str(argfile)],check=True)
    archive(BUILD/'mod-named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
    subprocess.run([str(java),'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(BUILD/'mod-named.jar'),str(BUILD/'mod-srg.jar'),str(SDK/'named-srg.tiny'),'named','srg',*cp,'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(BUILD/'mod-srg.jar') as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    resources=ROOT/'src/main/resources';entries.update({p.relative_to(resources).as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    overlay=ROOT/'inputs/Empirecraft-Translations-uk-ru-1.1.0.zip'
    assert sha(overlay)=='92fa291b65c9f726c30e9192d3d6edd9ebf14b68c82e15d1196a377e313d0758','Use the reviewed translation input'
    with zipfile.ZipFile(overlay) as z:
        for name in z.namelist():
            if name.startswith(('assets/','licenses/')) or name=='CREDITS.md':entries['builtin/translations/'+name]=z.read(name)
    # Our naval flag messages postdate the old overlay. Include their full catalog.
    with zipfile.ZipFile(REPO/'mods/empirecraft-engagement-1.1.0.jar') as z:
        for language in ['en_us','uk_ua','ru_ru']:
            name='assets/empirecraft_engagement/lang/'+language+'.json'
            entries['builtin/translations/'+name]=z.read(name)
    entries['builtin/translations/pack.mcmeta']=(json.dumps({'pack':{'pack_format':15,'description':'Empirecraft: вбудовані переклади українською та російською'}},ensure_ascii=False)+'\n').encode()
    entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMixinConfigs: empirecraft_pack_fixes.mixins.json\n\n'
    entries['LICENSE']=(ROOT/'LICENSE').read_bytes()
    target=ROOT/'dist'/NAME;archive(target,entries);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+NAME+'\n')
    print('PACK_FIXES_BUILD_PASS',NAME,sha(target),'entries=',len(entries))
if __name__=='__main__':main()
