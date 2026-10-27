#!/usr/bin/env python3
"""Reproducible, native B&C/Vinery patches. Original mod versions remain compatible."""
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
EXPORTS=['--add-exports=java.base/jdk.internal.org.objectweb.asm'+s+'=ALL-UNNAMED' for s in ['','.tree','.tree.analysis']]
REMOVED={'vinery:chorus_wine','vinery:clark_wine'}
INPUTS={
 'brewinandchewin':('BrewinAndChewin-1.20.1-3.2.1 (1)-empirecraft-recipes.1.jar','eb9a877c2059d2728571199aed1318f68d18442b9e9f7db88ea1db949f569eb3','BrewinAndChewin-1.20.1-3.2.1-empirecraft-drinks.1.jar'),
 'vinery':('letsdo-vinery-forge-1.4.41.jar','5d7e11f6e2218562ce75d60788fdcfc9ccf59e4532757c18bf37759d5fc739ee','letsdo-vinery-forge-1.4.41-empirecraft-drinks.1.jar')}
REPLACEMENTS={
 'heating_cask':{'minecraft:blaze_powder':'minecraft:cauldron','minecraft:magma_block':'minecraft:campfire'},
 'dread_nog_from_egg_grog':{'minecraft:nether_wart':'minecraft:brown_mushroom','minecraft:fermented_spider_eye':'minecraft:poisonous_potato'},
 'red_rum_from_bloody_mary':{'minecraft:crimson_fungus':'minecraft:red_mushroom','minecraft:nether_wart':'minecraft:beetroot','minecraft:fermented_spider_eye':'minecraft:brown_mushroom','minecraft:shroomlight':'minecraft:glow_berries'},
 'scarlet_cheese_from_milk':{'minecraft:crimson_fungus':'minecraft:red_mushroom','minecraft:nether_wart':'minecraft:beetroot'},
 'steel_toe_stout_from_strongroot_ale':{'minecraft:crimson_fungus':'minecraft:red_mushroom','minecraft:nether_wart':'minecraft:beetroot'},
 'withering_dross_from_salty_folly':{'minecraft:wither_rose':'minecraft:lily_of_the_valley','minecraft:nether_wart':'minecraft:poisonous_potato'},
 'scarlet_pierogi':{'minecraft:nether_wart':'minecraft:beetroot'},
 'cristel_wine':{'minecraft:blaze_rod':'minecraft:rabbit_foot'},
 'banner_from_smithing_table':{'minecraft:netherite_ingot':'minecraft:gold_ingot','minecraft:netherite_upgrade_smithing_template':'minecraft:paper'}
}
def sha(b):return hashlib.sha256(b).hexdigest()
def git_sha(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def run(args):subprocess.run(list(map(str,args)),check=True)
def replace(d,mapping):
 if isinstance(d,str):return mapping.get(d,d)
 if isinstance(d,list):return [replace(v,mapping) for v in d]
 if isinstance(d,dict):return {k:replace(v,mapping) for k,v in d.items()}
 return d
def contains(d):
 if isinstance(d,str):return d in REMOVED
 if isinstance(d,list):return any(map(contains,d))
 if isinstance(d,dict):return any(map(contains,d.values()))
 return False
def clean(d):
 if isinstance(d,list):return [clean(v) for v in d if not (isinstance(v,str) and v in REMOVED) and not (isinstance(v,dict) and v.get('name') in REMOVED)]
 if isinstance(d,dict):return {k:clean(v) for k,v in d.items()}
 return d
def pack(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for n,b in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,10,27,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,b)
 with zipfile.ZipFile(path) as z:assert z.testzip() is None
def main():
 p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--classpath-dir',type=Path,action='append',required=True);p.add_argument('--install',action='store_true');a=p.parse_args()
 build=ROOT/'build';build.mkdir(exist_ok=True);cp=[]
 for d in a.classpath_dir:cp.extend(sorted(d.rglob('*.jar')))
 cp.sort(key=lambda p:(p.name!='runtime-srg.jar',str(p)))
 cp.extend(sorted(a.upstream.glob('*.jar')))
 run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',build/'classes',*sorted((ROOT/'src').glob('*.java'))])
 run(['java','com.sun.tools.javac.Main',*EXPORTS,'-d',build/'tool',ROOT/'NativePatch.java'])
 report=[]
 for mod,(old,expected,new) in INPUTS.items():
  path=a.upstream/old;raw=path.read_bytes()
  if sha(raw)!=expected:raise ValueError('Unreviewed upstream '+old+' '+sha(raw))
  with zipfile.ZipFile(path) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
  edits=[];removed=[]
  for n,b in list(files.items()):
   if not n.endswith('.json'):continue
   try:d=json.loads(b)
   except (ValueError,UnicodeError):continue
   if mod=='vinery':
    if '/recipes/' in n and contains(d.get('result')) or re.search(r'/(?:models/(?:item|block)|blockstates|loot_tables/blocks)/(?:chorus_wine|clark_wine)\.json$',n):
     removed.append(n);del files[n];continue
    if '/advancements/' in n:
     gone={k for k,v in d.get('criteria',{}).items() if contains(v)}
     for k in gone:del d['criteria'][k]
     if 'requirements' in d:d['requirements']=[[k for k in group if k not in gone] for group in d['requirements']];d['requirements']=[g for g in d['requirements'] if g]
    d=clean(d)
   if n.startswith('data/') and any(t in n for t in ['/recipes/','/advancements/recipes/']):
    d=replace(d,REPLACEMENTS.get(Path(n).stem,{}))
   if mod=='vinery' and n.startswith('assets/vinery/lang/'):
    uk=Path(n).stem=='uk_ua'
    d['tooltip.vinery.age']='Витримка: %s / 30 реальних діб' if uk else 'Age: %s / 30 real days'
    d['tooltip.vinery.next_upgrade']='Максимум: II рівень і 12:00 після 30 діб' if uk else 'Maximum: level II and 12:00 after 30 real days'
   encoded=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode()
   if d!=json.loads(b):files[n]=encoded;edits.append(n)
  if mod=='vinery':
   patched=build/'patched';shutil.rmtree(patched,ignore_errors=True)
   run(['java',*EXPORTS,'-cp',build/'tool','NativePatch',path,patched])
   for folder in [patched,build/'classes']:
    files.update({n.relative_to(folder).as_posix():n.read_bytes() for n in folder.rglob('*.class')})
  for n in list(files):
   if re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',n,re.I):del files[n]
  files['META-INF/empirecraft-drinks.json']=json.dumps({'build':1,'upstream_sha256':sha(raw),'removed':sorted(REMOVED) if mod=='vinery' else [],'changed_data':edits,'removed_files':removed},indent=2).encode()
  target=ROOT/'dist'/new;pack(target,files);b=target.read_bytes()
  row=dict(id=mod,old_file=old,file=new,sha256=sha(b),sha512=hashlib.sha512(b).hexdigest(),git_sha=git_sha(b),size=len(b),changed_data=edits,removed_files=removed)
  report.append(row);target.with_suffix('.jar.sha256').write_text(sha(b)+'  '+new+'\n')
  print('PASS built',new)
 write(ROOT/'audit/build.json',report)
 if a.install:install(report)
def install(report):
 spec=importlib.util.spec_from_file_location('adm',REPO/'medieval-create/update_adm.py');adm=importlib.util.module_from_spec(spec);spec.loader.exec_module(adm)
 lock=json.loads((REPO/'modpack-lock.json').read_text())
 adm_path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';original=adm.parse(adm_path.read_text());data=json.loads(json.dumps(original))
 for row in report:
  for side in ['mods','server/mods']:
   old=REPO/side/row['old_file'];old.unlink(missing_ok=True);old.with_suffix('.jar.sha256').unlink(missing_ok=True)
   out=REPO/side/row['file'];out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'dist'/row['file'],out);shutil.copyfile((ROOT/'dist'/row['file']).with_suffix('.jar.sha256'),out.with_suffix('.jar.sha256'))
  entry=next(e for e in lock['mods'] if e['id']==row['id']);entry.update({k:row[k] for k in ['file','sha256','sha512','size','git_sha']});entry['pin_reason']='Native overworld drinks / real-time wine aging; reproducible sources in drinks-balance/'
  next(e for e in data['modIds'] if e['id']==row['id'])['sha256']=row['sha256']
  for path in [REPO/'seasonal-calendar/installed-mods.json',REPO/'seasonal-calendar/audit/coverage.json']:
   if not path.exists():continue
   d=json.loads(path.read_text())
   if isinstance(d,list):
    for e in d:
     if e.get('name') in {row['old_file'],row['file']}:e.update(name=row['file'],path='server/mods/'+row['file'],sha=row['git_sha'],size=row['size'])
   else:
    hashes=d.get('jar_sha256',{})
    if row['old_file'] in hashes or row['file'] in hashes:hashes.pop(row['old_file'],None);hashes[row['file']]=row['sha256']
   write(path,d)
 assert all(e in data['modIds'] for e in original['modIds'] if e['id'] not in INPUTS)
 assert data['resourcePackIds']==original['resourcePackIds']
 data['integrityHash']=adm.integrity(data);adm.parse(json.dumps(data));write(adm_path,data);write(REPO/'modpack-lock.json',lock)
 write(ROOT/'release.json',[{k:r[k] for k in ['id','old_file','file','sha256','size']} for r in report])
if __name__=='__main__':main()
