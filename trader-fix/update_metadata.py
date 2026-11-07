#!/usr/bin/env python3
"""Pin the two reviewed addons without changing unrelated mod or resource-pack versions."""
import hashlib,importlib.util,json,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def run(script,*args):subprocess.run([sys.executable,str(REPO/script),*args],check=True)
def main():
    run('forging-balance/update_metadata.py')
    release=json.loads((REPO/'world-rules/build/native-release.json').read_text());name=release['file'];jar=REPO/'server/mods'/name
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==release['sha256']
    for relative in ['native-content/inputs.json','native-content/audit/build.json']:
        path=REPO/relative;data=json.loads(path.read_text());data['server/mods/'+name]=release['input_sha256'] if relative.endswith('inputs.json') else release['report'];write(path,data)
    path=REPO/'native-content/retained-code.json';data=json.loads(path.read_text())
    with zipfile.ZipFile(jar) as z:data[name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in sorted(z.namelist()) if n.startswith('ua/empirecraft/worldrules/') and n.endswith('.class')}
    write(path,data)
    run('validation/reconcile_modpack.py','--apply')
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_world_rules')
    row['pin_reason']='Preserves natural renewal, wilderness checks and prior content rules; disables wandering-trader night potion AI and filters retired NPC offer inputs/results before item migration. Existing valid offers keep stock, demand and prices; trader-fix/ contains full-runtime checks.';write(path,lock)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
    for mid,folder in [('empirecraft_world_rules','server/mods'),('empirecraft_forging','mods')]:
        entry=next(r for r in lock['mods'] if r['id']==mid);manifest['releases']=[r for r in manifest['releases'] if r['id']!=mid]+[dict(entry,artifact=folder+'/'+entry['file'])]
    write(path,manifest)
    sys.path.insert(0,str(REPO/'feudal'));spec=importlib.util.spec_from_file_location('trader_city',REPO/'feudal/city_release.py');city=importlib.util.module_from_spec(spec);spec.loader.exec_module(city)
    city.refresh_inventory();city.refresh_translations()
    run('native-content/update_metadata.py','--check');run('validation/reconcile_modpack.py');run('validation/check_installed_releases.py')
    print('TRADER_METADATA_PASS')
if __name__=='__main__':main()
