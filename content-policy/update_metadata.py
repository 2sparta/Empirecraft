#!/usr/bin/env python3
"""Update only this server policy's lock/audit records; client hashes and ADM are untouched."""
import argparse,copy,hashlib,json
from build import ROOT,REPO,NAME

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    raw=(REPO/'server/mods'/NAME).read_bytes();sha=hashlib.sha256(raw).hexdigest();blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    paths=[REPO/'modpack-lock.json',REPO/'seasonal-calendar/installed-mods.json',REPO/'seasonal-calendar/audit/coverage.json']
    old=[json.loads(p.read_text()) for p in paths];lock,inventory,coverage=copy.deepcopy(old)
    entry=next((r for r in lock['mods'] if r['id']=='empirecraft_content_policy'),None)
    if entry is None:
        entry=dict(id='empirecraft_content_policy',old_version=None,added=True,updated=False);lock['mods'].append(entry);lock['server_jar_count']+=1
    entry.update(file=NAME,sides=['server'],version='1.0.1',source='local:content-policy',size=len(raw),sha256=sha,sha512=hashlib.sha512(raw).hexdigest(),git_sha=blob,pin_reason='Retired Spawn volcano content/boots, redundant Boomsticks ores, diamond/netherite combat weapons; exact-Mixin and recipe regression tests in content-policy/.')
    record=next((r for r in inventory if r['name'].startswith('empirecraft-content-policy-')),None)
    if record is None:record={};inventory.append(record)
    record.update(name=NAME,sha=blob,size=len(raw),path='server/mods/'+NAME);inventory.sort(key=lambda r:r['name'])
    for name in list(coverage['jar_sha256']):
        if name.startswith('empirecraft-content-policy-') and name!=NAME:del coverage['jar_sha256'][name]
    coverage['jar_sha256'][NAME]=sha;coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(inventory)
    assert lock['client_jar_count']==old[0]['client_jar_count']
    assert all(row in lock['mods'] for row in old[0]['mods'] if row['id']!='empirecraft_content_policy')
    for path,before,after in zip(paths,old,[lock,inventory,coverage]):
        if args.check:assert before==after,'Stale content-policy metadata: '+str(path)
        else:path.write_text(json.dumps(after,ensure_ascii=False,indent=2)+'\n')
    print('PASS server policy lock/audit hashes; other mod and client records preserved')

if __name__=='__main__':main()
