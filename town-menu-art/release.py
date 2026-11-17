#!/usr/bin/env python3
"""Package only the visual mod, the matching server controls and their installer."""
from pathlib import Path
import hashlib,json,shutil,zipfile
from build import ROOT,REPO,DIST,MOD_NAME,PLUGIN_NAME,jar

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def encode(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
def stage():
    bundle=REPO/'work/town-menu-art/bundle'
    shutil.rmtree(bundle,ignore_errors=True);bundle.mkdir(parents=True)
    release={'name':'Empirecraft Town Hall','version':'1.0.0','minecraft':'1.20.1','forge':'47.4.23','mods':[{'id':'empirecraft_menu_art','version':'1.0.0','file':MOD_NAME,'sides':['client'],'sha256':sha(DIST/MOD_NAME)}],'plugins':[{'name':'EmpirecraftFeudal','version':'1.19.4','file':PLUGIN_NAME,'replaces_sha256':json.loads((ROOT/'inputs.json').read_text())['feudal_sha256'],'sides':['server'],'sha256':sha(DIST/PLUGIN_NAME)}],'files':{'client':{},'server':{}}}
    for side,folder,name in [('client','mods',MOD_NAME),('server','plugins',PLUGIN_NAME)]:
        for suffix in ['', '.sha256']:
            relative=folder+'/'+name+suffix;target=bundle/side/relative;target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(DIST/(name+suffix),target);release['files'][side][relative]=sha(target)
    (bundle/'release.json').write_bytes(encode(release))
    for name in ['update_installation.py','README-UA.md']:shutil.copy2(ROOT/name,bundle/name)
    for name in ['preview.png','icons.png']:shutil.copy2(ROOT/'build/previews'/name,bundle/name)
    return release,bundle

def main():
    release,bundle=stage();files={p.relative_to(bundle).as_posix():p.read_bytes() for p in bundle.rglob('*') if p.is_file()}
    target=DIST/'Empirecraft-Town-Hall-1.0.0-install.zip';jar(target,files)
    # jar() also writes a checksum for ZIPs; normalize that suffix.
    target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n');target.with_suffix('.jar.sha256').unlink(missing_ok=True)
    sources={}
    for p in ROOT.rglob('*'):
        if p.is_file() and not any(part in {'build','dist','__pycache__'} for part in p.relative_to(ROOT).parts):sources['town-menu-art/'+p.relative_to(ROOT).as_posix()]=p.read_bytes()
    for name in ['TownMenu.java','ProvisionController.java','MenuSkin.java']:
        p=REPO/'feudal/src/main/java/ua/empirecraft/feudal'/name;sources[p.relative_to(REPO).as_posix()]=p.read_bytes()
    p=REPO/'feudal/src/main/resources/plugin.yml';sources[p.relative_to(REPO).as_posix()]=p.read_bytes()
    jar(DIST/'Empirecraft-Town-Hall-1.0.0-sources.zip',sources)
    p=DIST/'Empirecraft-Town-Hall-1.0.0-sources.zip';p.with_suffix('.zip.sha256').write_text(sha(p)+'  '+p.name+'\n');p.with_suffix('.jar.sha256').unlink(missing_ok=True)
    (ROOT/'release.json').write_bytes(encode(release))
    print('TOWN_HALL_RELEASE_PASS',target)
if __name__=='__main__':main()
