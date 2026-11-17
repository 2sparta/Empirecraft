import json,subprocess,sys,tempfile,zipfile
from pathlib import Path
from build import ROOT,NAME,sha
import update_installation as u
p=ROOT/'dist/Empirecraft-TFC-Network-Hotfix-1-install.zip'
with tempfile.TemporaryDirectory(prefix='tfc-protocol-install-') as t:
 base=Path(t);bundle=base/'bundle'
 with zipfile.ZipFile(p) as z:z.extractall(bundle)
 for line in (bundle/'SHA256SUMS.txt').read_text().splitlines():expected,n=line.split('  ',1);assert sha((bundle/n).read_bytes())==expected
 release=json.loads((bundle/'release.json').read_text())
 for side in ['client','server']:
  root=base/side;(root/'mods').mkdir(parents=True);(root/'world').mkdir();(root/'world/KEEP').write_text('KEEP')
  with zipfile.ZipFile(root/'mods/TerraFirmaCraft-old.jar','w') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="tfc"\nversion="${file.jarVersion}"\n')
  if side=='server':
   f=root/'config/admanticheat/idfile/admanticheat_id_list.json';f.parent.mkdir(parents=True);d=dict(osName='fixture',cpuInfo='fixture',ramInfo='fixture',gpuInfo='fixture',anticheatVersion='test',lastUpdated=1,resourcePackIds=[],modIds=[dict(id='keep',version='1',sha256='a'*64,filePresent=True)]);d['integrityHash']=u.integrity(d);f.write_text(json.dumps(d))
 command=[sys.executable,str(bundle/'update_installation.py'),'--client',str(base/'client'),'--server',str(base/'server')];r=subprocess.run(command,capture_output=True,text=True);assert r.returncode==0,r.stdout+r.stderr
 for side in ['client','server']:
  root=base/side;assert sha((root/'mods'/NAME).read_bytes())==release['sha256'];assert not (root/'mods/TerraFirmaCraft-old.jar').exists();assert (root/'.empirecraft-backups/tfc-protocol-1/mods/TerraFirmaCraft-old.jar').is_file();assert (root/'world/KEEP').read_text()=='KEEP'
 d,_=u.parse_adm((base/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text());assert d['modIds'][0]['id']=='keep' and d['modIds'][1]['sha256']==release['sha256'];r=subprocess.run(command,capture_output=True,text=True);assert r.returncode==0 and r.stdout.count('already current')==2
 before={str(f):sha(f.read_bytes()) for side in ['client','server'] for f in (base/side).rglob('*') if f.is_file()};f=bundle/'server/mods'/NAME;f.write_bytes(b'bad');r=subprocess.run(command,capture_output=True,text=True);assert r.returncode!=0;assert all(sha(Path(n).read_bytes())==v for n,v in before.items())
report=dict(status='passed',zip_sha256=sha(p.read_bytes()),actual_zip_cli_both_sides=True,backups=True,idempotent=True,corruption_rejected_before_both_sides_write=True,adm_integrity_and_other_rows_preserved=True,world_untouched=True);(ROOT/'audit/bundle.json').write_text(json.dumps(report,indent=2)+'\n');print('TFC_VERSION_INSTALLER_PASS')
