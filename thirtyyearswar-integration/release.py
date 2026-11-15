"""Update both checked-out pack sides and package only the requested mod/resources."""
import argparse,copy,hashlib,json,shutil,zipfile
from pathlib import Path
import update_installation as u
from patch_upstream import JAR
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;ID='thirtyyearswar';VERSION='1.3-empirecraft.1'
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(u.encode(v))
def package():
 generated=json.loads((ROOT/'audit/generated.json').read_text());raw=(REPO/'mods'/JAR).read_bytes();digest=u.sha(raw);files={}
 release=dict(id=ID,version=VERSION,file=JAR,sha256=digest,files={})
 for side,root in [('client',REPO),('server',REPO/'server')]:
  payload={'mods/'+JAR:raw,'mods/'+JAR+'.sha256':(REPO/'mods'/(JAR+'.sha256')).read_bytes()}
  for name,expected in generated['files'].items():
   source=REPO/'kubejs'/name;data=source.read_bytes();assert u.sha(data)==expected;payload['kubejs/'+name]=data
  release['files'][side]={n:u.sha(v) for n,v in payload.items()}
  for n,v in payload.items():files[side+'/'+n]=v
 return release,files

def install_metadata(release):
 raw=(REPO/'mods'/JAR).read_bytes();digest=u.sha(raw);blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
 row=dict(id=ID,version=VERSION,file=JAR,sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=blob,sides=['client','server'],source='uploaded:epic-knights-thirty-years-war-1.20.1-forge-1.3.jar',added=True,updated=True,pin_reason='Metadata-only Epic Knights 10.10 compatibility, API audit 144 references; all crafting integrated with TFC/Overgeared/Create. See thirtyyearswar-integration/audit.')
 for name,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
  p=REPO/name;data=json.loads(p.read_text());data[key]=[v for v in data[key] if v['id']!=ID]+[dict(row)]
  if key=='releases':data[key][-1]['artifact']='mods/'+JAR
  else:data[key].sort(key=lambda v:v['id']);data['updated_mod_count']=sum(bool(v.get('updated')) for v in data[key])
  for side,folder in [('client','mods'),('server','server/mods')]:data[side+'_jar_count']=len(list((REPO/folder).glob('*.jar')))
  write(p,data)
 for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/name;rows=json.loads(p.read_text());rows=[v for v in rows if not v['name'].startswith('epic-knights-thirty-years-war-')];rows.append(dict(name=JAR,path=folder+'/'+JAR,size=len(raw),sha=blob));write(p,rows)
 p=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(p.read_text());data['jar_sha256'][JAR]=digest;data['jar_count']=len(data['jar_sha256']);write(p,data)
 p=REPO/'native-content/retained-code.json';data=json.loads(p.read_text())
 with zipfile.ZipFile(REPO/'mods'/JAR) as z:data[JAR]={n:u.sha(z.read(n)) for n in z.namelist() if n.endswith('.class')}
 write(p,data)
 adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';before=adm.read_text(encoding='utf-8-sig');original,report=u.parse_adm(before);updated=u.adm_update(original,release);adm.write_text(u.render_adm(before,original,updated,report))
 assert [v for v in original['modIds'] if v['id']!=ID]==[v for v in updated['modIds'] if v['id']!=ID]
 assert original['resourcePackIds']==updated['resourcePackIds']
 for name,expected in release['files']['client'].items():assert u.sha((REPO/name).read_bytes())==expected and (REPO/name).read_bytes()==(REPO/'server'/name).read_bytes()
 write(ROOT/'audit/installed.json',dict(status='passed',sha256=digest,both_sides_identical=True,unrelated_adm_rows_preserved=True))

def main():
 p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');a=p.parse_args();release,files=package()
 runtime=json.loads((ROOT/'audit/runtime.json').read_text());assert runtime['status']=='passed' and runtime['probe_sha256']==u.sha((ROOT/'Probe.java').read_bytes()) and runtime['generated_sha256']==u.sha((ROOT/'recipes.json').read_bytes())
 if a.install:install_metadata(release)
 write(ROOT/'release.json',release);files['release.json']=u.encode(release);files['update_installation.py']=(ROOT/'update_installation.py').read_bytes();files['README.uk.md']=(ROOT/'README.uk.md').read_bytes()
 for name in ['generated.json','upstream.json','runtime.json','installed.json','installer.json','limitations.json']:files['verification/'+name]=(ROOT/'audit'/name).read_bytes()
 files['SHA256SUMS.txt']=''.join(u.sha(raw)+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
 target=ROOT/'dist/Empirecraft-ThirtyYearsWar-1-install.zip';target.parent.mkdir(exist_ok=True)
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for name,raw in sorted(files.items()):
   info=zipfile.ZipInfo(name,(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
 target.with_suffix('.zip.sha256').write_text(u.sha(target.read_bytes())+'  '+target.name+'\n')
 print('TYW_RELEASE_PASS',target.name,u.sha(target.read_bytes()))
if __name__=='__main__':main()
