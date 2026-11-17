#!/usr/bin/env python3
"""Install the Chinese Weapons integration, with backups and ADM integrity."""
import argparse,copy,hashlib,json,re,shutil,time,tomllib,zipfile
from pathlib import Path

ID='chineseweapons'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def integrity(data):
    text=lambda v:'null' if v is None else str(v)
    raw=''.join(text(data[k])+'|' for k in ['osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'])
    raw+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    raw+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return sha(raw.encode())
ADM_ROW=re.compile(r'^\s*-\s+(\S+)\s+@\s+(.*?)\s+\[([a-fA-F0-9]*)\]\s+\{(file|nofile)\}\s*$')
def parse_adm(text):
    if text.lstrip('\ufeff \t\r\n').startswith('{'):return json.loads(text.lstrip('\ufeff')),False
    labels={'OS':'osName','CPU':'cpuInfo','RAM':'ramInfo','GPU':'gpuInfo','Anticheat Version':'anticheatVersion','Last Updated':'lastUpdated','Integrity Hash':'integrityHash'}
    data={'modIds':[],'resourcePackIds':[]};section=''
    for line in text.splitlines():
        if line.startswith('['):section=line
        field=re.fullmatch(r'\s*(OS|CPU|RAM|GPU|Anticheat Version|Last Updated|Integrity Hash):\s*(.*?)\s*',line)
        if field:data[labels[field[1]]]=int(field[2]) if field[1]=='Last Updated' else field[2]
        row=ADM_ROW.fullmatch(line)
        if row:data['modIds'].append(dict(id=row[1],version=row[2],sha256=row[3] or None,filePresent=row[4]=='file'))
        elif section=='[Resource Pack IDs]':
            pack=re.fullmatch(r'\s*-\s+(.*?)\s+\[([a-fA-F0-9]*)\]\s*',line)
            if pack:data['resourcePackIds'].append(dict(filename=pack[1],sha256=pack[2] or None))
    if any(key not in data for key in labels.values()) or not data['modIds']:raise ValueError('Unsupported ADM format')
    if integrity(data)!=data['integrityHash']:raise ValueError('Existing local ADM integrity mismatch')
    return data,True

def render_adm(text,original,updated,report):
    if original==updated:return text
    if not report:return json.dumps(updated,ensure_ascii=False,indent=2)+'\n'
    lines=text.splitlines(keepends=True);new={row['id']:row for row in updated['modIds']};old={row['id']:row for row in original['modIds']};last=None
    def row_text(row,ending='\n'):
        return '  - {:<30} @ {:<15} [{}] {{{}}}{}'.format(row['id'],row['version'],row['sha256'] or '', 'file' if row['filePresent'] else 'nofile',ending)
    for i,line in enumerate(lines):
        row=ADM_ROW.fullmatch(line)
        if row:
            last=i
            if row[1] not in new:lines[i]='';continue
            if old[row[1]]!=new[row[1]]:lines[i]=row_text(new[row[1]],'\r\n' if line.endswith('\r\n') else '\n')
        for label,key in [('Last Updated','lastUpdated'),('Integrity Hash','integrityHash')]:
            if re.match(r'\s*'+label+':',line):lines[i]=re.sub(r'(^\s*'+label+r':\s*)[^\r\n]*',lambda m:m[1]+str(updated[key]),line)
    if last is None:raise ValueError('Missing ADM mod report')
    added=[row_text(row) for row in updated['modIds'] if row['id'] not in old]
    lines[last+1:last+1]=added;result=''.join(lines)
    if parse_adm(result)[0]!=updated:raise ValueError('ADM report failed round-trip validation')
    return result

def adm_update(data,release):
    if integrity(data)!=data['integrityHash']:raise ValueError('Existing ADM integrity does not match')
    result=copy.deepcopy(data);result['modIds']=list(result['modIds']);rows=[r for r in result['modIds'] if r['id']==ID]
    if len(rows)>1:raise ValueError('Duplicate Chinese Weapons identity in ADM')
    row=dict(id=ID,version=release['version'],sha256=release['sha256'],filePresent=True)
    if rows:rows[0].update(row)
    else:result['modIds'].append(row)
    for extra in release.get('additional_mods',[]):
        existing=[entry for entry in result['modIds'] if entry['id']==extra['id']];entry=dict(id=extra['id'],version=extra['version'],sha256=extra['sha256'],filePresent=True)
        if existing:existing[0].update(entry)
        else:result['modIds'].append(entry)
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
        if any((m['modId']==ID or m['modId'] in {v['id'] for v in release.get('additional_mods',[])}) and old.name not in {release['file'],*[v['file'] for v in release.get('additional_mods',[])]} for m in mods):
            retire.append(old)
            if old.with_suffix('.jar.sha256').exists():retire.append(old.with_suffix('.jar.sha256'))
    adm=root/'config/admanticheat/idfile/admanticheat_id_list.json'
    if side=='server' and adm.is_file():
        original_text=adm.read_bytes().decode('utf-8-sig');original,report=parse_adm(original_text);updated=adm_update(original,release)
        if original!=updated:writes[adm]=render_adm(original_text,original,updated,report).encode('utf-8')
    return root,writes,retire
def apply(prepared):
    root,writes,retire=prepared;backup=root/'.empirecraft-backups/chinese-weapons-1';changed=[]
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
