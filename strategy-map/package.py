#!/usr/bin/env python3
"""Package and read back the tested client/server ZIP without including a global ADM whitelist."""
import hashlib,json,zipfile
from pathlib import Path
from build import VERSION, archive

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
    report=json.loads((ROOT/'dist/build-validation.json').read_text());assert report['status']=='passed'
    release=json.loads((ROOT/'dist/release.json').read_text());files={
        'README-UA.md':(ROOT/'README.uk.md').read_bytes(),
        'VALIDATION.json':(ROOT/'dist/build-validation.json').read_bytes(),
        'release.json':(ROOT/'dist/release.json').read_bytes(),
        'update_adm.py':(ROOT/'update_adm.py').read_bytes(),
        'adm_helper.py':(REPO/'fleet-menu/update_installation.py').read_bytes(),
        'REMOVE-OLD.txt':('З обох mods прибрати попередні empirecraft-towny-journeymap-*.jar, recruits-1.20.1-*.jar та TownyRecruits.jar.\n'
                          'Із серверного plugins прибрати попередній empirecraft-feudal-*.jar.\n'
                          'Потім скопіювати нові файли відповідного боку. Світи, маршрути, playerdata та бази не видаляти.\n').encode(),
    }
    for row in release['mods']:
        raw=(ROOT/'dist'/row['file']).read_bytes();assert hashlib.sha256(raw).hexdigest()==row['sha256']==report['artifacts'][row['file']]
        for side in ['client','server']:
            files[side+'/mods/'+row['file']]=raw
            files[side+'/mods/'+row['file']+'.sha256']=(row['sha256']+'  '+row['file']+'\n').encode()
    row=release['plugin'];raw=(ROOT/'dist'/row['file']).read_bytes();assert hashlib.sha256(raw).hexdigest()==row['sha256']
    files['server/plugins/'+row['file']]=raw;files['server/plugins/'+row['file']+'.sha256']=(row['sha256']+'  '+row['file']+'\n').encode()
    target=ROOT/f'dist/Empirecraft-Strategic-JourneyMap-{VERSION}.zip';archive(target,files)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
        for name,raw in files.items():assert z.read(name)==raw,name
    print('STRATEGY_ZIP_PASS',target.name,target.stat().st_size,hashlib.sha256(target.read_bytes()).hexdigest())

if __name__=='__main__':main()
