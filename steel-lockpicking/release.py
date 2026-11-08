#!/usr/bin/env python3
"""Package the verified client/server Locks overlay, translations and matching ADM."""
import argparse,hashlib,io,json,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO
def sha(raw):return hashlib.sha256(raw).hexdigest()
def archive(path,files,check):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    raw=stream.getvalue();checksum=sha(raw)+'  '+path.name+'\n'
    if check:assert path.read_bytes()==raw and path.with_suffix('.zip.sha256').read_text()==checksum,path
    else:path.parent.mkdir(exist_ok=True);path.write_bytes(raw);path.with_suffix('.zip.sha256').write_text(checksum)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    report=REPO/'validation/steel-lockpicking.json';result=json.loads(report.read_text());installed=REPO/'mods'/build.NAME
    assert result['status']=='passed' and result['installed_sha256']==sha(installed.read_bytes())
    assert result['native']['passed'] and result['old_world_locks_loaded']==3 and result['actual_server_restarts']==1
    assert result['session_checks']>=1307 and len(result['native']['checks'])>=65 and result['native_client_classes_linked']==6
    lock=json.loads((REPO/'modpack-lock.json').read_text());row=next(r for r in lock['mods'] if r['id']=='locks')
    assert row['sides']==['client','server'] and row['sha256']==result['installed_sha256'] and row['source']=='local:steel-lockpicking'
    files={'INSTALL-UA.md':(ROOT/'README.md').read_bytes(),'LOCKPICKING-CREDITS.md':(ROOT/'CREDITS.md').read_bytes(),
           'validation/steel-lockpicking.json':report.read_bytes(),'metadata/modpack-lock.json':(REPO/'modpack-lock.json').read_bytes(),
           'server/config/admanticheat/idfile/admanticheat_id_list.json':(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()}
    for side,folder in [('client','mods'),('server','server/mods')]:
        jar=REPO/folder/build.NAME;assert jar.read_bytes()==installed.read_bytes()
        files[side+'/mods/'+build.NAME]=jar.read_bytes();files[side+'/mods/'+build.NAME+'.sha256']=jar.with_suffix('.jar.sha256').read_bytes()
    # This installer carries the current shared integrity list, so carry the later
    # trader repair too when it is present in the same checkout.
    if (REPO/'trader-fix/README.md').exists():
        for mid in ['empirecraft_world_rules','empirecraft_forging']:
            dependency=next(r for r in lock['mods'] if r['id']==mid)
            for side,folder in [('client','mods'),('server','server/mods')]:
                if side not in dependency['sides']:continue
                p=REPO/folder/dependency['file'];assert sha(p.read_bytes())==dependency['sha256']
                files[side+'/mods/'+p.name]=p.read_bytes();files[side+'/mods/'+p.name+'.sha256']=p.with_suffix('.jar.sha256').read_bytes()
        files['TRADER-FIX.md']=(REPO/'trader-fix/README.md').read_bytes()
    pack='Empirecraft-Translations-uk-ru-1.1.0.zip';translation=REPO/'translations/dist'/pack
    adm=json.loads(files['server/config/admanticheat/idfile/admanticheat_id_list.json'])
    assert dict(filename=pack,sha256=sha(translation.read_bytes())) in adm['resourcePackIds']
    assert dict(id='locks',version=row['version'],sha256=row['sha256'],filePresent=True) in adm['modIds']
    files['client/resourcepacks/'+pack]=translation.read_bytes();files['client/resourcepacks/'+pack+'.sha256']=translation.with_suffix('.zip.sha256').read_bytes()
    files['REMOVE-OLD.txt']=('Remove '+build.OLD_NAME+' and its checksum from both client and server mods. Keep exactly the included Locks JAR on both sides. Keep empirecraft-locks-compat on the server. Preserve worlds, keys and configs. Apply the included resource pack and ADM with this update.\n').encode()
    # The bundled ADM also declares the combined seasonal client mod.
    seasonal=list((REPO/'mods').glob('empirecraft-seasonal-leaves-*.jar'))
    if seasonal:
        assert len(seasonal)==1
        leaf=seasonal[0]
        for side in ['client','server']:
            files[side+'/mods/'+leaf.name]=leaf.read_bytes()
            files[side+'/mods/'+leaf.name+'.sha256']=leaf.with_suffix('.jar.sha256').read_bytes()
        files['REMOVE-OLD-SEASONAL-LEAVES.txt']=b'Remove previous empirecraft-seasonal-leaves JARs and checksums from both mods folders. Install the same included version on client and server; preserve worlds and configs.\n'
    files['SHA256SUMS.txt']=''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    archive(ROOT/'dist/Empirecraft-Steel-Lockpicking-1.zip',files,args.check)
    sources={p.relative_to(REPO).as_posix():p.read_bytes() for p in ROOT.rglob('*') if p.is_file() and not {'build','dist','__pycache__'}.intersection(p.relative_to(ROOT).parts)}
    sources['validation/steel-lockpicking.json']=report.read_bytes();sources['.github/workflows/steel-lockpicking.yml']=(REPO/'.github/workflows/steel-lockpicking.yml').read_bytes()
    archive(ROOT/'dist/Empirecraft-Steel-Lockpicking-1-sources.zip',sources,args.check)
    print('STEEL_RELEASE_PASS',result['installed_sha256'])
if __name__=='__main__':main()
