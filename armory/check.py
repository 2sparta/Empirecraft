"""Fast data checks and executable installer regressions, without Minecraft stubs."""
import copy,hashlib,io,json,shutil,tempfile,zipfile
from pathlib import Path
from PIL import Image
from build import ROOT,REPO,NAME,sha
import resources,update_installation as installer,install_core as core
def main():
 files=resources.generate();recipes={n:json.loads(raw) for n,raw in files.items() if '/recipes/parts/' in n}
 assert len(recipes)==140
 for name,r in recipes.items():
  assert len({len(row) for row in r['pattern']})==1,name
  assert 1<=len(r['pattern'])<=3 and 1<=len(r['pattern'][0])<=3
  assert sum(row.count('T') for row in r['pattern'])==1
  assert r['hammering'] in (3,4) and r['blueprint'][0]=='armor'
  item=r['result']['item'].split(':')[1];model=json.loads(files['assets/empirecraft_armory/models/item/'+item+'.json']);assert model['textures']['layer0']=='empirecraft_armory:item/'+item
  Image.open(io.BytesIO(files['assets/empirecraft_armory/textures/item/'+item+'.png'])).verify()
 import sys
 sys.path.insert(0,str(REPO/'handbook'));import layout
 for name,raw in files.items():
  if '/entries/' in name:
   entry=json.loads(raw)
   assert all(layout.lines(page['text'])<=layout.MAX_LINES for page in entry['pages']),name
 print('ARMORY_DATA_PASS parts=140 rectangular_patterns=true textures=true guide_pages_fit=true')
 bundle=ROOT/'build/installer-fixture';shutil.rmtree(bundle,ignore_errors=True);bundle.mkdir(parents=True)
 raw=(ROOT/'dist'/NAME).read_bytes();release=dict(id=core.ID,file=NAME,version='1.0.0',sha256=hashlib.sha256(raw).hexdigest(),files={})
 for side in ['client','server']:
  for name,data in {'mods/'+NAME:raw,'config/overgeared-common.toml':(REPO/'config/overgeared-common.toml').read_bytes()}.items():
   p=bundle/side/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data);release['files'].setdefault(side,{})[name]=hashlib.sha256(data).hexdigest()
 results={}
 with tempfile.TemporaryDirectory(prefix='armory-installer-') as tmp:
  for side in ['client','server']:
   root=Path(tmp)/side;(root/'mods').mkdir(parents=True);(root/'config').mkdir()
   for id in ['overgeared','empirecraft_forging','tfc','empirecraft_armory']:
    path=root/'mods'/((id if id!=core.ID else 'empirecraft-armory')+'-0.9.jar')
    with zipfile.ZipFile(path,'w') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0"\n[[mods]]\nmodId="'+id+'"\nversion="0.9"\n')
   settings=(REPO/'config/overgeared-common.toml').read_text().replace('maxPotionTipping = 8','maxPotionTipping = 37')+'\n# retained local note\n';(root/'config/overgeared-common.toml').write_text(settings)
   (root/'world').mkdir();(root/'world/player-data-sentinel').write_text('KEEP')
   if side=='server':
    adm=root/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True);text=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text();adm.write_text(text);original,report=core.parse_adm(text)
   changes=core.apply(installer.plan(root,side,bundle,release));assert (root/'mods'/NAME).read_bytes()==raw
   assert not (root/'mods/empirecraft-armory-0.9.jar').exists();assert (root/'.empirecraft-backups/armory-1/mods/empirecraft-armory-0.9.jar').exists()
   assert 'maxPotionTipping = 37' in (root/'config/overgeared-common.toml').read_text() and '# retained local note' in (root/'config/overgeared-common.toml').read_text()
   assert (root/'world/player-data-sentinel').read_text()=='KEEP';assert core.apply(installer.plan(root,side,bundle,release))==[]
   if side=='server':
    updated,_=core.parse_adm(adm.read_text());assert [v for v in original['modIds'] if v['id']!=core.ID]==[v for v in updated['modIds'] if v['id']!=core.ID];assert core.integrity(updated)==updated['integrityHash']
   results[side]=dict(installed=True,backup=True,idempotent=True,local_settings_preserved=True,world_untouched=True)
  broken=copy.deepcopy(release);broken['files']['client']['mods/'+NAME]='0'*64
  try:installer.plan(Path(tmp)/'client','client',bundle,broken)
  except ValueError:pass
  else:raise AssertionError('Corrupt bundle accepted')
 report=dict(status='passed',sides=results,corrupt_bundle_rejected=True,unrelated_adm_rows_preserved=True);(ROOT/'audit/installer.json').write_text(json.dumps(report,indent=2)+'\n');print('ARMORY_INSTALLER_PASS',json.dumps(report))
if __name__=='__main__':main()
