#!/usr/bin/env python3
"""Package the same verified Sails binary for clients/server and the matching ADM file."""
from pathlib import Path
import hashlib, importlib.util, json, tomllib, zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
NAME = 'vs_sails-1.20.1-0.3.2-empirecraft.2-forge.jar'

def main():
    client = REPO/'mods'/NAME
    server = REPO/'server/mods'/NAME
    binary = client.read_bytes()
    assert binary == server.read_bytes(), 'Client/server Sails JARs differ'
    digest = hashlib.sha256(binary).hexdigest()
    with zipfile.ZipFile(client) as archive:
        assert archive.testzip() is None
        mods = tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods']
        assert any(m['modId'] == 'vs_sails' and m['version'] == '0.3.2-empirecraft.2' for m in mods)
        assert not any('SmokeMod' in n or 'SailForceTest' in n for n in archive.namelist())
    adm_path = REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    adm = json.loads(adm_path.read_text())
    spec = importlib.util.spec_from_file_location('sails_adm', ROOT/'update_adm.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    assert module.integrity(adm) == adm['integrityHash'], 'ADM integrity mismatch'
    entry, = [e for e in adm['modIds'] if e['id'] == 'vs_sails']
    assert entry == dict(id='vs_sails', version='0.3.2-empirecraft.2', sha256=digest, filePresent=True)
    entries = {
        'client/mods/'+NAME: binary,
        'server/mods/'+NAME: binary,
        'server/config/admanticheat/idfile/admanticheat_id_list.json': adm_path.read_bytes(),
        'INSTALL-UA.txt': (ROOT/'WIND-FIX.md').read_bytes(),
    }
    output = ROOT/'dist/Empirecraft-Sails-Wind-Fix.zip'
    output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output, 'w') as archive:
        for name, content in sorted(entries.items()):
            item = zipfile.ZipInfo(name, (2026,10,25,0,0,0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            archive.writestr(item, content)
    output.with_suffix('.zip.sha256').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
    print(output, digest)

if __name__ == '__main__': main()
