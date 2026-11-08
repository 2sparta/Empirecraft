#!/usr/bin/env python3
"""Package only the tool update, both current configs and its verified ADM entry."""
import hashlib,json,zipfile
from build import ROOT,REPO,NAME,archive
from update_metadata import update

def main():
    update(check=True)
    raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes()
    report=dict(addon=NAME,sha256=hashlib.sha256(raw).hexdigest(),minecraft='1.20.1',forge='47.4.23',overgeared='1.20.1-1.6.36',
        base_armor_bonus={'9_and_above':4,'8':2,'7':1.5,'other':1}, laceration_enabled=False,
        armor_speed_scale=0.8, blueprint_thresholds=[10,20,40],
        break_chances=dict(poor=1.0,well=0.5,expert=0.25,perfect=0.0,master=0.0),
        verified=['Java 17 compilation against installed native jars','78 bit-exact Efficiency III/V, mining-tick and wrong-tool comparisons','Native Forge Efficiency formula','Existing armor/recipes/projectile and blueprint regressions','Client and server Mixin transformations with ASM verification','Original broken 1.0.0 Mixin linkage regression','Broken tool guards, localized tooltip resources and throttled notices','Native content overlay and its creative guard retained','Identical client/server JARs and ADM integrity'],
        limitations=['Graphical and audible tool feedback was not exercised; full-runtime arrow names and NPC trades are covered separately in validation/trader-cut-items.json.'])
    combat_path=REPO/'validation/combat-armor-recruits.json'
    combat=json.loads(combat_path.read_text())
    assert combat['status']=='passed' and combat['runtime_jars'][NAME]==report['sha256']
    report['native_combat_checks']=sum(row['checks'] for row in combat['results'])
    report['armor_comparison']=combat['armor_balance_comparison']
    report['verified'].append('Full installed runtime, laceration migration and every registered armor item; validation/combat-armor-recruits.json')
    audit=ROOT/'audit/verification.json';audit.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    entries={'README.md':(ROOT/'README.md').read_bytes(),'verification.json':audit.read_bytes()}
    entries['validation/combat-armor-recruits.json']=combat_path.read_bytes()
    for side in ['client','server']:
        entries[side+'/mods/'+NAME]=raw
        entries[side+'/mods/'+NAME+'.sha256']=(hashlib.sha256(raw).hexdigest()+'  '+NAME+'\n').encode()
        config=REPO/('config/overgeared-common.toml' if side=='client' else 'server/config/overgeared-common.toml')
        entries[side+'/config/overgeared-common.toml']=config.read_bytes()
        general=REPO/('config/epicknights/general.json5' if side=='client' else 'server/config/epicknights/general.json5')
        entries[side+'/config/epicknights/general.json5']=general.read_bytes()
    entries['server/config/admanticheat/idfile/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    entries['REMOVE-OLD-JARS.txt']=b'Remove all older empirecraft-forging-*.jar files from client and server mods before copying this update.\n'
    target=ROOT/'dist/Empirecraft-Tools-1.1.3.zip';archive(target,entries)
    with zipfile.ZipFile(target) as z:assert z.testzip() is None and z.read('client/mods/'+NAME)==z.read('server/mods/'+NAME)
    print('PASS tool update package:',target.name,hashlib.sha256(target.read_bytes()).hexdigest())
if __name__=='__main__':main()
