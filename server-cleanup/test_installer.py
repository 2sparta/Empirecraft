#!/usr/bin/env python3
"""Exercise real JAR replacement, custom settings, ADM and untouched world bytes."""
import hashlib,json,shutil,tempfile
from pathlib import Path
from update_installation import apply,integrity
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def main():
    release=json.loads((ROOT/'release.json').read_text())
    def baseline(name):
        p=REPO/'server/mods'/name
        return p if p.exists() else next((REPO/'disabled-mods').rglob(name))
    with tempfile.TemporaryDirectory() as tmp:
        base=Path(tmp);server=base/'server';bundle=base/'bundle';(server/'mods').mkdir(parents=True)
        for row in release['mods']:
            p=bundle/'server/mods'/row['file'];p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'build/staged'/row['file'],p)
        shutil.copyfile(baseline('caupona-1.20.1-0.4.14.jar'),server/'mods/caupona-1.20.1-0.4.14.jar')
        shutil.copyfile(baseline('empirecraft-tfc-integration-1.0.2.jar'),server/'mods/empirecraft-tfc-integration-1.0.2.jar')
        adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
        data=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
        if not any(r['id']=='caupona' for r in data['modIds']):data['modIds'].append(dict(id='caupona',version='0.4.14',sha256=hashlib.sha256(baseline('caupona-1.20.1-0.4.14.jar').read_bytes()).hexdigest(),filePresent=True))
        data['integrityHash']=integrity(data);adm.write_text(json.dumps(data))
        untouched=[r for r in data['modIds'] if r['id'] not in {x['id'] for x in release['mods']}|{'caupona'}]
        rules=server/'config/empirecraft_world_rules-common.toml';rules.write_text('rareTreeChance = 0.027\nplants = ["supplementaries:wild_flax|temperate", "caupona:silphium_block|warm"]\n')
        config=server/'config/caupona-common.toml';config.write_text('test = true\n')
        tag=server/'kubejs/data/minecraft/tags/items/test.json';tag.parent.mkdir(parents=True);tag.write_text(json.dumps({'values':[{'id':'caupona:silphium','required':False},'minecraft:wheat']}))
        placed=server/'kubejs/data/caupona/worldgen/placed_feature/test.json';placed.parent.mkdir(parents=True);placed.write_text('{}')
        world=server/'world/region/r.0.0.mca';world.parent.mkdir(parents=True);world.write_bytes(b'WORLD MUST STAY UNTOUCHED\0'*100)
        original=world.read_bytes()
        changes=apply(server,'server',release,bundle)
        assert changes and world.read_bytes()==original
        assert not config.exists() and not placed.exists() and not (server/'mods/caupona-1.20.1-0.4.14.jar').exists()
        assert not (server/'mods/empirecraft-tfc-integration-1.0.2.jar').exists()
        assert 'rareTreeChance = 0.027' in rules.read_text() and 'caupona:' not in rules.read_text()
        assert json.loads(tag.read_text())['values']==['minecraft:wheat']
        new=json.loads(adm.read_text());assert integrity(new)==new['integrityHash']
        assert [r for r in new['modIds'] if r['id'] in {x['id'] for x in untouched}]==untouched
        assert not any(r['id']=='caupona' for r in new['modIds'])
        assert apply(server,'server',release,bundle)==[],'Installer must be idempotent'
        assert list((base/'Empirecraft-cleanup-backups/server/mods').glob('caupona*.jar'))
    print('CLEANUP_INSTALLER_PASS custom settings, ADM pins/integrity, idempotence, backups, world unchanged')
    (ROOT/'audit').mkdir(exist_ok=True)
    (ROOT/'audit/installer.json').write_text(json.dumps(dict(status='passed',mods={r['file']:r['sha256'] for r in release['mods']},
        source_sha256=hashlib.sha256((ROOT/'update_installation.py').read_bytes()).hexdigest(),
        checks=['custom settings preserved','ADM version pins and integrity','unrelated ADM entries preserved','idempotent second install','retired JARs backed up','world bytes unchanged']),indent=2)+'\n')
if __name__=='__main__':main()
