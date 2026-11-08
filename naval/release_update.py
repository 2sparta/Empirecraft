#!/usr/bin/env python3
"""Install only the three fleet components, update their metadata and package their exact bytes."""
import argparse,copy,hashlib,importlib.util,json,re,shutil,time,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;DIST=ROOT/'dist/fleet-fixes-1.0'
ITEMS=[('empirecraft_townymap','1.11.0','empirecraft-towny-journeymap-1.11.0.jar','empirecraft-towny-journeymap-'),('empirecraft_engagement','1.1.1','empirecraft-engagement-1.1.1.jar','empirecraft-engagement-')]
PLUGIN='empirecraft-feudal-1.19.0.jar';REPORT=REPO/'validation/fleet-fixes-1.0.json';ADM=Path('server/config/admanticheat/idfile/admanticheat_id_list.json')
def sha(raw):return hashlib.sha256(raw).hexdigest()
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def helper():
 spec=importlib.util.spec_from_file_location('fleet_adm_integrity',REPO/'ship-cannons/release.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
def details(mid,version,name):
 raw=(DIST/name).read_bytes();return dict(id=mid,version=version,file=name,sha256=sha(raw),sha512=hashlib.sha512(raw).hexdigest(),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),size=len(raw))
def install():
 for side,source,prefix in [('server/plugins',DIST/PLUGIN,'empirecraft-feudal-'),*[(side,DIST/name,prefix) for _,_,name,prefix in ITEMS for side in ['mods','server/mods']]]:
  folder=REPO/side;folder.mkdir(parents=True,exist_ok=True)
  for old in folder.glob(prefix+'*.jar'):
   if old.name!=source.name:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
  target=folder/source.name
  if target.is_symlink():target.unlink()
  shutil.copyfile(source,target);target.with_suffix('.jar.sha256').write_text(sha(target.read_bytes())+'  '+target.name+'\n')
 lockpath=REPO/'modpack-lock.json';lock=json.loads(lockpath.read_text());updated={}
 for mid,version,name,_ in ITEMS:
  row=next(r for r in lock['mods'] if r['id']==mid);row.update(details(mid,version,name));row['updated']=True
  row['pin_reason']=('Stable personal JourneyMap waypoint GUIDs, dated popup, verified empty-place expiry and searchable ship/player selectors; Feudal 1.19.0. Retains all other 1.10.0 client classes.' if mid=='empirecraft_townymap' else 'Two valid peaceful ensigns reject mutual ship damage in every water type without PvP tagging. Other 1.1.0 combat bytecode is preserved; paired with Sails 18 and Feudal 1.19.0.')
  updated[mid]=copy.deepcopy(row)
 write(lockpath,lock)
 path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
 for i,row in enumerate(manifest['releases']):
  if row['id'] in updated:manifest['releases'][i]=dict(updated[row['id']],artifact='naval/dist/fleet-fixes-1.0/'+updated[row['id']]['file'])
 write(path,manifest)
 for relative,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
  path=REPO/relative;rows=json.loads(path.read_text())
  for _,_,name,prefix in ITEMS:
   record=next(row for row in updated.values() if row['file']==name);rows=[row for row in rows if not row['name'].startswith(prefix)];rows.append(dict(name=name,path=side+'/'+name,size=record['size'],sha=record['git_sha']))
  write(path,sorted(rows,key=lambda r:r['name']))
 path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text())
 for _,_,name,prefix in ITEMS:
  coverage['jar_sha256']={k:v for k,v in coverage['jar_sha256'].items() if not k.startswith(prefix)};coverage['jar_sha256'][name]=sha((DIST/name).read_bytes())
 write(path,coverage)
 module=helper();adm=json.loads((REPO/ADM).read_text());assert module.integrity(adm)==adm['integrityHash']
 for mid,version,name,_ in ITEMS:next(row for row in adm['modIds'] if row['id']==mid).update(version=version,sha256=sha((DIST/name).read_bytes()))
 adm['lastUpdated']=int(time.time()*1000);adm['integrityHash']=module.integrity(adm);write(REPO/ADM,adm)
