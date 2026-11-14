#!/usr/bin/env python3
import copy,hashlib,json,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
import release
from update_installation import parse_adm,render_adm,integrity,encode

def main():
 r,files,bundle=release.stage();repo=ROOT.parent;old=repo/'mods/empirecraft-handbook-1.0.4.jar'
 if not old.exists():old=next((repo/'disabled-mods').rglob(old.name))
 original_text=(repo/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes().decode('utf-8-sig');original,is_report=parse_adm(original_text)
 for format in ['json','report']:
  with tempfile.TemporaryDirectory() as temp:
   client=Path(temp)/'client Українська';server=Path(temp)/'server with spaces'
   for root in [client,server]:
    (root/'mods').mkdir(parents=True);(root/'mods'/old.name).write_bytes(old.read_bytes());(root/'player-data-sentinel').write_bytes(b'unchanged')
   adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
   adm.write_bytes(encode(original) if format=='json' else original_text.encode());unrelated=[v for v in original['modIds'] if v['id']!=r['id']]
   command=[sys.executable,str(ROOT/'update_installation.py'),'--client',str(client),'--server',str(server),'--bundle',str(bundle)]
   subprocess.run(command,capture_output=True,check=True)
   updated,reported=parse_adm(adm.read_text());assert reported==(format=='report');assert updated['integrityHash']==integrity(updated)
   assert [v for v in updated['modIds'] if v['id']!=r['id']]==unrelated and updated['resourcePackIds']==original['resourcePackIds']
   for root,side in [(client,'client'),(server,'server')]:
    for relative,digest in r['files'][side].items():assert hashlib.sha256((root/relative).read_bytes()).hexdigest()==digest
    assert (root/'.empirecraft-backups/paper-pottery-1/mods'/old.name).read_bytes()==old.read_bytes();assert not (root/'mods'/old.name).exists();assert (root/'player-data-sentinel').read_bytes()==b'unchanged'
   before=adm.read_bytes();subprocess.run(command,capture_output=True,check=True);assert adm.read_bytes()==before
   corrupt=copy.deepcopy(updated);corrupt['integrityHash']='0'*64;adm.write_bytes(encode(corrupt))
   sentinel=client/'kubejs/server_scripts/zzzzzzzzzz_empirecraft_sandpaper.js';sentinel.write_text('local fixture')
   failed=subprocess.run(command,capture_output=True);assert failed.returncode!=0 and sentinel.read_text()=='local fixture'
 report=dict(status='passed',sha256=r['sha256'],updater_sha256=release.sha(ROOT/'update_installation.py'),formats=['json','ADM text report'],unrelated_adm_rows_preserved=True,resource_packs_preserved=True,idempotent=True,backups=True,preflight_all_sides=True)
 (ROOT/'audit/installer.json').write_bytes(encode(report));print('PAPER_POTTERY_INSTALLER_PASS')
if __name__=='__main__':main()
