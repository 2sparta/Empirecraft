"""Exercise the actual ZIP CLI, paired update, rollback copies, idempotence and preflight."""
import hashlib,json,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path
from build import ROOT,REPO,sha
import update_installation as u

def snapshot(p):return {str(f.relative_to(p)):sha(f) for f in p.rglob('*') if f.is_file()}
def main():
 checks=0
 def check(ok,msg):
  nonlocal checks
  checks+=1
  if not ok:raise AssertionError(msg)
 with tempfile.TemporaryDirectory(prefix='siegeworks-install-') as temp:
  t=Path(temp);bundle=t/'bundle'
  with zipfile.ZipFile(ROOT/'dist/Empirecraft-Siegeworks-1-install.zip') as z:z.extractall(bundle)
  release=json.loads((bundle/'release.json').read_text());roots={s:t/s for s in ['client','server']}
  def original(path):return subprocess.check_output(['git','show','e5de18a2:'+path],cwd=REPO)
  for side,root in roots.items():
   (root/'mods').mkdir(parents=True);(root/'world').mkdir();(root/'world/level.dat').write_bytes(b'WORLD-SENTINEL');(root/'unrelated.txt').write_bytes(b'preserve')
   for n in ['recruits-1.20.1-1.15.0.jar','siege-machines-1.20.1-forge-1.39 (2).jar','TownyRecruits.jar']+(['empirecraft-claim-guard-1.0.1.jar'] if side=='server' else []):(root/'mods'/n).write_bytes(original(('server/' if side=='server' else '')+'mods/'+n))
   for n in ['kubejs/data/empirecraft_mass/tags/blocks/timber_drive.json']+(['config/siege-machines-specs.toml','kubejs/server_scripts/5_enchantments.js','config/admanticheat/idfile/admanticheat_id_list.json'] if side=='server' else []):
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(original(('server/' if side=='server' else '')+n))
  # Repair a known stale entry only when its real server JAR matches the release.
  repairs={r['id']:r for r in release['inventory_repairs']}
  leaf=repairs['empirecraft_seasonal_leaves'];shutil.copy2(REPO/'server/mods'/leaf['file'],roots['server']/'mods'/leaf['file'])
  calendar=repairs['empirecraft_calendar'];custom=roots['server']/'mods'/calendar['file'];shutil.copy2(REPO/'server/mods'/calendar['file'],custom)
  with zipfile.ZipFile(custom,'a') as z:z.comment=b'LOCAL UNRECOGNIZED PATCH'
  adm=roots['server']/'config/admanticheat/idfile/admanticheat_id_list.json';text=adm.read_text();base,report=u.parse_adm(text);initial=json.loads(json.dumps(base));initial['modIds'].append(dict(id='user_custom_mod',version='7.0',sha256='f'*64,filePresent=True));initial['integrityHash']=u.integrity(initial);adm.write_text(u.render_adm(text,base,initial,report))
  before=snapshot(t/'server');args=[sys.executable,str(bundle/'update_installation.py'),'--client',str(roots['client']),'--server',str(roots['server'])]
  # A corrupt last server payload must stop preflight before either side changes.
  victim=bundle/'server'/next(reversed(release['files']['server']));raw=victim.read_bytes();victim.write_bytes(raw+b'corrupt');client_before=snapshot(roots['client']);r=subprocess.run(args,capture_output=True)
  check(r.returncode!=0,'Corrupt payload rejected');check(snapshot(roots['client'])==client_before and snapshot(roots['server'])==before,'No partial paired writes on corrupt payload');victim.write_bytes(raw)
  subprocess.run(args,check=True,capture_output=True)
  for side,root in roots.items():
   for n,digest in release['files'][side].items():check(sha(root/n)==digest,'Installed '+side+'/'+n)
   check(not list((root/'mods').glob('siege-machines-*')),'Retired old machine mod');check(not (root/'mods/recruits-1.20.1-1.15.0.jar').exists(),'Retired old recruits');check((root/'world/level.dat').read_bytes()==b'WORLD-SENTINEL' and (root/'unrelated.txt').read_bytes()==b'preserve','World and unrelated files intact')
   backup=root/'.empirecraft-backups/siegeworks-1';check((backup/'mods/recruits-1.20.1-1.15.0.jar').read_bytes()==original(('server/' if side=='server' else '')+'mods/recruits-1.20.1-1.15.0.jar'),'Exact old JAR backed up')
  check(not list((roots['client']/'mods').glob('empirecraft-claim-guard-*')),'Guard remains server only')
  adm=roots['server']/'config/admanticheat/idfile/admanticheat_id_list.json';after,_=u.parse_adm(adm.read_text());old=initial;ids={r['id'] for r in release['additional_mods']}|{'siegeworks','siegemachines'}|set(release['retired_adm_ids'])|{'empirecraft_seasonal_leaves'}
  check(u.integrity(after)==after['integrityHash'],'ADM integrity');check([r for r in old['modIds'] if r['id'] not in ids]==[r for r in after['modIds'] if r['id'] not in ids],'Unrelated ADM identities retained');check(not any(r['id'] in ['siegemachines','empirecraft_claim_guard'] for r in after['modIds']),'No old machine or server-only required client channel')
  check(next(r for r in after['modIds'] if r['id']=='empirecraft_seasonal_leaves')['sha256']==leaf['sha256'],'Known matching server JAR repairs stale ADM')
  check(next(r for r in after['modIds'] if r['id']=='empirecraft_calendar')==next(r for r in initial['modIds'] if r['id']=='empirecraft_calendar'),'Unknown local JAR never receives a guessed ADM hash')
  check(next(r for r in after['modIds'] if r['id']=='user_custom_mod')==initial['modIds'][-1],'Custom ADM entry preserved')
  state=snapshot(t);subprocess.run(args,check=True,capture_output=True);check(snapshot(t)==state,'Repeated update is byte-for-byte idempotent')
 report=dict(status='passed',checks=checks,zip_sha256=sha(ROOT/'dist/Empirecraft-Siegeworks-1-install.zip'));(ROOT/'audit/installer.json').write_text(json.dumps(report,indent=2)+'\n');print('SIEGEWORKS_INSTALLER_PASS',checks)
if __name__=='__main__':main()
