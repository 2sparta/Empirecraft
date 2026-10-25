#!/usr/bin/env python3
"""Package the server-only patch, preserving existing customized config installation choices."""
import hashlib
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def main():
    paths={
        'mods/empirecraft-world-rules-1.0.2.jar':'server/mods/empirecraft-world-rules-1.0.2.jar',
        'mods/empirecraft-world-rules-1.0.2.jar.sha256':'server/mods/empirecraft-world-rules-1.0.2.jar.sha256',
        'reference-configs/empirecraft_world_rules-common.toml':'server/config/empirecraft_world_rules-common.toml',
        'kubejs/server_scripts/5_enchantments.js':'server/kubejs/server_scripts/5_enchantments.js',
        'kubejs/server_scripts/zzzzz_empirecraft_content.js':'server/kubejs/server_scripts/zzzzz_empirecraft_content.js',
        'client-overlay/kubejs/client_scripts/empirecraft_content.js':'kubejs/client_scripts/empirecraft_content.js',
        'reference-configs/rusticdelight-common.toml':'server/config/rusticdelight-common.toml',
        'reference-configs/vinery.toml':'server/config/vinery.toml',
        'README-UK.md':'world-rules/README.md',
        'RESOURCE-AUDIT.md':'world-rules/RESOURCE-AUDIT.md',
        'audit/static-resources.json':'world-rules/audit/static-resources.json',
        'validation/world-rules-1.0.2.md':'validation/world-rules-1.0.2.md',
    }
    contents={target:(REPO/source).read_bytes() for target,source in paths.items()}
    jar=contents['mods/empirecraft-world-rules-1.0.2.jar']
    assert contents['mods/empirecraft-world-rules-1.0.2.jar.sha256'].decode().split()[0]==hashlib.sha256(jar).hexdigest(),'Rebuild the JAR checksum'
    contents['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(contents.items())).encode()
    out=ROOT/'dist/EmpireCraft-World-Rules-1.0.2-server.zip';out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out,'w') as package:
        for name,data in sorted(contents.items()):
            info=zipfile.ZipInfo(name,(2026,10,24,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            package.writestr(info,data)
    with zipfile.ZipFile(out) as package:
        assert package.testzip() is None
        for line in package.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1);assert hashlib.sha256(package.read(name)).hexdigest()==digest
    out.with_suffix('.zip.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
    print(out);print('Verified',len(contents),'package entries')

if __name__=='__main__':main()


