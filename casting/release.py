#!/usr/bin/env python3
"""Install only the new casting module and its own modpack/ADM identity."""
import argparse,hashlib,json,shutil,zipfile
from pathlib import Path
from update_installation import plan,apply,encode
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;JAR='empirecraft-casting-1.1.0.jar';ID='empirecraft_casting';NAME='Empirecraft-Metallurgy-2'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encode(v))
def archive(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for name,raw in sorted(files.items()):
   i=zipfile.ZipInfo(name,(2026,11,14,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def stage():
 artifact=ROOT/'dist'/JAR;release=dict(id=ID,version='1.1.0',file=JAR,sha256=sha(artifact),files={});files={}
 for side in ['client','server']:
  payload={'mods/'+JAR:artifact.read_bytes(),'mods/'+JAR+'.sha256':artifact.with_suffix('.jar.sha256').read_bytes(),'kubejs/client_scripts/empirecraft_metallurgy.js':(REPO/'kubejs/client_scripts/empirecraft_metallurgy.js').read_bytes()}
  if side=='server':payload['kubejs/server_scripts/zzzz_empirecraft_smithing.js']=(REPO/'server/kubejs/server_scripts/zzzz_empirecraft_smithing.js').read_bytes()
  release['files'][side]={n:hashlib.sha256(raw).hexdigest() for n,raw in payload.items()}
  for name,raw in payload.items():files[side+'/'+name]=raw
 bundle=ROOT/'build/bundle'
 for name,raw in files.items():p=bundle/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
 write(bundle/'release.json',release);write(ROOT/'release.json',release);return release,files,bundle

def install(release,bundle):
 prepared=[(side,root,plan(root,side,bundle,release)) for side,root in [('client',REPO),('server',REPO/'server')]]
 retired=[]
 for side,root,operation in prepared:
  retired.extend((side,root,p) for p in operation[2] if p.suffix=='.jar');apply(operation)
 manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text())
 for side,root,p in retired:
  relative=p.relative_to(REPO).as_posix();target=REPO/'disabled-mods/metallurgy-2026-11-14'/side/'mods'/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/'.empirecraft-backups/automatic-casting-1/mods'/p.name,target);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+target.name+'\n')
  if relative not in manifest['retired']:manifest['retired'].append(relative)
  manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
 write(manifest_path,manifest)
 for side,root,_ in prepared:
  backup=root/'.empirecraft-backups/automatic-casting-1'
  if backup.exists():
   shutil.copytree(backup,ROOT/'build/install-backup'/side,dirs_exist_ok=True);shutil.rmtree(backup)
   if not any(backup.parent.iterdir()):backup.parent.rmdir()
 artifact=ROOT/'dist'/JAR;raw=artifact.read_bytes();row=dict(id=ID,version='1.1.0',file=JAR,sha256=sha(artifact),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),sides=['client','server'],source='local:casting',added=True,updated=True,pin_reason='1.1.0: crucible hopper/funnel input, 16 fuel slots, steel/black-steel progression and heated quality forging across surviving metals. casting/audit/native.json.')
 for name,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
  p=REPO/name;data=json.loads(p.read_text());entries=[r for r in data[key] if r['id']==ID];value=dict(row)
  if key=='releases':value['artifact']='casting/dist/'+JAR
  if entries:entries[0].update(value)
  else:data[key].append(value)
  write(p,data)
 for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/name;rows=json.loads(p.read_text());value=dict(name=JAR,path=folder+'/'+JAR,size=len(raw),sha=row['git_sha']);existing=[r for r in rows if r['name'].startswith('empirecraft-casting-')]
  if existing:existing[0].update(value)
  else:rows.append(value)
  write(p,rows)
 p=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(p.read_text());data['jar_sha256']={k:v for k,v in data['jar_sha256'].items() if not k.startswith('empirecraft-casting-')};data['jar_sha256'][JAR]=sha(artifact);data['jar_count']=len(data['jar_sha256']);write(p,data)
 p=REPO/'native-content/retained-code.json';data={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith('empirecraft-casting-')}
 with zipfile.ZipFile(artifact) as z:data[JAR]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,data)

def main():
 p=argparse.ArgumentParser();p.add_argument('--stage-only',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();release,files,bundle=stage()
 if a.stage_only:print('CASTING_STAGED');return
 evidence={k:json.loads((ROOT/'audit'/(k+'.json')).read_text()) for k in ['build','native','resources','installer']}
 for record in evidence.values():assert record['status']=='passed' and record['sha256']==release['sha256']
 assert evidence['native']['probe_sha256']==sha(ROOT/'tests/CastingProbe.java') and evidence['native']['metallurgy_probe_sha256']==sha(ROOT/'tests/MetallurgyProbe.java') and evidence['native']['smithing_script_sha256']==sha(REPO/'server/kubejs/server_scripts/zzzz_empirecraft_smithing.js') and evidence['installer']['updater_sha256']==sha(ROOT/'update_installation.py')
 assert all(sha(REPO/name)==digest for name,digest in evidence['build']['source_sha256'].items())
 if a.install:
  install(release,bundle)
  import verify_installed
  verify_installed.main()
 if (ROOT/'audit/installed.json').exists():
  evidence['installed']=json.loads((ROOT/'audit/installed.json').read_text());assert evidence['installed']['status']=='passed' and evidence['installed']['sha256']==release['sha256']
 report=dict(status='passed',release=release,evidence=evidence,production_world_opened=False,graphical_client_tested=False);write(ROOT/'audit/release.json',report)
 files.update({'release.json':encode(release),'verification.json':encode(report),'README-UA.md':(ROOT/'README.uk.md').read_bytes(),'update_installation.py':(ROOT/'update_installation.py').read_bytes()})
 for p in ROOT.rglob('*'):
  if p.is_file() and not any(part in ['build','dist','__pycache__'] for part in p.relative_to(ROOT).parts):files['sources/'+p.relative_to(REPO).as_posix()]=p.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+n+'\n' for n,raw in sorted(files.items())).encode();target=ROOT/'dist'/(NAME+'-install.zip');archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n')
 with zipfile.ZipFile(target) as z:
  assert z.testzip() is None and not any('/config/' in n or '/logs/' in n or '/world/' in n or n.endswith(('.log','.db','.sqlite')) for n in z.namelist())
  for line in z.read('SHA256SUMS.txt').decode().splitlines():digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
 print('CASTING_RELEASE_PASS',sha(target))
if __name__=='__main__':main()
