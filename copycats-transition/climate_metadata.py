import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
p=REPO/'modpack-lock.json';lock=json.loads(p.read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_tfc');name='empirecraft-tfc-integration-1.0.4.jar';raw=(REPO/'mods'/name).read_bytes();assert raw==(REPO/'server/mods'/name).read_bytes()
row.update(file=name,version='1.0.4',old_version='1.0.3',updated=True,source='local:tfc-integration',sha256=hashlib.sha256(raw).hexdigest(),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),pin_reason='Retain LSO temperature on game threads; biome climate fallback on mesh workers prevents unsafe concurrent modifier-registry traversal.')
lock['updated_mod_count']=sum(bool(r.get('updated')) for r in lock['mods']);p.write_text(json.dumps(lock,ensure_ascii=False,indent=2)+'\n')
for name_,side in [('forging-balance/installed-client-mods.json','mods'),('seasonal-calendar/installed-mods.json','server/mods')]:
 p=REPO/name_;d=[r for r in json.loads(p.read_text()) if not r['name'].startswith('empirecraft-tfc-integration-')];d.append(dict(name=name,path=side+'/'+name,size=len(raw),sha=row['git_sha']));p.write_text(json.dumps(sorted(d,key=lambda r:r['name']),indent=2)+'\n')
p=REPO/'seasonal-calendar/audit/coverage.json';d=json.loads(p.read_text());d['jar_sha256']={k:v for k,v in d['jar_sha256'].items() if not k.startswith('empirecraft-tfc-integration-')};d['jar_sha256'][name]=row['sha256'];p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
p=REPO/'native-content/retained-code.json';d={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith('empirecraft-tfc-integration-')}
with zipfile.ZipFile(REPO/'mods'/name) as z:d[name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
p.write_text(json.dumps(d,indent=2)+'\n')
p=REPO/'validation/installed-releases-2026-11-01.json';d=json.loads(p.read_text());d['retired']=list(dict.fromkeys(d['retired']+[side+'mods/empirecraft-tfc-integration-1.0.3.jar' for side in ['', 'server/']]));p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
