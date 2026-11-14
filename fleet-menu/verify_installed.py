#!/usr/bin/env python3
"""Verify the targeted update against the hosting snapshot without opening world data."""
import hashlib,json,subprocess,sys,tomllib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;sys.path.insert(0,str(ROOT))
from update_installation import parse_adm,integrity

def before(path):return subprocess.check_output(['git','show','HEAD:'+path],cwd=REPO)
def main():
 release=json.loads((ROOT/'release.json').read_text());book=release['file'];paths=[REPO/'mods'/book,REPO/'server/mods'/book,ROOT/'dist'/book]
 assert all(hashlib.sha256(p.read_bytes()).hexdigest()==release['sha256'] for p in paths)
 for folder in ['mods','server/mods']:
  assert len(list((REPO/folder).glob('empirecraft-towny-journeymap-*.jar')))==1
 with zipfile.ZipFile(paths[0]) as z:
  assert all(not n.startswith(('net/minecraft/','org/bukkit/')) for n in z.namelist());mod=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'][0];assert mod['modId']==release['id'] and mod['version']==release['version']
 for path in ['modpack-lock.json','validation/installed-releases-2026-11-01.json']:
  old=json.loads(before(path));new=json.loads((REPO/path).read_text());key='mods' if path.startswith('modpack') else 'releases'
  assert [r for r in old[key] if r['id']!=release['id']]==[r for r in new[key] if r['id']!=release['id']]
  row=next(r for r in new[key] if r['id']==release['id']);assert row['file']==book and row['sha256']==release['sha256'] and row['version']==release['version']
 path='server/config/admanticheat/idfile/admanticheat_id_list.json';old,was_report=parse_adm(before(path).decode('utf-8-sig'));new,is_report=parse_adm((REPO/path).read_bytes().decode('utf-8-sig'))
 assert was_report==is_report and integrity(new)==new['integrityHash'];assert old['resourcePackIds']==new['resourcePackIds']
 assert [r for r in old['modIds'] if r['id']!=release['id']]==[r for r in new['modIds'] if r['id']!=release['id']]
 for side,folder in [('client',REPO),('server',REPO/'server')]:
  for relative,digest in release['files'][side].items():assert hashlib.sha256((folder/relative).read_bytes()).hexdigest()==digest
 result=dict(status='passed',bridge_sha256=release['sha256'],both_sides_identical=True,unrelated_mod_rows_preserved=True,adm_format_preserved=True,unrelated_adm_and_resource_packs_preserved=True,production_world_opened=False)
 (ROOT/'audit/installed.json').write_text(json.dumps(result,indent=2)+'\n');print('FLEET_MENU_INSTALLED_PASS')
if __name__=='__main__':main()
