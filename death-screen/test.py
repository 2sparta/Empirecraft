#!/usr/bin/env python3
"""Check the exact installed overlay, actual upstream phrase parser, native client ABI and decoded cues."""
import hashlib,json,os,re,subprocess,sys,tomllib,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent));import build
ROOT=build.ROOT;REPO=build.REPO;BUILD=build.BUILD
def run(*args,**kwargs):return subprocess.run(list(map(str,args)),check=True,**kwargs)
def main():
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';installed=REPO/'mods'/build.NAME;catalog=json.loads((ROOT/'catalog.json').read_text());sources={};identities=set();cases=[];cues=set()
 for jar in sorted((REPO/'server/mods').glob('*.jar')):
  with zipfile.ZipFile(jar) as z:
   for n in z.namelist():
    if re.fullmatch(r'data/[^/]+/damage_type/[^/]+\.json',n):
     identity=n.split('/')[1]+':'+n.split('/')[-1][:-5];sources[identity]=dict(file=jar.name,sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),definition=json.loads(z.read(n)))
 for row in catalog:
  cues.add(row['cue'])
  for kind in ['damage','entity','block']:
   for identity in row.get(kind,[]):
    assert (kind,identity) not in identities;identities.add((kind,identity))
    for lang in ['en_us','uk_ua','ru_ru']:
     assert len(row[lang])>=2 and all(0<len(s)<=240 for s in row[lang]);cases.append('\t'.join([kind,identity,row['cue'],lang,row[lang][0]]))
 assert set(sources)=={identity for kind,identity in identities if kind=='damage'},'Every installed non-vanilla damage definition must have a profile'
 with zipfile.ZipFile(build.BASE) as original,zipfile.ZipFile(installed) as jar:
  assert jar.testzip() is None
  doc=tomllib.loads(jar.read('META-INF/mods.toml').decode());assert doc['mods'][0]['version']=='1.1.0-beta.2-empirecraft.1'
  assert not (REPO/'server/mods'/build.NAME).exists(),'Client-only mod must not be installed on the server'
  for n in original.namelist():
   if n.endswith('.class') and n not in ['com/epicdeathscreen/client/CinematicDeathScreen.class','com/epicdeathscreen/client/ClientFiles.class']:assert original.read(n)==jar.read(n),n
   if n.endswith('.ogg') or n in ['LICENSE','AUDIO_CREDITS.md']:assert original.read(n)==jar.read(n),n
  sounds=json.loads(jar.read('assets/epicdeathscreen/sounds.json'));english=json.loads(jar.read('assets/epicdeathscreen/lang/en_us.json'))
  for lang in ['uk_ua','ru_ru']:
   texts=json.loads(jar.read('assets/epicdeathscreen/lang/'+lang+'.json'));assert set(english)==set(texts)
   for key,text in texts.items():assert re.findall(r'%s',text)==re.findall(r'%s',english[key]),key
   assert all(re.search('[А-Яа-яІіЇїЄєҐґ]',text) for text in texts.values()),lang
  for cue in cues:
   name='empirecraft/'+cue;assert sounds[name]['subtitle'] in english
   path=ROOT/'resources/assets/epicdeathscreen/sounds'/('empirecraft/'+cue+'.ogg');assert jar.read('assets/epicdeathscreen/sounds/'+name+'.ogg')==path.read_bytes()
   run('ffmpeg','-hide_banner','-loglevel','error','-i',path,'-f','null','-')
 fixture=BUILD/'profile-cases.tsv';fixture.write_text('\n'.join(cases)+'\n');classes=BUILD/'tests';classes.mkdir(exist_ok=True)
 run(java,'com.sun.tools.javac.Main','--release','17','-proc:none','-encoding','UTF-8','-cp',installed,'-d',classes,ROOT/'tests/ProfileTest.java',ROOT/'tests/NativeLink.java')
 core=run(java,'-Xverify:all','-cp',os.pathsep.join(map(str,[classes,installed])),'ProfileTest',fixture,capture_output=True,text=True).stdout;print(core,end='');checks=int(re.search(r'checks=(\d+)',core)[1])
 libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)];remapper=os.pathsep.join(map(str,[build.SDK/'tiny-remapper.jar',build.SDK/'mapping-io.jar',*asm]));named=BUILD/'installed-named.jar';named.unlink(missing_ok=True)
 run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',installed,named,build.SDK/'named-srg.tiny','srg','named',build.SDK/'runtime-srg.jar','--ignoreConflicts')
 run(java,'-Xverify:all','-cp',os.pathsep.join(map(str,[classes,named,build.SDK/'runtime-named.jar',build.SDK/'client-named.jar',*libs])),'NativeLink')
 report=dict(status='passed',upstream_sha256=build.BASE_SHA,installed_sha256=hashlib.sha256(installed.read_bytes()).hexdigest(),side='client',damage_types=len(sources),entity_profiles=sum(kind=='entity' for kind,_ in identities),falling_block_profiles=sum(kind=='block' for kind,_ in identities),localized_profile_cases=len(cases),core_checks=checks,sounds=len(cues),native_client_classes_linked=5,graphical_client='not_run',live_audio_listening='not_run',damage_definitions=sources)
 (REPO/'validation/epic-death-screen-1.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('DEATH_SCREEN_VALIDATION_PASS',len(sources),'damage types',len(cues),'decoded OGG cues')
if __name__=='__main__':main()
