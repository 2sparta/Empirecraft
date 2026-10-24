#!/usr/bin/env python3
"""Rebuild the expected client inventory from the actual JARs; preserve resource packs."""
import argparse,copy,hashlib,io,json,os,re,tempfile,time,tomllib,zipfile
from pathlib import Path
from packaging.version import Version,InvalidVersion
ROOT=Path(__file__).resolve().parent.parent

def integrity(data):
    def val(v):return 'null' if v is None else str(v)
    text=''.join(val(data[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    text+=''.join(f"{m['id']}@{m['version']}[{val(m['sha256'])}]{{{1 if m['filePresent'] else 0}}};" for m in data['modIds'])
    text+=''.join(f"{m['filename']}[{val(m['sha256'])}];" for m in data['resourcePackIds'])
    return hashlib.sha256(text.encode()).hexdigest()

def load(path):
    text=path.read_text(encoding='utf-8-sig')
    if text.lstrip().startswith('{'):return json.loads(text)
    data={'modIds':[],'resourcePackIds':[]};section=''
    fields={'OS':'osName','CPU':'cpuInfo','RAM':'ramInfo','GPU':'gpuInfo','Anticheat Version':'anticheatVersion','Last Updated':'lastUpdated','Integrity Hash':'integrityHash'}
    for line in text.splitlines():
        line=line.strip()
        if line.startswith('[') and line.endswith(']'):section=line[1:-1];continue
        if section=='System Info':
            for label,key in fields.items():
                if line.startswith(label+':'):data[key]=line.split(':',1)[1].strip()
        if section=='Mod IDs':
            m=re.fullmatch(r'-\s+(\S+)\s+@\s+(.*?)\s+\[([0-9a-f]*)\]\s+\{(file|nofile)\}',line)
            if m:data['modIds'].append(dict(id=m[1],version=m[2],sha256=m[3] or None,filePresent=m[4]=='file'))
        if section=='Resource Pack IDs':
            m=re.fullmatch(r'-\s+(.*?)\s+\[([0-9a-f]*)\]',line)
            if m:data['resourcePackIds'].append(dict(filename=m[1],sha256=m[2] or None))
    data['lastUpdated']=int(data['lastUpdated'])
    if not data['modIds'] or not data['resourcePackIds']:raise ValueError('Unrecognised ADM export; nothing was written')
    return data

def manifest_version(archive):
    try:text=archive.read('META-INF/MANIFEST.MF').decode().replace('\r\n ','')
    except KeyError:return '0.0NONE'
    values=dict(line.split(': ',1) for line in text.splitlines() if ': ' in line)
    return values.get('Implementation-Version','0.0NONE')

def version_key(value):
    try:return (1,Version(value))
    except InvalidVersion:return (0,tuple(re.findall(r'\d+|[^\d]+',value)))

def inventory(mods):
    selected={}
    def visit(contents,root_hash,nested=False,depth=0):
        if depth>8:raise ValueError('Nested JAR depth exceeds eight')
        with zipfile.ZipFile(io.BytesIO(contents)) as z:
            if z.testzip() is not None:raise ValueError('Corrupt JAR')
            if 'META-INF/mods.toml' in z.namelist():
                metadata=tomllib.loads(z.read('META-INF/mods.toml').decode())
                for mod in metadata.get('mods',[]):
                    mid=mod['modId'];version=str(mod.get('version','1')).replace('${file.jarVersion}',manifest_version(z))
                    if '${' in version:raise ValueError('Unresolved mod version: '+mid+' '+version)
                    entry=dict(id=mid,version=version,sha256=None if nested else root_hash,filePresent=not nested)
                    old=selected.get(mid)
                    if old is None or version_key(version)>version_key(old['version']) or (version==old['version'] and not nested):selected[mid]=entry
            if 'META-INF/jarjar/metadata.json' in z.namelist():
                for jar in json.loads(z.read('META-INF/jarjar/metadata.json')).get('jars',[]):visit(z.read(jar['path']),root_hash,True,depth+1)
    for path in sorted(mods.glob('*.jar')):
        content=path.read_bytes();visit(content,hashlib.sha256(content).hexdigest())
    if not {'vs_sails','valkyrienskies','architectury','kotlinforforge','admanticheat'}<=selected.keys():raise ValueError('Client dependencies are incomplete')
    return sorted(selected.values(),key=lambda m:m['id'].lower())

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mods',type=Path,default=ROOT/'mods')
    parser.add_argument('--id-file',type=Path,default=ROOT/'server/config/admanticheat/idfile/admanticheat_id_list.json')
    parser.add_argument('--check',action='store_true');args=parser.parse_args()
    old=load(args.id_file);expected=inventory(args.mods)
    if args.check:
        if old['modIds']!=expected or integrity(old)!=old['integrityHash']:raise SystemExit('ADM does not match the client JARs')
        print('ADM hashes, versions, resource-pack entries and integrity hash verified');return
    if old['modIds']==expected and integrity(old)==old['integrityHash']:
        print('ADM already matches the client inventory');return
    result=copy.deepcopy(old);result['modIds']=expected
    for field in ['osName','cpuInfo','ramInfo','gpuInfo']:result[field]='Unknown'
    result['lastUpdated']=int(time.time()*1000);result['integrityHash']=integrity(result)
    assert result['resourcePackIds']==old['resourcePackIds']
    descriptor,name=tempfile.mkstemp(prefix='.sails-adm-',dir=args.id_file.parent)
    try:
        with os.fdopen(descriptor,'w',encoding='utf-8') as out:json.dump(result,out,indent=2,ensure_ascii=False);out.write('\n');out.flush();os.fsync(out.fileno())
        os.replace(name,args.id_file)
    finally:Path(name).unlink(missing_ok=True)
    print('ADM updated:',len(expected),'mod IDs; resource packs preserved; protection settings untouched')

if __name__=='__main__':main()
