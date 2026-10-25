#!/usr/bin/env python3
"""Package the tested arcade Sails binary and its exact ADM entry."""
from pathlib import Path
import hashlib, importlib.util, json, tomllib, zipfile
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NAME='vs_sails-1.20.1-0.3.2-empirecraft.5-forge.jar'
def main():
    client=REPO/'mods'/NAME;server=REPO/'server/mods'/NAME
    binary=client.read_bytes();assert binary==server.read_bytes()
    digest=hashlib.sha256(binary).hexdigest()
    for folder in [client.parent,server.parent]:
        assert [p.name for p in folder.glob('vs_sails-*.jar')]==[NAME], 'Remove obsolete Sails JARs first'
    with zipfile.ZipFile(client) as z:
        assert z.testzip() is None
        assert any(m['modId']=='vs_sails' and m['version']=='0.3.2-empirecraft.5' for m in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'])
        assert 'com/quintonc/vs_sails/client/AnchorVisualRenderer.class' in z.namelist()
        assert not any('SmokeMod' in n or 'SailForceTest' in n for n in z.namelist())
    adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';data=json.loads(adm.read_text())
    spec=importlib.util.spec_from_file_location('sails_adm',ROOT/'update_adm.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    assert m.integrity(data)==data['integrityHash']
    entry,=[v for v in data['modIds'] if v['id']=='vs_sails']
    assert entry==dict(id='vs_sails',version='0.3.2-empirecraft.5',sha256=digest,filePresent=True)
    entries={'client/mods/'+NAME:binary,'server/mods/'+NAME:binary,
        'server/config/admanticheat/idfile/admanticheat_id_list.json':adm.read_bytes(),'INSTALL-UA.txt':(ROOT/'ARCADE.md').read_bytes()}
    output=ROOT/'dist/Empirecraft-Sails-Arcade-Fix.zip';output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output,'w') as z:
        for name,content in sorted(entries.items()):
            item=zipfile.ZipInfo(name,(2026,10,25,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,content)
    output.with_suffix('.zip.sha256').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
    print(output,digest)
if __name__=='__main__':main()
