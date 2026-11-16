#!/usr/bin/env python3
"""Compile real APIs in Mojang names, then remap the standalone addon to Forge SRG."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import resources
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk');REF=Path('/workspace/Empirecraft')
NAME='empirecraft-armory-1.0.0.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def archive(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for n,raw in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,16,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def run(args,label):
 r=subprocess.run(list(map(str,args)),capture_output=True,text=True);(ROOT/'build'/f'{label}.log').write_text(r.stdout+r.stderr)
 if r.returncode:raise RuntimeError(label+'\n'+r.stderr[-10000:])
 print(label+'_PASS',flush=True)
def main():
 p=argparse.ArgumentParser();p.add_argument('--no-install',action='store_true');args=p.parse_args()
 inputs=[ROOT/'resources.py',*sorted((ROOT/'src').rglob('*'))];fingerprint={str(f.relative_to(ROOT)):sha(f) for f in inputs if f.is_file()}
 b=ROOT/'build';b.mkdir(exist_ok=True);libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm];named=b/'named';named.mkdir(exist_ok=True)
 for prefix in ['TerraFirmaCraft-','overgeared-']:
  source=next((REPO/'mods').glob(prefix+'*.jar'));target=named/source.name;pin=target.with_suffix('.source.sha256')
  if not target.exists() or not pin.exists() or pin.read_text()!=sha(source):
   target.unlink(missing_ok=True);run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',source,target,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar',*libs,'--ignoreConflicts'],'remap-'+prefix);pin.write_text(sha(source))
 access=Path('/workspace/work/flux-continuous/casting/build/compile-access.jar');assert access.exists()
 cp=[access,SDK/'runtime-named.jar',SDK/'client-named.jar',SDK/'client-original.jar',SDK/'annotations.jar',*named.glob('*.jar'),*libs,*list((REPO/'mods').glob('*.jar'))]
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 run([JDK/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'src/main/java').rglob('*.java'))],'compile')
 archive(b/'named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});output=b/'srg.jar';output.unlink(missing_ok=True)
 run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',b/'named.jar',output,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'reobf')
 with zipfile.ZipFile(output) as z:files={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
 files.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in (ROOT/'src/main/resources').rglob('*') if f.is_file()});files.update(resources.generate())
 files['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
 assert all(sha(ROOT/n)==digest for n,digest in fingerprint.items()),'Build source changed during compilation'
 files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_armory.mixins.json\r\nImplementation-Version: 1.0.0\r\n\r\n';target=ROOT/'dist'/NAME;archive(target,files);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+NAME+'\n')
 (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/build.json').write_text(json.dumps(dict(status='passed',sha256=sha(target),source_sha256=fingerprint,java=17,production_world_opened=False),indent=2)+'\n')
 if not args.no_install:
  for relative in ['mods','server/mods']:
   shutil.copy2(target,REPO/relative/NAME);shutil.copy2(target.with_suffix('.jar.sha256'),REPO/relative/(NAME+'.sha256'))
 print('ARMORY_BUILD_PASS',sha(target))
if __name__=='__main__':main()
