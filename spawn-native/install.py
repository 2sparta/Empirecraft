#!/usr/bin/env python3
"""Install the verified client/server payload and back up every replaced or obsolete file."""
import argparse,hashlib,json,shutil,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def integrity(d):
    def text(v):return 'null' if v is None else str(v)
    s=''.join(text(d[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    s+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in d['modIds'])
    s+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in d['resourcePackIds'])
    return hashlib.sha256(s.encode()).hexdigest()
def child(root,name):
    result=(root/name).resolve()
    if not result.is_relative_to(root):raise ValueError('Unsafe package path: '+name)
    return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path);p.add_argument('--server',type=Path);a=p.parse_args()
    targets={side:folder.resolve() for side,folder in [('client',a.client),('server',a.server)] if folder is not None}
    if not targets:p.error('Specify --client and/or --server. Stop Minecraft and the server first.')
    expected={line[66:]:line[:64] for line in (ROOT/'SHA256SUMS.txt').read_text().splitlines()}
    for name,sha in expected.items():
        if hashlib.sha256(child(ROOT,name).read_bytes()).hexdigest()!=sha:raise ValueError('Package checksum mismatch: '+name)
    obsolete=json.loads((ROOT/'DELETE-THESE-FILES.json').read_text());release,=json.loads((ROOT/'release.json').read_text())
    changes=[];adm_plan=None
    for side,folder in targets.items():
        if not folder.is_dir():raise ValueError('Installation folder does not exist: '+str(folder))
        for name in expected:
            if name.startswith(side+'/'):changes.append((side,child(folder,name[len(side)+1:]),child(ROOT,name)))
        for name in obsolete:
            if name.startswith(side+'/'):changes.append((side,child(folder,name[len(side)+1:]),None))
        if side=='server':
            path=folder/'config/admanticheat/idfile/admanticheat_id_list.json'
            if path.exists():
                data=json.loads(path.read_text(encoding='utf-8-sig'))
                if data['integrityHash']!=integrity(data):raise ValueError('Existing ADM integrity mismatch; no files changed')
                row=next(r for r in data['modIds'] if r['id']=='spawn');row.update(version=release['version'],sha256=release['sha256'],filePresent=True)
                data['lastUpdated']=int(time.time()*1000);data['integrityHash']=integrity(data);adm_plan=(path,data)
    backup_name='.empirecraft-spawn-backup-'+str(int(time.time()*1000))
    def backup(side,path):
        if path.exists():
            destination=targets[side]/backup_name/path.relative_to(targets[side]);destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,destination)
    for side,path,source in changes:
        if source is not None and path.exists() and source.read_bytes()==path.read_bytes():continue
        backup(side,path)
        if source is None:path.unlink(missing_ok=True)
        else:path.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,path)
    if adm_plan:
        path,data=adm_plan;backup('server',path);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print('Installed native Spawn for '+', '.join(targets)+'. Replaced files are backed up in '+backup_name)
if __name__=='__main__':main()
