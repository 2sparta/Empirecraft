#!/usr/bin/env python3
"""Install Town Hall menus, preserving settings, unrelated mods and ADM rows."""
import argparse,copy,hashlib,json,re,shutil,time,tomllib,zipfile
from pathlib import Path
def sha(raw):return hashlib.sha256(raw).hexdigest()
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def integrity(data):
    text=lambda v:'null' if v is None else str(v)
    raw=''.join(text(data[k])+'|' for k in ['osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'])
    raw+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    raw+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return sha(raw.encode())
def update_adm(data,release):
    if integrity(data)!=data['integrityHash']:raise ValueError('Existing ADM integrity does not match')
    result=copy.deepcopy(data)
    for module in release['mods']:
        if 'client' not in module['sides']:continue
        rows=[r for r in result['modIds'] if r['id']==module['id']]
        if len(rows)>1:raise ValueError('Duplicate ADM mod '+module['id'])
        row=dict(id=module['id'],version=module['version'],sha256=module['sha256'],filePresent=True)
        if rows:rows[0].update(row)
        else:result['modIds'].append(row)
    if result!=data:
        result['lastUpdated']=max(int(time.time()*1000),int(data['lastUpdated'])+1);result['integrityHash']=integrity(result)
    return result
def plan(root,side,bundle,release):
    root=root.resolve();assert root.is_dir(),root
    writes={};retire=[]
    for relative,digest in release['files'][side].items():
        assert not Path(relative).is_absolute() and '..' not in Path(relative).parts
        raw=(bundle/side/relative).read_bytes()
        if sha(raw)!=digest:raise ValueError('Bundle hash mismatch: '+relative)
        writes[root/relative]=raw
    modules={m['id']:m for m in release['mods'] if side in m['sides']}
    for old in (root/'mods').glob('*.jar'):
        with zipfile.ZipFile(old) as z:
            if 'META-INF/mods.toml' not in z.namelist():continue
            ids={m['modId'] for m in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']}
        if any(mid in modules and old.name!=modules[mid]['file'] for mid in ids):retire.append(old)
    if side=='server':
        properties=root/'server.properties'
        if properties.is_file():
            raw=properties.read_bytes()
            for key,value in release.get('server_properties',{}).items():
                assert key=='simulation-distance' and value=='8'
                pattern=rb'(?m)^'+re.escape(key.encode())+rb'[ \t]*=[^\r\n]*'
                replacement=(key+'='+value).encode()
                raw=re.sub(pattern,replacement,raw) if re.search(pattern,raw) else raw+(b'' if not raw or raw.endswith(b'\n') else b'\n')+replacement+b'\n'
            writes[properties]=raw
        plugins={p['name']:p for p in release['plugins']}
        for old in (root/'plugins').glob('*.jar'):
            with zipfile.ZipFile(old) as z:
                if 'plugin.yml' not in z.namelist():continue
                match=re.search(r'^name:\s*([^\r\n]+)',z.read('plugin.yml').decode('utf-8'),re.M)
            name=match[1].strip().strip('\"\'') if match else ''
            if name in plugins:
                module=plugins[name]
                if sha(old.read_bytes()) not in {module['sha256'],module['replaces_sha256']}:
                    raise ValueError('Unrecognized '+name+' build; refusing to replace '+old.name)
                if old.name!=module['file']:retire.append(old)
        adm=root/'config/admanticheat/idfile/admanticheat_id_list.json'
        if adm.is_file():writes[adm]=encode(update_adm(json.loads(adm.read_text(encoding='utf-8-sig')),release))
    retire+=[p.with_suffix('.jar.sha256') for p in list(retire) if p.with_suffix('.jar.sha256').is_file()]
    return root,writes,retire
def apply(prepared):
    root,writes,retire=prepared;backup=root/'.empirecraft-backups/town-icons-1.1';changed=[]
    def save(path):
        target=backup/path.relative_to(root)
        if target.exists() and target.read_bytes()!=path.read_bytes():target=target.with_name(target.name+'.'+sha(path.read_bytes())[:12])
        target.parent.mkdir(parents=True,exist_ok=True)
        if not target.exists():shutil.copy2(path,target)
    for path in retire:save(path);path.unlink();changed.append(str(path.relative_to(root)))
    for path,raw in writes.items():
        if path.exists() and path.read_bytes()==raw:continue
        if path.exists():save(path)
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);changed.append(str(path.relative_to(root)))
    return changed
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--client',type=Path);parser.add_argument('--server',type=Path);parser.add_argument('--bundle',type=Path,default=Path(__file__).resolve().parent);a=parser.parse_args()
    if not a.client and not a.server:parser.error('Specify --client and/or --server game folder')
    release=json.loads((a.bundle/'release.json').read_text())
    prepared=[(side,plan(root,side,a.bundle,release)) for side,root in [('client',a.client),('server',a.server)] if root]
    for side,operation in prepared:print(side,':',', '.join(apply(operation)) or 'already current')
if __name__=='__main__':main()
