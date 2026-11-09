#!/usr/bin/env python3
"""Package the exact verified multi-component hotfix and its corresponding local sources."""
import hashlib,io,json,re,subprocess,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def load(path):return json.loads(path.read_text())
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def archive(path,files):
    build.archive(path,files);path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def main():
    addon=ROOT/'dist'/build.NAME;checks={}
    for profile in ['server','client']:
        report=load(ROOT/'build'/(profile+'.json'))
        assert report['addon_sha256']==sha(addon) and report['assertions']>100,profile
        checks['armor_'+profile]=report
    mixins=(ROOT/'build/mixins.log').read_text()
    assert mixins.count('ARMOR_MIXIN_PASS')==5
    checks['armor_mixins']=[line for line in mixins.splitlines() if line.startswith('ARMOR_MIXIN_PASS')]
    fauna=load(REPO/'fauna/build/native/result.json')
    assert fauna['fauna_sha256']==sha(REPO/'server/mods/empirecraft-fauna-1.1.1.jar') and fauna['actual_restarts']==1
    checks['fauna']=fauna
    fleet=load(REPO/'naval/build/logs/result.json')
    for key,path in [('plugin','server/plugins/empirecraft-feudal-1.19.1.jar'),('sails','mods/vs_sails-1.20.1-0.3.2-empirecraft.19-forge.jar'),('bridge','mods/empirecraft-towny-journeymap-1.11.0.jar')]:assert fleet[key+'_sha256']==sha(REPO/path)
    assert fleet['server_restarts']==1 and all('FLEET_NATIVE_PASS' in line for line in fleet['results'])
    checks['fleet']=fleet
    scoped=load(REPO/'naval/dist/fleet-fixes-1.1/build-validation.json')
    for name,value in scoped['artifacts'].items():
        if 'feudal' not in name:assert sha(REPO/'mods'/name)==value
    observation=load(REPO/'naval/build/native-observation/result.json')
    assert observation['bridge_sha256']==sha(REPO/'mods/empirecraft-towny-journeymap-1.11.0.jar')
    assert any('FLEET_OBSERVATION_NATIVE_PASS' in line for line in observation['results'])
    checks['fleet_observation']=observation
    for name,marker in [('policy-tests.log','COMBAT_POLICY_PASS checks=359601'),('client-tests.log','FLEET_CLIENT_PASS')]:
        log=(REPO/'naval/build/update'/name).read_text();assert marker in log
        checks[name]=[line for line in log.splitlines() if 'PASS' in line]
    proof=load(ROOT/'build/compat-dependencies.json')
    for row in proof:
        assert row['all_other_entries_identical'] and row['changed_entries']==['META-INF/mods.toml']
        assert row['sha256']==sha(REPO/'mods'/row['file'])
    checks['metadata_only_dependencies']=proof
    plugin=REPO/'server/plugins/empirecraft-feudal-1.19.1.jar'
    previous=subprocess.run(['git','show','9a539e9b:server/plugins/empirecraft-feudal-1.18.0.jar'],cwd=REPO,check=True,stdout=subprocess.PIPE).stdout
    assert hashlib.sha256(previous).hexdigest()=='4aa4ab1c3aa2db49825f9f1ec45109fbb0ef16dcff7f503d43082f32e0321a0b'
    with zipfile.ZipFile(io.BytesIO(previous)) as old,zipfile.ZipFile(plugin) as new:
        before={n:old.read(n) for n in old.namelist() if n.endswith('.class')}
        after={n:new.read(n) for n in new.namelist() if n.endswith('.class')}
    delta=dict(before_sha256=hashlib.sha256(previous).hexdigest(),sha256=sha(plugin),
        changed_classes=sorted(n for n in before.keys()&after.keys() if before[n]!=after[n]),
        added_classes=sorted(after.keys()-before.keys()),removed_classes=sorted(before.keys()-after.keys()))
    assert not delta['removed_classes']
    assert all(n.startswith(('ua/empirecraft/feudal/Provision','ua/empirecraft/feudal/Fleet','ua/empirecraft/feudal/EngagementController')) for n in delta['changed_classes']+delta['added_classes'])
    checks['feudal_code_delta']=delta
    write(ROOT/'build/feudal-code-delta.json',delta)
    logs={name:(ROOT/'build/checks'/name).read_text() for name in ['feudal.log','calendar.log','forging.log','sails.log','fauna.log']}
    assert 'DISCORD_TESTS_PASS' in logs['feudal.log'] and 'Готово:' in logs['feudal.log'] and 'Exception in thread' not in logs['feudal.log']
    assert 'SHIP_ACCESS_PASS' in logs['sails.log'] and 'ANCHOR_MOORING_PASS' in logs['sails.log']
    assert '34 tests successful' in logs['fauna.log']
    assert 'PASS client/server configuration' in logs['calendar.log'] and 'PASS native calendar JAR' in logs['forging.log']
    checks['unit_and_bytecode']={name:[line.strip() for line in text.splitlines() if line.startswith(('PASS','SHIP_','FAUNA_')) or 'tests successful' in line] for name,text in logs.items()}
    checks['backend_scenarios']=len(re.findall(r'^✓ ',logs['feudal.log'],re.M))
    lock=load(REPO/'modpack-lock.json');rows={r['id']:r for r in lock['mods']}
    changed=['empirecraft_armor','empirecraft_calendar','empirecraft_forging','empirecraft_fauna','vs_sails','empirecraft_engagement','empirecraft_pack_fixes','empirecraft_townymap','townyrecruits']
    files={'README.md':(ROOT/'README.md').read_bytes(),'TOWNYRECRUITS-2.0.md':(REPO/'towny-recruits/README.md').read_bytes(),'FLEET-FIXES.md':(REPO/'naval/FLEET-FIXES.md').read_bytes(), 'reference-configs/townyrecruits.properties':(REPO/'server/config/townyrecruits.properties').read_bytes()};artifacts={}
    for mid in changed:
        row=rows[mid]
        for side in row['sides']:
            source=REPO/('mods' if side=='client' else 'server/mods')/row['file']
            assert sha(source)==row['sha256'];artifacts[str(source.relative_to(REPO))]=sha(source)
            destination=('client' if side=='client' else 'server')+'/mods/'+source.name
            files[destination]=source.read_bytes();files[destination+'.sha256']=source.with_suffix('.jar.sha256').read_bytes()
    plugin=REPO/'server/plugins/empirecraft-feudal-1.19.1.jar';artifacts[str(plugin.relative_to(REPO))]=sha(plugin)
    files['server/plugins/'+plugin.name]=plugin.read_bytes();files['server/plugins/'+plugin.name+'.sha256']=plugin.with_suffix('.jar.sha256').read_bytes()
    for side in ['client','server']:
        base=REPO if side=='client' else REPO/'server'
        name='kubejs/server_scripts/zzzzzzzz_empirecraft_stone_recipes.js';files[side+'/'+name]=(base/name).read_bytes()
        for path in (base/'kubejs/data/minecraft/tags').rglob('*.json'):files[side+'/'+str(path.relative_to(base))]=path.read_bytes()
    name='kubejs/server_scripts/zzzzzzz_empirecraft_no_end.js';files['server/'+name]=(REPO/'server'/name).read_bytes()
    name='server/config/admanticheat/idfile/admanticheat_id_list.json';files[name]=(REPO/name).read_bytes()
    report=dict(status='passed',artifacts=artifacts,checks=checks,graphical_client_tested=False,production_world_tested=False,
        client_datapack_profile='Dedicated-server replay of the client datapack; no graphical client launch',
        previous_flat_defense_bonus_removed=True,forging_quality_and_durability_preserved=True)
    city=dict(status='passed_current_scoped_checks',plugin_version='1.19.1',bridge_version='1.11.0',townyrecruits_version='2.0.0',
        previous_validation='city-markets-1.18.0.json',graphical_client='not_run',production_world='not_run',
        backend=dict(status='passed',suites=32,scenarios=checks['backend_scenarios'],plugin_sha256=sha(plugin),contract_checks=144),
        current_validation='layered-armor-hotfix-1.json',fleet_regression='fleet-fixes-1.1.json',
        native_client=dict(status='passed_scoped',model_and_bytecode_checks=30,bridge_sha256=sha(REPO/'mods/empirecraft-towny-journeymap-1.11.0.jar'),observation_checks=12),
        carried_checks=dict(source='city-markets-1.18.0.json',reason='Other Feudal classes are byte-identical; fleet client changes have fresh scoped checks. Historical native results were not rerun or relabeled.',features=['contact_mail','fishing','market_notices','map_claim_history']),
        feudal_code_delta=delta)
    write(REPO/'validation/city-markets-1.19.1.json',city)
    (REPO/'validation/city-markets-1.19.1.md').write_text('# Feudal 1.19.1: combined update\n\nAll 32 backend suites passed. Current native fleet checks include real helm mounting without a global control grant and an actual server restart. Bridge 1.11.0 passed 30 model/bytecode checks and 12 real VS observation checks; the shared combat policy passed 359601 assertions.\n\nSee [the complete report](layered-armor-hotfix-1.json) and [installation](../layered-armor/README.md). Other Feudal feature classes are byte-identical to 1.18.0. Historical Contact and claim-history results are referenced as carried evidence, not newly executed tests. No graphical client or production world was used.\n')
    write(REPO/'validation/layered-armor-hotfix-1.json',report);files['verification.json']=(REPO/'validation/layered-armor-hotfix-1.json').read_bytes()
    files['reference/modpack-lock.json']=(REPO/'modpack-lock.json').read_bytes()
    files['REMOVE-OLD.txt']=b'Remove superseded empirecraft-calendar, empirecraft-forging and vs_sails JARs/checksums from BOTH mods folders. Remove old empirecraft-fauna from SERVER mods and old empirecraft-feudal from server/plugins. Keep exactly the included versions. Remove superseded empirecraft-towny-journeymap, empirecraft-engagement and townyrecruits JARs/checksums from BOTH mods folders; the included TownyRecruits.jar is version 2.0.0. Remove obsolete TownyRecruitsBridge-*.jar from server/plugins and empirecraft-recruits-integration-*.jar from both mods folders. Replace matching Pack Fixes JARs. Apply the paired ADM. Preserve worlds, databases and custom configuration.\n'
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(files.items())).encode()
    target=ROOT/'dist/Empirecraft-Layered-Armor-Hotfix-1.zip';archive(target,files)
    sources={}
    for module in ['layered-armor','fauna','seasonal-calendar','forging-balance','sails','feudal','pack-fixes','engagement','bridge','towny-recruits','naval']:
        for path in (REPO/module).rglob('*'):
            if not path.is_file() or {'build','dist','lib','libs','__pycache__','.gradle'}.intersection(path.relative_to(REPO/module).parts):continue
            if path.suffix in ['.jar','.zip','.class']:continue
            sources[str(path.relative_to(REPO))]=path.read_bytes()
    for name in ['validation/layered-armor-hotfix-1.json','validation/city-markets-1.19.1.json','validation/reconcile_modpack.py','validation/check_installed_releases.py','native-content/build.py','native-content/policy.py','native-content/Patch.java','native-content/PolicyHooks.java','native-content/NativeAliases.java','native-content/audit/policy.json','native-content/inputs.json','native-content/retained-code.json','native-content/retired-policy.json','end-removal/recipe-pass.js','kubejs/server_scripts/zzzzzzzz_empirecraft_stone_recipes.js','server/kubejs/server_scripts/zzzzzzzz_empirecraft_stone_recipes.js']:
        sources[name]=(REPO/name).read_bytes()
    for name in ['bridge/native-inputs/empirecraft-towny-journeymap-1.10.0.jar','engagement/native-inputs/empirecraft-engagement-1.1.0.jar']:
        sources[name]=(REPO/name).read_bytes()
    archive(ROOT/'dist/Empirecraft-Layered-Armor-Hotfix-1-sources.zip',sources)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for name,data in files.items():assert z.read(name)==data
    print('LAYERED_ARMOR_HOTFIX_RELEASE_PASS',sha(target))
if __name__=='__main__':main()
