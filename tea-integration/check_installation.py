import copy,json,tempfile,subprocess,sys,zipfile
from pathlib import Path
import release
import update_installation as u
ROOT=Path(__file__).resolve().parent
r,files=release.package();checks=0
for upgrade in [False,True]:
 with tempfile.TemporaryDirectory(prefix='tea-install-') as temp:
  temp=Path(temp);bundle=temp/'bundle';bundle.mkdir()
  for n,raw in files.items():p=bundle/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
  (bundle/'release.json').write_bytes(u.encode(r))
  roots=[temp/'client Українська',temp/'server']
  for root in roots:
   (root/'mods').mkdir(parents=True);(root/'maps').mkdir();(root/'maps/personal').write_text('preserve')
   with zipfile.ZipFile(root/'mods/other.jar','w') as z:z.writestr('META-INF/mods.toml','[[mods]]\nmodId="other"\nversion="1.0"\n')
   if upgrade:(root/'mods/replaced-old.jar').write_bytes((ROOT.parent/'disabled-mods/tea-2026-11-15/client/mods/FarmersDelight-1.20.1-1.2.11a-empirecraft-no-end.1.jar').read_bytes())
  data=dict(osName='fixture',cpuInfo='fixture',ramInfo='fixture',gpuInfo='fixture',anticheatVersion='fixture',lastUpdated=1,modIds=[dict(id='other',version='1.0',sha256='1'*64,filePresent=True)],resourcePackIds=[dict(filename='personal.zip',sha256='2'*64)])
  data['integrityHash']=u.integrity(data);adm=roots[1]/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True);adm.write_bytes(u.encode(data))
  plans=[u.plan(root,side,bundle,r) for root,side in zip(roots,['client','server'])]
  for plan in plans:u.apply(plan)
  after,_=u.parse_adm(adm.read_text());assert after['modIds'][0]==data['modIds'][0] and after['resourcePackIds']==data['resourcePackIds'] and u.integrity(after)==after['integrityHash']
  saved=adm.read_bytes()
  for root,side in zip(roots,['client','server']):
   for n,digest in r['files'][side].items():assert u.sha((root/n).read_bytes())==digest
   assert (root/'maps/personal').read_text()=='preserve' and (root/'mods/other.jar').exists()
   if upgrade:assert not (root/'mods/replaced-old.jar').exists() and (root/'.empirecraft-backups/tea-1/mods/replaced-old.jar').exists()
   u.apply(u.plan(root,side,bundle,r))
  assert adm.read_bytes()==saved
  # A failed server preflight never writes the already prepared client operation.
  (bundle/next(n for n in files if n.startswith('server/mods/') and n.endswith('.jar'))).write_bytes(b'corrupt')
  sentinel=roots[0]/'mods'/r['file'];before=sentinel.read_bytes()
  result=subprocess.run([sys.executable,str(ROOT/'update_installation.py'),'--client',str(roots[0]),'--server',str(roots[1]),'--bundle',str(bundle)],capture_output=True)
  assert result.returncode!=0 and sentinel.read_bytes()==before
  checks+=1
(ROOT/'audit/installer.json').write_bytes(u.encode(dict(status='passed',cases=checks,fresh_and_upgrade=True,idempotent=True,both_sides_preflight=True,personal_files_preserved=True,unrelated_adm_rows_preserved=True)))
print('TEA_INSTALLER_PASS cases='+str(checks))
