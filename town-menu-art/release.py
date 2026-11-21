#!/usr/bin/env python3
"""Package the same registered icon items for both sides and the current Feudal plugin."""
from pathlib import Path
import hashlib,json,shutil
from build import ROOT,REPO,DIST,MOD_NAME,PLUGIN_NAME,jar
VERSION='1.1.0'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def stage():
    bundle=REPO/'work/town-menu-art/bundle-1.1'
    shutil.rmtree(bundle,ignore_errors=True);bundle.mkdir(parents=True)
    release={'name':'Empirecraft · Іконки міста','version':VERSION,'minecraft':'1.20.1','forge':'47.4.23','native_items':46,'default_menu_language':'uk','mods':[{'id':'empirecraft_menu_art','version':VERSION,'file':MOD_NAME,'sides':['client','server'],'sha256':sha(DIST/MOD_NAME)}],'plugins':[{'name':'EmpirecraftFeudal','version':'1.20.3','file':PLUGIN_NAME,'replaces_sha256':json.loads((ROOT/'inputs-1.1.json').read_text())['feudal_sha256'],'sides':['server'],'sha256':sha(DIST/PLUGIN_NAME)}],'files':{'client':{},'server':{}}}
    for side,folder,name in [('client','mods',MOD_NAME),('server','mods',MOD_NAME),('server','plugins',PLUGIN_NAME)]:
        for suffix in ['', '.sha256']:
            relative=folder+'/'+name+suffix;target=bundle/side/relative;target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(DIST/(name+suffix),target);release['files'][side][relative]=sha(target)
    (bundle/'release.json').write_bytes(encode(release))
    for name in ['update_installation.py','README-UA.md','icons.json']:shutil.copy2(ROOT/name,bundle/name)
    for name in ['preview.png','icons.png']:shutil.copy2(ROOT/'build/previews-1.1'/name,bundle/name)
    return release,bundle

def main():
    release,bundle=stage();files={p.relative_to(bundle).as_posix():p.read_bytes() for p in bundle.rglob('*') if p.is_file()}
    target=DIST/f'Empirecraft-Town-Icons-{VERSION}-install.zip';jar(target,files)
    sources={}
    for p in ROOT.rglob('*'):
        if p.is_file() and not any(part in {'build','dist','__pycache__','native-inputs'} for part in p.relative_to(ROOT).parts):sources['town-menu-art/'+p.relative_to(ROOT).as_posix()]=p.read_bytes()
    for name in ['TownMenu.java','ProvisionController.java','MenuIcons.java','CommandPreferences.java','CommandLanguage.java']:
        p=REPO/'feudal/src/main/java/ua/empirecraft/feudal'/name;sources[p.relative_to(REPO).as_posix()]=p.read_bytes()
    for relative in ['feudal/src/main/resources/plugin.yml','feudal/src/test/java/ua/empirecraft/feudal/LocalizationTests.java']:
        p=REPO/relative;sources[relative]=p.read_bytes()
    jar(DIST/f'Empirecraft-Town-Icons-{VERSION}-sources.zip',sources)
    (ROOT/'release.json').write_bytes(encode(release));print('TOWN_ICONS_RELEASE_PASS',target)
if __name__=='__main__':main()
