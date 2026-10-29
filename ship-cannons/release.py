#!/usr/bin/env python3
"""Verify Ship Cannons and optionally update local ADM/lock records while the server is stopped."""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path
import time
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
MANIFEST=ROOT/'release.json'
MODS=[('empirecraft_ship_cannons','1.2.0','empirecraft-ship-cannons-1.2.0.jar','empirecraft-ship-cannons-1.1.0.jar')]

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
            for required in ['ua/empirecraft/shipcannons/RecoilController.class','ua/empirecraft/shipcannons/CBCRecoil.class','ua/empirecraft/shipcannons/GrapeshotDamage.class','ua/empirecraft/shipcannons/mixin/CarriageMixin.class']:
                if required not in z.namelist():raise ValueError('Missing ship cannon implementation: '+required)
            if any(n.startswith(('net/minecraft/','org/valkyrienskies/','rbasamoyai/')) for n in z.namelist()):raise ValueError('Bundled dependency classes')
            if b'SlingshotProjectileEntity' not in z.read('ua/empirecraft/shipcannons/mixin/CannonballTickMixin.class'):raise ValueError('Missing item projectile bridge')
        rows.append(dict(id=mid,version=version,file=name,replaces=old,sides=['client','server'],sha256=sha,sha512=hashlib.sha512(data).hexdigest(),size=len(data)))
    return rows

def prepare_adm(original,rows,sort_ids=True):
    if integrity(original)!=original['integrityHash']:raise ValueError('Existing local ADM integrity mismatch')
    result=copy.deepcopy(original)
    for row in rows:
        matches=[x for x in result['modIds'] if x['id']==row['id']]
        if len(matches)>1:raise ValueError('Duplicate ship mod ID in ADM')
        entry=dict(id=row['id'],version=row['version'],sha256=row['sha256'],filePresent=True)
        if matches:matches[0].update(entry)
        else:result['modIds'].append(entry)
    if result['modIds']!=original['modIds']:
        if sort_ids:result['modIds'].sort(key=lambda x:x['id'])
        result['lastUpdated']=int(time.time()*1000);result['integrityHash']=integrity(result)
    return result

ADM_ROW=re.compile(r'^\s*-\s+(\S+)\s+@\s+(.*?)\s+\[([a-fA-F0-9]*)\]\s+\{(file|nofile)\}\s*$')
def parse_adm(text):
    if text.lstrip('\ufeff \t\r\n').startswith('{'):return json.loads(text.lstrip('\ufeff')),False
    labels={'OS':'osName','CPU':'cpuInfo','RAM':'ramInfo','GPU':'gpuInfo','Anticheat Version':'anticheatVersion','Last Updated':'lastUpdated','Integrity Hash':'integrityHash'}
    data={'modIds':[],'resourcePackIds':[]};section=''
    for line in text.splitlines():
        if line.startswith('['):section=line
        field=re.fullmatch(r'\s*(OS|CPU|RAM|GPU|Anticheat Version|Last Updated|Integrity Hash):\s*(.*?)\s*',line)
        if field:data[labels[field[1]]]=int(field[2]) if field[1]=='Last Updated' else field[2]
        row=ADM_ROW.fullmatch(line)
        if row:data['modIds'].append(dict(id=row[1],version=row[2],sha256=row[3] or None,filePresent=row[4]=='file'))
        elif section=='[Resource Pack IDs]':
            pack=re.fullmatch(r'\s*-\s+(.*?)\s+\[([a-fA-F0-9]*)\]\s*',line)
            if pack:data['resourcePackIds'].append(dict(filename=pack[1],sha256=pack[2] or None))
    if any(key not in data for key in labels.values()) or not data['modIds']:raise ValueError('Unsupported ADM format')
    if integrity(data)!=data['integrityHash']:raise ValueError('Existing local ADM integrity mismatch')
    return data,True

