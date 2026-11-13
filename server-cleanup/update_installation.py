#!/usr/bin/env python3
"""Install the cleanup into explicit game folders; preserve settings and back up changes."""
import argparse,copy,hashlib,json,re,shutil,time,tomllib,zipfile
from pathlib import Path
from filtering import REMOVED,DROP,prune,namespace_path,encode,format_json

ROOT=Path(__file__).resolve().parent
def digest(raw):return hashlib.sha256(raw).hexdigest()
def integrity(data):
    text=lambda v:'null' if v is None else str(v)
    raw=''.join(text(data[k])+'|' for k in ['osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'])
    raw+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    raw+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return digest(raw.encode())
def prepare_adm(data,release):
    if integrity(data)!=data['integrityHash']:raise ValueError('Existing ADM integrityHash does not match')
    result=copy.deepcopy(data);result['modIds']=[r for r in result['modIds'] if r['id'] not in REMOVED]
    for row in release['mods']:
        if 'client' not in row['sides']:continue
        existing=[r for r in result['modIds'] if r['id']==row['id']]
        if len(existing)>1:raise ValueError('Duplicate ADM mod '+row['id'])
        value=dict(id=row['id'],version=row['version'],sha256=row['sha256'],filePresent=True)
        if existing:existing[0].update(value)
        else:result['modIds'].append(value)
    if result!=data:
        result['lastUpdated']=int(time.time()*1000);result['integrityHash']=integrity(result)
    return result
def apply(root,side,release,bundle,backup_root=None):
    root=root.resolve();bundle=bundle.resolve()
    backup=(backup_root or root.parent/'Empirecraft-cleanup-backups')/side
    updates=[row for row in release['mods'] if side in row['sides']]
    for row in updates:
        source=bundle/side/'mods'/row['file']
        if digest(source.read_bytes())!=row['sha256']:raise ValueError('Wrong update JAR '+str(source))
    adm=root/'config/admanticheat/idfile/admanticheat_id_list.json'
    planned=None
    if side=='server' and adm.is_file():
        current=json.loads(adm.read_text(encoding='utf-8-sig'));planned=prepare_adm(current,release)
    changed=[]
    def save(p):
        dest=backup/p.relative_to(root);dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists() and dest.read_bytes()!=p.read_bytes():dest=dest.with_name(dest.name+'.'+digest(p.read_bytes())[:12])
        if not dest.exists():shutil.copyfile(p,dest)
    def write(p,raw):
        if p.exists() and p.read_bytes()==raw:return
        if p.exists():save(p)
        p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw);changed.append(str(p))
    ids={r['id'] for r in updates}
    for old in (root/'mods').glob('*.jar'):
        with zipfile.ZipFile(old) as z:
            if 'META-INF/mods.toml' not in z.namelist():continue
            metadata=tomllib.loads(z.read('META-INF/mods.toml').decode())
        found={m['modId'] for m in metadata['mods']}
        if not found&(ids|REMOVED):continue
        desired=next((row for row in updates if row['file']==old.name),None)
        if desired and digest(old.read_bytes())==desired['sha256']:continue
        save(old);old.unlink();changed.append(str(old))
        checksum=old.with_suffix('.jar.sha256')
        if checksum.exists():save(checksum);checksum.unlink()
    for row in updates:
        p=root/'mods'/row['file'];write(p,(bundle/side/'mods'/row['file']).read_bytes())
        write(p.with_suffix('.jar.sha256'),(row['sha256']+'  '+p.name+'\n').encode())
    for directory in ['config','defaultconfigs','kubejs/data']:
        base=root/directory
        if not base.exists():continue
        for p in base.rglob('*'):
            if not p.is_file() or p==adm:continue
            relative=p.relative_to(root).as_posix()
            retired=namespace_path(relative) or any(part.lower()==ns or p.name.lower().startswith(ns+'-') or p.name.lower()==ns+'.toml' for ns in REMOVED for part in p.relative_to(base).parts)
            if retired:save(p);p.unlink();changed.append(str(p));continue
            if p.suffix=='.json':
                try:before=json.loads(p.read_text(encoding='utf-8-sig'))
                except (ValueError,UnicodeError):continue
                after=prune(before)
                if after is DROP:save(p);p.unlink();changed.append(str(p))
                elif after!=before:write(p,format_json(after,p.read_bytes()))
    rules=root/'config/empirecraft_world_rules-common.toml'
    if rules.exists():
        text=rules.read_text()
        for ns in REMOVED:
            token=re.escape(ns)+r':[^"\n]+'
            text=re.sub('"'+token+r'"\s*,\s*','',text)
            text=re.sub(r',\s*"'+token+'"','',text)
        write(rules,text.encode())
    for p in (root/'kubejs/server_scripts').glob('*.js'):
        text=p.read_text();match=re.search(r'(var EC_NO_END = )(\{[^\n]+\})(;)',text)
        if not match:continue
        value=json.loads(match[2]);after=prune(value)
        if value!=after:write(p,(text[:match.start(2)]+json.dumps(after,ensure_ascii=False,separators=(',',':'))+text[match.end(2):]).encode())
    if planned is not None:write(adm,encode(planned))
    return changed
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path);p.add_argument('--client',type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args()
    if not (a.server or a.client):p.error('Provide --server and/or --client game directories')
    if not a.apply:p.error('Stop the game/server, then use --apply to install into the specified directories')
    release=json.loads((ROOT/'release.json').read_text());total=0
    for side,folder in [('server',a.server),('client',a.client)]:
        if folder:total+=len(apply(folder,side,release,ROOT))
    print('CLEANUP_INSTALL_PASS changes='+str(total)+'; backups are outside the active game folders')
if __name__=='__main__':main()
