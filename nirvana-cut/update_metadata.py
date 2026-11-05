#!/usr/bin/env python3
import hashlib,importlib.util,json,subprocess,sys,zipfile
from pathlib import Path
from build import REPO,NAME

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def run(*args):subprocess.run([sys.executable,*args],cwd=REPO,check=True)
def main():
 raw=(REPO/'mods'/NAME).read_bytes();digest=hashlib.sha256(raw).hexdigest()
 p=REPO/'native-content/audit/build.json';a=json.loads(p.read_text())
 for side in ['mods','server/mods']:a[side+'/'+NAME].update(sha256=digest,size=len(raw),nirvana_creepers_removed=True)
 write(p,a)
 p=REPO/'modpack-lock.json';a=json.loads(p.read_text());row=next(m for m in a['mods'] if m['id']=='nirvana');row['source']='local:nirvana-cut';row['pin_reason']='Native removal of Reefer, spawn egg, creation/conversion code and Creeper hook. Vanilla creepers, decorative head and other Nirvana features retained.';write(p,a)
 run('validation/reconcile_modpack.py','--apply')
 run('native-content/update_metadata.py')
 p=REPO/'block-mass/inputs.json';a=json.loads(p.read_text())
 for row in a:
  if row['jar']==NAME:row['sha256']=digest
 write(p,a)
 for locale in ['uk_ua','ru_ru']:
  p=REPO/'translations/pack/assets/nirvana/lang'/(locale+'.json')
  if p.exists():
   a=json.loads(p.read_text());a.pop('entity.nirvana.reefer',None);a.pop('item.nirvana.reefer_spawn_egg',None);write(p,a)
 run('translations/scan.py')
 run('translations/build.py')
 p=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';a=json.loads(p.read_text());pack=REPO/'translations/dist/Empirecraft-Translations-uk-ru-1.1.0.zip'
 for row in a['resourcePackIds']:
  if row['filename']==pack.name:row['sha256']=hashlib.sha256(pack.read_bytes()).hexdigest()
 spec=importlib.util.spec_from_file_location('adm',REPO/'ship-cannons/release.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper);a['integrityHash']=helper.integrity(a);write(p,a)
 print('PASS installed native Nirvana version, shared hashes, lock, ADM, inventories, material input and translation pack')
if __name__=='__main__':main()
