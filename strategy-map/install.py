#!/usr/bin/env python3
"""Install only the tested map/army components into this checkout, preserving unrelated lock and ADM rows."""
import argparse,copy,hashlib,importlib.util,json,time,tomllib,zipfile
from pathlib import Path
from build import VERSION, FEUDAL_VERSION

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
MODS=[('empirecraft_townymap',f'empirecraft-towny-journeymap-{VERSION}.jar','empirecraft-towny-journeymap-1.12.0.jar'),
      ('townyrecruits','TownyRecruits.jar','TownyRecruits-2.0.2.jar'),
      ('recruits','recruits-1.20.1-1.15.2-empirecraft.1.jar','recruits-1.20.1-1.15.2.jar')]
PLUGIN=f'empirecraft-feudal-{FEUDAL_VERSION}.jar'

def encode(data):return (json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
def sha(raw):return hashlib.sha256(raw).hexdigest()
def mod(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        return tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'][0]

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    report=json.loads((ROOT/'dist/build-validation.json').read_text());assert report['status']=='passed'
    spec=importlib.util.spec_from_file_location('fleet_installer',REPO/'fleet-menu/update_installation.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    lockpath=REPO/'modpack-lock.json';manifestpath=REPO/'validation/installed-releases-2026-11-01.json';admpath=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    lock=json.loads(lockpath.read_text());manifest=json.loads(manifestpath.read_text());admtext=admpath.read_text(encoding='utf-8-sig');adm,isreport=helper.parse_adm(admtext)
    assert helper.integrity(adm)==adm['integrityHash']
    original_mods=copy.deepcopy(lock['mods']);original_releases=copy.deepcopy(manifest['releases']);original_adm=copy.deepcopy(adm)
    writes={};retire=[];releases=[];changed_ids={row[0] for row in MODS}
    for identity,name,baseline in MODS:
        source=ROOT/'dist'/name;raw=source.read_bytes();digest=sha(raw);assert report['artifacts'][name]==digest
        metadata=mod(source);assert metadata['modId']==identity
        rows=[row for row in lock['mods'] if row['id']==identity];records=[row for row in manifest['releases'] if row['id']==identity];assert len(rows)==len(records)==1
        oldfile=rows[0]['file'];entry=dict(rows[0]);entry.update(file=name,version=metadata['version'],source='local:strategy-map',updated=True,
            sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),
            pin_reason='Strategic JourneyMap: compact UI, historical country cards, TFC climate, optical ships and migrated Recruits routes. strategy-map/dist/build-validation.json.')
        checksum=(digest+'  '+name+'\n').encode()
        for folder in ['mods','server/mods']:
            old=REPO/folder/oldfile;target=REPO/folder/name
            if args.check:
                assert oldfile==name and target.read_bytes()==raw
                assert target.with_suffix('.jar.sha256').read_bytes()==checksum
                assert len([p for p in (REPO/folder).glob('*.jar') if identity in [m['modId'] for m in mod_list(p)]])==1
            else:
                installed=old.read_bytes()
                assert installed in [(ROOT/'native-inputs'/baseline).read_bytes(),raw] or sha(installed)==rows[0]['sha256'],str(old)
                writes[target]=raw;writes[target.with_suffix('.jar.sha256')]=checksum
                if old!=target:
                    retire.extend([old,old.with_suffix('.jar.sha256')]);relative=old.relative_to(REPO).as_posix()
                    if relative not in manifest['retired']:manifest['retired'].append(relative)
                    manifest['retired_artifacts'][relative]=(ROOT/'native-inputs'/baseline).relative_to(REPO).as_posix()
        if args.check:assert rows[0]==entry and records[0]==dict(entry,artifact='mods/'+name)
        rows[0].update(entry);records[0].clear();records[0].update(entry,artifact='mods/'+name)
        admrows=[row for row in adm['modIds'] if row['id']==identity];assert len(admrows)==1
        adm_entry=dict(id=identity,version=metadata['version'],sha256=digest,filePresent=True)
        if args.check:assert admrows[0]==adm_entry
        admrows[0].update(adm_entry);releases.append(dict(id=identity,file=name,version=metadata['version'],sha256=digest))
    raw=(ROOT/'dist'/PLUGIN).read_bytes();assert sha(raw)==report['artifacts'][PLUGIN]
    target=REPO/'server/plugins'/PLUGIN;old=REPO/'server/plugins/empirecraft-feudal-1.19.4.jar';checksum=(sha(raw)+'  '+PLUGIN+'\n').encode()
    if args.check:
        assert not old.exists() and target.read_bytes()==raw and target.with_suffix('.jar.sha256').read_bytes()==checksum
    else:
        if old.exists():assert old.read_bytes()==(ROOT/'native-inputs'/old.name).read_bytes();retire.extend([old,old.with_suffix('.jar.sha256')])
        writes[target]=raw;writes[target.with_suffix('.jar.sha256')]=checksum
    assert [row for row in lock['mods'] if row['id'] not in changed_ids]==[row for row in original_mods if row['id'] not in changed_ids]
    assert [row for row in manifest['releases'] if row['id'] not in changed_ids]==[row for row in original_releases if row['id'] not in changed_ids]
    assert [row for row in adm['modIds'] if row['id'] not in changed_ids]==[row for row in original_adm['modIds'] if row['id'] not in changed_ids]
    assert adm['resourcePackIds']==original_adm['resourcePackIds']
    if args.check:print('STRATEGY_INSTALL_CHECK_PASS: three paired mods, Feudal, checksums, metadata, lock, ADM integrity and duplicate IDs');return
    if adm!=original_adm:
        adm['lastUpdated']=max(int(time.time()*1000),int(original_adm['lastUpdated'])+1);adm['integrityHash']=helper.integrity(adm)
    lock['updated_mod_count']=sum(bool(row.get('updated')) for row in lock['mods'])
    writes[lockpath]=encode(lock);writes[manifestpath]=encode(manifest);writes[admpath]=helper.render_adm(admtext,original_adm,adm,isreport).encode()
    for path,contents in writes.items():path.write_bytes(contents)
    for path in retire:path.unlink(missing_ok=True)
    (ROOT/'dist/release.json').write_bytes(encode(dict(mods=releases,plugin=dict(file=PLUGIN,version=FEUDAL_VERSION,sha256=sha(raw)),unrelated_entries_preserved=True)))
    print('STRATEGY_INSTALL_PASS: checkout updated, unrelated mods and resource packs preserved')

def mod_list(path):
    with zipfile.ZipFile(path) as z:
        return [] if 'META-INF/mods.toml' not in z.namelist() else tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']

if __name__=='__main__':main()
