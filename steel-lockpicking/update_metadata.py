#!/usr/bin/env python3
"""Pin the reviewed overlay as the new Locks input, keeping the exact client/server pair."""
import hashlib,importlib.util,json,subprocess,sys,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def main():
    installed=REPO/'mods'/build.NAME;raw=installed.read_bytes();digest=hashlib.sha256(raw).hexdigest()
    assert raw==(REPO/'server/mods'/build.NAME).read_bytes()
    path=REPO/'native-content/inputs.json';inputs=json.loads(path.read_text())
    inputs={(key.rsplit('/',1)[0]+'/'+build.NAME if key.endswith('/'+build.OLD_NAME) else key):value for key,value in inputs.items()}
    for side in ['mods','server/mods']:inputs[side+'/'+build.NAME]=digest
    write(path,inputs)
    path=REPO/'native-content/retained-code.json';pins=json.loads(path.read_text());pins.pop(build.OLD_NAME,None)
    with zipfile.ZipFile(installed) as z:
        keep=['melonslise/locks/common/init/LocksItems.class','melonslise/locks/common/init/LocksNetwork.class',
              'melonslise/locks/common/container/LockPickingContainer.class','melonslise/locks/client/init/LocksScreens.class']
        keep += [n for n in z.namelist() if n.startswith('ua/empirecraft/locks/') and n.endswith('.class')]
        pins[build.NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in sorted(keep)}
    write(path,pins)
    subprocess.run([sys.executable,str(REPO/'validation/reconcile_modpack.py'),'--apply'],check=True)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());row=next(r for r in lock['mods'] if r['id']=='locks')
    row.update(source='local:steel-lockpicking',updated=True,pin_reason='Restored durable steel-only lockpick and animated pin menu; server-only randomized sessions, harder reset/wear/cooldown, claim checks and EN/UK/RU. Preserves existing Locks world/key format and furniture guards; native old-world upgrade verified in steel-lockpicking/.')
    lock['updated_mod_count']=sum(bool(r.get('updated')) for r in lock['mods']);write(path,lock)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
    manifest['releases']=[r for r in manifest['releases'] if r['id']!='locks']+[dict(row,artifact='mods/'+build.NAME)]
    write(path,manifest)
    sys.path.insert(0,str(REPO/'feudal'))
    spec=importlib.util.spec_from_file_location('locks_city_release',REPO/'feudal/city_release.py');city=importlib.util.module_from_spec(spec);spec.loader.exec_module(city)
    city.refresh_inventory();city.refresh_translations()
    subprocess.run([sys.executable,str(REPO/'validation/reconcile_modpack.py')],check=True)
    print('STEEL_METADATA_PASS',digest)
if __name__=='__main__':main()
