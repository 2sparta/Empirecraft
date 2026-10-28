#!/usr/bin/env python3
"""Pinned native JAR surgery, including registration providers embedded by other mods."""
import argparse,copy,difflib,hashlib,io,json,os,re,shutil,struct,subprocess,zipfile
from pathlib import Path
from policy import blocked,alias,METALS,THEME

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
EXPORTS=['--add-exports=java.base/jdk.internal.org.objectweb.asm'+s+'=ALL-UNNAMED' for s in ['', '.tree','.tree.analysis']]
BUILD=ROOT/'build'
CACHE={}
def relocate_helper(raw,package):
 """Rename only constant-pool UTF8 entries; retain javac's exact verified bytecode."""
 out=bytearray(raw[:10]);pos=10;i=1;count=int.from_bytes(raw[8:10],'big')
 while i<count:
  tag=raw[pos];out.append(tag);pos+=1
  if tag==1:
   size=int.from_bytes(raw[pos:pos+2],'big');pos+=2
   value=raw[pos:pos+size].replace(b'TEMPLATE/',package.replace('.','/').encode()+b'/');pos+=size
   out.extend(len(value).to_bytes(2,'big'));out.extend(value)
  else:
   size={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
   out.extend(raw[pos:pos+size]);pos+=size
   if tag in {5,6}:i+=1
  i+=1
 out.extend(raw[pos:]);return bytes(out)
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def archive(entries):
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as z:
  # JarInputStream and Forge providers require the manifest before ordinary entries.
  ordered=sorted(entries,key=lambda name:(name!='META-INF/MANIFEST.MF',name))
  for name in ordered:
   raw=entries[name]
   if name.endswith('/') or re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',name,re.I):continue
   info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 return out.getvalue()
def entries(raw):
 with zipfile.ZipFile(io.BytesIO(raw)) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def strings(data):
 if isinstance(data,str):return {data}
 if isinstance(data,dict):return set().union(*(strings(x) for x in data.values())) if data else set()
 if isinstance(data,list):return set().union(*(strings(x) for x in data)) if data else set()
 return set()
def replace(data,aliases):
 if isinstance(data,str):return aliases.get(data,data)
 if isinstance(data,list):return [replace(x,aliases) for x in data]
 if isinstance(data,dict):return {k:replace(v,aliases) for k,v in data.items()}
 return data
def constant_strings(raw):
 pos=10;count=int.from_bytes(raw[8:10],'big');pool={};refs=[];i=1
 while i<count:
  tag=raw[pos];pos+=1
  if tag==1:
   size=int.from_bytes(raw[pos:pos+2],'big');pos+=2;pool[i]=raw[pos:pos+size].decode('utf-8','replace');pos+=size
  elif tag==8:refs.append(int.from_bytes(raw[pos:pos+2],'big'));pos+=2
  elif tag in [3,4,9,10,11,12,17,18]:pos+=4
  elif tag in [5,6]:pos+=8;i+=1
  elif tag in [7,16,19,20]:pos+=2
  elif tag==15:pos+=3
  else:raise ValueError(tag)
  i+=1
 return [pool[x] for x in refs]
def read_json(raw):
 try:
  text=raw.decode('utf-8-sig')
  text=re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m[1] or '',text)
  text=re.sub(r'("(?:\\.|[^"\\])*")|,\s*(?=[}\]])',lambda m:m[1] or '',text)
  return json.loads(text)
 except (ValueError,UnicodeDecodeError):return None
