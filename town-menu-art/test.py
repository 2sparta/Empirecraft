#!/usr/bin/env python3
"""Validate every native icon resource and the paired, reversible installer."""
from pathlib import Path
import hashlib,io,json,re,subprocess,sys,tempfile,tomllib,zipfile
from PIL import Image
from release import stage,MOD_NAME,PLUGIN_NAME,VERSION
from update_installation import integrity,encode
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    release,bundle=stage();catalog=json.loads((ROOT/'icons.json').read_text());assert len(catalog)==46
    registered=re.findall(r'"([a-z_]+)"',(ROOT/'src/main/java/ua/empirecraft/menuart/IconCatalog.java').read_text())
    assert registered==list(catalog), 'Registered items and artwork catalog differ'
    with zipfile.ZipFile(ROOT/'dist'/MOD_NAME) as z:
        assert z.testzip() is None
        meta=tomllib.loads(z.read('META-INF/mods.toml').decode());assert meta['mods'][0]['modId']=='empirecraft_menu_art' and meta['mods'][0]['version']==VERSION
        assert 'displayTest' not in meta['mods'][0]
        assert all(row['side']=='BOTH' for row in meta['dependencies']['empirecraft_menu_art'])
        assert not any(n.startswith('assets/minecraft/') or n.endswith('.mixins.json') or '/textures/gui/' in n or 'TownHallRenderer' in n for n in z.namelist())
        prefix='assets/empirecraft_menu_art/'
        uk=json.loads(z.read(prefix+'lang/uk_ua.json'))
        assert uk==json.loads(z.read(prefix+'lang/en_us.json'))==json.loads(z.read(prefix+'lang/ru_ru.json'))
        assert len(uk)==len(catalog)
        for name,row in catalog.items():
            key='item.empirecraft_menu_art.'+name;assert uk[key]==row['label'] and re.search('[А-Яа-яІіЇїЄєҐґ]',uk[key])
            model=json.loads(z.read(prefix+'models/item/'+name+'.json'))
            assert model=={'parent':'minecraft:item/generated','textures':{'layer0':'empirecraft_menu_art:item/'+name}}
            image=Image.open(io.BytesIO(z.read(prefix+'textures/item/'+name+'.png')))
            assert image.size==(32,32) and image.getchannel('A').getextrema()==(255,255)
        resources=ROOT/'src/main/resources'
        for p in resources.rglob('*'):
            if p.is_file() and '/textures/gui/' not in p.as_posix():assert z.read(p.relative_to(resources).as_posix())==p.read_bytes(),p
    report=json.loads((ROOT/'audit/build.json').read_text());assert report['status']=='passed'
    assert all(sha(ROOT/'dist'/n)==h for n,h in report['sha256'].items())
    with tempfile.TemporaryDirectory() as tmp:
        base=Path(tmp);client=base/'клієнт з пробілами';server=base/'сервер з пробілами'
        (client/'mods').mkdir(parents=True);(server/'plugins').mkdir(parents=True)
        previousmod=ROOT/'dist/empirecraft-menu-art-1.0.0.jar';(client/'mods'/previousmod.name).write_bytes(previousmod.read_bytes())
        old=ROOT/'native-inputs/empirecraft-feudal-1.20.2.jar'
        target=server/'plugins'/old.name;target.write_bytes(old.read_bytes())
        db=server/'plugins/EmpirecraftFeudal/feudal.mv.db';db.parent.mkdir();db.write_bytes(b'existing-player-and-town-data')
        config=server/'plugins/EmpirecraftFeudal/config.yml';config.write_bytes(b'keep-custom-settings: true\n')
        properties=server/'server.properties';properties.write_bytes(b'view-distance=10\r\nsimulation-distance=8\r\n')
        adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
        original=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
        original['modIds']=[r for r in original['modIds'] if r['id']!='empirecraft_menu_art'];original['integrityHash']=integrity(original);adm.write_bytes(encode(original))
        args=[sys.executable,str(ROOT/'update_installation.py'),'--client',str(client),'--server',str(server),'--bundle',str(bundle)]
        first=subprocess.run(args,check=True,capture_output=True,text=True)
        assert not target.exists() and not (client/'mods'/previousmod.name).exists()
        assert (server/'.empirecraft-backups/town-icons-1.1/plugins'/old.name).read_bytes()==old.read_bytes()
        assert (client/'.empirecraft-backups/town-icons-1.1/mods'/previousmod.name).read_bytes()==previousmod.read_bytes()
        assert db.read_bytes()==b'existing-player-and-town-data' and config.read_bytes()==b'keep-custom-settings: true\n'
        assert properties.read_bytes()==b'view-distance=10\r\nsimulation-distance=8\r\n'
        updated=json.loads(adm.read_text());assert updated['integrityHash']==integrity(updated)
        assert [r for r in updated['modIds'] if r['id']!='empirecraft_menu_art']==original['modIds']
        assert updated['resourcePackIds']==original['resourcePackIds']
        assert (server/'mods'/MOD_NAME).read_bytes()==(client/'mods'/MOD_NAME).read_bytes()
        for side,root in [('client',client),('server',server)]:
            for name,h in release['files'][side].items():assert sha(root/name)==h
        before=adm.read_bytes();subprocess.run(args,check=True,capture_output=True);assert adm.read_bytes()==before
        sentinel=client/'mods'/MOD_NAME;sentinel.write_bytes(b'local-client-edit')
        invalid=json.loads(adm.read_text());invalid['integrityHash']='0'*64;adm.write_bytes(encode(invalid))
        result=subprocess.run(args,capture_output=True);assert result.returncode and sentinel.read_bytes()==b'local-client-edit'
        adm.write_bytes(before)
        unknown=server/'plugins/empirecraft-feudal-9.0.0.jar'
        with zipfile.ZipFile(unknown,'w') as z:z.writestr('plugin.yml','name: EmpirecraftFeudal\nversion: 9.0.0\n')
        result=subprocess.run(args,capture_output=True);assert result.returncode and sentinel.read_bytes()==b'local-client-edit'
        unknown.unlink()
        artifact=bundle/'server/plugins'/PLUGIN_NAME;raw=artifact.read_bytes();artifact.write_bytes(b'bad')
        try:
            result=subprocess.run(args,capture_output=True);assert result.returncode and sentinel.read_bytes()==b'local-client-edit'
        finally:artifact.write_bytes(raw)
    checks={'status':'passed','native_item_models':len(catalog),'ukrainian_labels':len(uk),'same_mod_on_both_sides':True,'no_screen_overlay_or_vanilla_resource_override':True,'old_mod_retired':True,'idempotent_install':True,'backups':True,'database_and_settings_preserved':True,'unrelated_adm_rows_preserved':True,'all_sides_preflight':True,'tampered_bundle_rejected':True,'unknown_plugin_build_rejected':True}
    (ROOT/'audit/installer-1.1.json').write_bytes(encode(checks));print('NATIVE_TOWN_ICONS_PACKAGE_AND_INSTALLER_PASS')
if __name__=='__main__':main()
