"""Exercise the actual distributed ZIP and CLI on isolated client/server folders."""
import hashlib,json,subprocess,sys,tempfile,zipfile
from pathlib import Path
import update_installation as core
ROOT=Path(__file__).resolve().parent
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 package=ROOT/'dist/Empirecraft-Chinese-Weapons-1-install.zip'
 with tempfile.TemporaryDirectory(prefix='armory-real-zip-') as tmp:
  root=Path(tmp);bundle=root/'bundle'
  with zipfile.ZipFile(package) as archive:
   assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in archive.namelist());archive.extractall(bundle)
  checked=0
  for line in (bundle/'SHA256SUMS.txt').read_text().splitlines():
   expected,name=line.split('  ',1);assert digest(bundle/name)==expected,name;checked+=1
  release=json.loads((bundle/'release.json').read_text());rows=[release]
  for side in ['client','server']:
   target=root/side;(target/'mods').mkdir(parents=True);(target/'world').mkdir();(target/'world/KEEP').write_text('world and player sentinel')
   for mod in ['overgeared','tfc','chineseweapons']:
    with zipfile.ZipFile(target/'mods'/(mod+'-old.jar'),'w') as jar:jar.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="'+mod+'"\nversion="0.9"\n')
   if side=='server':
    adm=target/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
    original=dict(osName='fixture',cpuInfo='fixture',ramInfo='fixture',gpuInfo='fixture',anticheatVersion='fixture',lastUpdated=1,modIds=[dict(id='untouched',version='test',sha256='a'*64,filePresent=True)],resourcePackIds=[]);original['integrityHash']=core.integrity(original);adm.write_text(json.dumps(original))
  command=[sys.executable,str(bundle/'update_installation.py'),'--client',str(root/'client'),'--server',str(root/'server')]
  result=subprocess.run(command,capture_output=True,text=True);assert result.returncode==0,result.stdout+result.stderr
  for side in ['client','server']:
   target=root/side
   for row in rows:
    assert digest(target/'mods'/row['file'])==row['sha256'];assert not (target/'mods'/(row['id']+'-old.jar')).exists();assert (target/'.empirecraft-backups/chinese-weapons-1/mods'/(row['id']+'-old.jar')).is_file()
   assert (target/'world/KEEP').read_text()=='world and player sentinel'
  updated,_=core.parse_adm((root/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text());assert updated['modIds'][0]==original['modIds'][0]
  for row in rows:assert next(v for v in updated['modIds'] if v['id']==row['id'])['sha256']==row['sha256']
  repeat=subprocess.run(command,capture_output=True,text=True);assert repeat.returncode==0 and repeat.stdout.count('already current')==2,repeat.stdout+repeat.stderr
  snapshot={str(p.relative_to(root)):digest(p) for side in ['client','server'] for p in (root/side).rglob('*') if p.is_file()}
  corrupt=bundle/'server/mods'/release['file'];corrupt.write_bytes(corrupt.read_bytes()+b'corrupt');result=subprocess.run(command,capture_output=True,text=True);assert result.returncode!=0
  assert all(digest(root/name)==value for name,value in snapshot.items()),'Either side changed before all inputs were validated'
 report=dict(status='passed',zip_sha256=digest(package),files_verified=checked,actual_cli_install_both_sides=True,chinese_mod_updated=True,old_jars_backed_up=True,idempotent=True,adm_integrity_and_unrelated_rows_preserved=True,world_sentinel_preserved=True,corrupt_server_payload_prevents_both_sides_from_writing=True)
 (ROOT/'audit/bundle.json').write_text(json.dumps(report,indent=2)+'\n');print('CHINESE_BUNDLE_PASS',json.dumps(report))
if __name__=='__main__':main()
