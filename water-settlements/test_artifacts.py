"""Check executable/resource boundaries and both installed sides, independently of builders."""
from pathlib import Path
import io,zipfile,json,hashlib,sys,tomllib
from inputs import read_base
R=Path(__file__).resolve().parent;REPO=R.parent
sys.path.insert(0,str(REPO/'handbook'));from layout import lines
old=read_base('mods/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.3.jar','a1f82645ecbad06c5ae70b9db2b4c5e39375d63ee9485e31ae04df273e279c04')
name='TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar';changed=[];checks=0
with zipfile.ZipFile(io.BytesIO(old)) as before,zipfile.ZipFile(REPO/'mods'/name) as after:
 before_names=set(before.namelist());after_names=set(after.namelist())
 for n in before_names:
  if n.endswith('/'):continue
  if before.read(n)==after.read(n):continue
  assert n.startswith('assets/tfc/patchouli_books/field_guide/'),n;changed.append(n)
 for n in after_names:
  if '/field_guide/' not in n or not n.endswith('.json') or '/entries/' not in n:continue
  if n not in changed:continue
  entry=json.loads(after.read(n));assert 'advancement' not in entry
  assert entry['pages'][0]['type']=='tfc:wrapped_text',n
  for p in entry['pages']:assert lines(p.get('text',''))<=13,(n,p)
 assert len(changed)>=200,len(changed)
 for n in after_names:
  if n not in before_names:assert n.startswith('assets/tfc/patchouli_books/field_guide/'),n
for name in ['TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar','vs_sails-1.20.1-0.3.2-empirecraft.19.4-forge.jar','TownyRecruits.jar','empirecraft-handbook-1.0.6.jar']:
 a=(REPO/'mods'/name).read_bytes();assert a==(REPO/'server/mods'/name).read_bytes(),name
 assert a==(R/'dist'/name).read_bytes(),name
 for directory in ['mods','server/mods']:assert hashlib.sha256(a).hexdigest() in (REPO/directory/(name+'.sha256')).read_text()
with zipfile.ZipFile(REPO/'mods/empirecraft-handbook-1.0.6.jar') as z:
 catalog=json.loads(z.read('empirecraft-handbook-catalog.json'));assert len(catalog['entries'])==json.loads((REPO/'handbook/audit/content.json').read_text())['entries']
 assert not any('weak_steel' in x['id'] or 'bismuth_bronze' in x['id'] for x in catalog['entries'])
assert not (REPO/'mods/empirecraft-frontier-1.0.0.jar').exists()
assert (REPO/'server/plugins/empirecraft-frontier-1.0.0.jar').read_bytes()==(R/'dist/empirecraft-frontier-1.0.0.jar').read_bytes()
report=dict(status='passed',native_tfc_resources_updated=len(changed),tfc_code_and_protocol_unchanged=True,guide_layout_max_lines=13,shared_jars_identical=4,handbook_entries=len(catalog['entries']),frontier_server_only=True)
(R/'artifacts.json').write_text(json.dumps(report,indent=2)+'\n');print('WATER_SETTLEMENTS_ARTIFACTS_PASS',json.dumps(report))
