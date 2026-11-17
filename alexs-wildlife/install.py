#!/usr/bin/env python3
"""Install the verified paired wildlife bundle, preserving worlds and unrelated mod identities."""
import argparse,copy,hashlib,json,os,shutil,time,tomllib,zipfile
from pathlib import Path

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def integrity(data):
    text=lambda v:'null' if v is None else str(v)
    value=''.join(text(data[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    value+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    value+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()
def modids(path):
    with zipfile.ZipFile(path) as z:
        if 'META-INF/mods.toml' not in z.namelist():return set()
        return {m['modId'] for m in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']}
def install(client,server,bundle,apply=False):
    release=json.loads((bundle/'release.json').read_text());actions=[]
    for row in release['mods']:
        source=bundle/'mods'/row['file'];assert sha(source)==row['sha256'],source
        for side,base in [('client',client),('server',server)]:
            folder=base/'mods';old=[p for p in folder.glob('*.jar') if row['id'] in modids(p)]
            assert len(old)<=1,('Duplicate existing mod',side,row['id'])
            if old and old[0].name==row['file'] and sha(old[0])==row['sha256']:continue
            if old and sha(old[0]) not in row['replace_sha256']:raise ValueError('Unrecognized installed '+str(old[0])+'; preserve and review this build before replacing it')
            actions.append((base,source,folder/row['file'],old[0] if old else None))
    adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';planned=None
    if adm.exists():
        original=json.loads(adm.read_text(encoding='utf-8-sig'));assert integrity(original)==original['integrityHash'],'Existing ADM integrity mismatch'
        planned=copy.deepcopy(original)
        for row in release['mods']:
            matches=[r for r in planned['modIds'] if r['id']==row['id']];assert len(matches)<=1,row['id']
            value=dict(id=row['id'],version=row['version'],sha256=row['sha256'],filePresent=True)
            if matches:matches[0].update(value)
            else:planned['modIds'].append(value)
        if planned==original:planned=None
        else:planned['lastUpdated']=int(time.time()*1000);planned['integrityHash']=integrity(planned)
    for base,source,target,old in actions:print(('Replace '+str(old)+' -> ' if old else 'Add ')+str(target))
    if planned is not None:print('Update four verified mod identities in '+str(adm))
    if not apply:return len(actions)
    # Every identity and input was checked before the first mutation.
    for base,source,target,old in actions:
        target.parent.mkdir(parents=True,exist_ok=True)
        if old:
            backup=base/'.empirecraft-backups/wildlife-1.0.0'/sha(old)/old.name;backup.parent.mkdir(parents=True,exist_ok=True)
            if not backup.exists():shutil.copy2(old,backup)
        temporary=target.with_suffix('.jar.wildlife-tmp');shutil.copyfile(source,temporary);os.replace(temporary,target)
        if old and old!=target:
            old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+target.name+'\n')
    if planned is not None:
        backup=server/'.empirecraft-backups/wildlife-1.0.0'/sha(adm)/adm.name;backup.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(adm,backup)
        adm.write_bytes(encode(planned))
    print('WILDLIFE_INSTALL_PASS changed_jars='+str(len(actions)));return len(actions)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--bundle',type=Path,default=Path(__file__).resolve().parent);p.add_argument('--apply',action='store_true');a=p.parse_args();install(a.client.resolve(),a.server.resolve(),a.bundle.resolve(),a.apply)
