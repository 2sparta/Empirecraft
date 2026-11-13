#!/usr/bin/env python3
"""Exercise backups, idempotency, unrelated ADM state and preflight failure safety."""
import hashlib,json,shutil,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
import build,release_writing
from update_installation import integrity

def main():
    release,files,bundle=release_writing.stage();repo=ROOT.parent
    with tempfile.TemporaryDirectory() as temporary:
        base=Path(temporary);client=base/'client Українська';server=base/'server with spaces'
        previous=repo/'disabled-mods/caupona-removal-2026-11-13/server/mods/empirecraft-handbook-1.0.1.jar'
        old_bytes=previous.read_bytes()
        for root in [client,server]:
            (root/'mods').mkdir(parents=True);(root/'mods'/previous.name).write_bytes(old_bytes)
            (root/'keep-player-settings.txt').write_text('unchanged')
        adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
        profile=json.loads((repo/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
        old=next(r for r in profile['modIds'] if r['id']==build.NS);old.update(version='1.0.1',sha256=hashlib.sha256(old_bytes).hexdigest())
        profile['integrityHash']=integrity(profile);adm.write_bytes(build.encode(profile))
        unrelated=[r for r in profile['modIds'] if r['id']!=build.NS]
        args=[sys.executable,str(ROOT/'update_installation.py'),'--client',str(client),'--server',str(server),'--bundle',str(bundle)]
        subprocess.run(args,check=True,capture_output=True)
        result=json.loads(adm.read_text());assert result['integrityHash']==integrity(result)
        assert unrelated==[r for r in result['modIds'] if r['id']!=build.NS]
        assert profile['resourcePackIds']==result['resourcePackIds']
        for root,side in [(client,'client'),(server,'server')]:
            assert (root/'.empirecraft-backups/handbook-1.0.3/mods'/previous.name).read_bytes()==old_bytes
            assert not (root/'mods'/previous.name).exists()
            assert (root/'keep-player-settings.txt').read_text()=='unchanged'
            for relative,digest in release['files'][side].items():assert hashlib.sha256((root/relative).read_bytes()).hexdigest()==digest
        before=adm.read_bytes();subprocess.run(args,check=True,capture_output=True);assert adm.read_bytes()==before
        # An invalid server profile must stop before an otherwise valid client update.
        corrupt=json.loads(adm.read_text());corrupt['integrityHash']='0'*64;adm.write_bytes(build.encode(corrupt))
        changed_path=client/'kubejs/server_scripts/zzzzzzzzz_empirecraft_paper_books.js';changed_path.write_text('local fixture\n')
        failed=subprocess.run(args,capture_output=True);assert failed.returncode!=0 and changed_path.read_text()=='local fixture\n'
    report={'status':'passed','sha256':release['sha256'],'updater_sha256':release_writing.sha(ROOT/'update_installation.py'),'backups':True,'idempotent':True,'preserves_unrelated_adm_entries':True,'preserves_resource_pack_profile':True,'preflight_all_sides_before_writes':True,'paths_with_spaces_and_ukrainian':True}
    (ROOT/'audit/writing-installer.json').write_bytes(build.encode(report));print('HANDBOOK_INSTALLER_PASS')
if __name__=='__main__':main()
