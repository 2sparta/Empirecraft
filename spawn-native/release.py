#!/usr/bin/env python3
"""Publish exact Spawn hashes, preserving unrelated lock and ADM records."""
import argparse,copy,hashlib,json,time
from build import ROOT,REPO,NAME,ORIGINAL,VERSION
def write(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n')
def integrity(d):
    def text(v):return 'null' if v is None else str(v)
    s=''.join(text(d[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    s+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in d['modIds'])
    s+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in d['resourcePackIds'])
    return hashlib.sha256(s.encode()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
    raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes()
    sha=hashlib.sha256(raw).hexdigest()
    row=dict(id='spawn',version=VERSION,file=NAME,replaces=ORIGINAL,sides=['client','server'],sha256=sha,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw))
    lockpath=REPO/'modpack-lock.json';oldlock=json.loads(lockpath.read_text());lock=copy.deepcopy(oldlock)
    entry=next(r for r in lock['mods'] if r['id']=='spawn');entry.update({k:v for k,v in row.items() if k!='replaces'});entry.update(source='local:spawn-native',updated=True,pin_reason='Native Spawn registry/data removal; spawn-native/audit/removal.json and Forge saved-world tests.');entry.pop('git_sha',None)
    admpath=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';oldadm=json.loads(admpath.read_text(encoding='utf-8-sig'));assert oldadm['integrityHash']==integrity(oldadm)
    adm=copy.deepcopy(oldadm);entry=next(r for r in adm['modIds'] if r['id']=='spawn');entry.update(version=VERSION,sha256=sha,filePresent=True)
    if adm['modIds']!=oldadm['modIds']:adm['lastUpdated']=int(time.time()*1000);adm['integrityHash']=integrity(adm)
    assert all(r in lock['mods'] for r in oldlock['mods'] if r['id']!='spawn')
    assert all(r in adm['modIds'] for r in oldadm['modIds'] if r['id']!='spawn');assert adm['resourcePackIds']==oldadm['resourcePackIds']
    for path,old,new in [(lockpath,oldlock,lock),(admpath,oldadm,adm),(ROOT/'release.json',json.loads((ROOT/'release.json').read_text()) if (ROOT/'release.json').exists() else None,[row])]:
        if a.check:assert old==new,'Stale release metadata: '+str(path)
        elif old!=new:write(path,new)
    coveragepath=REPO/'seasonal-calendar/audit/coverage.json'
    coverage=json.loads(coveragepath.read_text());before=copy.deepcopy(coverage)
    coverage['jar_sha256'].pop(ORIGINAL,None);coverage['jar_sha256'][NAME]=sha;coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()))
    if a.check:assert before==coverage,'Stale Spawn coverage hash'
    elif before!=coverage:write(coveragepath,coverage)
    for folder in [REPO/'mods',REPO/'server/mods']:
        for suffix in ['', '.sha256']:(folder/(ORIGINAL+suffix)).unlink(missing_ok=True)
    for suffix in ['', '.sha256']:(REPO/'server/mods'/('empirecraft-content-policy-1.0.0.jar'+suffix)).unlink(missing_ok=True)
    print('PASS Spawn release: identical client/server JAR, lock and ADM; unrelated records preserved')
if __name__=='__main__':main()
