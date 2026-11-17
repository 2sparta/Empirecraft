#!/usr/bin/env python3
"""Validate shipping textures, pinned patch scope and reversible/idempotent installation."""
from pathlib import Path
import hashlib,json,subprocess,sys,tempfile,tomllib,zipfile
from PIL import Image
from release import stage
from update_installation import integrity,encode
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    release,bundle=stage()
    with zipfile.ZipFile(ROOT/'dist/empirecraft-menu-art-1.0.0.jar') as z:
        assert z.testzip() is None
        meta=tomllib.loads(z.read('META-INF/mods.toml').decode());assert meta['mods'][0]['modId']=='empirecraft_menu_art'
        assert not any(n.startswith('assets/minecraft/') or n.endswith('.mixins.json') for n in z.namelist())
        import io
        atlas=Image.open(io.BytesIO(z.read('assets/empirecraft_menu_art/textures/gui/controls.png')))
        panel=Image.open(io.BytesIO(z.read('assets/empirecraft_menu_art/textures/gui/town_hall.png')))
        assert atlas.size==panel.size==(256,256)
        catalog=json.loads((ROOT/'catalog.json').read_text());assert set(map(int,catalog))==set(range(742100,742144))
        for n in range(44):
            tile=atlas.crop(((n%8)*32,(n//8)*32,(n%8+1)*32,(n//8+1)*32))
            assert tile.getchannel('A').getextrema()==(255,255),n
    report=json.loads((ROOT/'audit/build.json').read_text());assert report['status']=='passed'
    assert all(sha(ROOT/'dist'/n)==h for n,h in report['sha256'].items())
    with tempfile.TemporaryDirectory() as tmp:
        base=Path(tmp);client=base/'клієнт з пробілами';server=base/'server with spaces'
        (client/'mods').mkdir(parents=True);(server/'plugins').mkdir(parents=True)
        old=REPO/'menu-update/dist/empirecraft-feudal-1.19.3.jar'
        target=server/'plugins'/old.name;target.write_bytes(old.read_bytes())
        db=server/'plugins/EmpirecraftFeudal/feudal.mv.db';db.parent.mkdir();db.write_bytes(b'existing-player-and-town-data')
        config=server/'plugins/EmpirecraftFeudal/config.yml';config.write_bytes(b'keep-custom-settings: true\n')
        properties=server/'server.properties';properties.write_bytes(b'view-distance=10\r\nsimulation-distance=8\r\n')
        adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
        original=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
        original['modIds']=[r for r in original['modIds'] if r['id']!='empirecraft_menu_art'];original['integrityHash']=integrity(original);adm.write_bytes(encode(original))
        args=[sys.executable,str(ROOT/'update_installation.py'),'--client',str(client),'--server',str(server),'--bundle',str(bundle)]
        subprocess.run(args,check=True,capture_output=True)
        assert not target.exists()
        assert (server/'.empirecraft-backups/town-hall-1/plugins'/old.name).read_bytes()==old.read_bytes()
        assert db.read_bytes()==b'existing-player-and-town-data' and config.read_bytes()==b'keep-custom-settings: true\n'
        assert properties.read_bytes()==b'view-distance=10\r\nsimulation-distance=8\r\n'
        updated=json.loads(adm.read_text());assert updated['integrityHash']==integrity(updated)
        assert [r for r in updated['modIds'] if r['id']!='empirecraft_menu_art']==original['modIds']
        assert updated['resourcePackIds']==original['resourcePackIds']
        assert not (server/'mods').exists(), 'Client-only mod must not be installed on server'
        for side,root in [('client',client),('server',server)]:
            for name,h in release['files'][side].items():assert sha(root/name)==h
        before=adm.read_bytes();subprocess.run(args,check=True,capture_output=True);assert adm.read_bytes()==before
        # Both sides must preflight before either changes.
        sentinel=client/'mods/empirecraft-menu-art-1.0.0.jar';sentinel.write_bytes(b'local-client-edit')
        invalid=json.loads(adm.read_text());invalid['integrityHash']='0'*64;adm.write_bytes(encode(invalid))
        result=subprocess.run(args,capture_output=True);assert result.returncode and sentinel.read_bytes()==b'local-client-edit'
        adm.write_bytes(before)
        unknown=server/'plugins/empirecraft-feudal-9.0.0.jar'
        with zipfile.ZipFile(unknown,'w') as z:z.writestr('plugin.yml','name: EmpirecraftFeudal\nversion: 9.0.0\n')
        result=subprocess.run(args,capture_output=True);assert result.returncode and sentinel.read_bytes()==b'local-client-edit'
        unknown.unlink()
        # Bundle tampering must also stop before touching either side.
        artifact=bundle/'server/plugins/empirecraft-feudal-1.19.4.jar';raw=artifact.read_bytes();artifact.write_bytes(b'bad')
        try:
            result=subprocess.run(args,capture_output=True);assert result.returncode and sentinel.read_bytes()==b'local-client-edit'
        finally:artifact.write_bytes(raw)
    checks={'status':'passed','44_opaque_control_tiles':True,'native_item_models_and_fonts_untouched':True,'idempotent_install':True,'backups':True,'database_and_settings_preserved':True,'unrelated_adm_rows_preserved':True,'client_only_allocation':True,'all_sides_preflight':True,'tampered_bundle_rejected':True,'unknown_plugin_build_rejected':True}
    (ROOT/'audit/installer.json').write_bytes(encode(checks));print('TOWN_HALL_PACKAGE_AND_INSTALLER_PASS')
if __name__=='__main__':main()
