#!/usr/bin/env python3
"""Package the reviewed client GUI and update only its bridge identity in the modpack."""
import argparse,hashlib,json,shutil,zipfile
from pathlib import Path
from update_installation import plan,apply,encode
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;JAR='empirecraft-towny-journeymap-1.11.2.jar';NAME='Empirecraft-Fleet-Menu-1';ID='empirecraft_townymap'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encode(v))
def archive(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for n,raw in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,14,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def stage():
 artifact=ROOT/'dist'/JAR;files={};release=dict(id=ID,version='1.11.2',file=JAR,sha256=sha(artifact),files={})
 relative={'mods/'+JAR:artifact.read_bytes(),'mods/'+JAR+'.sha256':artifact.with_suffix('.jar.sha256').read_bytes()}
 for side in ['client','server']:
  release['files'][side]={n:hashlib.sha256(raw).hexdigest() for n,raw in relative.items()}
  for n,raw in relative.items():files[side+'/'+n]=raw
 bundle=ROOT/'build/bundle'
 for n,raw in files.items():p=bundle/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
 write(bundle/'release.json',release);write(ROOT/'release.json',release);return release,files,bundle

def install(release,bundle):
 manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text());old=[]
 prepared=[(side,root,plan(root,side,bundle,release)) for side,root in [('client',REPO),('server',REPO/'server')]]
 for side,root,operation in prepared:old.extend((side,root,p) for p in operation[2] if p.suffix=='.jar');apply(operation)
 for side,root,p in old:
  relative=p.relative_to(REPO).as_posix();target=REPO/'disabled-mods/fleet-menu-2026-11-14'/side/'mods'/p.name;target.parent.mkdir(parents=True,exist_ok=True)
  shutil.copyfile(root/'.empirecraft-backups/fleet-menu-1/mods'/p.name,target);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+target.name+'\n')
  if relative not in manifest['retired']:manifest['retired'].append(relative)
  manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
 for side,root,_ in prepared:
  backup=root/'.empirecraft-backups/fleet-menu-1'
  if backup.exists():
   shutil.copytree(backup,ROOT/'build/install-backup'/side,dirs_exist_ok=True);shutil.rmtree(backup)
   if not any(backup.parent.iterdir()):backup.parent.rmdir()
 artifact=REPO/'mods'/JAR;raw=artifact.read_bytes();row_update=dict(version='1.11.2',file=JAR,sha256=sha(artifact),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
 entries=[row for row in manifest['releases'] if row['id']==ID]
 if entries:entries[0].update(row_update,artifact='mods/'+JAR)
 else:manifest['releases'].append(dict(id=ID,**row_update,sides=['client','server'],source='local:bridge',artifact='mods/'+JAR))
 write(manifest_path,manifest)
 p=REPO/'modpack-lock.json';lock=json.loads(p.read_text())
 for row in lock['mods']:
  if row['id']==ID:row.update(row_update,updated=True,pin_reason='1.11.2: responsive maritime fleet UI, native form selectors and scoped scrolling. fleet-menu/audit/build.json.')
 write(p,lock)
 for folder,inventory in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/inventory;rows=json.loads(p.read_text())
  for row in rows:
   if row['name'].startswith('empirecraft-towny-journeymap-'):row.update(name=JAR,path=folder+'/'+JAR,size=len(raw),sha=row_update['git_sha'])
  write(p,rows)
 p=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(p.read_text());coverage['jar_sha256']={k:v for k,v in coverage['jar_sha256'].items() if not k.startswith('empirecraft-towny-journeymap-')};coverage['jar_sha256'][JAR]=sha(artifact);write(p,coverage)
 p=REPO/'native-content/retained-code.json';retained={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith('empirecraft-towny-journeymap-')}
 with zipfile.ZipFile(artifact) as z:retained[JAR]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,retained)

def main():
 p=argparse.ArgumentParser();p.add_argument('--stage-only',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();release,files,bundle=stage()
 if a.stage_only:print('FLEET_MENU_STAGED');return
 build=json.loads((ROOT/'audit/build.json').read_text());installer=json.loads((ROOT/'audit/installer.json').read_text());packaged=json.loads((ROOT/'audit/packaged.json').read_text())
 assert build['status']==installer['status']==packaged['status']=='passed' and build['sha256']==installer['sha256']==packaged['sha256']==release['sha256']
 assert installer['updater_sha256']==sha(ROOT/'update_installation.py')
 report=dict(status='passed',release=release,build=build,packaged=packaged,installer=installer,production_world_opened=False,graphical_client_tested=False)
 if a.install:
  install(release,bundle)
  import verify_installed
  verify_installed.main()
 if (ROOT/'audit/installed.json').exists():
  report['installed']=json.loads((ROOT/'audit/installed.json').read_text());assert report['installed']['status']=='passed' and report['installed']['bridge_sha256']==release['sha256']
 write(ROOT/'audit/release.json',report)
 files.update({'release.json':encode(release),'verification.json':encode(report),'INSTALL-UA.md':(ROOT/'INSTALL-UA.md').read_bytes(),'update_installation.py':(ROOT/'update_installation.py').read_bytes()})
 for p in (ROOT/'preview').glob('*'):
  if p.is_file():files['preview/'+p.name]=p.read_bytes()
 for base in [ROOT,REPO/'bridge/src',REPO/'naval/tests',REPO/'menu-update/tests']:
  for p in base.rglob('*'):
   if p.is_file() and not any(part in ['build','dist','__pycache__','preview'] for part in p.relative_to(base).parts):files['sources/'+p.relative_to(REPO).as_posix()]=p.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+n+'\n' for n,raw in sorted(files.items())).encode();target=ROOT/'dist'/(NAME+'-install.zip');archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n')
 with zipfile.ZipFile(target) as z:
  assert z.testzip() is None and not any('/config/' in n or '/logs/' in n or '/world/' in n or n.endswith(('.log','.db','.sqlite')) for n in z.namelist())
  for line in z.read('SHA256SUMS.txt').decode().splitlines():digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
 print('FLEET_MENU_RELEASE_PASS',sha(target))
if __name__=='__main__':main()