def compat(previous):
 module=helper();adm=json.loads(previous.read_text());assert module.integrity(adm)==adm['integrityHash'];army=next(row for row in adm['modIds'] if row['id']=='townyrecruits');assert army['version']=='2.0.0' and army['sha256']=='3c2f7f3651de06156f61088c6fe4109a98e847b8da88547397d0ede068c0b276'
 for mid,version,name,_ in ITEMS:next(row for row in adm['modIds'] if row['id']==mid).update(version=version,sha256=sha((DIST/name).read_bytes()))
 adm['lastUpdated']=json.loads((REPO/ADM).read_text())['lastUpdated'];adm['integrityHash']=module.integrity(adm);write(ROOT/'compat/TownyRecruits-2.0-admanticheat_id_list.json',adm)
def record():
 logs=ROOT/'build/update';native=json.loads((ROOT/'build/native-observation/result.json').read_text());artifacts=json.loads((DIST/'build-validation.json').read_text())['artifacts']
 assert native['bridge_sha256']==artifacts[ITEMS[0][2]],'Native test used another bridge JAR'
 policy=int(re.search(r'COMBAT_POLICY_PASS checks=(\d+)',(logs/'policy-tests.log').read_text())[1]);client=int(re.search(r'FLEET_CLIENT_PASS checks=(\d+)',(logs/'client-tests.log').read_text())[1]);fleet=int(re.search(r'FLEET_SERVICE_PASS cases=(\d+)',(logs/'FleetTests.log').read_text())[1])
 assert policy>=359601 and client>=28 and fleet>=18 and any('FLEET_OBSERVATION_NATIVE_PASS checks=12' in line for line in native['results'])
 native['results']=[re.sub(r'\x1b\[[0-9;]*m','',line).strip() for line in native['results']]
 retained={}
 for base,target,changed in [(REPO/'bridge/native-inputs/empirecraft-towny-journeymap-1.10.0.jar',DIST/ITEMS[0][2],lambda n:n in {'ua/empirecraft/townymap/client/FleetScreen.class','ua/empirecraft/townymap/client/FleetMapPlugin.class','ua/empirecraft/townymap/FleetNetwork.class'}),
                             (REPO/'engagement/native-inputs/empirecraft-engagement-1.1.0.jar',DIST/ITEMS[1][2],lambda n:n.startswith('ua/empirecraft/engagement/CombatPolicy'))]:
  with zipfile.ZipFile(base) as old,zipfile.ZipFile(target) as new:
   names=[n for n in old.namelist() if n.endswith('.class') and not changed(n)];assert all(old.read(n)==new.read(n) for n in names);retained[target.name]=len(names)
 write(REPORT,dict(status='passed_scoped_checks',artifacts=artifacts,backend_suites=8,fleet_scenarios=fleet,policy_assertions=policy,client_model_and_bytecode_checks=client,unchanged_native_classes=retained,native=native,graphical_client_tested=False,production_world_tested=False,full_production_modpack_tested=False,large_battle_performance_measured=False))
 audit=ROOT/'audit/fleet-fixes-1.0';audit.mkdir(parents=True,exist_ok=True)
 for name in ['FleetTests.log','ShipRegistrationTests.log','client-tests.log','bridge-overlay.log','policy-tests.log']:shutil.copyfile(logs/name,audit/name)
 write(audit/'native-observation.json',native)
