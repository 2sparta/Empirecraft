#!/usr/bin/env python3
"""Restore the steel-only Locks UI/item without changing its persisted world format."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
BUILD=ROOT/'build'
SDK=REPO/'sails/build'
BASE=ROOT/'native-inputs/Locks-SteelOnly-original.jar'
DONOR=ROOT/'native-inputs/locks_reforged-1.8.2.jar'
BASE_SHA='948fbf6c1f2817aaaab3db66f464b82ae0f69a79c729971b994bac84fe55e6a2'
DONOR_SHA='0ce88fe297ff9f34bbc464e7d1c8f3092946fde768f199d4e012334bae66d114'
NAME='locks-steel-only-1.20.1-1.2.4-empirecraft.1.jar'
OLD_NAME='Locks-SteelOnly-forge-1_20_1-1_2_4_nogen (1).jar'
EXPORTS=['--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']

def jar(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as output:
        for name,data in sorted(entries.items(),key=lambda row:(row[0]!='META-INF/MANIFEST.MF',row[0])):
            info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            output.writestr(info,data)

def run(*args): subprocess.run(list(map(str,args)),check=True)

def main():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==BASE_SHA
    assert hashlib.sha256(DONOR.read_bytes()).hexdigest()==DONOR_SHA
    BUILD.mkdir(exist_ok=True)
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
    named=BUILD/'base-named.jar';named.unlink(missing_ok=True)
    run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',BASE,named,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts')
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[named,SDK/'runtime-named.jar',SDK/'client-named.jar',*libs])),'-d',classes,*sorted((ROOT/'src').rglob('*.java'))]
    (BUILD/'javac.args').write_text('\n'.join('"'+str(a)+'"' for a in args))
    run(java,'com.sun.tools.javac.Main','@'+str(BUILD/'javac.args'))
    helper=BUILD/'helper-named.jar';jar(helper,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    srg=BUILD/'helper-srg.jar';srg.unlink(missing_ok=True)
    run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',helper,srg,SDK/'named-srg.tiny','named','srg',SDK/'runtime-named.jar',SDK/'client-named.jar',named,'--ignoreConflicts')
    run(java,'com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',BUILD/'patcher',ROOT/'Patch.java')
    patched=BUILD/'patched';shutil.rmtree(patched,ignore_errors=True)
    run(java,*EXPORTS,'-cp',BUILD/'patcher','Patch',BASE,patched)
    with zipfile.ZipFile(BASE) as z: entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    for p in patched.rglob('*.class'): entries[p.relative_to(patched).as_posix()]=p.read_bytes()
    with zipfile.ZipFile(srg) as z: entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    imported=['assets/locks/models/item/steel_lock_pick.json','assets/locks/textures/item/steel_lock_pick.png','assets/locks/textures/gui/steel_lock_pick.png']
    with zipfile.ZipFile(DONOR) as z:
        for name in imported: entries[name]=z.read(name)
    recipe=dict(type='minecraft:crafting_shaped',pattern=[' SS',' S ','S  '],key={'S':{'tag':'forge:ingots/steel'}},result={'item':'locks:steel_lock_pick','count':1})
    entries['data/locks/recipes/steel_lock_pick.json']=(json.dumps(recipe,indent=2)+'\n').encode()
    entries['data/locks/tags/items/lock_picks.json']=b'{"replace":false,"values":["locks:steel_lock_pick"]}\n'
    for p in (ROOT/'resources').rglob('*'):
        if p.is_file():
            name=p.relative_to(ROOT/'resources').as_posix()
            if '/lang/' in name:
                entries[name]=(json.dumps((json.loads(entries[name]) if name in entries else {})|json.loads(p.read_text()),ensure_ascii=False,indent=2)+'\n').encode()
            else: entries[name]=p.read_bytes()
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version="1.20.1-1.2.4"',b'version="1.20.1-1.2.4-empirecraft.1"')
    entries['META-INF/empirecraft-steel-lockpicking.json']=(json.dumps(dict(base_sha256=BASE_SHA,donor_sha256=DONOR_SHA,imported_assets=imported,source='steel-lockpicking/',protocol='empirecraft-steel-picking-1'),indent=2)+'\n').encode()
    entries['LOCKPICKING-CREDITS.md']=(ROOT/'CREDITS.md').read_bytes()
    output=BUILD/NAME;jar(output,entries);digest=hashlib.sha256(output.read_bytes()).hexdigest()
    for side in ['mods','server/mods']:
        old=REPO/side/OLD_NAME;old.unlink(missing_ok=True);old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        target=REPO/side/NAME;shutil.copyfile(output,target);target.with_suffix('.jar.sha256').write_text(digest+'  '+NAME+'\n')
    print('STEEL_LOCKPICKING_BUILD_PASS',NAME,digest)

if __name__=='__main__': main()
