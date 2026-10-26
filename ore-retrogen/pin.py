#!/usr/bin/env python3
"""Pin ore mods; optionally apply the small ADM entry to a local server config."""
import argparse
import copy
import hashlib
import json
import time
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
SULFUR='sulfur_gunpowder-1.0.0-forge-1.20.1.jar'
RETRO='empirecraft-ore-retrogen-1.1.0.jar'
ENTRY=ROOT/'ore-retrogen/adm-entry.json'
MODS=ROOT/'ore-retrogen/modpack-entries.json'

def integrity(data):
    def text(v): return 'null' if v is None else str(v)
    value=''.join(text(data[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    value+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    value+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()

def prepare_adm(original,entry):
    if integrity(original)!=original['integrityHash']:raise ValueError('Existing ADM integrityHash mismatch')
    result=copy.deepcopy(original);matches=[e for e in result['modIds'] if e['id']==entry['id']]
    if len(matches)>1:raise ValueError('Duplicate sulfur mod ID in ADM')
    if matches and matches[0]==entry:return result
    if matches:matches[0].update(entry)
    else:result['modIds'].append(copy.deepcopy(entry));result['modIds'].sort(key=lambda e:e['id'])
    result['lastUpdated']=int(time.time()*1000);result['integrityHash']=integrity(result)
    return result

def prepare_lock(original,additions):
    result=copy.deepcopy(original)
    for entry in additions:
        matches=[e for e in result['mods'] if e['id']==entry['id']]
        if len(matches)>1:raise ValueError('Duplicate ore ID in local modpack lock')
        if matches:matches[0].update(entry)
        else:
            result['mods'].append(copy.deepcopy(entry))
            for side in entry['sides']:result[side+'_jar_count']+=1
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check',action='store_true',help='Check portable pins and checksums; does not read local machine metadata.')
    p.add_argument('--apply-lock',action='store_true',help='Merge only the two ore entries into the local modpack lock.')
    p.add_argument('--apply-adm',action='store_true',help='Update the existing ADM file on this local server only, preserving all other data.')
    p.add_argument('--server-home',type=Path,default=ROOT/'server',help='Local server to update when --apply-adm is used.')
    a=p.parse_args()
    additions=[];sulfur_entry=None
    for modid,name,sides,source in [('sulfur_gunpowder',SULFUR,['client','server'],'user-upload'),('empirecraft_ore_retrogen',RETRO,['server'],'local:ore-retrogen')]:
        path=ROOT/'server/mods'/name;data=path.read_bytes();sha=hashlib.sha256(data).hexdigest()
        if modid=='sulfur_gunpowder' and (ROOT/'mods'/name).read_bytes()!=data:raise SystemExit('Sulfur server/client mismatch')
        entry=dict(id=modid,file=name,sides=sides,version='1.1.0' if modid=='empirecraft_ore_retrogen' else '1.0.0',added=True,updated=modid=='empirecraft_ore_retrogen',source=source,
            sha256=sha,sha512=hashlib.sha512(data).hexdigest(),size=len(data),
            pin_reason='One-time sulfur/zinc/quartz deposits for the imported map; see ore-retrogen/README.md.')
        additions.append(entry)
        if modid=='sulfur_gunpowder':sulfur_entry=dict(id=modid,version='1.0.0',sha256=sha,filePresent=True)
    if a.check:
        if json.loads(MODS.read_text())!=additions or json.loads(ENTRY.read_text())!=sulfur_entry:raise SystemExit('Ore mod pins need updating')
    else:
        MODS.write_text(json.dumps(additions,ensure_ascii=False,indent=2)+'\n')
        ENTRY.write_text(json.dumps(sulfur_entry,indent=2)+'\n')
    for folder,name in [('mods',SULFUR),('server/mods',SULFUR),('server/mods',RETRO)]:
        path=ROOT/folder/name;expected=hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name+'\n'
        if a.check:
            if path.with_suffix('.jar.sha256').read_text()!=expected:raise SystemExit('Checksum sidecar mismatch: '+name)
        else:path.with_suffix('.jar.sha256').write_text(expected)
    if a.apply_lock:
        path=ROOT/'modpack-lock.json';original=json.loads(path.read_text());updated=prepare_lock(original,additions)
        if a.check:
            if original!=updated:raise SystemExit('Local modpack lock needs updating')
        else:path.write_text(json.dumps(updated,ensure_ascii=False,indent=2)+'\n')
        print('Local modpack lock updated with two ore entries; other entries preserved.')
    if a.apply_adm:
        path=a.server_home/'config/admanticheat/idfile/admanticheat_id_list.json'
        original=json.loads(path.read_text(encoding='utf-8-sig'));updated=prepare_adm(original,sulfur_entry)
        if original!=updated:
            if a.check:raise SystemExit('Local ADM needs updating')
            backup=path.with_suffix('.json.before-sulfur')
            if not backup.exists():backup.write_bytes(path.read_bytes())
            temporary=path.with_suffix('.json.tmp');temporary.write_text(json.dumps(updated,ensure_ascii=False,indent=2)+'\n');temporary.replace(path)
        print('Local ADM sulfur entry and integrity verified; other data preserved.')
    print('Ore mod pins, sidecars, minimal ADM entry and sulfur copies verified.')
if __name__=='__main__':main()
