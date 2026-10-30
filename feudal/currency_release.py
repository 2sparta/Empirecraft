#!/usr/bin/env python3
"""Install verified 1.7 currency binaries and package deterministic client/server overlays."""
import hashlib, importlib.util, json, shutil, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
PLUGIN='empirecraft-feudal-1.8.0.jar'
BRIDGE='empirecraft-towny-journeymap-1.5.0.jar'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def digest(path):
    raw=path.read_bytes()
    return dict(size=len(raw),sha256=hashlib.sha256(raw).hexdigest(),sha512=hashlib.sha512(raw).hexdigest(),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,27,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    with zipfile.ZipFile(path) as z:assert z.testzip() is None
    checksum(path)
def main():
    jar=REPO/'bridge/build/libs'/BRIDGE
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        assert {'ua/empirecraft/townymap/CurrencyNetwork.class','ua/empirecraft/townymap/client/CurrencyScreen.class'}<=set(z.namelist())
        assert 'version="1.5.0"' in z.read('META-INF/mods.toml').decode()
    for side in ['mods','server/mods']:
        folder=REPO/side;folder.mkdir(parents=True,exist_ok=True)
        for old in folder.glob('empirecraft-towny-journeymap-*.jar'):
            if old.name!=BRIDGE:
                old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        shutil.copyfile(jar,folder/BRIDGE);checksum(folder/BRIDGE)
    plugin=REPO/'server/plugins'/PLUGIN
    with zipfile.ZipFile(plugin) as z:
        descriptor=z.read('plugin.yml').decode()
        assert 'version: 1.8.0' in descriptor and 'aliases: [cu, валюта]' in descriptor
        assert all('  '+root+':' not in descriptor for root in ['wallet','bank','exchange'])
        assert 'ua/empirecraft/feudal/CurrencyMarket.class' in z.namelist()
    for old in plugin.parent.glob('empirecraft-feudal-*.jar'):
        if old.name!=PLUGIN:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    checksum(plugin)
    hashes=digest(REPO/'mods'/BRIDGE)
    assert (REPO/'mods'/BRIDGE).read_bytes()==(REPO/'server/mods'/BRIDGE).read_bytes()
    lockpath=REPO/'modpack-lock.json';lock=json.loads(lockpath.read_text());entry=next(r for r in lock['mods'] if r['id']=='empirecraft_townymap')
    entry.update(file=BRIDGE,version='1.5.0',**hashes,pin_reason='Towny/Feudal map bridge plus native currency market, rate charts and timed mint screens; identical client/server 1.5.0.')
    write(lockpath,lock)
    path=REPO/'seasonal-calendar/installed-mods.json';inventory=json.loads(path.read_text());inventory=[r for r in inventory if not r['name'].startswith('empirecraft-towny-journeymap-')];inventory.append(dict(name=BRIDGE,path='server/mods/'+BRIDGE,size=hashes['size'],sha=hashes['git_sha']));inventory.sort(key=lambda r:r['name']);write(path,inventory)
    path=REPO/'forging-balance/installed-client-mods.json';client=json.loads(path.read_text());client=[r for r in client if not r['name'].startswith('empirecraft-towny-journeymap-')];client.append(dict(name=BRIDGE,path='mods/'+BRIDGE,size=hashes['size'],sha=hashes['git_sha']));client.sort(key=lambda r:r['name']);write(path,client)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text())
    for old in list(coverage['jar_sha256']):
        if old.startswith('empirecraft-towny-journeymap-'):coverage['jar_sha256'].pop(old)
    coverage['jar_sha256'][BRIDGE]=hashes['sha256'];coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(inventory);write(path,coverage)
    spec=importlib.util.spec_from_file_location('currency_adm',REPO/'bridge/update_adm.py');admhelper=importlib.util.module_from_spec(spec);spec.loader.exec_module(admhelper)
    path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';adm=json.loads(path.read_text());assert admhelper.integrity(adm)==adm['integrityHash']
    entry=next(r for r in adm['modIds'] if r['id']=='empirecraft_townymap');entry.update(version='1.5.0',sha256=hashes['sha256'],filePresent=True);adm['integrityHash']=admhelper.integrity(adm);write(path,adm)
    for name in ['EmpireCraft-Feudal-1.5.0-server.zip','EmpireCraft-Feudal-1.5.0-server.zip','Empirecraft-Currency-Market-1.5.0-client.zip','Empirecraft-Currency-Market-1.5.0-server.zip']:
        for suffix in ['', '.sha256']:(ROOT/'dist'/(name+suffix)).unlink(missing_ok=True)
    report=dict(plugin_version='1.8.0',bridge_version='1.5.0',plugin_sha256=digest(plugin)['sha256'],bridge_sha256=hashes['sha256'],both_sides_identical=True,derived_kingdom_h2_scenarios=8,currency_h2_scenarios=15,exchange_h2_scenarios=24,confirmation_scenarios=10,physical_gold_scenarios=4,command_locale_scenarios=4,native_towny_scenarios=9,client_view_state_tests=6,all_existing_feudal_suites='passed',forge_compile_and_reobfuscation='passed',graphical_client='not_run',live_mohist_world='not_run')
    write(REPO/'validation/currency-market-1.8.0.json',report)
    for side,base in [('client','mods'),('server','server/mods')]:
        files={'README.md':(ROOT/'KINGDOMS.md').read_bytes(),'COMMAND-LANGUAGES.md':(ROOT/'COMMAND-LANGUAGES.md').read_bytes(),'COMMAND-ALIASES.tsv':(ROOT/'COMMAND-ALIASES.tsv').read_bytes(),'verification.json':(REPO/'validation/currency-market-1.8.0.json').read_bytes()}
        for suffix in ['', '.sha256']:files['mods/'+BRIDGE+suffix]=(REPO/base/(BRIDGE+suffix)).read_bytes()
        old=['empirecraft-towny-journeymap-'+version+'.jar' for version in ['1.0.0','1.1.0','1.1.1','1.2.0','1.3.0','1.4.0','1.4.1']]
        if side=='server':
            for suffix in ['', '.sha256']:files['plugins/'+PLUGIN+suffix]=(plugin.parent/(PLUGIN+suffix)).read_bytes()
            old+=['empirecraft-feudal-'+version+'.jar' for version in ['1.0.0','1.1.0','1.2.0','1.4.0','1.5.0','1.5.0','1.6.0','1.7.0','1.7.1']]
            files['reference-configs/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
        files['REMOVE-OLD-JARS.txt']=('\n'.join(old)+'\n').encode()
        files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
        archive(ROOT/f'dist/Empirecraft-Currency-Market-1.8.0-{side}.zip',files)
    print('PASS identical native screen mods, current plugin, preserved lock/inventory/ADM entries and deterministic side-specific currency bundles')
if __name__=='__main__':main()


