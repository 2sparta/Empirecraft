#!/usr/bin/env python3
import hashlib,json,subprocess,sys,tomllib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;sys.path.insert(0,str(ROOT))
from update_installation import parse_adm,integrity

def baseline(path):return subprocess.check_output(['git','show','HEAD:'+path],cwd=REPO)
def before(path):return json.loads(baseline(path))
def main():
 release=json.loads((ROOT/'release.json').read_text());name=release['file'];ID=release['id'];artifact=ROOT/'dist'/name;raw=artifact.read_bytes();digest=hashlib.sha256(raw).hexdigest();assert digest==release['sha256']
 for folder in ['mods','server/mods']:
  assert len(list((REPO/folder).glob('empirecraft-casting-*.jar')))==1 and (REPO/folder/name).read_bytes()==raw
 with zipfile.ZipFile(artifact) as z:
  mod=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'][0];assert mod['modId']==ID and mod['version']==release['version'];assert not any(n.startswith(('net/minecraft/','org/bukkit/')) for n in z.namelist())
 for path,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
  old=before(path);new=json.loads((REPO/path).read_text());assert [r for r in old[key] if r['id']!=ID]==[r for r in new[key] if r['id']!=ID];rows=[r for r in new[key] if r['id']==ID];assert len(rows)==1 and rows[0]['file']==name and rows[0]['sha256']==digest
 for path in ['forging-balance/installed-client-mods.json','seasonal-calendar/installed-mods.json']:
  old=before(path);new=json.loads((REPO/path).read_text());assert [r for r in old if not r['name'].startswith('empirecraft-casting-')]==[r for r in new if not r['name'].startswith('empirecraft-casting-')];row=next(r for r in new if r['name']==name);assert row['size']==len(raw) and row['sha']==hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
 for path,key in [('native-content/retained-code.json',None),('seasonal-calendar/audit/coverage.json','jar_sha256')]:
  old=before(path);new=json.loads((REPO/path).read_text());old=old[key] if key else old;new=new[key] if key else new;assert {k:v for k,v in old.items() if not k.startswith('empirecraft-casting-')}=={k:v for k,v in new.items() if not k.startswith('empirecraft-casting-')}
 path='server/config/admanticheat/idfile/admanticheat_id_list.json';old,was_report=parse_adm(baseline(path).decode('utf-8-sig'));new,is_report=parse_adm((REPO/path).read_bytes().decode('utf-8-sig'));assert was_report==is_report and integrity(new)==new['integrityHash'];assert old['resourcePackIds']==new['resourcePackIds'];assert [r for r in old['modIds'] if r['id']!=ID]==[r for r in new['modIds'] if r['id']!=ID];row=next(r for r in new['modIds'] if r['id']==ID);assert row['sha256']==digest and row['version']==release['version']
 result=dict(status='passed',sha256=digest,both_sides_identical=True,only_own_mod_rows_changed=True,adm_format_and_other_rows_preserved=True,production_world_opened=False);(ROOT/'audit/installed.json').write_text(json.dumps(result,indent=2)+'\n');print('CASTING_INSTALLED_PASS')
if __name__=='__main__':main()
