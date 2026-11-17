import hashlib,json,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME,archive,sha
import update_workbench as u
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def main():
 raw=(ROOT/'dist'/NAME).read_bytes();digest=sha(ROOT/'dist'/NAME);audit=json.loads((ROOT/'audit/runtime.json').read_text());assert audit['status']=='passed' and audit['mod_sha256']==digest
 lock=json.loads((REPO/'modpack-lock.json').read_text());old=next((r for r in lock['mods'] if r['id']=='empirecraft_armory'),{});row=dict(old,id='empirecraft_armory',file=NAME,version='1.1.0',sides=['client','server'],added=True,sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),source='local:armory',updated=True,pin_reason='Counted stage requirements, inventory and treatment hints, direct JEI component recipes, separate assembly/treatment/repair tabs and an original full opaque workbench block. Existing assembly, quality, repair and saved work IDs retained.')
 lock['mods']=[r for r in lock['mods'] if r['id']!='empirecraft_armory']+[row];lock['mods'].sort(key=lambda r:r['id']);lock['updated_mod_count']=sum(bool(r.get('updated')) for r in lock['mods']);lock['client_jar_count']=len(list((REPO/'mods').glob('*.jar')));lock['server_jar_count']=len(list((REPO/'server/mods').glob('*.jar')));write(REPO/'modpack-lock.json',lock)
 p=REPO/'validation/installed-releases-2026-11-01.json';d=json.loads(p.read_text());d['releases']=[r for r in d['releases'] if r['id']!='empirecraft_armory']+[dict(row,artifact='mods/'+NAME)];d['client_jar_count']=lock['client_jar_count'];d['server_jar_count']=lock['server_jar_count'];d['retired']=list(dict.fromkeys(d.get('retired',[])+['mods/'+old['file'],'server/mods/'+old['file']])) if old.get('file') and old['file']!=NAME else d.get('retired',[])
 write(p,d)
 for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/name;d=[r for r in json.loads(p.read_text()) if not r['name'].startswith('empirecraft-armory-')];d.append(dict(name=NAME,path=folder+'/'+NAME,size=len(raw),sha=row['git_sha']));write(p,sorted(d,key=lambda r:r['name']))
 p=REPO/'seasonal-calendar/audit/coverage.json';d=json.loads(p.read_text());d['jar_sha256']={k:v for k,v in d['jar_sha256'].items() if not k.startswith('empirecraft-armory-')};d['jar_sha256'][NAME]=digest;d['jar_count']=len(d['jar_sha256']);write(p,d)
 p=REPO/'native-content/retained-code.json';d={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith('empirecraft-armory-')}
 with zipfile.ZipFile(ROOT/'dist'/NAME) as z:d[NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,d)
 release=dict(id='empirecraft_armory',file=NAME,version='1.1.0',sha256=digest,files={});files={}
 for side in ['client','server']:
  payload={'kubejs/assets/empirecraft_forging/models/item/lockwork.json':(REPO/('server/' if side=='server' else '')/'kubejs/assets/empirecraft_forging/models/item/lockwork.json').read_bytes(),'mods/'+NAME:raw,'mods/'+NAME+'.sha256':(ROOT/'dist'/(NAME+'.sha256')).read_bytes()};release['files'][side]={n:hashlib.sha256(v).hexdigest() for n,v in payload.items()};files.update({side+'/'+n:v for n,v in payload.items()})
 adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=adm.read_text(encoding='utf-8-sig');before,report=u.parse_adm(text);after=u.adm_update(before,release);assert [r for r in before['modIds'] if r['id']!='empirecraft_armory']==[r for r in after['modIds'] if r['id']!='empirecraft_armory'];adm.write_text(u.render_adm(text,before,after,report))
 write(ROOT/'release.json',release);write(ROOT/'ui-release.json',release);files['release.json']=(ROOT/'ui-release.json').read_bytes()
 for n in ['update_workbench.py','WORKBENCH.uk.md']:files[n]=(ROOT/n).read_bytes()
 for p in (ROOT/'audit').glob('*.json'):
  if p.name in ['build.json','runtime.json','ui-resources.json','ui-client.json']:files['verification/'+p.name]=p.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(v).hexdigest()+'  '+n+'\n' for n,v in sorted(files.items())).encode();p=ROOT/'dist/Empirecraft-Workbench-UI-1.1.0-install.zip';archive(p,files);p.with_suffix('.zip.sha256').write_text(sha(p)+'  '+p.name+'\n');print('WORKBENCH_RELEASE_PASS',sha(p),p.stat().st_size)
if __name__=='__main__':main()
