#!/usr/bin/env python3
"""Install only handbook resources and writing recipes, with backups and ADM integrity."""
import argparse,copy,hashlib,json,shutil,time,tomllib,zipfile
from pathlib import Path

ID='empirecraft_guidebook'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def integrity(data):
    text=lambda v:'null' if v is None else str(v)
    raw=''.join(text(data[k])+'|' for k in ['osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'])
    raw+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    raw+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return sha(raw.encode())
def adm_update(data,release):
    if integrity(data)!=data['integrityHash']:raise ValueError('Existing ADM integrity does not match')
    result=copy.deepcopy(data);rows=[r for r in result['modIds'] if r['id']==ID]
    if len(rows)>1:raise ValueError('Duplicate handbook identity in ADM')
    row=dict(id=ID,version=release['version'],sha256=release['sha256'],filePresent=True)
    if rows:rows[0].update(row)
    else:result['modIds'].append(row)
    if result!=data:
        result['lastUpdated']=max(int(time.time()*1000),int(data['lastUpdated'])+1)
        result['integrityHash']=integrity(result)
    return result
def plan(root,side,bundle,release):
    root=root.resolve();assert root.is_dir(),root
    writes={};retire=[]
    for relative,digest in release['files'][side].items():
        assert not Path(relative).is_absolute() and '..' not in Path(relative).parts
        raw=(bundle/side/relative).read_bytes()
        if sha(raw)!=digest:raise ValueError('Bundle hash mismatch: '+relative)
        writes[root/relative]=raw
    for old in (root/'mods').glob('*.jar'):
        with zipfile.ZipFile(old) as z:
            if 'META-INF/mods.toml' not in z.namelist():continue
            mods=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']
        if any(m['modId']==ID for m in mods) and old.name!=release['file']:
            retire.append(old)
            if old.with_suffix('.jar.sha256').exists():retire.append(old.with_suffix('.jar.sha256'))
    adm=root/'config/admanticheat/idfile/admanticheat_id_list.json'
    if side=='server' and adm.is_file():writes[adm]=encode(adm_update(json.loads(adm.read_text(encoding='utf-8-sig')),release))
    return root,writes,retire
def apply(prepared):
    root,writes,retire=prepared;backup=root/'.empirecraft-backups/handbook-1.0.3';changed=[]
    def save(path):
        target=backup/path.relative_to(root)
        if target.exists() and target.read_bytes()!=path.read_bytes():target=target.with_name(target.name+'.'+sha(path.read_bytes())[:12])
        target.parent.mkdir(parents=True,exist_ok=True)
        if not target.exists():shutil.copy2(path,target)
    for path in retire:
        save(path);path.unlink();changed.append(str(path.relative_to(root)))
    for path,raw in writes.items():
        if path.exists() and path.read_bytes()==raw:continue
        if path.exists():save(path)
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);changed.append(str(path.relative_to(root)))
    return changed
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path);p.add_argument('--server',type=Path)
    p.add_argument('--bundle',type=Path,default=Path(__file__).resolve().parent)
    a=p.parse_args()
    if not a.client and not a.server:p.error('Specify --client and/or --server game folder')
    release=json.loads((a.bundle/'release.json').read_text())
    plans=[(side,plan(root,side,a.bundle,release)) for side,root in [('client',a.client),('server',a.server)] if root]
    for side,prepared in plans:print(side,':',', '.join(apply(prepared)) or 'already current')
if __name__=='__main__':main()
