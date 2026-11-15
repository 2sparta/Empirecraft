"""Keep the existing native exclusions when upgrading Farmer's Delight for Hands-On."""
import hashlib, importlib.util, json, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
FD = 'FarmersDelight-1.20.1-1.3.4-empirecraft.1.jar'
INPUTS = {
    'FarmersDelight-1.20.1-1.3.4.jar': '34f03eb671ea1ca579b00c98e4541404cb96d1a292014aed9cc5eb45ad8afbce',
    'diegesis-forge-1.20.1-1.0.0-beta.10.jar': '2b7c9f5b24190c70cbc15e6233731680c2e23622f154346b2ede7c4fb8666bed',
    'handson_delight-forge-1.20.1-1.5.0.jar': '7b581e265ac60d0af6a8c5480fe0d6ceb8608a9109573eae5203d20cabb8f373',
    'farmersrespite-1.20.1-2.1.2.jar': '21c15ef71f66684cbd94080e04649741bdf2b2ea6a6d542783e8961365d090bb',
}

def main():
    for name, expected in INPUTS.items():
        assert hashlib.sha256((ROOT/'upstream'/name).read_bytes()).hexdigest() == expected, name
    native = REPO/'native-content'
    sys.path.insert(0, str(native))
    spec = importlib.util.spec_from_file_location('tea_native_build', native/'build.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.BUILD = ROOT/'build/native'
    module.BUILD.mkdir(parents=True, exist_ok=True)
    subprocess.run(['java', 'com.sun.tools.javac.Main', *module.EXPORTS, '-proc:none', '-d', str(module.BUILD), str(native/'Patch.java')], check=True)
    subprocess.run(['javac', '--release', '17', '-proc:none', '-d', str(module.BUILD/'shared'), str(native/'PolicyHooks.java')], check=True)
    source = ROOT/'upstream/FarmersDelight-1.20.1-1.3.4.jar'
    policy = json.loads((native/'audit/policy.json').read_text())
    report = dict(deleted=[], rewritten=[], boundaries=0)
    raw = module.patch_jar(source.read_bytes(), policy['aliases'], set(policy['removed']), report)
    # Keep upstream mod metadata/version; the filename identifies our native overlay.
    baseline=ROOT/'dist/native-inputs'/FD
    baseline.parent.mkdir(parents=True,exist_ok=True);baseline.write_bytes(source.read_bytes())
    binaries = {FD: raw}
    for pattern in ['diegesis-*.jar', 'handson_delight-*.jar']:
        p = next((ROOT/'upstream').glob(pattern)); binaries[p.name] = p.read_bytes()
    for folder, side in [(REPO/'mods', 'client'), (REPO/'server/mods', 'server')]:
        for old in folder.glob('FarmersDelight-*.jar'):
            if old.name != FD:
                backup = REPO/'disabled-mods/tea-2026-11-15'/side/'mods'; backup.mkdir(parents=True, exist_ok=True)
                shutil.move(old, backup/old.name)
                checksum = old.with_suffix('.jar.sha256')
                if checksum.exists(): shutil.move(checksum, backup/checksum.name)
        for name, value in binaries.items():
            (folder/name).write_bytes(value)
            (folder/(name+'.sha256')).write_text(hashlib.sha256(value).hexdigest()+'  '+name+'\n')
    report.update(original_sha256=hashlib.sha256(source.read_bytes()).hexdigest(), installed_sha256=hashlib.sha256(raw).hexdigest(), upstream_url='https://cdn.modrinth.com/data/R2OftAxM/versions/SiIpcZzM/FarmersDelight-1.20.1-1.3.4.jar', native_policy_sha256=hashlib.sha256((native/'audit/policy.json').read_bytes()).hexdigest(), uploaded_respite_preserved_as_existing_patched_build=True)
    (ROOT/'audit/upstream.json').write_text(json.dumps(report, indent=2)+'\n')
    print('TEA_BUILD_PASS binaries='+str(len(binaries)))

if __name__ == '__main__': main()
