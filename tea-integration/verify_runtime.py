"""Verify the finished native run against the exact delivered files."""
import argparse,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();runtime=a.work/'runtime'
assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file()
raw=(a.work/'native.log').read_text(errors='replace');markers=[l for l in raw.splitlines() if 'TEA_NATIVE_' in l];print('\n'.join(markers))
if 'TEA_NATIVE_PASS' not in raw or 'TEA_NATIVE_FAIL' in raw:
    print('\n'.join(l for l in raw.splitlines() if any(s in l for s in ['Exception','Error','ERROR','Missing','required','Loading']))[-11000:]);raise SystemExit(1)
scriptlog=(runtime/'logs/kubejs/server.log').read_text(errors='replace')
assert '[ERROR]' not in scriptlog,('Recipe script errors',scriptlog[-5000:])
generated=json.loads((ROOT/'audit/generated.json').read_text())
for name,digest in generated['files'].items():
    if name.endswith('.properties'):
        # Hands-On rewrites its properties at startup, adding headers/defaults.
        def properties(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))
        wanted=properties(REPO/name);actual=properties(runtime/name)
        assert all(actual.get(k)==v for k,v in wanted.items()),('Station configuration changed',name)
    elif name.startswith('config/'):
        import tomllib
        assert tomllib.loads((REPO/name).read_text())==tomllib.loads((runtime/name).read_text()),('Config changed',name)
    else:assert hashlib.sha256((runtime/name).read_bytes()).hexdigest()==digest,('Fixture resource changed during run',name)
for f in (REPO/'mods').glob('*.jar'):
    if f.name.startswith(('FarmersDelight-','diegesis-','handson_delight-','farmersrespite-')):assert f.read_bytes()==(runtime/'mods'/f.name).read_bytes(),('Fixture mod changed during run',f.name)
integrations={}
for pattern in ['TownyRecruits.jar','empirecraft-casting-*.jar']:
    f=next((REPO/'mods').glob(pattern));assert f.read_bytes()==(runtime/'mods'/f.name).read_bytes(),('Fixture integration differs',f.name)
    integrations[f.name]=hashlib.sha256(f.read_bytes()).hexdigest()
(ROOT/'audit/runtime.json').write_text(json.dumps(dict(status='passed',integration_mod_sha256=integrations,probe_sha256=hashlib.sha256((ROOT/'Probe.java').read_bytes()).hexdigest(),generated_sha256=hashlib.sha256((ROOT/'audit/generated.json').read_bytes()).hexdigest(),mod_sha256={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (REPO/'mods').glob('*.jar') if f.name.startswith(('FarmersDelight-','diegesis-','handson_delight-','farmersrespite-'))},markers=markers,production_world_opened=False,graphical_client_tested=False),indent=2)+'\n')
