#!/usr/bin/env python3
"""Exercise the real packaged update in disposable client/server folders."""
import importlib.util,json,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
def main():
    spec=importlib.util.spec_from_file_location('spawn_install',ROOT/'install.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    with tempfile.TemporaryDirectory(prefix='spawn-install-test-') as temp:
        root=Path(temp);payload=root/'payload';client=root/'client';server=root/'server';client.mkdir();server.mkdir()
        with zipfile.ZipFile(ROOT/'dist/Empirecraft-Spawn-Native-1.0.0.zip') as archive:archive.extractall(payload)
        obsolete=json.loads((payload/'DELETE-THESE-FILES.json').read_text())
        for name in obsolete:
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'old fixture')
        adm=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text(encoding='utf-8-sig'))
        row=next(r for r in adm['modIds'] if r['id']=='spawn');row.update(version='4.0.8-1.20.1',sha256='0'*64);adm['integrityHash']=module.integrity(adm)
        adm_path=server/'config/admanticheat/idfile/admanticheat_id_list.json';adm_path.parent.mkdir(parents=True);adm_path.write_text(json.dumps(adm))
        keep=server/'world/test.dat';keep.parent.mkdir();keep.write_bytes(b'world must remain intact')
        subprocess.run(['python3',str(payload/'install.py'),'--client',str(client),'--server',str(server)],check=True)
        assert all(not (root/name).exists() for name in obsolete)
        assert keep.read_bytes()==b'world must remain intact'
        updated=json.loads(adm_path.read_text());assert updated['integrityHash']==module.integrity(updated)
        assert all(row in updated['modIds'] for row in adm['modIds'] if row['id']!='spawn')
        assert updated['resourcePackIds']==adm['resourcePackIds']
        for side in ['client','server']:
            assert (root/side/'mods/spawn-4.0.8-1.20.1-empirecraft.1.jar').exists()
            assert len(list((root/side).glob('.empirecraft-spawn-backup-*')))==1
        # A damaged payload is rejected before any installation file is touched.
        jar=payload/'client/mods/spawn-4.0.8-1.20.1-empirecraft.1.jar';jar.write_bytes(b'corrupt')
        original=(client/'mods'/jar.name).read_bytes()
        result=subprocess.run(['python3',str(payload/'install.py'),'--client',str(client)],capture_output=True,text=True)
        assert result.returncode and 'checksum mismatch' in result.stderr
        assert (client/'mods'/jar.name).read_bytes()==original
    print('PASS packaged installer: checksum rejection, exact cleanup, backups, world and unrelated ADM preservation')
if __name__=='__main__':main()
