#!/usr/bin/env python3
"""Package the identical mandatory addon and the updated existing ADM list."""
import hashlib, json, zipfile
from build import ROOT, REPO, NAME, archive
from update_metadata import update

def main():
    update(check=True)
    jar = (REPO/'mods'/NAME).read_bytes()
    lock = json.loads((REPO/'modpack-lock.json').read_text())
    calendar = next(row for row in lock['mods'] if row['id'] == 'empirecraft_calendar')
    calendar_raw = (REPO/'mods'/calendar['file']).read_bytes()
    assert calendar_raw == (REPO/'server/mods'/calendar['file']).read_bytes()
    assert hashlib.sha256(calendar_raw).hexdigest() == calendar['sha256']
    report = {
        'addon': NAME, 'sha256': hashlib.sha256(jar).hexdigest(),
        'minecraft': '1.20.1', 'forge': '47.4.23', 'overgeared': '1.20.1-1.6.36',
        'calendar': calendar['file'], 'calendar_sha256': calendar['sha256'],
        'verified': ['Java 17 compilation against installed mods', 'required native server and client Mixin injections and ASM verification', 'no forbidden runtime Mixin references in executable descriptors or lambda bootstrap arguments', 'real transformed packet class loading and handler MethodHandle resolution with Mixin classes forbidden', 'original 1.0.0 fixture reproduces the reported NoClassDefFoundError', 'native Epic Knights plate alias and 40 diamond/netherite weapon exclusions', 'five exact equipment quality stat profiles and integer durability rounding', 'four Maximilian anvil recipes and idempotent reload', 'ordinary steel armor and potion-arrow removal', 'ranged damage snapshots for bows, crossbows and arquebuses', 'quality boundaries for every blueprint level', 'transitive upgrade graph including cycles', 'slot speed ordering', 'hit report validation', 'one gunpowder per server shot, zero client consumption', 'complete automatically loaded en_us/uk_ua/ru_ru calendar and armor blueprint resources', 'identical client/server JARs and ADM integrity'],
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
    for side in ['client', 'server']:
        entries[side+'/mods/'+calendar['file']] = calendar_raw
        entries[side+'/mods/'+calendar['file']+'.sha256'] = (calendar['sha256']+'  '+calendar['file']+'\n').encode()
    for side,base in [('client','mods'),('server','server/mods')]:
        for suffix in ['', '.sha256']:
            entries[side+'/mods/empirecraft-towny-journeymap-1.2.0.jar'+suffix]=(REPO/base/('empirecraft-towny-journeymap-1.2.0.jar'+suffix)).read_bytes()
    target = ROOT/'dist/Empirecraft-Equipment-1.1.1.zip'
    for old in (ROOT/'dist').glob('Empirecraft-Forging-Calendar-Hotfix-*.zip*'): old.unlink()
    for old in (ROOT/'dist').glob('Empirecraft-Overgeared-*.zip*'): old.unlink()
    archive(target, entries)
    with zipfile.ZipFile(target) as out:
        assert out.testzip() is None
        assert out.read('client/mods/'+NAME) == out.read('server/mods/'+NAME)
    print('PASS installation package:', target.name, hashlib.sha256(target.read_bytes()).hexdigest())

if __name__ == '__main__': main()