def render_adm(text,original,updated,report):
    if original==updated:return text
    if not report:return json.dumps(updated,ensure_ascii=False,indent=2)+'\n'
    lines=text.splitlines(keepends=True);new={row['id']:row for row in updated['modIds']};old={row['id']:row for row in original['modIds']};last=None
    def row_text(row,ending='\n'):
        return '  - {:<30} @ {:<15} [{}] {{{}}}{}'.format(row['id'],row['version'],row['sha256'] or '', 'file' if row['filePresent'] else 'nofile',ending)
    for i,line in enumerate(lines):
        row=ADM_ROW.fullmatch(line)
        if row:
            last=i
            if old[row[1]]!=new[row[1]]:lines[i]=row_text(new[row[1]],'\r\n' if line.endswith('\r\n') else '\n')
        for label,key in [('Last Updated','lastUpdated'),('Integrity Hash','integrityHash')]:
            if re.match(r'\s*'+label+':',line):lines[i]=re.sub(r'(^\s*'+label+r':\s*)[^\r\n]*',lambda m:m[1]+str(updated[key]),line)
    if last is None:raise ValueError('Missing ADM mod report')
    added=[row_text(row) for row in updated['modIds'] if row['id'] not in old]
    lines[last+1:last+1]=added;result=''.join(lines)
    if parse_adm(result)[0]!=updated:raise ValueError('ADM report failed round-trip validation')
    return result

def prepare_lock(original,rows):
    result=copy.deepcopy(original)
    for row in rows:
        matches=[x for x in result['mods'] if x['id']==row['id']]
        if len(matches)>1:raise ValueError('Duplicate ship mod ID in lock')
        entry={k:v for k,v in row.items() if k!='replaces'}
        entry.update(source='local:ship-cannons',updated=True,pin_reason='Broadside heel, CBC/carriage recoil, deck-relative driving and wide grapeshot; see ship-cannons/README.md.')
        if matches:matches[0].update(entry);matches[0].pop('git_sha',None)
        else:
            result['mods'].append(entry)
            # This release replaces an already installed JAR; a missing lock row does not add a physical JAR.
            if not row.get('replaces'):
                for side in row['sides']:result[side+'_jar_count']+=1
    if 'updated_mod_count' in result:result['updated_mod_count']=sum(bool(row.get('updated')) for row in result['mods'])
    return result

def save_with_backup(path,data):
    backup=path.with_suffix(path.suffix+'.before-ship-cannons-1.2.0')
    if not backup.exists():backup.write_bytes(path.read_bytes())
    temporary=path.with_suffix(path.suffix+'.tmp');temporary.write_text(data if isinstance(data,str) else json.dumps(data,ensure_ascii=False,indent=2)+'\n');temporary.replace(path)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check',action='store_true')
    p.add_argument('--apply',action='store_true',help='Update local ADM/lock and disable superseded Ship Cannons JARs; stop server and clients first.')
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
        planned=[]
        if adm.exists():
            text=adm.read_text(encoding='utf-8');original,report=parse_adm(text);updated=prepare_adm(original,rows,sort_ids=not report)
            planned.append((adm,text,render_adm(text,original,updated,report)))
        lock=REPO/'modpack-lock.json'
        if lock.exists():
            old=json.loads(lock.read_text());planned.append((lock,old,prepare_lock(old,rows)))
        disable=[]
        for folder in [REPO/'mods',a.server_home/'mods']:
            for old in folder.glob('empirecraft-ship-cannons-*.jar'):
                if old.name==rows[0]['file']:continue
                disabled=old.with_suffix('.jar.disabled')
                if disabled.exists() and disabled.read_bytes()!=old.read_bytes():raise ValueError('Conflicting disabled backup: '+str(disabled))
                disable.append((old,disabled))
        if a.check and (disable or any(before!=after for _,before,after in planned)):raise SystemExit('Local records or installed versions need updating')
        if not a.check:
            for path,before,after in planned:
                if before!=after:save_with_backup(path,after)
            for old,disabled in disable:
                old.replace(disabled);old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        print('Local ship mod records verified; unrelated metadata preserved.')
    print('Ship Cannons JARs, paired copies, checksums and release manifest verified.')
if __name__=='__main__':main()

