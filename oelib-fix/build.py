#!/usr/bin/env python3
import hashlib,importlib.util,json,os,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';NAME='OELib-forge-1.20.1-0.2.4.1.jar';BASE=ROOT/'native-inputs'/NAME
SHA='f475dbf5c9be9164a5a5f28a59460fb0f4b83c57c6b615d04c350c882c7c471f';ENTRY='cc/sighs/oelib/network/api/NetworkAutoRegistration.class'
def main():
 assert hashlib.sha256(BASE.read_bytes()).hexdigest()==SHA;BUILD.mkdir(exist_ok=True);java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
 flags=['--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
 subprocess.run([java,'com.sun.tools.javac.Main',*flags,'-proc:none','-d',BUILD,ROOT/'Patch.java'],check=True)
 subprocess.run([java,*flags,'-cp',BUILD,'Patch',BASE,BUILD/'patched.class'],check=True)
 sys.path.insert(0,str(REPO/'native-content'));spec=importlib.util.spec_from_file_location('oelib_native_build',REPO/'native-content/build.py');native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
 policy=json.loads((REPO/'native-content/audit/policy.json').read_text());report=dict(deleted=[],rewritten=[],boundaries=0)
 raw=native.patch_jar(BASE.read_bytes(),policy['aliases'],set(policy['removed']),report,False,{ENTRY:(BUILD/'patched.class').read_bytes()});digest=hashlib.sha256(raw).hexdigest()
 for side in ['mods','server/mods']:
  p=REPO/side/NAME;p.write_bytes(raw);p.with_suffix('.jar.sha256').write_text(digest+'  '+NAME+'\n')
  path=REPO/'native-content/audit/build.json';audit=json.loads(path.read_text());audit[side+'/'+NAME]=dict(**report,sha256=digest,size=len(raw));path.write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
 path=REPO/'native-content/retained-code.json';pins=json.loads(path.read_text());pins.setdefault(NAME,{})[ENTRY]=hashlib.sha256((BUILD/'patched.class').read_bytes()).hexdigest();path.write_text(json.dumps(pins,ensure_ascii=False,indent=2)+'\n')
 print('OELIB_BUILD_PASS',digest)
if __name__=='__main__':main()
