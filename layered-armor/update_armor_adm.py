#!/usr/bin/env python3
"""Update armor identity and explicitly withdrawn client IDs, preserving other ADM entries."""
import argparse
import hashlib
import json
import re
import shutil
import time
import zipfile
from pathlib import Path


def integrity(data):
    def text(value):return 'null' if value is None else str(value)
    value=''.join(text(data[key])+'|' for key in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    value+=''.join('{}@{}[{}]{{{}}};'.format(text(row['id']),text(row['version']),text(row['sha256']),1 if row['filePresent'] else 0) for row in data['modIds'])
    value+=''.join('{}[{}];'.format(text(row['filename']),text(row['sha256'])) for row in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()


def update(path,jar,removed_client_mods=()):
    with zipfile.ZipFile(jar) as archive:
        descriptor=archive.read('META-INF/mods.toml').decode('utf-8')
    version=re.search(r'^version="([^"]+)"$',descriptor,re.M)
    if not re.search(r'^modId="empirecraft_armor"$',descriptor,re.M) or not version:
        raise ValueError('Expected an Empirecraft Layered Armor JAR with an explicit version')
    version=version[1];removed=set(removed_client_mods)
    if 'empirecraft_armor' in removed:raise ValueError('Cannot withdraw the module being updated')
    data=json.loads(path.read_text(encoding='utf-8'))
    if integrity(data)!=data['integrityHash']:
        raise ValueError('Existing ADM integrity does not match; the profile must be valid before the armor-only update')
    row=dict(id='empirecraft_armor',version=version,sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),filePresent=True)
    previous=[entry for entry in data['modIds'] if entry['id']=='empirecraft_armor']
    if len(previous)>1:raise ValueError('Duplicate armor identity in ADM')
    if previous==[row] and not any(entry['id'] in removed for entry in data['modIds']):return False
    unrelated=[dict(entry) for entry in data['modIds'] if entry['id'] not in removed|{'empirecraft_armor'}]
    data['modIds']=[row if entry['id']=='empirecraft_armor' else entry for entry in data['modIds'] if entry['id'] not in removed]
    if not previous:data['modIds'].append(row)
    assert unrelated==[entry for entry in data['modIds'] if entry['id'] not in removed|{'empirecraft_armor'}]
    data['lastUpdated']=max(int(time.time()*1000),int(data['lastUpdated'])+1)
    data['integrityHash']=integrity(data)
    backup=path.with_name(path.name+'.before-armor-'+version)
    if not backup.exists():shutil.copy2(path,backup)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return True


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id-file',type=Path,required=True)
    parser.add_argument('--jar',type=Path,default=Path('server/mods/empirecraft-layered-armor-1.0.4.jar'))
    parser.add_argument('--remove-client-mod',action='append',default=[],help='Explicit retired client mod ID; may be repeated')
    args=parser.parse_args()
    print('Updated armor identity and requested retirements' if update(args.id_file,args.jar,args.remove_client_mod) else 'Armor identity already current')
