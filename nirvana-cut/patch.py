#!/usr/bin/env python3
"""Reproducible native removal; also applied by the existing native-content rebuild."""
import hashlib,io,json,os,re,shutil,subprocess,urllib.request,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
BUILD=ROOT/'build'
VERSION='1.2.5-empirecraft.1'
MARKER='META-INF/empirecraft-nirvana-creepers.json'
REMOVED_CLASSES=[
 'galena/nirvana/world/entity/Reefer.class',
 'galena/nirvana/world/entity/renderer/ReeferRenderer.class',
 'galena/nirvana/world/entity/ICustomCreeper.class',
 'galena/nirvana/mixins/CreeperMixin.class',
]
REMOVED_RESOURCES=['assets/nirvana/models/item/reefer_spawn_egg.json','data/nirvana/loot_tables/entities/reefer.json','data/nirvana/tags/entity_types/creeper_like.json']
def classpath():
 jars=[]
 for name,digest in json.loads((ROOT/'asm-inputs.json').read_text()).items():
  path=BUILD/'asm'/(name+'-9.7.jar');path.parent.mkdir(parents=True,exist_ok=True)
  if not path.exists():path.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/ow2/asm/'+name+'/9.7/'+path.name).read())
  assert hashlib.sha256(path.read_bytes()).hexdigest()==digest,path
  jars.append(str(path))
 return os.pathsep.join(jars)

def archive(entries):
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  for name,raw in sorted(entries.items(),key=lambda row:(row[0]!='META-INF/MANIFEST.MF',row[0])):
   if name.endswith('/'):continue
   entry=zipfile.ZipInfo(name,(2026,10,28,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,raw)
 return out.getvalue()

def patch_entries(entries):
 data=dict(entries)
 if MARKER in data:
  assert json.loads(data[MARKER])['version']==VERSION
  return data
 BUILD.mkdir(exist_ok=True)
 cp=classpath();source=ROOT/'Cut.java';stamp=BUILD/'compiler.sha256';digest=hashlib.sha256(source.read_bytes()).hexdigest()
 if not stamp.exists() or stamp.read_text()!=digest or not (BUILD/'Cut.class').exists():
  compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
  subprocess.run([*compiler,'-source','17','-target','17','-proc:none','-cp',cp,'-d',str(BUILD),str(source)],check=True)
  stamp.write_text(digest)
 path=BUILD/'input.jar';path.write_bytes(archive(data));output=BUILD/'patched';shutil.rmtree(output,ignore_errors=True)
 subprocess.run(['java','-cp',str(BUILD)+os.pathsep+cp,'Cut',str(path),str(output)],check=True)
 for path in output.rglob('*.class'):data[path.relative_to(output).as_posix()]=path.read_bytes()
 for name in REMOVED_CLASSES+REMOVED_RESOURCES:
  assert name in data,name
  del data[name]
 head=json.loads(data['assets/nirvana/mob_head_effects/reefer.json'])
 assert head['entity_types']==['nirvana:reefer']
 head['entity_types']=[]
 data['assets/nirvana/mob_head_effects/reefer.json']=(json.dumps(head,indent=2)+'\n').encode()
 config=json.loads(data['nirvana.mixins.json']);assert 'CreeperMixin' in config['mixins'];config['mixins'].remove('CreeperMixin');data['nirvana.mixins.json']=(json.dumps(config,indent=2)+'\n').encode()
 # The remaining refmap entries and decorative head renderer are retained.
 def clean(value):
  if isinstance(value,dict):return {k:clean(v) for k,v in value.items() if k.replace('.','/')!='galena/nirvana/mixins/CreeperMixin'}
  if isinstance(value,list):return [clean(v) for v in value]
  return value
 for name in list(data):
  if name.endswith('refmap.json'):
   original=json.loads(data[name]);changed=clean(original)
   if changed!=original:data[name]=(json.dumps(changed,indent=2)+'\n').encode()
  if re.fullmatch(r'assets/nirvana/lang/[^/]+\.json',name):
   values=json.loads(data[name]);before=dict(values)
   for key in ['entity.nirvana.reefer','item.nirvana.reefer_spawn_egg']:values.pop(key,None)
   if values!=before:data[name]=(json.dumps(values,ensure_ascii=False,indent=2)+'\n').encode()
 raw,count=re.subn(rb'(?m)^version\s*=\s*"1\.2\.5"',('version = "'+VERSION+'"').encode(),data['META-INF/mods.toml']);assert count==1;data['META-INF/mods.toml']=raw
 data['META-INF/MANIFEST.MF']=data['META-INF/MANIFEST.MF'].replace(b'Implementation-Version: 1.2.5\r\n',('Implementation-Version: '+VERSION+'\r\n').encode())
 for name,raw in data.items():
  if name.endswith('.class'):
   for removed in REMOVED_CLASSES:assert removed[:-6].encode() not in raw,(name,removed)
 data[MARKER]=(json.dumps({'version':VERSION,'removed_entity':'nirvana:reefer','removed_item':'nirvana:reefer_spawn_egg','spawn_and_conversion_code_removed':True,'vanilla_creeper_preserved':True,'decorative_head_preserved':True},indent=2)+'\n').encode()
 return data
