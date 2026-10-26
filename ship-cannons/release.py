#!/usr/bin/env python3
"""Verify the two ship fixes and optionally update local ADM/lock records while the server is stopped."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import time
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
MANIFEST=ROOT/'release.json'
MODS=[('vs_sails','0.3.2-empirecraft.6','vs_sails-1.20.1-0.3.2-empirecraft.6-forge.jar','vs_sails-1.20.1-0.3.2-empirecraft.5-forge.jar'),
      ('empirecraft_ship_cannons','1.0.1','empirecraft-ship-cannons-1.0.1.jar','empirecraft-ship-cannons-1.0.0.jar')]

def integrity(data):
    def text(v):return 'null' if v is None else str(v)
    value=''.join(text(data[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    value+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    value+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()

def manifest():
    rows=[]
    for mid,version,name,old in MODS:
        data=(REPO/'mods'/name).read_bytes()
        if data!=(REPO/'server/mods'/name).read_bytes():raise ValueError('Client/server JAR mismatch: '+mid)
        sha=hashlib.sha256(data).hexdigest()
        for folder in ['mods','server/mods']:
            if (REPO/folder/(name+'.sha256')).read_text()!=sha+'  '+name+'\n':raise ValueError('Checksum sidecar mismatch')
        with zipfile.ZipFile(REPO/'mods'/name) as z:
            if z.testzip() or any('SmokeMod' in n or '/test/' in n for n in z.namelist()):raise ValueError('Invalid production JAR')
            if mid=='vs_sails':
                mass=json.loads(z.read('data/vs_sails/vs_mass/vs_sails.json'))
                if next(x['mass'] for x in mass if x.get('tag')=='vs_sails:buoys')!=100:raise ValueError('Unsafe buoy mass')
                if 'com/quintonc/vs_sails/ship/BuoyancySafety.class' not in z.namelist():raise ValueError('Missing water-only buoyancy safety')
            elif b'SlingshotProjectileEntity' not in z.read('ua/empirecraft/shipcannons/mixin/CannonballTickMixin.class'):raise ValueError('Missing item projectile bridge')
        rows.append(dict(id=mid,version=version,file=name,replaces=old,sides=['client','server'],sha256=sha,sha512=hashlib.sha512(data).hexdigest(),size=len(data)))
    return rows

def prepare_adm(original,rows):
    if integrity(original)!=original['integrityHash']:raise ValueError('Existing local ADM integrity mismatch')
    result=copy.deepcopy(original)
    for row in rows:
        matches=[x for x in result['modIds'] if x['id']==row['id']]
        if len(matches)>1:raise ValueError('Duplicate ship mod ID in ADM')
        entry=dict(id=row['id'],version=row['version'],sha256=row['sha256'],filePresent=True)
        if matches:matches[0].update(entry)
        else:result['modIds'].append(entry)
    if result['modIds']!=original['modIds']:
        result['modIds'].sort(key=lambda x:x['id']);result['lastUpdated']=int(time.time()*1000);result['integrityHash']=integrity(result)
    return result

def prepare_lock(original,rows):
    result=copy.deepcopy(original)
    for row in rows:
        matches=[x for x in result['mods'] if x['id']==row['id']]
        if len(matches)>1:raise ValueError('Duplicate ship mod ID in lock')
        entry={k:v for k,v in row.items() if k!='replaces'}
        entry.update(source='local:sails' if row['id']=='vs_sails' else 'local:ship-cannons',updated=True,pin_reason='Water-only bounded buoyancy and item cannon world transitions; see ship-cannons/README.md.')
        if matches:matches[0].update(entry);matches[0].pop('git_sha',None)
        else:
            result['mods'].append(entry)
            for side in row['sides']:result[side+'_jar_count']+=1
    return result

def save_with_backup(path,data):
    backup=path.with_suffix(path.suffix+'.before-ship-safety')
    if not backup.exists():backup.write_bytes(path.read_bytes())
    temporary=path.with_suffix(path.suffix+'.tmp');temporary.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n');temporary.replace(path)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check',action='store_true')
    p.add_argument('--apply',action='store_true',help='Update local ADM/lock and disable the two superseded JARs; stop server and clients first.')
    p.add_argument('--server-home',type=Path,default=REPO/'server')
    a=p.parse_args();rows=manifest()
    if a.check:
        if rows!=json.loads(MANIFEST.read_text()):raise SystemExit('Ship release manifest needs updating')
    else:MANIFEST.write_text(json.dumps(rows,indent=2)+'\n')
    if a.apply:
        for row in rows:
            installed=a.server_home/'mods'/row['file']
            if not installed.exists() or hashlib.sha256(installed.read_bytes()).hexdigest()!=row['sha256']:
                raise SystemExit('Install the matching new server JAR first: '+row['file'])
        adm=a.server_home/'config/admanticheat/idfile/admanticheat_id_list.json'
        original=json.loads(adm.read_text(encoding='utf-8-sig'));updated=prepare_adm(original,rows)
        planned=[(adm,original,updated)]
        lock=REPO/'modpack-lock.json'
        if lock.exists():
            old=json.loads(lock.read_text());planned.append((lock,old,prepare_lock(old,rows)))
        if a.check and any(before!=after for _,before,after in planned):raise SystemExit('Local records need updating')
        if not a.check:
            for path,before,after in planned:
                if before!=after:save_with_backup(path,after)
            for folder in [REPO/'mods',a.server_home/'mods']:
                for row in rows:
                    old=folder/row['replaces']
                    if old.exists():old.rename(old.with_suffix('.jar.disabled'))
                    old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        print('Local ship mod records verified; unrelated metadata preserved.')
    print('Ship safety JARs, paired copies, checksums and minimal release manifest verified.')
if __name__=='__main__':main()
