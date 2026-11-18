import copy,importlib.util,json,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
spec=importlib.util.spec_from_file_location('wildlife_install',ROOT/'install.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class InstallerTest(unittest.TestCase):
    def test_pair_backup_idempotence_and_preservation(self):
        with tempfile.TemporaryDirectory() as tmp:
            client=Path(tmp)/'клієнт з пробілами';server=Path(tmp)/'server with spaces'
            for base in [client,server]:
                (base/'mods').mkdir(parents=True)
                old=next(p for p in [REPO/'mods/spawn-4.0.8-1.20.1-empirecraft.1.jar',REPO/'disabled-mods/alexs-wildlife/mods/spawn-4.0.8-1.20.1-empirecraft.1.jar'] if p.exists())
                (base/'mods'/old.name).write_bytes(old.read_bytes())
                (base/'world').mkdir();(base/'world/level.dat').write_bytes(b'existing-world-never-modified')
            adm=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True)
            original=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text());original['modIds']=[r for r in original['modIds'] if r['id'] not in {'alexsmobs','citadel','empirecraft_wildlife'}];original['integrityHash']=module.integrity(original);adm.write_bytes(module.encode(original));before=adm.read_bytes()
            bundle=ROOT/'dist/bundle'
            self.assertEqual(module.install(client,server,bundle,False),8)
            self.assertEqual(adm.read_bytes(),before)
            self.assertEqual(module.install(client,server,bundle,True),8)
            installed=json.loads(adm.read_text());self.assertEqual(module.integrity(installed),installed['integrityHash'])
            changed={'alexsmobs','citadel','spawn','empirecraft_wildlife'}
            self.assertEqual([r for r in original['modIds'] if r['id'] not in changed],[r for r in installed['modIds'] if r['id'] not in changed])
            snapshots={p:p.read_bytes() for base in [client,server] for p in (base/'mods').glob('*')}
            for base in [client,server]:
                self.assertFalse((base/'mods'/old.name).exists());self.assertTrue(list((base/'.empirecraft-backups').rglob(old.name)))
                self.assertEqual((base/'world/level.dat').read_bytes(),b'existing-world-never-modified')
            self.assertEqual(module.install(client,server,bundle,True),0)
            self.assertEqual(installed,json.loads(adm.read_text()))
            self.assertTrue(all(p.read_bytes()==raw for p,raw in snapshots.items()))
if __name__=='__main__':unittest.main()
