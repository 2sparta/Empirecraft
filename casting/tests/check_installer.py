#!/usr/bin/env python3
import copy,hashlib,json,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
import release
from update_installation import parse_adm,render_adm,integrity,encode

def main():
 r,files,bundle=release.stage();original_text=subprocess.check_output(['git','show','origin/main:server/config/admanticheat/idfile/admanticheat_id_list.json'],cwd=ROOT.parent).decode('utf-8-sig');original,_=parse_adm(original_text)
 cases=0
 for report in [False,True]:
  for upgrade in [False,True]:
   with tempfile.TemporaryDirectory() as temp:
    client=Path(temp)/'client Українська';server=Path(temp)/'server with spaces';old='empirecraft-casting-0.9.0.jar';oldraw=None
    for root in [client,server]:
     (root/'mods').mkdir(parents=True);(root/'player-data-sentinel').write_bytes(b'unchanged')
     with zipfile.ZipFile(root/'mods/unrelated.jar','w') as z:z.writestr('META-INF/mods.toml','[[mods]]\nmodId="unrelated"\nversion="1.0.0"\n')
     with zipfile.ZipFile(root/'mods/sulfur-old.jar','w') as z:z.writestr('META-INF/mods.toml','[[mods]]\nmodId="sulfur_gunpowder"\nversion="1.0.0"\n')
     if upgrade:
      with zipfile.ZipFile(root/'mods'/old,'w') as z:z.writestr('META-INF/mods.toml','[[mods]]\nmodId="empirecraft_casting"\nversion="0.9.0"\n')
      oldraw=(root/'mods'/old).read_bytes()
    adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True);baseline=copy.deepcopy(original)
    if upgrade:
     replacement=dict(id=r['id'],version='0.9.0',sha256=hashlib.sha256(oldraw).hexdigest(),filePresent=True);existing=next((row for row in baseline['modIds'] if row['id']==r['id']),None)
     if existing is None:baseline['modIds'].append(replacement)
     else:existing.update(replacement)
     baseline['integrityHash']=integrity(baseline)
    text=render_adm(original_text,original,baseline,True) if report else encode(baseline).decode();adm.write_text(text)
    unrelated=[row for row in baseline['modIds'] if row['id'] not in {r['id'],'sulfur_gunpowder','empirecraft_ore_retrogen'}];before_files={(root/'mods/unrelated.jar'): (root/'mods/unrelated.jar').read_bytes() for root in [client,server]}
    command=[sys.executable,str(ROOT/'update_installation.py'),'--client',str(client),'--server',str(server),'--bundle',str(bundle)];subprocess.run(command,capture_output=True,check=True)
    updated,is_report=parse_adm(adm.read_text());assert is_report==report and integrity(updated)==updated['integrityHash'];assert [row for row in updated['modIds'] if row['id'] not in {r['id'],'sulfur_gunpowder','empirecraft_ore_retrogen'}]==unrelated and updated['resourcePackIds']==baseline['resourcePackIds']
    for root,side in [(client,'client'),(server,'server')]:
     for relative,digest in r['files'][side].items():assert hashlib.sha256((root/relative).read_bytes()).hexdigest()==digest
     assert (root/'player-data-sentinel').read_bytes()==b'unchanged'
     assert not (root/'mods/sulfur-old.jar').exists() and (root/'.empirecraft-backups/automatic-casting-1/mods/sulfur-old.jar').exists()
     if upgrade:assert not (root/'mods'/old).exists() and (root/'.empirecraft-backups/automatic-casting-1/mods'/old).read_bytes()==oldraw
    assert all(p.read_bytes()==raw for p,raw in before_files.items());before=adm.read_bytes();subprocess.run(command,capture_output=True,check=True);assert adm.read_bytes()==before
    broken=copy.deepcopy(updated);broken['integrityHash']='0'*64;adm.write_bytes(encode(broken));sentinel=client/('mods/'+r['file']+'.sha256');sentinel.write_text('local fixture');failed=subprocess.run(command,capture_output=True);assert failed.returncode!=0 and sentinel.read_text()=='local fixture';cases+=1
 (ROOT/'audit/installer.json').write_bytes(encode(dict(status='passed',sha256=r['sha256'],updater_sha256=release.sha(ROOT/'update_installation.py'),cases=cases,fresh_and_upgrade=True,formats=['JSON','ADM text report'],unrelated_files_and_rows_preserved=True,idempotent=True,preflight_both_sides=True,backups=True)));print('CASTING_INSTALLER_PASS cases='+str(cases))
if __name__=='__main__':main()
