#!/usr/bin/env python3
"""Overlay only reviewed ecology classes on installed native-cut JARs; keep every other byte."""
import hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent
B=R/'ecology-balance/build'
TARGETS={
 'empirecraft-world-rules-1.0.2.jar':('world-rules','worldrules',['NaturalRenewal','RulesConfig','TreeRules']),
 'empirecraft-calendar-1.1.1.jar':('seasonal-calendar','calendar',['CalendarMod','HabitatRules']),
}
def dump(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def writejar(p,data):
 with zipfile.ZipFile(p,'w') as z:
  for n,v in sorted(data.items(),key=lambda e:(e[0]!='META-INF/MANIFEST.MF',e[0])):
   i=zipfile.ZipInfo(n,(2026,10,29,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,v)
def compile_overlay():
 B.mkdir(exist_ok=True)
 jars=sorted({p.resolve() for root in [R/'server/libraries',R/'server/mods',R/'mods'] for p in root.rglob('*.jar')},key=lambda p:(p.name!='forge-1.20.1-47.4.23-server.jar',str(p)))
 assert any(p.name=='server-1.20.1-20230612.114412-srg.jar' for p in jars),'Missing Forge compilation runtime'
 sources=[R/module/'src/main/java/ua/empirecraft'/package/(name+'.java') for module,package,names in TARGETS.values() for name in names]
 classes=B/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*map(str,sources)]
 argfile=B/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args))
 subprocess.run(['java','com.sun.tools.javac.Main','@'+str(argfile)],check=True)
 report={}
 native_path=R/'native-content/audit/build.json';native=json.loads(native_path.read_text())
 retained_path=R/'native-content/retained-code.json';retained=json.loads(retained_path.read_text())
 for name,(module,package,names) in TARGETS.items():
  paths=[p for side in ['mods','server/mods'] if (p:=R/side/name).exists()]
  assert paths and all(p.read_bytes()==paths[0].read_bytes() for p in paths),'Input side mismatch: '+name
  with zipfile.ZipFile(paths[0]) as z:data={n:z.read(n) for n in z.namelist()}
  changed={p.relative_to(classes).as_posix():p.read_bytes() for p in (classes/'ua/empirecraft'/package).glob('*.class')}
  for n in changed:
   assert n.split('/')[-1].split('$')[0].removesuffix('.class') in names,n
  preserved={n:hashlib.sha256(v).hexdigest() for n,v in data.items() if n not in changed}
  data.update(changed)
  for p in paths:
   writejar(p,data);p.with_suffix('.jar.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+name+'\n')
   if p.relative_to(R).as_posix() in native:
    native[p.relative_to(R).as_posix()].update(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),size=p.stat().st_size)
  for n in changed:
   if n in retained.get(name,{}):retained[name][n]=hashlib.sha256(changed[n]).hexdigest()
  report[name]={'changed_entries':sorted(changed),'preserved_entries':preserved}
 dump(native_path,native);dump(retained_path,retained);dump(R/'ecology-balance/audit/overlay.json',report)
 print('PASS compiled ecology overlays and retained all other native registrations, migrations and ship physics')
def tree_overrides():
 # Follow configured/placed selector references, rather than reducing berries, flowers or fallen logs.
 data={}
 for p in (R/'server/mods').glob('*.jar'):
  with zipfile.ZipFile(p) as z:
   for n in z.namelist():
    if n.startswith('data/') and not n.startswith('data/minecraft/') and any(k in n for k in ['/worldgen/configured_feature/','/worldgen/placed_feature/']) and n.endswith('.json'):
     text=z.read(n).decode('utf-8-sig')
     # Minecraft/Gson accepts comments and trailing commas in native data files.
     text=re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[1] or '',text)
     text=re.sub(r'("(?:\\.|[^"\\])*")|,\s*(?=[}\]])',lambda m:m[1] or '',text)
     try:data[n]=json.loads(text)
     except ValueError as e:raise ValueError(str(p)+':'+n) from e
 def identity(n):
  a=n.split('/');return a[1]+':'+ '/'.join(a[4:]).removesuffix('.json')
 configured={identity(n):v for n,v in data.items() if '/configured_feature/' in n}
 placed={identity(n):v for n,v in data.items() if '/placed_feature/' in n}
 trees={k for k,v in configured.items() if v.get('type')=='minecraft:tree'}
 def refs(v):
  if isinstance(v,str):return {v}
  if isinstance(v,dict):return set().union(set(),*(refs(x) for x in v.values()))
  if isinstance(v,list):return set().union(set(),*(refs(x) for x in v))
  return set()
 # Native selectors can point to placed features which point back to configured trees.
 for _ in range(len(data)+1):
  extended=trees|{k for k,v in {**configured,**placed}.items() if refs(v)&trees}
  if extended==trees:break
  trees=extended
 overrides=[]
 for n,v in data.items():
  if '/placed_feature/' not in n or identity(n) not in trees:continue
  # Sapling feature selectors have no biome placement and must keep player-grown trees unchanged.
  if not any(p.get('type')=='minecraft:biome' for p in v.get('placement',[])):continue
  v['placement'].insert(0,{'type':'minecraft:rarity_filter','chance':8})
  for side in ['kubejs','server/kubejs']:dump(R/side/n,v)
  overrides.append(n)
 assert overrides,'No mod tree worldgen placements found; check current mod data schema'
 dump(R/'ecology-balance/audit/worldgen.json',{'rarity_divisor':8,'overrides':sorted(overrides)})
 print('PASS rare mod/fruit tree worldgen overrides:',len(overrides))
def update_metadata():
 # Refresh native hashes first; its audit continues to verify the full prior content exclusions.
 subprocess.run(['python3',str(R/'native-content/update_metadata.py')],check=True)
 import importlib.util
 spec=importlib.util.spec_from_file_location('adm',R/'medieval-create/update_adm.py');adm_helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(adm_helper)
 lockp=R/'modpack-lock.json';lock=json.loads(lockp.read_text())
 inventories=[R/'seasonal-calendar/installed-mods.json',R/'forging-balance/installed-client-mods.json']
 inv=[json.loads(p.read_text()) for p in inventories]
 coveragep=R/'seasonal-calendar/audit/coverage.json';coverage=json.loads(coveragep.read_text())
 admp=R/'server/config/admanticheat/idfile/admanticheat_id_list.json';adm=adm_helper.parse(admp.read_text());resources=adm['resourcePackIds']
 for name in TARGETS:
  raw=(R/'server/mods'/name).read_bytes();sha=hashlib.sha256(raw).hexdigest();git=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
  for row in lock['mods']:
   if row.get('file')==name:
    row.update(size=len(raw),sha256=sha,sha512=hashlib.sha512(raw).hexdigest(),git_sha=git)
  for rows in inv:
   for row in rows:
    if row['name']==name:row.update(size=len(raw),sha=git)
  if name in coverage['jar_sha256']:coverage['jar_sha256'][name]=sha
  modid='empirecraft_calendar' if 'calendar' in name else 'empirecraft_world_rules'
  for row in adm['modIds']:
   if row['id']==modid and row['filePresent']:row['sha256']=sha
 adm['integrityHash']=adm_helper.integrity(adm);assert adm['resourcePackIds']==resources
 for p,v in [(lockp,lock),*zip(inventories,inv),(coveragep,coverage),(admp,adm)]:dump(p,v)
 print('PASS installed inventories, lock and ADM integrity')
if __name__=='__main__':
 compile_overlay();tree_overrides();update_metadata()