def verify():
 module=helper();adm=json.loads((REPO/ADM).read_text());assert module.integrity(adm)==adm['integrityHash'];lock=json.loads((REPO/'modpack-lock.json').read_text())
 report=json.loads(REPORT.read_text());assert report['native']['bridge_sha256']==sha((DIST/ITEMS[0][2]).read_bytes())
 for mid,version,name,prefix in ITEMS:
  row=next(r for r in lock['mods'] if r['id']==mid);expected=details(mid,version,name);assert all(row[k]==v for k,v in expected.items())
  assert next(r for r in adm['modIds'] if r['id']==mid)==dict(id=mid,version=version,sha256=expected['sha256'],filePresent=True)
  for side in ['mods','server/mods']:
   installed=REPO/side/name;assert installed.read_bytes()==(DIST/name).read_bytes();assert len(list((REPO/side).glob(prefix+'*.jar')))==1
  assert report['artifacts'][name]==expected['sha256']
 assert (REPO/'server/plugins'/PLUGIN).read_bytes()==(DIST/PLUGIN).read_bytes();assert len(list((REPO/'server/plugins').glob('empirecraft-feudal-*.jar')))==1
 for name,expected in report['artifacts'].items():
  path=DIST/name;assert sha(path.read_bytes())==expected;assert path.with_suffix('.jar.sha256').read_text()==expected+'  '+name+'\n'
  with zipfile.ZipFile(path) as z:assert z.testzip() is None
 optional=ROOT/'compat/TownyRecruits-2.0-admanticheat_id_list.json'
 if optional.exists():
  value=json.loads(optional.read_text());assert module.integrity(value)==value['integrityHash'];assert next(r for r in value['modIds'] if r['id']=='townyrecruits')['version']=='2.0.0'
def archive(path,files,check):
 import io
 output=io.BytesIO()
 with zipfile.ZipFile(output,'w') as z:
  for name,raw in sorted(files.items()):
   entry=zipfile.ZipInfo(name,(2026,11,8,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,raw)
 raw=output.getvalue();checksum=sha(raw)+'  '+path.name+'\n'
 if check:assert path.read_bytes()==raw and path.with_suffix('.zip.sha256').read_text()==checksum,path
 else:path.write_bytes(raw);path.with_suffix('.zip.sha256').write_text(checksum)
def package(check):
 base={'INSTALL-UA.md':(ROOT/'FLEET-FIXES.md').read_bytes(),'validation.json':REPORT.read_bytes(),str(ADM):(REPO/ADM).read_bytes()}
 for mid,version,name,_ in ITEMS:
  for side in ['client','server']:
   base[side+'/mods/'+name]=(DIST/name).read_bytes();base[side+'/mods/'+name+'.sha256']=(DIST/(name+'.sha256')).read_bytes()
 base['server/plugins/'+PLUGIN]=(DIST/PLUGIN).read_bytes();base['server/plugins/'+PLUGIN+'.sha256']=(DIST/(PLUGIN+'.sha256')).read_bytes()
 for special in [False,True]:
  files=dict(base)
  if special:
   optional=ROOT/'compat/TownyRecruits-2.0-admanticheat_id_list.json'
   if not optional.exists():continue
   files[str(ADM)]=optional.read_bytes()
  files['SHA256SUMS']=''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(files.items())).encode();archive(DIST/('Empirecraft-Fleet-Fixes-1.0'+('-TownyRecruits-2.0' if special else '')+'.zip'),files,check)
 sources={}
 for folder in ['feudal/src','bridge/src','bridge/gradle','engagement/src','naval/tests','naval/compat','naval/audit','bridge/native-inputs','engagement/native-inputs']:
  for file in (REPO/folder).rglob('*'):
   if file.is_file():sources[file.relative_to(REPO).as_posix()]=file.read_bytes()
 for path in ['naval/build_update.py','naval/release_update.py','naval/smoke_observation.py','naval/FleetClientOverlay.java','naval/FLEET-FIXES.md','feudal/build.py','feudal/release.py','bridge/build.gradle','bridge/overlay.py','bridge/settings.gradle','bridge/gradle.properties','bridge/gradlew','bridge/gradlew.bat','sails/tools/OfflineMappings.java','ship-cannons/release.py','validation/fleet-fixes-1.0.json']:
  sources[path]=(REPO/path).read_bytes()
 for name in sources:
  if name.endswith('.bat'):sources[name]=sources[name].replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')
 archive(DIST/'Empirecraft-Fleet-Fixes-1.0-source.zip',sources,check)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--record',action='store_true');p.add_argument('--check',action='store_true');p.add_argument('--townyrecruits-2-adm',type=Path);a=p.parse_args()
 if a.apply:install()
 if a.townyrecruits_2_adm:compat(a.townyrecruits_2_adm)
 if a.record:record()
 verify();package(a.check);print('FLEET_UPDATE_RELEASE_PASS')
if __name__=='__main__':main()
