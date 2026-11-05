"""Keep the installed native resource exclusions when rebuilding the calendar."""
import hashlib
import importlib.util
import json
import sys


def apply(root, raw, name):
    native = root.parent / 'native-content'
    if not (native / 'inputs.json').exists():
        return raw
    sys.path.insert(0, str(native))
    try:
        spec = importlib.util.spec_from_file_location('calendar_native_build', native / 'build.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
    finally:
        sys.path.pop(0)
    module.BUILD.mkdir(parents=True, exist_ok=True)
    baseline = root / 'dist/native-inputs' / name
    baseline.parent.mkdir(parents=True, exist_ok=True)
    baseline.write_bytes(raw)
    policy = json.loads((native / 'audit/policy.json').read_text())
    report = dict(deleted=[], rewritten=[], boundaries=0)
    patched = module.patch_jar(raw, policy['aliases'], set(policy['removed']), report, False)
    report.update(sha256=hashlib.sha256(patched).hexdigest(), size=len(patched))
    (root / 'build/native-release.json').write_text(json.dumps(dict(file=name,
        input_sha256=hashlib.sha256(raw).hexdigest(), sha256=report['sha256'], report=report), indent=2) + '\n')
    return patched
