#!/usr/bin/env python3
import argparse,copy,hashlib,json
from build import HERE,REPO,NAME

def integrity(data):
    def text(value):return 'null' if value is None else str(value)
    value=''.join(text(data[k])+'|' for k in ['osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'])
    value+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    value+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()
def main(check=False):
    raw=(REPO/'mods'/NAME).read_bytes();sha256=hashlib.sha256(raw).hexdigest();gitsha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    changes={}
    p=REPO/'modpack-lock.json';old=json.loads(p.read_text());new=copy.deepcopy(old)
    entry=next(e for e in new['mods'] if e['id']=='chatplus')
    entry.update(file=NAME,version='2.8.1-empirecraft.1',old_version='2.8.1',updated=True,source='local:chat-channels',git_sha=gitsha,sha256=sha256,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),pin_reason='Five fixed server-routed channels, independent tab history and unread badges; general config UI removed, direct chat/window controls retained. Reproducible pinned upstream patch in chat-channels/.')
    new['updated_mod_count']=sum(bool(e.get('updated')) for e in new['mods'])
    assert [e for e in old['mods'] if e['id']!='chatplus']==[e for e in new['mods'] if e['id']!='chatplus']
    changes[p]=(old,new)
    p=REPO/'forging-balance/installed-client-mods.json';old=json.loads(p.read_text());new=copy.deepcopy(old)
    matches=[e for e in new if e['name'].startswith('chatplus-')];assert len(matches)==1
    matches[0].update(name=NAME,path='mods/'+NAME,sha=gitsha,size=len(raw));new.sort(key=lambda e:e['name']);changes[p]=(old,new)
    p=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';old=json.loads(p.read_text());new=copy.deepcopy(old)
    assert integrity(old)==old['integrityHash']
    entry=next(e for e in new['modIds'] if e['id']=='chatplus');desired=dict(id='chatplus',version='2.8.1-empirecraft.1',sha256=sha256,filePresent=True)
    if entry!=desired:entry.clear();entry.update(desired);new['lastUpdated']+=1;new['integrityHash']=integrity(new)
    assert new['resourcePackIds']==old['resourcePackIds']
    changes[p]=(old,new)
    for path,(old,new) in changes.items():
        if check:assert old==new,'Stale '+str(path)
        elif old!=new:path.write_text(json.dumps(new,ensure_ascii=False,indent=2)+'\n')
    print('PASS ChatPlus modpack, client inventory and ADM integrity pins:',sha256)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');main(p.parse_args().check)
