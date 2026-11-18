import importlib.util,json,tempfile,hashlib,shutil
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('installer',R/'install.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
with tempfile.TemporaryDirectory() as d:
 r=Path(d);c=r/'client';server=r/'server'
 for p in [c,server]:
  (p/'world').mkdir(parents=True);(p/'world/level.dat').write_bytes(b'WORLD_SENTINEL')
  (p/'mods').mkdir();shutil.copy2(R/'dist/knockedback-1.6.2-empirecraft.1.jar',p/'mods/knockedback-1.6.2-empirecraft.1.jar')
 adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
 data=dict(osName='test',cpuInfo='test',ramInfo='test',gpuInfo='test',anticheatVersion='test',lastUpdated=1,modIds=[dict(id='custom',version='1',sha256='0'*64,filePresent=True)],resourcePackIds=[])
 data['integrityHash']=m.integrity(data);adm.write_text(json.dumps(data))
 m.install(c,server,R/'build/bundle',True)
 first={str(p.relative_to(r)):p.read_bytes() for p in r.rglob('*') if p.is_file()}
 m.install(c,server,R/'build/bundle',True)
 second={str(p.relative_to(r)):p.read_bytes() for p in r.rglob('*') if p.is_file()}
 assert first==second,'Repeated installation changes bytes'
 for p in [c,server]:
  assert not (p/'mods/knockedback-1.6.2-empirecraft.1.jar').exists()
  assert list((p/'.empirecraft-backups/knockedback-2').rglob('knockedback-1.6.2-empirecraft.1.jar'))
 updated=json.loads(adm.read_text());assert updated['modIds'][0]==data['modIds'][0]
 assert len(updated['modIds'])==3 and updated['integrityHash']==m.integrity(updated)
 for p in [c,server]:assert (p/'world/level.dat').read_bytes()==b'WORLD_SENTINEL'
print('INSTALL_PASS paired bytes, unrelated ADM, integrity, idempotence, worlds unchanged')
