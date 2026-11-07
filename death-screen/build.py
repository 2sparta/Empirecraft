#!/usr/bin/env python3
"""Reproduce the client-only MIT upstream overlay against the pinned native Forge client API."""
import hashlib,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';SDK=REPO/'sails/build'
BASE=ROOT/'native-inputs/epicdeathscreen_forge_1.20.1_1.1.0_beta.2.jar'
BASE_SHA='a54e337cd4879c410aa67a4bfb4d158268880dec49f11ee14ca61971c6a35107'
NAME='epicdeathscreen-forge-1.20.1-1.1.0-beta.2-empirecraft.1.jar'
EXPORTS=['--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
def jar(path,entries):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as out:
  for name,data in sorted(entries.items()):
   entry=zipfile.ZipInfo(name,(2026,10,24,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;out.writestr(entry,data)
def run(*args):subprocess.run(list(map(str,args)),check=True)
def main():
 assert hashlib.sha256(BASE.read_bytes()).hexdigest()==BASE_SHA
 BUILD.mkdir(exist_ok=True);java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
 remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]));named=BUILD/'base-named.jar';named.unlink(missing_ok=True)
 run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',BASE,named,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts')
 classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir();cp=[named,SDK/'runtime-named.jar',SDK/'client-named.jar',*libs]
 args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'src').rglob('*.java'))]
 (BUILD/'javac.args').write_text('\n'.join('"'+str(a)+'"' for a in args));run(java,'com.sun.tools.javac.Main','@'+str(BUILD/'javac.args'))
 helper=BUILD/'helper-named.jar';jar(helper,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});srg=BUILD/'helper-srg.jar';srg.unlink(missing_ok=True)
 run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',helper,srg,SDK/'named-srg.tiny','named','srg',SDK/'runtime-named.jar',SDK/'client-named.jar',named,'--ignoreConflicts')
 run(java,'com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',BUILD/'patcher',ROOT/'tools/Patch.java')
 patched=BUILD/'patched';shutil.rmtree(patched,ignore_errors=True);run(java,*EXPORTS,'-cp',BUILD/'patcher','Patch',BASE,patched)
 with zipfile.ZipFile(BASE) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 for p in patched.rglob('*.class'):entries[p.relative_to(patched).as_posix()]=p.read_bytes()
 with zipfile.ZipFile(srg) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
 for p in (ROOT/'resources').rglob('*'):
  if p.is_file():
   name=p.relative_to(ROOT/'resources').as_posix()
   if '/lang/' in name and name in entries:
    entries[name]=(json.dumps(json.loads(entries[name])|json.loads(p.read_bytes()),ensure_ascii=False,indent=2)+'\n').encode()
   else:entries[name]=p.read_bytes()
 sounds=json.loads(entries['assets/epicdeathscreen/sounds.json']);sounds.update(json.loads((ROOT/'sounds-added.json').read_text()))
 entries['assets/epicdeathscreen/sounds.json']=(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n').encode()
 catalog=json.loads((ROOT/'catalog.json').read_text());rules=[];phrases={lang:entries['assets/epicdeathscreen/phrases/'+lang+'.txt'].decode().rstrip() for lang in ['en_us','uk_ua','ru_ru']}
 for row in catalog:
  for kind in ['damage','entity','block']:
   for identity in row.get(kind,[]):
    rules.append('\t'.join([kind,identity,row['cue']]))
    section=(('damage:empirecraft:falling_'+identity.replace(':','_')) if kind=='block' else kind+':'+identity)
    for lang in phrases:phrases[lang]+='\n\n['+section+']\n'+'\n'.join(row[lang])
 entries['assets/epicdeathscreen/empirecraft/profiles.tsv']=('\n'.join(rules)+'\n').encode()
 for lang,text in phrases.items():entries['assets/epicdeathscreen/phrases/'+lang+'.txt']=(text+'\n').encode()
 entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version="1.1.0-beta.2"',b'version="1.1.0-beta.2-empirecraft.1"')
 entries['META-INF/empirecraft-death-screen.json']=(json.dumps(dict(upstream_sha256=BASE_SHA,client_only=True,rules=len(rules),cues=len({r['cue'] for r in catalog}),source='death-screen/'),sort_keys=True)+'\n').encode()
 entries['AUDIO-CREDITS-Empirecraft.md']=(ROOT/'AUDIO-CREDITS.md').read_bytes()
 entries['epicdeathscreen-phrases-help.txt']+=('\nУКРАЇНСЬКА\nРедагуйте config/epicdeathscreen/phrases/uk_ua.txt: одна фраза на рядок, UTF-8.\nСекції: [generic], [fall], [fire], [drown], [explosion], [void], [frost], [magic], [mob], [hardcore].\nДля модів: [damage:musketmod:bullet] або [entity:recruits:bowman]. Змінні: {player}, {attacker}.\nПорядок вибору: тип шкоди, сутність, тема, загальні фрази. Налаштування phrases=replace лишає власні фрази.\nЗміни діють із наступної смерті або перегляду. Помилковий файл не замінює останні робочі фрази.\nЗвук причини смерті регулюється параметром «Звуки касети». Власні файли не перезаписуються.\n').encode()
 output=BUILD/NAME;jar(output,entries);target=REPO/'mods'/NAME;shutil.copyfile(output,target)
 target.with_suffix('.jar.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+NAME+'\n')
 print('DEATH_SCREEN_BUILD_PASS',NAME,hashlib.sha256(target.read_bytes()).hexdigest(),'rules='+str(len(rules)))
if __name__=='__main__':main()
