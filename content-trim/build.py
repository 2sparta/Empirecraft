#!/usr/bin/env python3
"""Reproducible native JAR trimming from exact upstream inputs; no decompiler or downloads."""
import argparse, hashlib, json, os, re, shutil, subprocess, zipfile
from pathlib import Path
from policy import FAMILIES, SHIELDS, MOBS, INPUTS

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
NAMES = {
 'epic-knights-all-in-one-1_20_1-forge (1).jar': 'epic-knights-all-in-one-1_20_1-forge-empirecraft.1.jar',
 'chipped-forge-1.20.1-3.0.7.jar': 'chipped-forge-1.20.1-3.0.7-empirecraft.1.jar',
 'chippedexttras-1.4.jar': 'chippedexttras-1.4-empirecraft.1.jar',
}
MOD = 'empirecraft-content-trim-1.0.0.jar'
EXPORTS = [f'--add-exports=java.base/jdk.internal.org.objectweb.asm{x}=ALL-UNNAMED' for x in ['', '.tree', '.tree.analysis']]
ENV = dict(os.environ, JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2')

def write(path, value):
 path.parent.mkdir(parents=True, exist_ok=True)
 path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')

def archive(path, entries):
 path.parent.mkdir(parents=True, exist_ok=True)
 with zipfile.ZipFile(path, 'w') as z:
  for name, data in sorted(entries.items()):
   i=zipfile.ZipInfo(name, (2026,10,26,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,data)
 with zipfile.ZipFile(path) as z: assert z.testzip() is None
 path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')

def ids_in(value):
 if isinstance(value, str): return {value}
 if isinstance(value, list): return set().union(*(ids_in(v) for v in value)) if value else set()
 if isinstance(value, dict): return set().union(*(ids_in(v) for v in value.values())) if value else set()
 return set()

def trim_resources(entries, removed, tags):
 result={}; deleted=[]
 language_keys={kind+'.'+i.replace(':','.') for i in removed for kind in ['item','block']}
 removed_tags={'#'+t for t in tags}
 for name, raw in entries.items():
  if name.endswith('/') or re.match(r'META-INF/[^/]+\.(SF|RSA|DSA)$',name,re.I):continue
  parts=name.split('/')
  leaf=parts[-1].removesuffix('.json')
  asset_id=(parts[1]+':'+leaf) if len(parts)>2 and parts[0] in ['assets','data'] else ''
  if name.endswith('.json') and asset_id in removed and any(s in name for s in ['/blockstates/','/models/item/','/models/block/','/loot_tables/blocks/']):
   deleted.append(name);continue
  if name.endswith('.json'):
   try: data=json.loads(raw)
   except ValueError: result[name]=raw;continue
   references=ids_in(data)
   if '/recipes/' in name or '/advancements/' in name:
    if references & removed: deleted.append(name);continue
    if isinstance(data,dict) and isinstance(data.get('tags'),list): data['tags']=[t for t in data['tags'] if t not in tags]
   if '/tags/' in name and isinstance(data,dict):
    data['values']=[v for v in data.get('values',[]) if not ((ids_in(v) & removed) or (ids_in(v) & removed_tags))]
   if '/lang/' in name and isinstance(data,dict):
    data={k:v for k,v in data.items() if k not in language_keys}
   # Only rewrite changed JSON; retain all unrelated assets byte-for-byte.
   if data != json.loads(raw): raw=(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode()
  result[name]=raw
 return result, deleted

def main():
 p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--classpath-dir',action='append',type=Path);a=p.parse_args()
 build=ROOT/'build';build.mkdir(exist_ok=True)
 sources={}
 for name,sha in INPUTS.items():
  raw=(a.upstream/name).read_bytes();assert hashlib.sha256(raw).hexdigest()==sha,'Unreviewed upstream: '+name
  with zipfile.ZipFile(a.upstream/name) as z:sources[name]={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 chipped=sources['chipped-forge-1.20.1-3.0.7.jar']
 removed_chipped=set()
 for f in FAMILIES:
  tag=json.loads(chipped['data/chipped/tags/blocks/'+f+'.json'])
  removed_chipped.update(v for v in tag['values'] if isinstance(v,str) and v.startswith('chipped:'))
 shields={'magistuarmory:'+m+'_'+s for m in ['diamond','netherite'] for s in SHIELDS}
 extras=sources['chippedexttras-1.4.jar'];regpath='assets/chippedexttras/generated/registry.json';registry=json.loads(extras[regpath]);kept=[];removed_extra=set()
 for row in registry:
  if row['base'] in removed_chipped:removed_extra.update(v for k,v in row.items() if k!='base' and v)
  else:kept.append(row)
 extras[regpath]=(json.dumps(kept,ensure_ascii=False,separators=(',',':'))+'\n').encode()
 removed=removed_chipped|removed_extra|shields
 write(ROOT/'audit/removed.json',dict(families=FAMILIES,shields=sorted(shields),chipped=sorted(removed_chipped),extras=sorted(removed_extra),mobs=['minecraft:'+m for m in MOBS]))
 (build/'families.txt').write_text('\n'.join(sorted(FAMILIES))+'\n')
 subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',str(build),str(ROOT/'PatchRegistries.java')],check=True,env=ENV)
 targets={
  'epic-knights-all-in-one-1_20_1-forge (1).jar':('com/magistuarmory/api/item/ModItemsProvider.class','knights'),
  'chipped-forge-1.20.1-3.0.7.jar':('earth/terrarium/chipped/common/registry/ModBlocks.class',str(build/'families.txt')),
 }
 reports={}
 for old,entries in sources.items():
  if old in targets:
   cls,kind=targets[old];out=build/(kind.replace('/','_') if kind=='knights' else 'chipped.class')
   subprocess.run(['java',*EXPORTS,'-cp',str(build),'PatchRegistries',str(a.upstream/old),str(out),cls,kind],check=True,env=ENV)
   entries[cls]=out.read_bytes()
  entries,deleted=trim_resources(entries,removed,{'chipped:'+f for f in FAMILIES})
  entries['META-INF/empirecraft-trim.json']=(json.dumps(dict(build='empirecraft.1',upstream_sha256=INPUTS[old],removed=sorted(shields if 'knights' in old else removed_chipped if 'chipped-' in old else removed_extra)),sort_keys=True)+'\n').encode()
  for side in ['mods','server/mods']:archive(REPO/side/NAMES[old],entries)
  raw=(REPO/'mods'/NAMES[old]).read_bytes();reports[old]=dict(file=NAMES[old],sha256=hashlib.sha256(raw).hexdigest(),size=len(raw),deleted_resource_count=len(deleted))
  print('PASS native trimmed JAR',NAMES[old],len(deleted),'removed resource files')
 # Keep the server-only behavior separate from both-side native registry edits.
 jars=sorted({p.resolve() for root in (a.classpath_dir or [REPO/'server/libraries']) for p in root.rglob('*.jar')},key=lambda p:(p.name!='runtime-srg.jar',str(p)))
 cp=os.pathsep.join(map(str,jars));classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',str(classes),*map(str,(ROOT/'src/main/java').rglob('*.java'))],check=True,env=ENV)
 resources=ROOT/'src/main/resources'
 write(resources/'removed-content.json',dict(replacements={i:('magistuarmory:steel_'+i.split('_',1)[1] if i in shields else 'minecraft:stone') for i in sorted(removed)}))
 write(resources/'blocked-mobs.json',['minecraft:'+m for m in MOBS])
 files={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
 files.update({p.relative_to(resources).as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()})
 files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nImplementation-Version: 1.0.0\r\n\r\n'
 archive(REPO/'server/mods'/MOD,files)
 write(ROOT/'audit/build.json',dict(upstream=INPUTS,jars=reports,removed_shields=len(shields),removed_chipped=len(removed_chipped),removed_extras=len(removed_extra)))
 print('PASS server mob guard, migration mappings and water-only slime recipe')

if __name__=='__main__':main()
