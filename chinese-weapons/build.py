"""Pinned Chinese Weapons overlay; preserve author models and attribution."""
import hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
import resources
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;REF=Path('/workspace/Empirecraft');SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk')
NAME='chineseweapons-forge-1.20.1-1.2.1.6-empirecraft.1.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def archive(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for n,raw in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,17,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def main():
 pin=json.loads((ROOT/'inputs.json').read_text());source=ROOT/'upstream'/pin['file'];assert sha(source)==pin['sha256'];b=ROOT/'build';b.mkdir(exist_ok=True)
 def run(args,label):
  r=subprocess.run(list(map(str,args)),capture_output=True,text=True);(b/(label+'.log')).write_text(r.stdout+r.stderr)
  if r.returncode:raise RuntimeError(label+'\n'+r.stderr[-7000:])
  print(label+'_PASS',flush=True)
 libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]
 with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 original=dict(files);raw=b/'original';patched=b/'patched';shutil.rmtree(raw,ignore_errors=True);shutil.rmtree(patched,ignore_errors=True);raw.mkdir()
 for n,body in files.items():
  if n.endswith('.class'):
   p=raw/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(body)
 tool=b/'tool';tool.mkdir(exist_ok=True);run([JDK/'bin/javac','-cp',os.pathsep.join(map(str,asm)),'-d',tool,ROOT/'tools/Patch.java'],'tool-compile');run([JDK/'bin/java','-cp',os.pathsep.join(map(str,[tool,*asm])),'Patch',raw,patched],'patch')
 files.update({p.relative_to(patched).as_posix():p.read_bytes() for p in patched.rglob('*.class')})
 named=b/'named';named.mkdir(exist_ok=True)
 for prefix in ['empirecraft-armory-','TerraFirmaCraft-','overgeared-']:
  f=next((REPO/'mods').glob(prefix+'*.jar'));target=named/f.name;marker=target.with_suffix('.input.sha256')
  if not target.exists() or not marker.exists() or marker.read_text()!=sha(f):
   target.unlink(missing_ok=True);run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',f,target,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar',*libs,'--ignoreConflicts'],'remap-'+prefix);marker.write_text(sha(f))
 cp=[Path('/workspace/work/flux-continuous/casting/build/compile-access.jar'),SDK/'runtime-named.jar',SDK/'client-named.jar',*named.glob('*.jar'),*libs,*list((REPO/'mods').glob('*.jar'))]
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir();run([JDK/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'src/main/java').rglob('*.java'))],'compile')
 archive(b/'named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});srg=b/'srg.jar';srg.unlink(missing_ok=True);run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',b/'named.jar',srg,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'reobf')
 with zipfile.ZipFile(srg) as z:files.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
 policy=resources.generate(files,next((REPO/'mods').glob('TerraFirmaCraft-*.jar')))
 metadata=files['META-INF/mods.toml'].decode().replace('version="1.2.1.6"','version="'+pin['version']+'"').replace('versionRange="15.12.2,)"','versionRange="[15.12.2,)"')
 for mod,version in [('empirecraft_armory','1.0.0'),('empirecraft_forging','1.1.5')]:metadata+='\n[[dependencies.chineseweapons]]\nmodId="'+mod+'"\nmandatory=true\nversionRange="['+version+',)"\nordering="AFTER"\nside="BOTH"\n'
 files['META-INF/mods.toml']=metadata.encode();manifest=files.get('META-INF/MANIFEST.MF',b'Manifest-Version: 1.0\r\n').decode().rstrip();files['META-INF/MANIFEST.MF']=(manifest+'\r\nMixinConfigs: empirecraft_chinese.mixins.json\r\n\r\n').encode()
 files['empirecraft_chinese.mixins.json']=resources.encoded(dict(required=True,minVersion='0.8',package='ua.empirecraft.chinese.mixin',compatibilityLevel='JAVA_17',mixins=['ArmorProfiles','CombatProfiles','WeaponKinds','Enchantments','HorseAssembly','JavelinQuality'],injectors={'defaultRequire':1}))
 target=ROOT/'dist'/NAME;archive(target,files);digest=sha(target);target.with_suffix('.jar.sha256').write_text(digest+'  '+NAME+'\n')
 for folder in ['mods','server/mods']:
  for old in (REPO/folder).glob('chineseweapons-*.jar*'):old.unlink()
  shutil.copy2(target,REPO/folder/NAME);shutil.copy2(target.with_suffix('.jar.sha256'),REPO/folder/(NAME+'.sha256'))
 (ROOT/'audit/build.json').write_text(json.dumps(dict(status='passed',source_sha256=pin['sha256'],sha256=digest,java=17,all_original_textures_and_models_retained=all(files[n]==body for n,body in original.items() if n.endswith('.png') or '/models/' in n),removed_item_aliases=policy['aliases'],kept_armor=policy['armor'],horse_armor=policy['horse'],weapon_recipes=policy['weapon_recipes']),indent=2)+'\n');print('CHINESE_BUILD_PASS',digest)
if __name__=='__main__':main()
