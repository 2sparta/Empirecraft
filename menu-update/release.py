#!/usr/bin/env python3
"""Stage, verify and install the cumulative handbook/menu update without replacing player data."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
from update_installation import plan,apply,encode
ROOT=build.ROOT;REPO=build.REPO;NAME='Empirecraft-Book-Menus-1'
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encode(value))
def stage():
 files={}
 with zipfile.ZipFile(REPO/'pack-fixes/dist/Empirecraft-Runtime-Repair-1-install.zip') as z:
  previous=json.loads(z.read('release.json'))
  for side,rows in previous['files'].items():
   for path,digest in rows.items():
    raw=z.read(side+'/'+path);assert hashlib.sha256(raw).hexdigest()==digest
    if not path.startswith('mods/empirecraft-handbook-'):files[side+'/'+path]=raw
 mods=[m for m in previous['mods'] if m['id']!='empirecraft_guidebook']
 for mid,version,p in [('empirecraft_guidebook','1.0.4',REPO/'handbook/dist/empirecraft-handbook-1.0.4.jar'),('empirecraft_townymap','1.11.1',ROOT/'dist/empirecraft-towny-journeymap-1.11.1.jar')]:
  mods.append(dict(id=mid,version=version,file=p.name,sha256=build.sha(p),sides=['client','server']))
  for side in ['client','server']:
   files[side+'/mods/'+p.name]=p.read_bytes();files[side+'/mods/'+p.name+'.sha256']=p.with_suffix('.jar.sha256').read_bytes()
 plugin=ROOT/'dist/empirecraft-feudal-1.19.3.jar'
 for p in [plugin,plugin.with_suffix('.jar.sha256')]:files['server/plugins/'+p.name]=p.read_bytes()
 release={'mods':mods,'plugins':previous['plugins']+[dict(name='EmpirecraftFeudal',version='1.19.3',file=plugin.name,sha256=build.sha(plugin))],
          'server_properties':previous['server_properties'],'files':{side:{n.removeprefix(side+'/'):hashlib.sha256(raw).hexdigest() for n,raw in files.items() if n.startswith(side+'/')} for side in ['client','server']}}
 bundle=ROOT/'build/bundle'
 if bundle.exists():shutil.rmtree(bundle)
 for name,raw in files.items():p=bundle/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
 write(bundle/'release.json',release);write(ROOT/'release.json',release);return release,files,bundle

def install(release,bundle):
 manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text());retired=[]
 prepared=[(side,root,plan(root,side,bundle,release)) for side,root in [('client',REPO),('server',REPO/'server')]]
 for side,root,operation in prepared:
  retired.extend((side,root,p,p.relative_to(REPO).as_posix()) for p in operation[2] if p.suffix=='.jar');apply(operation)
 for side,root,p,relative in retired:
  target=REPO/'disabled-mods/menu-language-2026-11-13'/side/p.relative_to(root);target.parent.mkdir(parents=True,exist_ok=True)
  shutil.copyfile(root/'.empirecraft-backups/book-menus-1'/p.relative_to(root),target);target.with_suffix('.jar.sha256').write_text(build.sha(target)+'  '+target.name+'\n')
  if relative not in manifest['retired']:manifest['retired'].append(relative)
  manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
 for side,root,_ in prepared:
  backup=root/'.empirecraft-backups/book-menus-1'
  if backup.exists():
   shutil.copytree(backup,ROOT/'build/installation-backup'/side,dirs_exist_ok=True);shutil.rmtree(backup)
   if not any(backup.parent.iterdir()):backup.parent.rmdir()
 modules={m['id']:m for m in release['mods']}
 for row in manifest['releases']:
  if row['id'] in modules:row['artifact']='mods/'+modules[row['id']]['file']
 write(manifest_path,manifest)
 lock_path=REPO/'modpack-lock.json';lock=json.loads(lock_path.read_text())
 for row in lock['mods']:
  if row['id']=='empirecraft_guidebook':row.update(updated=True,pin_reason='1.0.4: five native Patchouli navigation groups; all chapter/article IDs preserved. handbook/audit/navigation-test.json.')
  if row['id']=='empirecraft_townymap':row.update(updated=True,pin_reason='1.11.1: independent persisted menu locale and localized fleet selectors. menu-update/audit/verification.json.')
 write(lock_path,lock);subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
 for folder,inventory in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  rows=[]
  for p in sorted((REPO/folder).glob('*.jar')):
   raw=p.read_bytes();rows.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
  write(REPO/inventory,rows)
 p=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(p.read_text());coverage['jar_sha256']={j.name:build.sha(j) for j in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(p,coverage)
 p=REPO/'native-content/retained-code.json';retained={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith(('empirecraft-handbook-','empirecraft-towny-journeymap-'))}
 for prefix in ['empirecraft-handbook-','empirecraft-towny-journeymap-']:
  j=next((REPO/'mods').glob(prefix+'*.jar'))
  with zipfile.ZipFile(j) as z:retained[j.name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,retained)

def main():
 p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');p.add_argument('--stage-only',action='store_true');a=p.parse_args();release,files,bundle=stage()
 if a.stage_only:print('BOOK_MENUS_STAGED');return
 tests=json.loads((ROOT/'audit/verification.json').read_text());installer=json.loads((ROOT/'audit/installer.json').read_text())
 assert tests['status']==installer['status']=='passed'
 for row in release['mods']:
  if row['id'] in ['empirecraft_townymap']:assert tests['sha256'][row['file']]==row['sha256']
 for row in release['plugins']:
  if row['name']=='EmpirecraftFeudal':assert tests['sha256'][row['file']]==row['sha256']
 assert installer['mods']=={m['id']:m['sha256'] for m in release['mods']}
 assert installer['plugins']=={m['name']:m['sha256'] for m in release['plugins']}
 assert installer['updater_sha256']==build.sha(ROOT/'update_installation.py')
 book=next(m for m in release['mods'] if m['id']=='empirecraft_guidebook');evidence={}
 for key in ['layout-test','navigation-test','client-template']:
  evidence[key]=json.loads((REPO/'handbook/audit'/(key+'.json')).read_text());assert evidence[key]['status']=='passed' and evidence[key]['sha256']==book['sha256']
 packaged=json.loads((ROOT/'audit/packaged.json').read_text());assert packaged['status']=='passed' and packaged['sha256']==tests['sha256']
 report=dict(status='passed',release=release,backend_client=tests,packaged=packaged,handbook=evidence,installer=installer,graphical_client_tested=False,production_world_opened=False)
 write(ROOT/'audit/release.json',report)
 if a.install:install(release,bundle)
 files.update({'release.json':encode(release),'update_installation.py':(ROOT/'update_installation.py').read_bytes(),'INSTALL-UA.md':(ROOT/'INSTALL-UA.md').read_bytes(),'verification.json':encode(report)})
 sources={}
 for folder in ['menu-update','handbook','feudal/src','bridge/src']:
  for p in (REPO/folder).rglob('*'):
   if p.is_file() and not any(part in ['build','dist','audit','__pycache__','native-inputs'] for part in p.relative_to(REPO).parts):sources[p.relative_to(REPO).as_posix()]=p.read_bytes()
 for relative in ['feudal/build.py','feudal/release.py','feudal/LOCALIZATION.md','bridge/build.gradle']:sources[relative]=(REPO/relative).read_bytes()
 source=ROOT/'dist'/(NAME+'-sources.zip');build.jar(source,sources);source.with_suffix('.jar.sha256').unlink(missing_ok=True);files[source.name]=source.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
 target=ROOT/'dist'/(NAME+'-install.zip');build.jar(target,files);target.with_suffix('.jar.sha256').unlink(missing_ok=True);target.with_suffix('.zip.sha256').write_text(build.sha(target)+'  '+target.name+'\n')
 with zipfile.ZipFile(target) as z:
  assert z.testzip() is None
  for line in z.read('SHA256SUMS.txt').decode().splitlines():
   digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
 if a.install:subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
 print('BOOK_MENUS_RELEASE_PASS',build.sha(target))
if __name__=='__main__':main()
