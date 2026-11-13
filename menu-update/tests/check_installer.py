#!/usr/bin/env python3
"""Check the complete bundle's backups, plugin replacement, settings patch and ADM isolation."""
import hashlib,json,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;sys.path.insert(0,str(ROOT))
from release import stage
from update_installation import integrity,encode
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    release,_,bundle=stage()
    with tempfile.TemporaryDirectory() as temp:
        base=Path(temp);client=base/'client Українська';server=base/'server with spaces'
        book=REPO/'disabled-mods/handbook-writing-2026-11-13/client/mods/empirecraft-handbook-1.0.2.jar'
        module=REPO/'mods/empirecraft-pack-fixes-1.0.1.jar'
        if not module.exists():module=next((REPO/'disabled-mods').rglob(module.name))
        plugin=REPO/'server/plugins/empirecraft-feudal-1.19.2.jar'
        if not plugin.exists():plugin=next((REPO/'disabled-mods').rglob(plugin.name))
        bridge=REPO/'mods/empirecraft-towny-journeymap-1.11.0.jar'
        if not bridge.exists():bridge=next((REPO/'disabled-mods').rglob(bridge.name))
        for root in [client,server]:
            (root/'mods').mkdir(parents=True)
            for old in [book,module,bridge]:(root/'mods'/old.name).write_bytes(old.read_bytes())
            (root/'personal-settings.txt').write_text('unchanged')
        (server/'plugins').mkdir();(server/'plugins'/plugin.name).write_bytes(plugin.read_bytes())
        db=server/'plugins/EmpirecraftFeudal/state.mv.db';db.parent.mkdir(parents=True);db.write_bytes(b'existing-player-preferences-and-state')
        properties=server/'server.properties';properties.write_bytes(b'view-distance=16\r\nsimulation-distance=16\r\nfixture-secret=keep-this\r\n')
        adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
        original=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text());selected={'empirecraft_guidebook','empirecraft_pack_fixes','empirecraft_townymap'}
        for row in original['modIds']:
            if row['id']=='empirecraft_guidebook':row.update(version='1.0.2',sha256=digest(book))
            if row['id']=='empirecraft_pack_fixes':row.update(version='1.0.1',sha256=digest(module))
        original['integrityHash']=integrity(original);adm.write_bytes(encode(original));unrelated=[r for r in original['modIds'] if r['id'] not in selected]
        args=[sys.executable,str(ROOT/'update_installation.py'),'--bundle',str(bundle),'--client',str(client),'--server',str(server)]
        subprocess.run(args,capture_output=True,check=True)
        for root,side in [(client,'client'),(server,'server')]:
            for relative,expected in release['files'][side].items():assert digest(root/relative)==expected
            for old in [book,module,bridge]:assert not (root/'mods'/old.name).exists() and (root/'.empirecraft-backups/book-menus-1/mods'/old.name).read_bytes()==old.read_bytes()
            assert (root/'personal-settings.txt').read_text()=='unchanged'
        assert not (server/'plugins'/plugin.name).exists();assert (server/'.empirecraft-backups/book-menus-1/plugins'/plugin.name).read_bytes()==plugin.read_bytes()
        assert db.read_bytes()==b'existing-player-preferences-and-state'
        assert properties.read_bytes()==b'view-distance=16\r\nsimulation-distance=8\r\nfixture-secret=keep-this\r\n'
        actual=json.loads(adm.read_text());assert actual['integrityHash']==integrity(actual)
        assert [r for r in actual['modIds'] if r['id'] not in selected]==unrelated
        assert actual['resourcePackIds']==original['resourcePackIds']
        raw=adm.read_bytes();subprocess.run(args,capture_output=True,check=True);assert adm.read_bytes()==raw
        bad=json.loads(adm.read_text());bad['integrityHash']='0'*64;adm.write_bytes(encode(bad))
        sentinel=client/'kubejs/server_scripts/zzzzzzzzz_empirecraft_paper_books.js';sentinel.write_text('local-change')
        failed=subprocess.run(args,capture_output=True);assert failed.returncode!=0 and sentinel.read_text()=='local-change'
    report={'status':'passed','mods':{r['id']:r['sha256'] for r in release['mods']},'plugins':{r['name']:r['sha256'] for r in release['plugins']},'updater_sha256':digest(ROOT/'update_installation.py'),'preserves_existing_database_bytes':True,'idempotent':True,'backups':True,'unrelated_adm_entries_preserved':True,'all_sides_preflighted':True,'server_properties_targeted_patch':{'simulation-distance':8,'view-distance':'preserved','other_keys':'preserved'},'paths_with_spaces_and_ukrainian':True}
    (ROOT/'audit/installer.json').write_bytes(encode(report));print('BOOK_MENUS_INSTALLER_PASS')
if __name__=='__main__':main()
