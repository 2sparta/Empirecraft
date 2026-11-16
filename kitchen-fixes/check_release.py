"""Exercise the actual ZIP/installer: both sides, backups, ADM preservation, idempotence and corruption rejection."""
import hashlib,json,shutil,subprocess,tempfile,zipfile
from pathlib import Path
import update_installation as u
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sha=lambda b:hashlib.sha256(b).hexdigest()
pin=json.loads((ROOT/'inputs.json').read_text())
archive=ROOT/'dist/Empirecraft-Kitchen-Fixes-1-install.zip'
with tempfile.TemporaryDirectory(prefix='empirecraft-kitchen-install-') as folder:
 base=Path(folder);bundle=base/'bundle'
 with zipfile.ZipFile(archive) as z:
  assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in z.namelist());z.extractall(bundle)
 release=json.loads((bundle/'release.json').read_text());identities={release['id'],*[v['id'] for v in release['additional_mods']]}
 results={}
 for side in ['client','server']:
  root=base/side;(root/'mods').mkdir(parents=True);keep=root/'personal-notes.txt';keep.write_text('Keep unrelated files.\n')
  oldnames=list(pin['jars'])+['diegesis-forge-1.20.1-1.0.0-beta.10.jar']
  for name in oldnames:(root/'mods'/name).write_bytes(subprocess.check_output(['git','-C',str(REPO),'show',pin['source_ref']+':mods/'+name]))
  if side=='server':
   adm=root/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True);adm.write_bytes(subprocess.check_output(['git','-C',str(REPO),'show',pin['source_ref']+':server/config/admanticheat/idfile/admanticheat_id_list.json']));before,_=u.parse_adm(adm.read_text(encoding='utf-8-sig'))
  changed=u.apply(u.plan(root,side,bundle,release));assert changed
  for rel,digest in release['files'][side].items():assert sha((root/rel).read_bytes())==digest,rel
  assert not any((root/'mods'/name).exists() for name in oldnames)
  assert all((root/'.empirecraft-backups/kitchen-1/mods'/name).exists() for name in oldnames)
  assert keep.read_text()=='Keep unrelated files.\n'
  if side=='server':
   after,_=u.parse_adm(adm.read_text());assert [r for r in before['modIds'] if r['id'] not in identities]==[r for r in after['modIds'] if r['id'] not in identities]
   assert before['resourcePackIds']==after['resourcePackIds'];assert u.integrity(after)==after['integrityHash']
  assert not u.apply(u.plan(root,side,bundle,release)), 'Second install must be idempotent'
  results[side]={'installed_files':len(release['files'][side]),'backups':len(oldnames),'idempotent':True,'unrelated_files_preserved':True}
 first=next(iter(release['files']['client']));bad=bundle/'client'/first;bad.write_bytes(bad.read_bytes()+b'corruption')
 try:u.plan(base/'client','client',bundle,release)
 except ValueError as e:assert 'Bundle hash mismatch' in str(e)
 else:raise AssertionError('Corrupt payload accepted')
 report={'status':'passed','archive_sha256':sha(archive.read_bytes()),'corruption_rejected':True,'sides':results,'unrelated_adm_rows_preserved':True}
 (ROOT/'audit/installer.json').write_text(json.dumps(report,indent=2)+'\n');print('KITCHEN_INSTALLER_PASS',json.dumps(report))
