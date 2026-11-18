#!/usr/bin/env python3
"""Narrow overlays of pinned integrations, preserving unrelated native classes and assets."""
import hashlib,importlib.util,json,os,subprocess,sys,zipfile
from pathlib import Path
from build import ROOT,REPO,BUILD,JDK,archive,classpath
def files(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def main():
 BUILD.mkdir(parents=True,exist_ok=True);jdk=JDK;pins=json.loads((ROOT/'inputs.json').read_text())
 # Use an immutable input; never rebuild from an already patched installed jar.
 original=(ROOT/'inputs/empirecraft-armory-1.2.0.jar').read_bytes();assert hashlib.sha256(original).hexdigest()==pins['armory_sha256']
 baseline=BUILD/'armory-input.jar';baseline.write_bytes(original);armory=files(baseline)
 source=(REPO/'armory/src/main/java/ua/empirecraft/armory/GunsmithManual.java').read_text()
 for named,srg in {'Items.AIR':'Items.f_41852_','Ingredient.of(':'Ingredient.m_204132_(','TagKey.create(':'TagKey.m_203882_(','Registries.ITEM,':'Registries.f_256913_,'}.items():source=source.replace(named,srg)
 java=BUILD/'armory-src/ua/empirecraft/armory/GunsmithManual.java';java.parent.mkdir(parents=True,exist_ok=True);java.write_text(source)
 classes=BUILD/'armory-classes';classes.mkdir(exist_ok=True)
 subprocess.run([str(jdk/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,classpath())),'-d',str(classes),str(java)],check=True)
 armory['ua/empirecraft/armory/GunsmithManual.class']=(classes/'ua/empirecraft/armory/GunsmithManual.class').read_bytes()
 asm=list((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));tools=BUILD/'tools';tools.mkdir(exist_ok=True)
 subprocess.run([str(jdk/'bin/javac'),'-cp',os.pathsep.join(map(str,asm)),'-d',str(tools),str(ROOT/'tools/PatchJei.java')],check=True)
 category='ua/empirecraft/armory/client/GunsmithJei$Category.class';inp=BUILD/'category.class';out=BUILD/'category-patched.class';inp.write_bytes(armory[category])
 subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,[tools,*asm])),'PatchJei',str(inp),str(out)],check=True);armory[category]=out.read_bytes()
 armory['META-INF/mods.toml']=armory['META-INF/mods.toml'].replace(b'version="1.2.0"',b'version="1.2.1"')
 armory['META-INF/MANIFEST.MF']=armory['META-INF/MANIFEST.MF'].replace(b'Implementation-Version: 1.2.0',b'Implementation-Version: 1.2.1')
 archive(ROOT/'dist/empirecraft-armory-1.2.1.jar',armory)
 spec=importlib.util.spec_from_file_location('chinese_policy',REPO/'chinese-weapons/resources.py');policy=importlib.util.module_from_spec(spec);spec.loader.exec_module(policy)
 chinese=files(REPO/'chinese-weapons/dist/chineseweapons-forge-1.20.1-1.2.1.6-empirecraft.1.jar');aliases=policy.policy()['aliases']
 chinese['empirecraft/chinese-aliases.json']=(json.dumps(aliases,ensure_ascii=False,indent=2)+'\n').encode()
 retired=set(k for k in aliases if k.startswith('chineseweapons:diamond_') and any(k.endswith('_'+f) for f in ['dagger_axe','glaive','guduo','sky_piercing_halberd','song_javelin','tang_dynasty_heng_saber']))
 def canonical(value):
  if isinstance(value,str):return aliases.get(value,value) if value in retired else value
  if isinstance(value,list):return [canonical(v) for v in value]
  if isinstance(value,dict):return {k:canonical(v) for k,v in value.items()}
  return value
 removed=[]
 for name in list(chinese):
  if not (name.startswith('data/') and name.endswith('.json')):continue
  data=json.loads(chinese[name]);result=data.get('result',{});output=result.get('item') if isinstance(result,dict) else result
  if '/recipes/' in name and output in retired:removed.append(name);del chinese[name]
  elif '/weapon_attributes/' in name and 'chineseweapons:'+Path(name).stem in retired:del chinese[name]
  elif '/tags/' in name:
   data['values']=[v for v in data.get('values',[]) if (v.get('id') if isinstance(v,dict) else v) not in retired];chinese[name]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
  else:chinese[name]=(json.dumps(canonical(data),ensure_ascii=False,indent=2)+'\n').encode()
 chinese['META-INF/mods.toml']=chinese['META-INF/mods.toml'].replace(b'version="1.2.1.6-empirecraft.1"',b'version="1.2.1.6-empirecraft.2"')
 archive(ROOT/'dist/chineseweapons-forge-1.20.1-1.2.1.6-empirecraft.2.jar',chinese)
 (BUILD/'integration-overlays.json').write_text(json.dumps({'removed_diamond_weapons':sorted(retired),'removed_recipes':removed,'armory_changed_classes':['ua/empirecraft/armory/GunsmithManual.class',category]},indent=2)+'\n')
 print('INTEGRATION_OVERLAYS_PASS')
if __name__=='__main__':main()
