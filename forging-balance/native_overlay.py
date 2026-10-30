"""Apply the current native retirement policy to this addon, keeping its creative-mode guard."""
import importlib.util,hashlib,json,subprocess,sys
from pathlib import Path

def apply(root,raw,name):
    repo=root.parent;native=repo/'native-content';policy=native/'audit/policy.json'
    if not (native/'inputs.json').exists():return raw,None
    if not policy.exists():raise SystemExit('Missing current native-content/audit/policy.json; refusing to drop native guards')
    sys.path.insert(0,str(native))
    try:
        spec=importlib.util.spec_from_file_location('empirecraft_native_build',native/'build.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    finally:sys.path.pop(0)
    module.BUILD.mkdir(parents=True,exist_ok=True)
    subprocess.run(['java','com.sun.tools.javac.Main',*module.EXPORTS,'-proc:none','-d',str(module.BUILD),str(native/'Patch.java')],check=True)
    shared=module.BUILD/'forging-helper';shared.mkdir(exist_ok=True)
    subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-d',str(shared),str(native/'PolicyHooks.java')],check=True)
    baseline=root/'dist/native-inputs'/name;baseline.parent.mkdir(parents=True,exist_ok=True);baseline.write_bytes(raw)
    data=json.loads(policy.read_text());report=dict(deleted=[],rewritten=[],boundaries=0)
    patched=module.patch_jar(raw,data['aliases'],set(data['removed']),report,True,policy_helper=(shared/'TEMPLATE/PolicyHooks.class').read_bytes())
    report.update(sha256=hashlib.sha256(patched).hexdigest(),size=len(patched))
    release=dict(file=name,input_sha256=hashlib.sha256(raw).hexdigest(),sha256=report['sha256'],report=report)
    (root/'build/native-release.json').write_text(json.dumps(release,ensure_ascii=False,indent=2)+'\n')
    return patched,release
