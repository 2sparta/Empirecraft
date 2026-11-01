#!/usr/bin/env python3
"""Package current Sails and update only its ADM/lock row, preserving the current modpack."""
import argparse,hashlib,importlib.util,io,json,tomllib,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO;NAME='vs_sails-1.20.1-'+build.VERSION+'-forge.jar'
def digest(raw):return hashlib.sha256(raw).hexdigest()
def helper():
 spec=importlib.util.spec_from_file_location('ship_release',REPO/'ship-cannons/release.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--check',action='store_true');a=p.parse_args()
 raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes()
 sha=digest(raw)
 with zipfile.ZipFile(REPO/'mods'/NAME) as z:
  assert z.testzip() is None
  assert any(m['modId']=='vs_sails' and m['version']==build.VERSION for m in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'])
  assert all(n in z.namelist() for n in ['com/quintonc/vs_sails/ship/SailDrive.class','com/quintonc/vs_sails/ship/SailExposure.class','com/quintonc/vs_sails/ship/SailResponse.class','com/quintonc/vs_sails/compat/RiggingEvents.class','com/quintonc/vs_sails/wind/SmoothWind.class'])
  assert not any('SmokeMod' in n or n.startswith(('net/minecraft/','org/valkyrienskies/','com/copycatsplus/')) for n in z.namelist())
 row=dict(id='vs_sails',version=build.VERSION,file=NAME,sides=['client','server'],sha256=sha,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw))
 manifest=ROOT/'smooth-release.json';m=helper()
 adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=adm.read_text();original,report=m.parse_adm(text)
 updated=m.prepare_adm(original,[row],sort_ids=not report)
 lock=REPO/'modpack-lock.json';locked=json.loads(lock.read_text());planned=json.loads(lock.read_text())
 match,=[x for x in planned['mods'] if x['id']=='vs_sails'];match.update(row,updated=True,source='local:native-content',pin_reason='Sails 13 blocks powered headwind sailing, bounds drag assistance by wind allowance and adds water-only stationary roll/pitch trim; prior hull and Towny protections retained.',git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
 planned['updated_mod_count']=sum(bool(x.get('updated')) for x in planned['mods'])
 assert original['resourcePackIds']==updated['resourcePackIds']
 assert [x for x in original['modIds'] if x['id']!='vs_sails']==[x for x in updated['modIds'] if x['id']!='vs_sails']
 overlay=json.loads((ROOT/'build/native-overlay.json').read_text()) if a.apply else json.loads((REPO/'native-content/audit/build.json').read_text())['mods/'+NAME]
 if not a.apply:
  with zipfile.ZipFile(REPO/'mods'/NAME) as z:overlay=dict(overlay,input_sha256=json.loads(z.read('META-INF/empirecraft-native-content.json'))['input_sha256'])
 with zipfile.ZipFile(REPO/'mods'/NAME) as z:
  marker=json.loads(z.read('META-INF/empirecraft-native-content.json'))
  assert marker['input_sha256']==overlay['input_sha256'] and overlay['sha256']==sha
  assert all(path not in z.namelist() for path in overlay['deleted'])
 if a.apply:
  for filename in ['inputs.json','audit/build.json','retained-code.json']:
   path=REPO/'native-content'/filename;data=json.loads(path.read_text())
   old=next(item['file'] for item in locked['mods'] if item['id']=='vs_sails')
   if filename=='retained-code.json':
    data.pop(old,None)
    with zipfile.ZipFile(REPO/'mods'/NAME) as z:
     data[NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class') and n.startswith(('com/quintonc/vs_sails/ship/','com/quintonc/vs_sails/util/','com/quintonc/vs_sails/config/','com/quintonc/vs_sails/compat/','com/quintonc/vs_sails/wind/','com/quintonc/vs_sails/protection/','com/quintonc/vs_sails/mixin/compat/'))}
   else:
    for side in ['mods','server/mods']:
     data.pop(side+'/'+old,None)
     data[side+'/'+NAME]=overlay['input_sha256'] if filename=='inputs.json' else {k:v for k,v in overlay.items() if k!='input_sha256'}
   path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
  if original!=updated:adm.write_text(m.render_adm(text,original,updated,report))
  if planned!=locked:lock.write_text(json.dumps(planned,ensure_ascii=False,indent=2)+'\n')
  manifest.write_text(json.dumps(row,indent=2)+'\n')
  snapshot=ROOT/'native-inputs'/NAME;snapshot.parent.mkdir(exist_ok=True)
  original_input=(ROOT/'build/sails-untrimmed.jar').read_bytes()
  assert digest(original_input)==overlay['input_sha256']
  snapshot.write_bytes(original_input)
  git_blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
  for filename,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
   path=REPO/filename;rows=json.loads(path.read_text())
   for entry in rows:
    if entry['name'].startswith('vs_sails-'):
     entry.update(name=NAME,path=side+'/'+NAME,size=len(raw),sha=git_blob)
   path.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
  coverage=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(coverage.read_text())
  for old in list(data['jar_sha256']):
   if old.startswith('vs_sails-'):data['jar_sha256'].pop(old)
  data['jar_sha256'][NAME]=sha;coverage.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
  for folder in [REPO/'mods',REPO/'server/mods']:
   for old in folder.glob('vs_sails-*.jar'):
    if old.name!=NAME:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
 if a.check:
  for side in ['mods','server/mods']:
   path=side+'/'+NAME
   assert json.loads((REPO/'native-content/inputs.json').read_text())[path]==overlay['input_sha256']
   assert json.loads((REPO/'native-content/audit/build.json').read_text())[path]['sha256']==sha
  assert digest((ROOT/'native-inputs'/NAME).read_bytes())==overlay['input_sha256']
  assert json.loads(manifest.read_text())==row
  assert original==updated and planned==locked,'Sails installation records need --apply'
  git_blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
  for filename,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
   rows=json.loads((REPO/filename).read_text())
   entry,=[entry for entry in rows if entry['name'].startswith('vs_sails-')]
   assert (entry['name'],entry['path'],entry['size'],entry['sha'])==(NAME,side+'/'+NAME,len(raw),git_blob),('Stale Sails inventory',filename)
  coverage=json.loads((REPO/'seasonal-calendar/audit/coverage.json').read_text())['jar_sha256']
  assert {name:value for name,value in coverage.items() if name.startswith('vs_sails-')}=={NAME:sha},'Stale Sails calendar coverage'
 for folder in [REPO/'mods',REPO/'server/mods']:
  assert [f.name for f in folder.glob('vs_sails-*.jar')]==[NAME]
  assert (folder/(NAME+'.sha256')).read_text()==sha+'  '+NAME+'\n'
 output=ROOT/'dist/Empirecraft-Sails-13-Wind-Stability.zip';output.parent.mkdir(exist_ok=True)
 entries={'client/mods/'+NAME:raw,'server/mods/'+NAME:raw,'server/config/admanticheat/idfile/admanticheat_id_list.json':adm.read_bytes(),'server/config/vs_sails/ship-protection.json':(REPO/'server/config/vs_sails/ship-protection.json').read_bytes(),'INSTALL-UA.txt':(ROOT/'WIND-STABILITY.md').read_bytes()}
 buffer=io.BytesIO()
 with zipfile.ZipFile(buffer,'w') as z:
  for name,data in sorted(entries.items()):
   entry=zipfile.ZipInfo(name,(2026,10,29,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,data)
 package=buffer.getvalue();checksum=digest(package)+'  '+output.name+'\n'
 if a.check:
  assert output.read_bytes()==package,'Sails distribution differs from the installed production package'
  assert output.with_suffix('.zip.sha256').read_text()==checksum,'Stale Sails distribution checksum'
 else:
  output.write_bytes(package)
  output.with_suffix('.zip.sha256').write_text(checksum)
 print('SAILS_SMOOTH_RELEASE_PASS',sha)
if __name__=='__main__':main()
