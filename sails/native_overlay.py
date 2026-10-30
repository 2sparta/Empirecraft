"""Reapply the repository's reviewed native content exclusions to every rebuilt Sails JAR."""
import hashlib,importlib.util,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def apply_native(raw):
 native=ROOT.parent/'native-content';sys.path.insert(0,str(native))
 spec=importlib.util.spec_from_file_location('sails_native_cut',native/'build.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 m.BUILD.mkdir(parents=True,exist_ok=True)
 subprocess.run(['java','com.sun.tools.javac.Main',*m.EXPORTS,'-proc:none','-d',str(m.BUILD),str(native/'Patch.java')],check=True)
 subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-d',str(m.BUILD/'shared'),str(native/'NativeAliases.java'),str(native/'PolicyHooks.java')],check=True)
 policy=json.loads((native/'audit/policy.json').read_text())
 report=dict(deleted=[],rewritten=[],boundaries=0)
 changed=m.patch_jar(raw,policy['aliases'],set(policy['removed']),report,True)
 report.update(sha256=hashlib.sha256(changed).hexdigest(),size=len(changed),input_sha256=hashlib.sha256(raw).hexdigest())
 (ROOT/'build/native-overlay.json').write_text(json.dumps(report,indent=2)+'\n')
 assert report['boundaries']>0,'Sails registration exclusions were not embedded'
 (ROOT/'build/sails-untrimmed.jar').write_bytes(raw)
 print('SAILS_NATIVE_OVERLAY_PASS',len(report['deleted']),'removed resources')
 return changed
