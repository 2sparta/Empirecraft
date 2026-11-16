"""Package the new addon and update its one ADM identity without replacing other rows."""
import hashlib,json,shutil,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME,sha,archive
import install_core as core
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def main():
 jar=ROOT/'dist'/NAME;raw=jar.read_bytes();digest=sha(jar)
 runtime=json.loads((ROOT/'audit/runtime.json').read_text());assert runtime['status']=='passed' and runtime['mod_sha256']==digest
 row=dict(id=core.ID,file=NAME,version='1.0.0',sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),sides=['client','server'],source='local:armory',updated=True,pin_reason='Component-based armor forging, controlled heat treatment, persistent staged assembly and component repairs. Source and verification: armory/.')
 release={k:row[k] for k in ['id','file','version','sha256']};release['files']={};files={}
 for side,folder in [('client',REPO),('server',REPO/'server')]:
  assert (folder/'mods'/NAME).read_bytes()==raw
  payload={'mods/'+NAME:raw,'mods/'+NAME+'.sha256':(ROOT/'dist'/NAME).with_suffix('.jar.sha256').read_bytes(),'config/overgeared-common.toml':(folder/'config/overgeared-common.toml').read_bytes()}
  release['files'][side]={n:hashlib.sha256(v).hexdigest() for n,v in payload.items()};files.update({side+'/'+n:v for n,v in payload.items()})
 for filename,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
  p=REPO/filename;data=json.loads(p.read_text());previous=next((v for v in data[key] if v['id']==core.ID),{});updated=dict(previous,**row)
  if key=='releases':updated['artifact']='mods/'+NAME
  data[key]=[v for v in data[key] if v['id']!=core.ID]+[updated]
  if key=='mods':data[key].sort(key=lambda v:v['id']);data['updated_mod_count']=sum(bool(v.get('updated')) for v in data[key])
  for side,path in [('client','mods'),('server','server/mods')]:data[side+'_jar_count']=len(list((REPO/path).glob('*.jar')))
  write(p,data)
 for folder,filename in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/filename;data=json.loads(p.read_text());data=[v for v in data if not v['name'].startswith('empirecraft-armory-')];data.append(dict(name=NAME,path=folder+'/'+NAME,size=len(raw),sha=row['git_sha']));write(p,sorted(data,key=lambda v:v['name']))
 p=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(p.read_text());data['jar_sha256'][NAME]=digest;data['jar_count']=len(data['jar_sha256']);write(p,data)
 p=REPO/'native-content/retained-code.json';data=json.loads(p.read_text())
 with zipfile.ZipFile(jar) as z:data[NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,data)
 p=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=p.read_text(encoding='utf-8-sig');original,report=core.parse_adm(text);updated=core.adm_update(original,release);assert [v for v in original['modIds'] if v['id']!=core.ID]==[v for v in updated['modIds'] if v['id']!=core.ID];p.write_text(core.render_adm(text,original,updated,report))
 write(ROOT/'release.json',release)
 for name in ['release.json','update_installation.py','install_core.py','README.uk.md','LICENSE']:files[name]=(ROOT/name).read_bytes()
 for f in (ROOT/'audit').glob('*.json'):
  if f.name not in ['installer.json','delivery.json']:files['verification/'+f.name]=f.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(body).hexdigest()+'  '+name+'\n' for name,body in sorted(files.items())).encode()
 target=ROOT/'dist/Empirecraft-Armorer-Workshop-1-install.zip';archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n');print('ARMORY_RELEASE_PASS',target,sha(target))
if __name__=='__main__':main()
