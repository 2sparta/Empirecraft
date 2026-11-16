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
 lock=json.loads((REPO/'modpack-lock.json').read_text());forging=next(v for v in lock['mods'] if v['id']=='empirecraft_forging');assert forging['version']=='1.1.5'
 ottoman_file='hamzabus_ottoman_mod-1.0.4-forge-empirecraft.1.jar';ottoman_raw=(REPO/'mods'/ottoman_file).read_bytes();ottoman_digest=hashlib.sha256(ottoman_raw).hexdigest()
 assert runtime['dependency_sha256'][ottoman_file]==ottoman_digest
 ottoman=dict(id='hamzabus_ottoman_mod',file=ottoman_file,version='1.0.4-empirecraft.1',sha256=ottoman_digest,sha512=hashlib.sha512(ottoman_raw).hexdigest(),size=len(ottoman_raw),git_sha=hashlib.sha1(b'blob '+str(len(ottoman_raw)).encode()+b'\0'+ottoman_raw).hexdigest(),sides=['client','server'],source='local:ottoman (HamzaBU, MIT)',added=True,updated=True,pin_reason='User-supplied Ottoman Empire 1.0.4: canonical steel, component armor assembly, native weapon forging, PR149 armor/weapon specialization, directional shield without knockback and removal of special Topuz armor damage. Original models and MIT credits preserved.')
 rows=[row,forging,ottoman];ids={v['id'] for v in rows}
 release['additional_mods']=[{k:v[k] for k in ['id','file','version','sha256']} for v in [forging,ottoman]]
 for side,folder in [('client',REPO),('server',REPO/'server')]:
  assert (folder/'mods'/NAME).read_bytes()==raw
  payload={'mods/'+NAME:raw,'mods/'+NAME+'.sha256':(ROOT/'dist'/NAME).with_suffix('.jar.sha256').read_bytes(),'config/overgeared-common.toml':(folder/'config/overgeared-common.toml').read_bytes()}
  for relative in ['mods/'+forging['file'],'mods/'+forging['file']+'.sha256','mods/'+ottoman_file,'mods/'+ottoman_file+'.sha256','kubejs/server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js']:payload[relative]=(folder/relative).read_bytes()
  release['files'][side]={n:hashlib.sha256(v).hexdigest() for n,v in payload.items()};files.update({side+'/'+n:v for n,v in payload.items()})
 for filename,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
  p=REPO/filename;data=json.loads(p.read_text())
  for value in rows:
   previous=next((v for v in data[key] if v['id']==value['id']),{});updated=dict(previous,**value)
   if key=='releases':
    old=previous.get('artifact');updated['artifact']='mods/'+value['file']
    if old and old!=updated['artifact']:
     for retired in [old,'server/'+old]:
      if retired not in data.setdefault('retired',[]):data['retired'].append(retired)
   data[key]=[v for v in data[key] if v['id']!=value['id']]+[updated]
  if key=='mods':data[key].sort(key=lambda v:v['id']);data['updated_mod_count']=sum(bool(v.get('updated')) for v in data[key])
  for side,path in [('client','mods'),('server','server/mods')]:data[side+'_jar_count']=len(list((REPO/path).glob('*.jar')))
  write(p,data)
 for folder,filename in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/filename;data=json.loads(p.read_text());prefixes=('empirecraft-armory-','empirecraft-forging-','hamzabus_ottoman_mod-');data=[v for v in data if not v['name'].startswith(prefixes)]
  for v in rows:data.append(dict(name=v['file'],path=folder+'/'+v['file'],size=v['size'],sha=v['git_sha']))
  write(p,sorted(data,key=lambda v:v['name']))
 p=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(p.read_text());data['jar_sha256']={k:v for k,v in data['jar_sha256'].items() if not k.startswith(('empirecraft-armory-','empirecraft-forging-','hamzabus_ottoman_mod-'))}
 for v in rows:data['jar_sha256'][v['file']]=v['sha256']
 data['jar_count']=len(data['jar_sha256']);write(p,data)
 p=REPO/'native-content/retained-code.json';data=json.loads(p.read_text());data={k:v for k,v in data.items() if not k.startswith(('empirecraft-armory-','empirecraft-forging-','hamzabus_ottoman_mod-'))}
 for v in rows:
  with zipfile.ZipFile(REPO/'mods'/v['file']) as z:data[v['file']]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,data)
 p=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=p.read_text(encoding='utf-8-sig');original,report=core.parse_adm(text);updated=core.adm_update(original,release);assert [v for v in original['modIds'] if v['id'] not in ids]==[v for v in updated['modIds'] if v['id'] not in ids];p.write_text(core.render_adm(text,original,updated,report))
 write(ROOT/'release.json',release)
 for name in ['release.json','update_installation.py','install_core.py','README.uk.md','LICENSE']:files[name]=(ROOT/name).read_bytes()
 for f in (ROOT/'audit').glob('*.json'):
   if f.name not in ['installer.json','delivery.json','bundle.json','upload-payload.json','launcher-preview.json']:files['verification/'+f.name]=f.read_bytes()
 for f in (REPO/'ottoman/audit').glob('*.json'):files['verification/ottoman-'+f.name]=f.read_bytes()
 files['OTTOMAN.uk.md']=(REPO/'ottoman/README.uk.md').read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(body).hexdigest()+'  '+name+'\n' for name,body in sorted(files.items())).encode()
 target=ROOT/'dist/Empirecraft-Armorer-Workshop-1-install.zip';archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n');print('ARMORY_RELEASE_PASS',target,sha(target))
if __name__=='__main__':main()
