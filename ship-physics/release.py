#!/usr/bin/env python3
"""Install/check the paired ship fix and preserve unrelated inventory and ADM entries."""
import argparse, hashlib, importlib.util, io, json, subprocess, sys, tomllib, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NAME='empirecraft-ship-physics-1.2.2.jar'
SAILS='vs_sails-1.20.1-0.3.2-empirecraft.19-forge.jar'
def digest(raw):return hashlib.sha256(raw).hexdigest()
def load_helper():
    spec=importlib.util.spec_from_file_location('ship_release',REPO/'ship-cannons/release.py')
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def encode(data):return json.dumps(data,ensure_ascii=False,indent=2)+'\n'
def main():
    p=argparse.ArgumentParser(description=__doc__)
    mode=p.add_mutually_exclusive_group();mode.add_argument('--apply',action='store_true');mode.add_argument('--check',action='store_true')
    a=p.parse_args();m=load_helper()
    if a.apply:subprocess.run([sys.executable,REPO/'sails/release_smooth.py','--apply'],check=True)
    raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes()
    sha=digest(raw)
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        assert z.testzip() is None
        assert any(x['modId']=='empirecraft_ship_physics' and x['version']=='1.2.2' for x in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'])
        assert 'ua/empirecraft/shipphysics/ImpactSpread.class' in z.namelist()
        assert 'ua/empirecraft/shipphysics/CollisionSounds.class' in z.namelist()
        assert not any('SmokeMod' in n or n.startswith(('net/minecraft/','org/valkyrienskies/')) for n in z.namelist())
    row=dict(id='empirecraft_ship_physics',version='1.2.2',file=NAME,sides=['client','server'],
             sha256=sha,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),
             git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),
             source='local:ship-physics',old_version='1.2.1',updated=True,
             pin_reason='Material-specific positional impact and fracture sounds after protection checks, with per-ship cooldown; retained radial damage and native contact fixes. Install with Sails 18.')
    lock=REPO/'modpack-lock.json';locked=json.loads(lock.read_text());planned=json.loads(lock.read_text())
    matches=[r for r in planned['mods'] if r['id']==row['id']];assert len(matches)<=1
    if matches:matches[0].update(row)
    else:planned['mods'].append(row)
    planned['updated_mod_count']=sum(bool(r.get('updated')) for r in planned['mods'])
    adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    text=adm.read_text();original,report=m.parse_adm(text);updated=m.prepare_adm(original,[row],sort_ids=False)
    assert original['resourcePackIds']==updated['resourcePackIds']
    assert [r for r in original['modIds'] if r['id']!=row['id']]==[r for r in updated['modIds'] if r['id']!=row['id']]
    if a.apply:
        lock.write_text(encode(planned))
        if original!=updated:adm.write_text(m.render_adm(text,original,updated,report))
        (ROOT/'release.json').write_text(encode(row))
        for folder in [REPO/'mods',REPO/'server/mods']:
            for old in folder.glob('empirecraft-ship-physics-*.jar'):
                if old.name!=NAME:
                    old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        # Refresh the Sails-only distribution after updating the paired ADM row.
        subprocess.run([sys.executable,REPO/'sails/release_smooth.py'],check=True)
    for filename,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
        path=REPO/filename;rows=json.loads(path.read_text())
        entry,=[entry for entry in rows if entry['name'].startswith('empirecraft-ship-physics-')]
        expected=dict(name=NAME,path=side+'/'+NAME,size=len(raw),sha=row['git_sha'])
        if a.apply:
            entry.update(expected);path.write_text(encode(rows))
        if a.check:assert all(entry[key]==value for key,value in expected.items()),('Stale Ship Physics inventory',filename)
    coverage=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(coverage.read_text())
    if a.apply:
        for name in list(data['jar_sha256']):
            if name.startswith('empirecraft-ship-physics-'):data['jar_sha256'].pop(name)
        data['jar_sha256'][NAME]=sha;coverage.write_text(encode(data))
    if a.check:assert {name:value for name,value in data['jar_sha256'].items() if name.startswith('empirecraft-ship-physics-')}=={NAME:sha}
    if a.check:
        assert original==updated and planned==locked,'Stale ship installation records'
        assert json.loads((ROOT/'release.json').read_text())==row
        subprocess.run([sys.executable,REPO/'sails/release_smooth.py','--check'],check=True)
    for folder in [REPO/'mods',REPO/'server/mods']:
        assert [p.name for p in folder.glob('empirecraft-ship-physics-*.jar')]==[NAME]
        assert (folder/(NAME+'.sha256')).read_text()==sha+'  '+NAME+'\n'
        assert len(list(folder.glob('*.jar')))==planned[('client' if folder.name=='mods' and folder.parent==REPO else 'server')+'_jar_count']
    entries={'INSTALL-UA.md':(REPO/'sails/WATER-SEALING.md').read_bytes(),
             'server/config/admanticheat/idfile/admanticheat_id_list.json':adm.read_bytes()}
    for side,relative in [('client','config/valkyrienskies/valkyrienskies-client.toml'),
                          ('client','defaultconfigs/valkyrienskies/vs-core-server.toml'),
                          ('server','defaultconfigs/valkyrienskies/vs-core-server.toml'),
                          ('server','config/vs_sails/config.cfg'),('server','config/vs_sails/ship-protection.json')]:
        source=REPO/relative if side=='client' else REPO/'server'/relative
        entries[side+'/'+relative]=source.read_bytes()
    for name in [NAME,SAILS]:
        content=(REPO/'mods'/name).read_bytes();assert content==(REPO/'server/mods'/name).read_bytes()
        for side in ['client','server']:
            entries[side+'/mods/'+name]=content
            entries[side+'/mods/'+name+'.sha256']=(digest(content)+'  '+name+'\n').encode()
    output=ROOT/'dist/Empirecraft-Ship-Safety-18.zip';output.parent.mkdir(exist_ok=True)
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,'w') as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,4,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    package=buffer.getvalue();checksum=digest(package)+'  '+output.name+'\n'
    if a.check:
        assert output.read_bytes()==package
        assert output.with_suffix('.zip.sha256').read_text()==checksum
    else:output.write_bytes(package);output.with_suffix('.zip.sha256').write_text(checksum)
    print('SHIP_PHYSICS_RELEASE_PASS',sha)
if __name__=='__main__':main()
