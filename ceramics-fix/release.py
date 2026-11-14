#!/usr/bin/env python3
"""Stage the recipe/guide repair and keep only its own mod/ADM metadata current."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
from update_installation import plan,apply,encode
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BOOK='empirecraft-handbook-1.0.5.jar';NAME='Empirecraft-Paper-Pottery-1'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encode(v))
def archive(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for n,raw in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,14,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def stage():
 book=REPO/'handbook/dist'/BOOK;files={};release=dict(id='empirecraft_guidebook',version='1.0.5',file=BOOK,sha256=sha(book),files={})
 for side,folder in [('client','kubejs'),('server','server/kubejs')]:
  relative={'mods/'+BOOK:book.read_bytes(),'mods/'+BOOK+'.sha256':book.with_suffix('.jar.sha256').read_bytes()}
  for name in ['zzzzzzzzz_empirecraft_paper_books.js','zzzzzzzzzz_empirecraft_sandpaper.js']:relative['kubejs/server_scripts/'+name]=(REPO/folder/'server_scripts'/name).read_bytes()
  release['files'][side]={n:hashlib.sha256(raw).hexdigest() for n,raw in relative.items()}
  for n,raw in relative.items():files[side+'/'+n]=raw
 assert release['files']['client']==release['files']['server'];bundle=ROOT/'build/bundle'
 for n,raw in files.items():p=bundle/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
 write(bundle/'release.json',release);write(ROOT/'release.json',release);return release,files,bundle

def install(release,bundle):
 manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text());old=[]
 prepared=[(side,root,plan(root,side,bundle,release)) for side,root in [('client',REPO),('server',REPO/'server')]]
 for side,root,operation in prepared:
  old.extend((side,root,p) for p in operation[2] if p.suffix=='.jar');apply(operation)
 for side,root,p in old:
  relative=p.relative_to(REPO).as_posix();target=REPO/'disabled-mods/paper-pottery-2026-11-14'/side/'mods'/p.name;target.parent.mkdir(parents=True,exist_ok=True)
  shutil.copyfile(root/'.empirecraft-backups/paper-pottery-1/mods'/p.name,target);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+target.name+'\n')
  if relative not in manifest['retired']:manifest['retired'].append(relative)
  manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
 for side,root,_ in prepared:
  backup=root/'.empirecraft-backups/paper-pottery-1'
  if backup.exists():
   shutil.copytree(backup,ROOT/'build/install-backup'/side,dirs_exist_ok=True);shutil.rmtree(backup)
   if not any(backup.parent.iterdir()):backup.parent.rmdir()
 book=REPO/'mods'/BOOK;raw=book.read_bytes();row_update=dict(version='1.0.5',file=BOOK,sha256=sha(book),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
 for row in manifest['releases']:
  if row['id']==release['id']:row.update(row_update,artifact='mods/'+BOOK)
 write(manifest_path,manifest)
 p=REPO/'modpack-lock.json';lock=json.loads(p.read_text())
 for row in lock['mods']:
  if row['id']==release['id']:row.update(row_update,updated=True,pin_reason='1.0.5: verified V placement/pit firing, quern setup and TFC sandpaper compatibility. ceramics-fix/audit/native-fixed.json.')
 write(p,lock)
 for folder,inventory in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/inventory;rows=json.loads(p.read_text())
  for row in rows:
   if row['name'].startswith('empirecraft-handbook-'):row.update(name=BOOK,path=folder+'/'+BOOK,size=len(raw),sha=row_update['git_sha'])
  write(p,rows)
 p=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(p.read_text());coverage['jar_sha256']={k:v for k,v in coverage['jar_sha256'].items() if not k.startswith('empirecraft-handbook-')};coverage['jar_sha256'][BOOK]=sha(book);write(p,coverage)
 p=REPO/'native-content/retained-code.json';retained={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith('empirecraft-handbook-')};retained[BOOK]={};write(p,retained)

def main():
 p=argparse.ArgumentParser();p.add_argument('--stage-only',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();release,files,bundle=stage()
 if a.stage_only:print('PAPER_POTTERY_STAGED');return
 native=json.loads((ROOT/'audit/native-fixed.json').read_text());installer=json.loads((ROOT/'audit/installer.json').read_text());assert native['status']==installer['status']=='passed' and installer['sha256']==release['sha256']
 assert installer['updater_sha256']==sha(ROOT/'update_installation.py')
 assert native['script_sha256']==sha(REPO/'server/kubejs/server_scripts/zzzzzzzzzz_empirecraft_sandpaper.js')
 book_checks={}
 for key in ['layout-test','navigation-test','client-template']:
  book_checks[key]=json.loads((REPO/'handbook/audit'/(key+'.json')).read_text());assert book_checks[key]['status']=='passed' and book_checks[key]['sha256']==release['sha256']
 report=dict(status='passed',release=release,native=native,installer=installer,handbook=book_checks,production_world_opened=False,graphical_client_tested=False);write(ROOT/'audit/release.json',report)
 if a.install:install(release,bundle)
 installed=ROOT/'audit/installed.json'
 if installed.exists():
  report['installed']=json.loads(installed.read_text());assert report['installed']['status']=='passed' and report['installed']['book_sha256']==release['sha256'];write(ROOT/'audit/release.json',report)
 files.update({'release.json':encode(release),'verification.json':encode(report),'INSTALL-UA.md':(ROOT/'INSTALL-UA.md').read_bytes(),'update_installation.py':(ROOT/'update_installation.py').read_bytes()})
 for base in [ROOT,REPO/'handbook']:
  for p in base.rglob('*'):
   if p.is_file() and not any(part in ['build','dist','__pycache__'] for part in p.relative_to(base).parts):files['sources/'+p.relative_to(REPO).as_posix()]=p.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+n+'\n' for n,raw in sorted(files.items())).encode();target=ROOT/'dist'/(NAME+'-install.zip');archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n')
 with zipfile.ZipFile(target) as z:
  assert z.testzip() is None
  for line in z.read('SHA256SUMS.txt').decode().splitlines():digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
 print('PAPER_POTTERY_RELEASE_PASS',sha(target))
if __name__=='__main__':main()
