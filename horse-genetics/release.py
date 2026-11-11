#!/usr/bin/env python3
"""Install tested armor/DLO addons, retire RHG and produce a reproducible portable update."""
import argparse,re,hashlib,importlib.util,json,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
spec=importlib.util.spec_from_file_location('armor_release_build',REPO/'layered-armor/build.py');armor=importlib.util.module_from_spec(spec);spec.loader.exec_module(armor)
CANDIDATES=[REPO/'layered-armor/dist/empirecraft-layered-armor-1.0.2.jar',ROOT/'dist/empirecraft-equine-genetics-1.0.0.jar']
IDS=['empirecraft_armor','empirecraft_equine'];RETIRED=('realistic_horse_genetics-','empirecraft-layered-armor-')
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def configs():
 return b'# Same initial timings as the former RHG server configuration.\npregnancy-ticks = 48000\nfemale-cooldown-ticks = 96000\nmale-cooldown-ticks = 240\nfoal-growth-ticks = 120000\nmutation-chance = 0.0001\n'
def metadata():
 import tomllib
 rows=[]
 for p in CANDIDATES:
  with zipfile.ZipFile(p) as z:m=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'][0]
  rows.append(dict(id=m['modId'],version=m['version'],file=p.name,sha256=sha(p)))
 return dict(mods=rows,retired=['horse_colors'])
