#!/usr/bin/env python3
"""Synchronize the installed wildlife release with the pack's existing ledgers."""
import hashlib,json,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def write(p,value):p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def sha(raw):return hashlib.sha256(raw).hexdigest()
def main():
    release=json.loads((ROOT/'release.json').read_text());updates={r['id']:r for r in release['mods']}
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text())
    for mid,new in updates.items():
        old=next((r for r in lock['mods'] if r['id']==mid),None)
        if old is None:old=dict(id=mid,added=True);lock['mods'].append(old)
        old.update(file=new['file'],version=new['version'],sides=new['sides'],updated=True,source='uploaded:citadel' if mid=='citadel' else 'local:alexs-wildlife',pin_reason='Curated realistic Alex wildlife; Earth geographic spawn masks, Spawn ant replacement and whale yields; verified native tests and sources in alexs-wildlife/.')
    write(path,lock)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    path=REPO/'seasonal-calendar/installed-mods.json';inventory=json.loads(path.read_text());names={r['file'] for r in updates.values()}
    inventory=[r for r in inventory if not r['name'].startswith('spawn-') and r['name'] not in names]
    for row in updates.values():
        raw=(REPO/'server/mods'/row['file']).read_bytes();inventory.append(dict(name=row['file'],path='server/mods/'+row['file'],size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
    write(path,sorted(inventory,key=lambda r:r['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={n:h for n,h in coverage['jar_sha256'].items() if not n.startswith('spawn-')}
    for row in updates.values():coverage['jar_sha256'][row['file']]=row['sha256']
    coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained=json.loads(path.read_text());retained={n:v for n,v in retained.items() if not n.startswith('spawn-')}
    for row in updates.values():
        with zipfile.ZipFile(REPO/'server/mods'/row['file']) as z:retained[row['file']]={n:sha(z.read(n)) for n in sorted(z.namelist()) if n.endswith('.class')}
    write(path,retained)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text());manifest['releases']=[r for r in manifest['releases'] if r['id'] not in updates]
    rows=json.loads((REPO/'modpack-lock.json').read_text())['mods']
    for mid,row in updates.items():manifest['releases'].append(dict(next(r for r in rows if r['id']==mid),artifact='server/mods/'+row['file']))
    write(path,manifest);write(REPO/'validation/alexs-wildlife-1.json',release)
    print('WILDLIFE_METADATA_PASS')
if __name__=='__main__':main()
