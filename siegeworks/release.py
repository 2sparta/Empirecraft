"""Build the scoped paired update and synchronize installed inventories."""
import hashlib,json,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME,archive,sha
import update_installation as u

def write(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def main():
 audit=json.loads((ROOT/'audit/runtime.json').read_text());assert audit['status']=='passed'
 current=ROOT/'dist'/NAME
 if audit['mod_sha256']!=sha(current):
  tested=ROOT/'build/native-tested.jar';assert sha(tested)==audit['mod_sha256']
  with zipfile.ZipFile(tested) as before,zipfile.ZipFile(current) as after:
   assert set(before.namelist())==set(after.namelist())
   changes=[n for n in before.namelist() if before.read(n)!=after.read(n)]
   assert changes and set(changes)<=set('assets/empirecraft_siegeworks/lang/'+lang+'.json' for lang in ['uk_ua','en_us']),changes
  write(ROOT/'audit/native-equivalence.json',dict(status='passed',tested_sha256=sha(tested),release_sha256=sha(current),changed_entries=changes,all_bytecode_and_server_data_identical=True,tested_jar_source='https://github.com/2sparta/EmpireCraftLauncher/blob/3159494542baa44b1f5c01a48c17757fcc1734fb/launcher/audit/siegeworks-test/fixture.zip'))
 client=json.loads((ROOT/'audit/client.json').read_text());assert client['status']=='passed' and client['fixture']['mod_sha256']==sha(current)

 rows=[]
 for id,prefix,version,sides in [('siegeworks','siegeworks-','0.1.0-beta.11',['client','server']),('axiomata','axiomata-','0.1.0-beta.10',['client','server']),('recruits','recruits-','1.15.2',['client','server']),('townyrecruits','TownyRecruits','2.0.2',['client','server']),('empirecraft_claim_guard','empirecraft-claim-guard-','1.0.2',['server'])]:
  p=next((REPO/('server/mods' if sides==['server'] else 'mods')).glob(prefix+'*.jar'));raw=p.read_bytes()
  row=dict(id=id,file=p.name,version=version,sides=sides,added=id in ['siegeworks','axiomata'],updated=True,source='local:siegeworks' if id not in ['axiomata','recruits'] else 'user-upload:official-jar',sha256=sha(p),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),pin_reason='Paired Siegeworks integration: steel construction, Towny block protection, Recruits 1.15.2 compatibility.')
  rows.append(row)
  for side in sides:
   f=REPO/('server/mods' if side=='server' else 'mods')/p.name;assert sha(f)==row['sha256'];f.with_suffix('.jar.sha256').write_text(row['sha256']+'  '+p.name+'\n')
 ids={r['id'] for r in rows}|{'siegemachines'}
 lock=json.loads((REPO/'modpack-lock.json').read_text());old=[r for r in lock['mods'] if r['id'] in ids]
 lock['mods']=sorted([r for r in lock['mods'] if r['id'] not in ids]+rows,key=lambda r:r['id'])
 lock.update(client_jar_count=len(list((REPO/'mods').glob('*.jar'))),server_jar_count=len(list((REPO/'server/mods').glob('*.jar'))),updated_mod_count=sum(bool(r.get('updated')) for r in lock['mods']))
 write(REPO/'modpack-lock.json',lock)
 p=REPO/'validation/installed-releases-2026-11-01.json';d=json.loads(p.read_text());d['releases']=[r for r in d['releases'] if r['id'] not in ids]+[dict(r,artifact=('server/' if r['sides']==['server'] else '')+'mods/'+r['file']) for r in rows];d.update(client_jar_count=lock['client_jar_count'],server_jar_count=lock['server_jar_count'])
 retired=[('server/' if side=='server' else '')+'mods/'+r['file'] for r in old for side in r['sides'] if not any(n['file']==r['file'] for n in rows)]
 d['retired']=list(dict.fromkeys(d.get('retired',[])+retired));write(p,d)
 retired_names={r['file'] for r in old}
 for side,name in [('client','forging-balance/installed-client-mods.json'),('server','seasonal-calendar/installed-mods.json')]:
  p=REPO/name;d=[r for r in json.loads(p.read_text()) if r['name'] not in retired_names];d.extend(dict(name=r['file'],path=('server/' if side=='server' else '')+'mods/'+r['file'],size=r['size'],sha=r['git_sha']) for r in rows if side in r['sides']);write(p,sorted(d,key=lambda r:r['name']))
 p=REPO/'seasonal-calendar/audit/coverage.json';d=json.loads(p.read_text());d['jar_sha256']={k:v for k,v in d['jar_sha256'].items() if k not in retired_names};d['jar_sha256'].update({r['file']:r['sha256'] for r in rows if 'server' in r['sides']});d['jar_count']=len(d['jar_sha256']);write(p,d)
 p=REPO/'native-content/retained-code.json';d={k:v for k,v in json.loads(p.read_text()).items() if k not in retired_names}
 for r in rows:
  with zipfile.ZipFile(REPO/('server/mods' if r['sides']==['server'] else 'mods')/r['file']) as z:d[r['file']]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,d)
 primary=rows[0];release=dict(primary,additional_mods=rows[1:],retire_ids=['siegemachines'],retire_files={'server':['config/siege-machines-specs.toml']},files={});files={}
 extra=['kubejs/'+n for n in json.loads((ROOT/'audit/extra-files.json').read_text())]+['kubejs/data/empirecraft_mass/tags/blocks/timber_drive.json']
 for side in ['client','server']:
  base=REPO/('server' if side=='server' else '');payload={n:(base/n).read_bytes() for n in extra+(['kubejs/server_scripts/5_enchantments.js','config/empirecraft-calendar-coverage.json'] if side=='server' else [])}
  for r in rows:
   if side in r['sides']:
    for suffix in ['', '.sha256']:payload['mods/'+r['file']+suffix]=(base/'mods'/(r['file']+suffix)).read_bytes()
  release['files'][side]={n:hashlib.sha256(raw).hexdigest() for n,raw in payload.items()};files.update({side+'/'+n:raw for n,raw in payload.items()})
 adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=adm.read_text(encoding='utf-8-sig');before,report=u.parse_adm(text);after=u.adm_update(before,release)
 assert not any(r['id']=='empirecraft_claim_guard' for r in after['modIds']);assert [r for r in before['modIds'] if r['id'] not in ids]==[r for r in after['modIds'] if r['id'] not in ids]
 adm.write_text(u.render_adm(text,before,after,report));write(ROOT/'release.json',release);files['release.json']=(ROOT/'release.json').read_bytes()
 for n in ['update_installation.py','README.uk.md','LICENSE-upstream','LICENSE-ASSETS.md','LICENSE-TownyGate-MIT']:files[n]=(ROOT/n).read_bytes()
 for p in (ROOT/'audit').glob('*.json'):
  if p.name!='installer.json':files['verification/'+p.name]=p.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(v).hexdigest()+'  '+n+'\n' for n,v in sorted(files.items())).encode();p=ROOT/'dist/Empirecraft-Siegeworks-1-install.zip';archive(p,files);p.with_suffix('.zip.sha256').write_text(sha(p)+'  '+p.name+'\n');print('SIEGEWORKS_RELEASE_PASS',sha(p),p.stat().st_size)
if __name__=='__main__':main()