def trim(data,removed,aliases,report):
 output={}
 language_keys={kind+'.'+i.replace(':','.') for i in removed for kind in ['block','item','entity']}
 for name,raw in data.items():
  parts=name.split('/');leaf=parts[-1].removesuffix('.json').removesuffix('.png')
  identity=parts[1]+':'+leaf if len(parts)>2 and parts[0] in {'assets','data'} else ''
  if identity in removed and any(s in name for s in ['/models/','/blockstates/','/textures/','/loot_tables/blocks/']):report['deleted'].append(name);continue
  if name.endswith('.json'):
   value=read_json(raw)
   if value is not None:
    before=copy.deepcopy(value)
    if name=='assets/chippedexttras/generated/registry.json':
     value=[row for row in value if row['base'] not in removed]
    elif '/recipes/' in name or '/advancements/' in name:
     # Keep the complete native tin+copper mixture -> furnace bronze route.
     # Big Cannons keeps its reversible storage conversions, but loses its competing alloy recipe.
     if name=='data/createbigcannons/recipes/medieval/bronze.json' or name=='data/createbigcannons/advancements/recipes/medieval/bronze.json':report['deleted'].append(name);continue
     if name in {'data/antiquelegacy/recipes/bronze_ingot.json','data/antiquelegacy/recipes/bronze_nugget.json'}:report['deleted'].append(name);continue
     if strings(value)&(removed-set(METALS)):
      report['deleted'].append(name);continue
     # No iron-to-steel bypass or alternate nugget recycling route is introduced.
     out=strings(value.get('result',[]))|strings(value.get('results',[])) if isinstance(value,dict) else set()
     if out&{'magistuarmory:steel_ingot','magistuarmory:steel_nugget'}:report['deleted'].append(name);continue
     value=replace(value,METALS)
    elif '/tags/' in name and isinstance(value,dict):
     value['values']=[replace(v,METALS) for v in value.get('values',[]) if not (strings(v)&(removed-set(METALS)))]
     if name=='data/forge/tags/items/nuggets/bronze.json':value['values']=['createbigcannons:bronze_scrap']
     if name=='data/forge/tags/items/ingots/bronze.json':value['values']=['createbigcannons:bronze_ingot']
    elif '/lang/' in name and isinstance(value,dict):
     value={k:v for k,v in value.items() if k not in language_keys}
    elif '/loot_tables/' in name:
     def clean(v):
      if isinstance(v,dict):
       if v.get('type')=='minecraft:item' and v.get('name') in removed:return None
       return {k:clean(x) for k,x in v.items()}
      if isinstance(v,list):return [c for x in v if (c:=clean(x)) is not None]
      return v
     value=clean(value)
    elif name.startswith('data/'):
     # Custom fishing tables, Patchouli entries and worldgen resources must not retain
     # required references to registrations that no longer exist.
     if strings(value)&(removed-set(METALS)) or blocked(identity):report['deleted'].append(name);continue
     value=replace(value,METALS)
    if value!=before:raw=(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode();report['rewritten'].append(name)
  output[name]=raw
 return output

def patch_jar(raw,aliases,removed,report,native=True):
 digest=hashlib.sha256(raw).hexdigest()
 if digest in CACHE:
  changed,previous=CACHE[digest];report.update(copy.deepcopy(previous));return changed
 data=entries(raw);original=dict(data)
 # Every embedded copy must be patched too; JarJar chooses one provider version at launch.
 for name in list(data):
  if name.endswith('.jar'):
   sub={'deleted':[],'rewritten':[],'boundaries':0}
   changed=patch_jar(data[name],aliases,removed,sub,True)
   if changed!=data[name]:data[name]=changed;report.setdefault('embedded',{})[name]=sub
 key=hashlib.sha256(raw).hexdigest()[:12];package='ua.empirecraft.nativecut.j'+key;hooks=package.replace('.','/')+'/PolicyHooks'
 source=BUILD/key;source.mkdir(parents=True,exist_ok=True)
 input_file=source/'input.jar';input_file.write_bytes(archive(data));patched=source/'patched';shutil.rmtree(patched,ignore_errors=True)
 if native:subprocess.run(['java',*EXPORTS,'-cp',str(BUILD),'Patch',str(input_file),str(patched),hooks],check=True,stdout=subprocess.DEVNULL)
 if patched.exists():
  added={p.relative_to(patched).as_posix():p.read_bytes() for p in patched.rglob('*.class')};report['boundaries']=len(added);data.update(added)
  data[hooks+'.class']=relocate_helper((BUILD/'shared/TEMPLATE/PolicyHooks.class').read_bytes(),package)
  data[hooks.rsplit('/',1)[0]+'/aliases.tsv']=''.join(k+'\t'+v+'\n' for k,v in sorted(aliases.items())).encode()
 data=trim(data,removed,aliases,report)
 if data==original:CACHE[digest]=(raw,copy.deepcopy(report));return raw
 data['META-INF/empirecraft-native-content.json']=(json.dumps({'input_sha256':hashlib.sha256(raw).hexdigest(),'build':'native.1'},sort_keys=True)+'\n').encode()
 changed=archive(data);CACHE[digest]=(changed,copy.deepcopy(report));return changed

def main():
 p=argparse.ArgumentParser();p.add_argument('--inputs',type=Path,required=True);a=p.parse_args();BUILD.mkdir(exist_ok=True)
 pins=json.loads((ROOT/'inputs.json').read_text());sources={}
 for relative,digest in pins.items():
  raw=(a.inputs/relative).read_bytes();assert hashlib.sha256(raw).hexdigest()==digest,('Unreviewed native input',relative);sources[relative]=raw
 all_ids=set();block_ids=set();candidate_strings={};namespaces=set()
 for relative,raw in sources.items():
  if not relative.endswith('.jar'):continue
  data=entries(raw);ns=set();constants=set()
  for name,value in data.items():
   m=re.fullmatch(r'assets/([^/]+)/(models/item|blockstates)/([^/]+)\.json',name)
   if m:
    identity=m[1]+':'+m[3];all_ids.add(identity);ns.add(m[1]);namespaces.add(m[1])
    if m[2]=='blockstates':block_ids.add(identity)
   if name.endswith('.class'):
    constants.update(s for s in constant_strings(value) if re.fullmatch(r'[a-z][a-z0-9_:]*',s))
  candidate_strings[relative]=(ns,constants)
 removed={i for i in all_ids if blocked(i)}|set(METALS)
 # Native fluid sources, wall variants and dynamically named registries may have no item model.
 for ns,constants in candidate_strings.values():
  for value in constants:
   for identity in ([value] if ':' in value else [n+':'+value for n in ns if n not in {'minecraft','forge','c'}]):
    if blocked(identity):removed.add(identity)
 # Generated Chipped Extras declarations are authoritative even when they have no own models.
 for raw in sources.values():
  data=entries(raw);path='assets/chippedexttras/generated/registry.json'
  if path in data:
   for row in json.loads(data[path]):
    if row['base'] in removed:
     for k,v in row.items():
      if k!='base' and v:removed.add(v);block_ids.add(v)
 aliases={i:alias(i,all_ids,block_ids) for i in sorted(removed)}
 subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',str(BUILD),str(ROOT/'Fields.java')],check=True)
 fields=subprocess.check_output(['java',*EXPORTS,'-cp',str(BUILD),'Fields',str(a.inputs)],text=True)
 signatures={};groups={};by_name={Path(k).name:v[0] for k,v in candidate_strings.items()}
 for line in fields.splitlines():
  filename,name,signature=line.split('\t')
  for ns in by_name.get(filename,[]):
   identity=ns+':'+name
   if identity in all_ids:signatures[identity]=signature;groups.setdefault((ns,signature),set()).add(identity)
 for identity in sorted(removed):
  ns=identity.split(':')[0];signature=signatures.get(identity)
  if signature and identity not in METALS:
   candidates=groups.get((ns,signature),set())-removed
   if candidates and aliases[identity] not in candidates:
    aliases[identity]=max(sorted(candidates),key=lambda x:difflib.SequenceMatcher(None,identity,x).ratio())
 # Fruit jam and flowing IDs are built by concatenation, without a model or literal constant.
 for suffix in ['jam','jello']:
  identity='fruitsdelight:chorus_'+suffix;removed.add(identity);aliases[identity]='fruitsdelight:apple_'+suffix
 for identity,target in list(aliases.items()):
  if ':molten_nethersteel' in identity or identity in {'fruitsdelight:chorus_jam','fruitsdelight:chorus_jello'}:
   ns,name=identity.split(':');tns,tname=target.split(':');flow=ns+':flowing_'+name;removed.add(flow);aliases[flow]=tns+':flowing_'+tname
 write(ROOT/'audit/policy.json',{'removed':sorted(removed),'blocks':sorted(block_ids&removed),'aliases':aliases,'forbidden_pattern':THEME.pattern,'preserved_false_positives':['naturalist:crimson_froglass','naturalist:crimson_froglass_pane']})
 subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',str(BUILD),str(ROOT/'Patch.java')],check=True)
 subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-d',str(BUILD/'shared'),str(ROOT/'NativeAliases.java'),str(ROOT/'PolicyHooks.java')],check=True)
 reports={}
 for relative,raw in sources.items():
  report={'deleted':[],'rewritten':[],'boundaries':0}
  native_ids={i for i in removed if i in all_ids}
  native=any((m:=re.fullmatch(r'assets/([^/]+)/(?:models/item|blockstates)/([^/]+)\.json',n)) and m[1]+':'+m[2] in native_ids for n in entries(raw)) or Path(relative).name.startswith(('architectury-','moonlight-','resourcefullib-','empirecraft-no-end-'))
  changed=patch_jar(raw,aliases,removed,report,native)
  # The existing stack/world migration guard already owns both-side load hooks.
  if relative.endswith('/empirecraft-no-end-1.0.0.jar'):
   data=entries(changed);policy=json.loads(data['no-end.json'])
   data['ua/empirecraft/noend/NativeAliases.class']=(BUILD/'shared/ua/empirecraft/noend/NativeAliases.class').read_bytes()
   policy['replacements'].update({k:v for k,v in aliases.items() if not k.startswith('minecraft:') or k not in policy['replacements']})
   policy['blocks']=sorted(set(policy['blocks'])|(block_ids&removed))
   policy['entities']=sorted(set(policy['entities'])|{'minecraft:'+n for n in 'blaze ghast magma_cube wither wither_skeleton zombified_piglin piglin piglin_brute hoglin zoglin strider small_fireball fireball wither_skull'.split()})
   data['no-end.json']=(json.dumps(policy,ensure_ascii=False,separators=(',',':'),sort_keys=True)+'\n').encode();changed=archive(data)
  if changed==raw:continue
  target=REPO/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(changed);target.with_suffix(target.suffix+'.sha256').write_text(hashlib.sha256(changed).hexdigest()+'  '+target.name+'\n')
  report.update(sha256=hashlib.sha256(changed).hexdigest(),size=len(changed));reports[relative]=report
 write(ROOT/'audit/build.json',reports);print('PASS pinned native build:',len(reports),'JARs,',len(removed),'excluded identities')
if __name__=='__main__':main()
