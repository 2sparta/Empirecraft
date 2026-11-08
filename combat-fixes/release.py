#!/usr/bin/env python3
"""Ship the exact tested combat repair and its current integrity-list dependencies."""
import argparse,hashlib,io,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def sha(raw):return hashlib.sha256(raw).hexdigest()
def archive(path,files,check):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    raw=out.getvalue();checksum=sha(raw)+'  '+path.name+'\n'
    if check:assert path.read_bytes()==raw and path.with_suffix('.zip.sha256').read_text()==checksum,path
    else:path.parent.mkdir(exist_ok=True);path.write_bytes(raw);path.with_suffix('.zip.sha256').write_text(checksum)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args()
    report_path=REPO/'validation/combat-armor-recruits.json';report=json.loads(report_path.read_text())
    assert report['status']=='passed' and report['actual_server_restarts']==1
    assert len(report['results'])==2 and all(r['passed'] and r['egg_uses']==35 for r in report['results'])
    from audit import verify
    assert verify(report)==report['armor_balance_comparison']
    assert all(len([k for k in r if k.startswith('PvP after respawn at tick ')])==5 for r in report['results'])
    lock=json.loads((REPO/'modpack-lock.json').read_text());rows={r['id']:r for r in lock['mods']}
    adm_path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';adm=json.loads(adm_path.read_text())
    files={'INSTALL-UA.md':(ROOT/'README.md').read_bytes(),'validation/combat-armor-recruits.json':report_path.read_bytes(),
           'validation/trader-cut-items.json':(REPO/'validation/trader-cut-items.json').read_bytes(),
           'metadata/modpack-lock.json':(REPO/'modpack-lock.json').read_bytes(),'server/config/admanticheat/idfile/admanticheat_id_list.json':adm_path.read_bytes()}
    for mid in ['empirecraft_world_rules','empirecraft_forging','townyrecruits','locks']:
        row=rows[mid]
        for side,folder in [('client','mods'),('server','server/mods')]:
            if side not in row['sides']:continue
            path=REPO/folder/row['file'];raw=path.read_bytes();assert sha(raw)==row['sha256']==report['runtime_jars'][row['file']]
            files[side+'/mods/'+path.name]=raw;files[side+'/mods/'+path.name+'.sha256']=path.with_suffix('.jar.sha256').read_bytes()
        if 'client' in row['sides']:assert dict(id=mid,version=row['version'],sha256=row['sha256'],filePresent=True) in adm['modIds']
    for side,folder in [('client','config'),('server','server/config')]:
        path=REPO/folder/'epicknights/general.json5'
        assert '"disableLaceration": true' in path.read_text();files[side+'/config/epicknights/general.json5']=path.read_bytes()
    pack='Empirecraft-Translations-uk-ru-1.1.0.zip';path=REPO/'translations/dist'/pack
    if not any(r.get('id')=='empirecraft_pack_fixes' and r.get('filePresent') for r in adm['modIds']):assert dict(filename=pack,sha256=sha(path.read_bytes())) in adm['resourcePackIds']
    if not any(r.get('id')=='empirecraft_pack_fixes' and r.get('filePresent') for r in adm['modIds']):files['client/resourcepacks/'+pack]=path.read_bytes();files['client/resourcepacks/'+pack+'.sha256']=path.with_suffix('.zip.sha256').read_bytes()
    files['REMOVE-OLD.txt']=b'Replace empirecraft-forging-1.1.3.jar and townyrecruits-1.5.0.jar on client and server; replace empirecraft-world-rules-1.0.2.jar on server only. If the preceding Locks update is missing, remove Locks-SteelOnly-forge-1_20_1-1_2_4_nogen (1).jar and its checksum from both mods folders, keeping the included locks-steel-only JAR. Apply matching ADM and resource pack. Preserve all worlds and Towny data.\n'
    # The bundled ADM also declares the combined seasonal client mod.
    seasonal=list((REPO/'mods').glob('empirecraft-seasonal-leaves-*.jar'))
    if seasonal:
        assert len(seasonal)==1
        leaf=seasonal[0]
        for side in ['client','server']:
            files[side+'/mods/'+leaf.name]=leaf.read_bytes()
            files[side+'/mods/'+leaf.name+'.sha256']=leaf.with_suffix('.jar.sha256').read_bytes()
        files['REMOVE-OLD-SEASONAL-LEAVES.txt']=b'Remove previous empirecraft-seasonal-leaves JARs and checksums from both mods folders. Install the same included version on client and server; preserve worlds and configs.\n'
    import importlib.util as packfix_importlib
    packfix_spec=packfix_importlib.spec_from_file_location('packfix_package_support',REPO/'pack-fixes/package_support.py')
    packfix_helper=packfix_importlib.module_from_spec(packfix_spec);packfix_spec.loader.exec_module(packfix_helper)
    packfix_helper.add_updates(files,REPO)
    files['SHA256SUMS.txt']=''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    archive(ROOT/'dist/Empirecraft-Combat-Fixes-1.zip',files,args.check)
    sources={}
    for folder in ['combat-fixes','world-rules','forging-balance','towny-recruits']:
        for path in (REPO/folder).rglob('*'):
            if path.is_file() and not {'build','__pycache__','logs','crash-reports','.gradle','run'}.intersection(path.relative_to(REPO/folder).parts):
                if path.suffix in {'.log','.env'}:continue
                if 'dist' in path.relative_to(REPO/folder).parts and 'native-inputs' not in path.parts:continue
                sources[path.relative_to(REPO).as_posix()]=path.read_bytes()
    sources['validation/combat-armor-recruits.json']=report_path.read_bytes()
    sources['.github/workflows/combat-fixes.yml']=(REPO/'.github/workflows/combat-fixes.yml').read_bytes()
    archive(ROOT/'dist/Empirecraft-Combat-Fixes-1-sources.zip',sources,args.check)
    print('COMBAT_RELEASE_PASS',*[rows[mid]['sha256'] for mid in ['empirecraft_world_rules','empirecraft_forging','townyrecruits']])
if __name__=='__main__':main()