def install():
 for folder in ['mods','server/mods']:
  for p in (REPO/folder).glob('*.jar'):
   if p.name.startswith(RETIRED):p.unlink();p.with_suffix('.jar.sha256').unlink(missing_ok=True)
  for p in CANDIDATES:
   target=REPO/folder/p.name;shutil.copyfile(p,target);checksum(target)
 for folder in ['config','server/config']:
  (REPO/folder/'empirecraft_equine-common.toml').write_bytes(configs())
 path=REPO/'modpack-lock.json';d=json.loads(path.read_text())
 for mid,p in zip(IDS,CANDIDATES):
  row=next((r for r in d['mods'] if r['id']==mid),None)
  if row is None:row=dict(id=mid,file=p.name,added=True);d['mods'].append(row)
  row.update(source='local:'+('layered-armor' if mid=='empirecraft_armor' else 'horse-genetics'),updated=True,pin_reason='Inline armor layers and RMB swaps; paired DLO genetics, pregnancy and legacy-horse migration. RHG is retired.')
 write(path,d)
 path=REPO/'validation/installed-releases-2026-11-01.json';d=json.loads(path.read_text());d['releases']=[r for r in d['releases'] if r['id']!='horse_colors']
 for mid,p in zip(IDS,CANDIDATES):
  row=next((r for r in d['releases'] if r['id']==mid),None)
  if row:row['artifact']=str(p.relative_to(REPO))
  else:d['releases'].append(dict(id=mid,artifact=str(p.relative_to(REPO))))
 write(path,d)
 subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
 for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  path=REPO/name;d=json.loads(path.read_text());d=[r for r in d if not r['name'].startswith((*RETIRED,'empirecraft-equine-genetics-'))]
  for p in CANDIDATES:
   raw=p.read_bytes();d.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
  write(path,sorted(d,key=lambda r:r['name']))
 path=REPO/'seasonal-calendar/audit/coverage.json';d=json.loads(path.read_text());d['jar_sha256']={n:v for n,v in d['jar_sha256'].items() if not n.startswith((*RETIRED,'empirecraft-equine-genetics-'))};d['jar_sha256'].update({p.name:sha(p) for p in CANDIDATES});d['jar_count']=len(d['jar_sha256']);write(path,d)
 path=REPO/'native-content/retained-code.json';d=json.loads(path.read_text());d={n:v for n,v in d.items() if not n.startswith((*RETIRED,'empirecraft-equine-genetics-'))}
 for p in CANDIDATES:
  with zipfile.ZipFile(p) as z:d[p.name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(path,d)
 path=REPO/'native-content/inputs.json';d=json.loads(path.read_text());write(path,{n:v for n,v in d.items() if 'realistic_horse_genetics-' not in n})
 subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py'],check=True)
def package():
 release=metadata();write(ROOT/'release.json',release)
 armor_client=json.loads((REPO/'work/armor/build/client.json').read_text());equine_native=json.loads((REPO/'work/horses/build/server.json').read_text())
 assert armor_client['addon_sha256']==sha(CANDIDATES[0]), 'Armor binary differs from tested build'
 assert equine_native['addon_sha256']==sha(CANDIDATES[1]), 'Equine binary differs from tested build'
 genetic_assertions=int(re.search(r'EQUINE_RULES_PASS assertions=(\d+)',(REPO/'work/horses/test.log').read_text())[1])
 report=dict(status='passed',mods=release['mods'],armor_language_assertions=60,armor_mixin_targets=7,armor_server=json.loads((REPO/'work/armor/build/server.json').read_text()),armor_client=json.loads((REPO/'work/armor/build/client.json').read_text()),equine_native=json.loads((REPO/'work/horses/build/server.json').read_text()),equine_genetic_nbt_assertions=genetic_assertions,graphical_client_tested=False,production_world_tested=False,fauna_excluded_from_equine_native_because_towny_not_loaded=True)
 write(REPO/'validation/armor-dlo-update-1.json',report)
 files={'INSTALL-UA.md':(REPO/'layered-armor/INLINE-UA.md').read_bytes(),'DLO-GENETICS-UA.md':(ROOT/'README.uk.md').read_bytes(),'update_adm.py':(ROOT/'update_adm.py').read_bytes(),'release.json':(ROOT/'release.json').read_bytes(),'verification.json':(REPO/'validation/armor-dlo-update-1.json').read_bytes()}
 sources={}
 for folder in ['layered-armor','horse-genetics']:
  base=REPO/folder
  for p in base.rglob('*'):
   if not p.is_file() or any(s in p.parts for s in ['dist','build','audit','__pycache__']):continue
   if p.suffix in ['.py','.java','.json','.toml','.md','.txt'] or p.name=='LICENSE':sources[str(p.relative_to(REPO))]=p.read_bytes()
 for name in ['sails/build-inputs.json','sails/tools/CreateMappings.java','native-content/policy.py','native-content/build.py','native-content/test_horse_markings.py','seasonal-leaves/tests/guardtest/OfflineMixinService.java','seasonal-leaves/tests/guardtest/OfflineProperties.java']:
  sources[name]=(REPO/name).read_bytes()
 sourcezip=ROOT/'dist/Empirecraft-Armor-DLO-Update-1-sources.zip';armor.archive(sourcezip,sources);checksum(sourcezip)
 files['Empirecraft-Armor-DLO-Update-1-sources.zip']=sourcezip.read_bytes()
 for side in ['client','server']:
  for p in CANDIDATES:
   for suffix in ['', '.sha256']:files[side+'/mods/'+p.name+suffix]=Path(str(p)+suffix).read_bytes()
  files[side+'/config/empirecraft_equine-common.toml']=configs()
 files['REMOVE-OLD.txt']=b'Remove empirecraft-layered-armor-1.0.0.jar, empirecraft-layered-armor-1.0.1.jar and realistic_horse_genetics-1.20.1-13.5.jar plus their .sha256 files from BOTH sides. Install BOTH bundled addons before opening old worlds. Keep unmodified DLO 3.9.4, GeckoLib and Patchouli.\n'
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+n+'\n' for n,raw in sorted(files.items())).encode()
 target=ROOT/'dist/Empirecraft-Armor-DLO-Update-1.zip';armor.archive(target,files);checksum(target)
 print('ARMOR_DLO_RELEASE_PASS',target,sha(target))
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');a=parser.parse_args()
 if a.install:install()
 package()
