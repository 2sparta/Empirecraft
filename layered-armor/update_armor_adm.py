#!/usr/bin/env python3
"""Update only the armor JAR's ADM identity, preserving all other mod entries."""
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


def update(path,jar):
    with zipfile.ZipFile(jar) as archive:
        descriptor=archive.read('META-INF/mods.toml').decode('utf-8')
    if not re.search(r'^modId="empirecraft_armor"$',descriptor,re.M) or not re.search(r'^version="1\.0\.1"$',descriptor,re.M):
        raise ValueError('Expected Empirecraft Layered Armor 1.0.1 JAR')
    data=json.loads(path.read_text(encoding='utf-8'))
    if integrity(data)!=data['integrityHash']:
        raise ValueError('Existing ADM integrity does not match; the profile must be valid before the armor-only update')
    row=dict(id='empirecraft_armor',version='1.0.1',sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),filePresent=True)
    previous=[entry for entry in data['modIds'] if entry['id']=='empirecraft_armor']
    if previous==[row]:return False
    unrelated=[dict(entry) for entry in data['modIds'] if entry['id']!='empirecraft_armor']
    data['modIds']=[row if entry['id']=='empirecraft_armor' else entry for entry in data['modIds']]
    if not previous:data['modIds'].append(row)
    assert unrelated==[entry for entry in data['modIds'] if entry['id']!='empirecraft_armor']
    data['lastUpdated']=max(int(time.time()*1000),int(data['lastUpdated'])+1)
    data['integrityHash']=integrity(data)
    backup=path.with_name(path.name+'.before-armor-1.0.1')
    if not backup.exists():shutil.copy2(path,backup)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return True


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id-file',type=Path,required=True)
    parser.add_argument('--jar',type=Path,default=Path('server/mods/empirecraft-layered-armor-1.0.1.jar'))
    args=parser.parse_args()
    print('Updated armor identity only' if update(args.id_file,args.jar) else 'Armor identity already current')
