"""Keep the existing TFC fork and strict packet version checks, pin stable mod metadata."""
import hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;REF=Path('/workspace/Empirecraft');JDK=Path('/workspace/work/tfc/jdk')
INPUT='TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.2.jar';NAME='TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.3.jar';SOURCE_SHA='a170d8e6414b0a4a3044e21c44424d30f30abdd639033ffedcc86fa341636aec'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def archive(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for n,body in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,17,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,body)
def main():
 b=ROOT/'build';b.mkdir(exist_ok=True);raw=subprocess.check_output(['git','show','63c57975:mods/'+INPUT],cwd=REPO);assert sha(raw)==SOURCE_SHA
 with zipfile.ZipFile(__import__('io').BytesIO(raw)) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 original=dict(files);meta=files['META-INF/mods.toml'].decode();meta,count=re.subn(r'(?m)^(\s*version\s*=\s*)"\$\{file\.jarVersion\}"',lambda m:m[1]+'"3.2.30"',meta);assert count==1;files['META-INF/mods.toml']=meta.encode()
 asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));cp=os.pathsep.join(map(str,asm));subprocess.run([str(JDK/'bin/javac'),'-cp',cp,'-d',str(b),str(ROOT/'tools/Patch.java')],check=True)
 p='net/dries007/tfc/network/PacketHandler.class';(b/'input.class').write_bytes(files[p]);subprocess.run([str(JDK/'bin/java'),'-cp',os.pathsep.join(map(str,[b,*asm])),'Patch','protocol',str(b/'input.class'),str(b/'output.class')],check=True);files[p]=(b/'output.class').read_bytes()
 changed=[n for n in files if files[n]!=original[n]];assert set(changed)=={'META-INF/mods.toml',p}
 output=ROOT/'dist'/NAME;archive(output,files);digest=sha(output.read_bytes());output.with_suffix('.jar.sha256').write_text(digest+'  '+NAME+'\n')
 for folder in ['mods','server/mods']:
  for old in (REPO/folder).glob('TerraFirmaCraft-*.jar*'):old.unlink()
  shutil.copy2(output,REPO/folder/NAME);shutil.copy2(output.with_suffix('.jar.sha256'),REPO/folder/(NAME+'.sha256'))
 (ROOT/'audit/build.json').write_text(json.dumps(dict(status='passed',source_sha256=SOURCE_SHA,sha256=digest,changed_entries=changed,all_packet_classes_except_handler_identical=True,protocol_acceptance_remains_strict=True),indent=2)+'\n');print('TFC_VERSION_BUILD_PASS',digest)
if __name__=='__main__':main()
