"""Package paired client/server fixes and update exact hashes without changing unrelated ADM rows."""
import argparse,hashlib,json,sys,tomllib,zipfile
from pathlib import Path
import update_installation as u
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
IDS={'tfc','legendarysurvivaloverhaul','farmersrespite','handson_delight','diegesis'}
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(u.encode(value))
def rows():
 result=[]
 for name in json.loads((ROOT/'audit/build.json').read_text()):
  f=REPO/'mods'/name;raw=f.read_bytes()
  with zipfile.ZipFile(f) as z:
   mods=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']
   manifest=z.read('META-INF/MANIFEST.MF').decode() if 'META-INF/MANIFEST.MF' in z.namelist() else ''
  m=next(m for m in mods if m['modId'] in IDS)
  if m['version']=='${file.jarVersion}':m['version']=next(line.split(': ',1)[1].strip() for line in manifest.splitlines() if line.startswith('Implementation-Version: '))
  result.append(dict(id=m['modId'],file=name,version=m['version'],sha256=u.sha(raw),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),sides=['client','server'],source='local:kitchen-fixes',updated=True,pin_reason='Paired kitchen fixes layered over previous native exclusions. Exact inputs, overlays, tests and limits: kitchen-fixes/.'))
 assert {r['id'] for r in result}==IDS
 return result

def metadata(release,mods):
 for name,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
  p=REPO/name;data=json.loads(p.read_text());order=[r['id'] for r in data[key]];old={r['id']:r for r in data[key]}
  for r in mods:
   row=dict(old.get(r['id'],{}));row.update(r)
   if key=='releases':row['artifact']='mods/'+r['file']
   old[r['id']]=row
  data[key]=sorted(old.values(),key=lambda r:r['id']) if key=='mods' else [old[id] for id in order]+[old[id] for id in old if id not in order]
  if key=='mods':data['updated_mod_count']=sum(bool(v.get('updated')) for v in data[key])
  else:
   for oldjar in (REPO/'disabled-mods/kitchen-2026-11-16/mods').glob('*.jar'):
    for folder in ['mods/','server/mods/']:
     path=folder+oldjar.name
     if path not in data.setdefault('retired',[]):data['retired'].append(path)
  for side,folder in [('client','mods'),('server','server/mods')]:data[side+'_jar_count']=len(list((REPO/folder).glob('*.jar')))
  write(p,data)
 prefixes=('TerraFirmaCraft-','legendarysurvivaloverhaul-','farmersrespite-','handson_delight-','diegesis-')
 for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/name;data=json.loads(p.read_text());data=[v for v in data if not v['name'].startswith(prefixes)]
  data.extend(dict(name=r['file'],path=folder+'/'+r['file'],size=r['size'],sha=r['git_sha']) for r in mods);write(p,sorted(data,key=lambda v:v['name']))
 p=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(p.read_text());data['jar_sha256']={k:v for k,v in data['jar_sha256'].items() if not k.startswith(prefixes)};data['jar_sha256'].update({r['file']:r['sha256'] for r in mods});data['jar_count']=len(data['jar_sha256']);write(p,data)
 p=REPO/'native-content/retained-code.json';data=json.loads(p.read_text());data={k:v for k,v in data.items() if not k.startswith(prefixes)}
 for row in mods:
  with zipfile.ZipFile(REPO/'mods'/row['file']) as z:data[row['file']]={n:u.sha(z.read(n)) for n in z.namelist() if n.endswith('.class')}
 write(p,data)
 adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';before=adm.read_text(encoding='utf-8-sig');original,report=u.parse_adm(before);updated=u.adm_update(original,release)
 assert [v for v in original['modIds'] if v['id'] not in IDS]==[v for v in updated['modIds'] if v['id'] not in IDS]
 assert original['resourcePackIds']==updated['resourcePackIds']
 adm.write_text(u.render_adm(before,original,updated,report))
 write(ROOT/'audit/installed.json',dict(status='passed',mod_sha256={r['file']:r['sha256'] for r in mods},unrelated_adm_rows_preserved=True))

def main():
 p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');a=p.parse_args();mods=rows()
 runtime=json.loads((ROOT/'audit/runtime.json').read_text());assert runtime['status']=='passed' and runtime['probe_sha256']==u.sha((ROOT/'Probe.java').read_bytes()) and runtime['data_sha256']==u.sha((ROOT/'audit/data.json').read_bytes())
 for row in mods:assert runtime['mod_sha256'][row['file']]==row['sha256']
 first=next(r for r in mods if r['id']=='handson_delight');release={k:first[k] for k in ['id','file','version','sha256']};release.update(additional_mods=[{k:r[k] for k in ['id','file','version','sha256']} for r in mods if r!=first],files={})
 generated=json.loads((ROOT/'audit/data.json').read_text());payload={}
 for side,root in [('client',REPO),('server',REPO/'server')]:
  files={}
  for row in mods:
   for rel in ['mods/'+row['file'],'mods/'+row['file']+'.sha256']:files[rel]=(root/rel).read_bytes()
  for name,digest in generated['files'].items():
   rel='kubejs/'+name;raw=(root/rel).read_bytes();assert u.sha(raw)==digest;files[rel]=raw
  release['files'][side]={n:u.sha(raw) for n,raw in sorted(files.items())};payload.update({side+'/'+n:v for n,v in files.items()})
 if a.install:metadata(release,mods)
 write(ROOT/'release.json',release)
 for name in ['release.json','update_installation.py','README.uk.md']:payload[name]=(ROOT/name).read_bytes()
 for f in (ROOT/'audit').glob('*.json'):
  if f.name!='installer.json':payload['verification/'+f.name]=f.read_bytes()
 payload['SHA256SUMS.txt']=''.join(u.sha(v)+'  '+n+'\n' for n,v in sorted(payload.items())).encode()
 target=ROOT/'dist/Empirecraft-Kitchen-Fixes-1-install.zip'
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for name,raw in sorted(payload.items()):
   info=zipfile.ZipInfo(name,(2026,11,16,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
 target.with_suffix('.zip.sha256').write_text(u.sha(target.read_bytes())+'  '+target.name+'\n');print('KITCHEN_RELEASE_PASS',target,u.sha(target.read_bytes()))
if __name__=='__main__':main()
