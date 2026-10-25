#!/usr/bin/env python3
"""Package the identical mandatory addon and the updated existing ADM list."""
import hashlib, json, zipfile
from build import ROOT, REPO, NAME, archive
from update_metadata import update

def main():
    update(check=True)
    jar = (REPO/'mods'/NAME).read_bytes()
    report = {
        'addon': NAME, 'sha256': hashlib.sha256(jar).hexdigest(),
        'minecraft': '1.20.1', 'forge': '47.4.23', 'overgeared': '1.20.1-1.6.36',
        'verified': ['Java 17 compilation against installed mods', 'required native server and client Mixin injections and ASM verification', 'quality boundaries for every blueprint level', 'transitive upgrade graph including cycles', 'slot speed ordering', 'hit report validation', 'one gunpowder per server shot, zero client consumption', 'complete automatically loaded en_us/uk_ua/ru_ru calendar and armor blueprint resources', 'identical client/server JARs and ADM integrity'],
        'limitations': ['A full Minecraft client and Mohist world have not been launched.']
    }
    audit = ROOT/'audit/verification.json'; audit.parent.mkdir(exist_ok=True)
    audit.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    entries = {
        'client/mods/'+NAME: jar, 'server/mods/'+NAME: jar,
        'client/mods/'+NAME+'.sha256': (REPO/'mods'/(NAME+'.sha256')).read_bytes(),
        'server/mods/'+NAME+'.sha256': (REPO/'server/mods'/(NAME+'.sha256')).read_bytes(),
        'server/config/admanticheat/idfile/admanticheat_id_list.json': (REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes(),
        'README.md': (ROOT/'README.md').read_bytes(), 'verification.json': audit.read_bytes()
    }
    target = ROOT/'dist/Empirecraft-Forging-Calendar-Hotfix-1.0.0.zip'
    archive(target, entries)
    with zipfile.ZipFile(target) as out:
        assert out.testzip() is None
        assert out.read('client/mods/'+NAME) == out.read('server/mods/'+NAME)
    print('PASS installation package:', target.name, hashlib.sha256(target.read_bytes()).hexdigest())

if __name__ == '__main__': main()
