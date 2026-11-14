#!/usr/bin/env python3
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import resources
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
NAME='empirecraft-casting-1.1.0.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def jar(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for name,raw in sorted(files.items()):
   i=zipfile.ZipInfo(name,(2026,11,14,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
 path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def main():
 p=argparse.ArgumentParser();p.add_argument('--reference-repo',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);a=p.parse_args();ref=a.reference_repo.resolve();sdk=a.sdk.resolve();jdk=a.jdk.resolve();build=ROOT/'build';build.mkdir(exist_ok=True);logs=build/'logs';logs.mkdir(exist_ok=True)
 def run(command,name):
  r=subprocess.run(list(map(str,command)),capture_output=True,text=True);(logs/(name+'.log')).write_text(r.stdout+r.stderr)
  if r.returncode:print((r.stdout+r.stderr)[-6000:]);raise SystemExit(name+' failed')
  print(name+'_PASS',flush=True)
 libs=sorted((ref/'server/libraries').rglob('*.jar'));asm=sorted((ref/'server/libraries/org/ow2/asm').rglob('*.jar'));tool=build/'tools';tool.mkdir(exist_ok=True)
 run([jdk/'bin/javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,asm)),'-d',tool,ROOT/'tools/CompileAccess.java'],'access-compile')
 access=build/'compile-access.jar';run([jdk/'bin/java','-cp',os.pathsep.join(map(str,[tool,*asm])),'CompileAccess',sdk/'runtime-named.jar',sdk/'client-named.jar',access],'forge-access')
 remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm];deps=build/'named';deps.mkdir(exist_ok=True);named=[]
 for pattern in ['TerraFirmaCraft-*.jar','empirecraft-tfc-integration-*.jar']:
  original=next((ref/'mods').glob(pattern));target=deps/original.name
  if not target.exists():run([jdk/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',original,target,sdk/'named-srg.tiny','srg','named',sdk/'runtime-srg.jar',*libs,'--ignoreConflicts'],'remap-'+original.stem)
  named.append(target)
 fingerprint={f.relative_to(REPO).as_posix():sha(f) for f in [ROOT/'resources.py',ROOT/'metallurgy_resources.py',*sorted((ROOT/'src').rglob('*'))] if f.is_file()}
 cp=[access,sdk/'runtime-named.jar',sdk/'client-named.jar',sdk/'client-original.jar',*named,*libs,*sorted((ref/'mods').glob('*.jar'))];classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 run([jdk/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'src/main/java').rglob('*.java'))],'compile')
 input=build/'named.jar';output=build/'srg.jar';jar(input,{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});output.unlink(missing_ok=True)
 run([jdk/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',input,output,sdk/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'reobf')
 with zipfile.ZipFile(output) as z:files={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
 files.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in (ROOT/'src/main/resources').rglob('*') if f.is_file()});files.update(resources.generate(ref));files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_casting.mixins.json\r\n\r\n';artifact=ROOT/'dist'/NAME;jar(artifact,files)
 with zipfile.ZipFile(artifact) as z:
  assert z.testzip() is None and not any(n.startswith(('net/minecraft/','net/minecraftforge/','net/dries007/','org/bukkit/')) for n in z.namelist())
 assert all(sha(REPO/name)==digest for name,digest in fingerprint.items()), 'Build input changed while compiling'
 (ROOT/'audit/build.json').write_text(json.dumps(dict(status='passed',sha256=sha(artifact),java_release=17,source_sha256=fingerprint,graphical_client_tested=False,production_world_opened=False),indent=2)+'\n');print('CASTING_BUILD_PASS',sha(artifact))
if __name__=='__main__':main()
