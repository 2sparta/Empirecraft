#!/usr/bin/env python3
"""Package only artifacts exercised by native death/spawn tests."""
import argparse,json,shutil,tomllib,zipfile
from pathlib import Path
from build import ROOT,REPO,AM,MOD,SPAWN,pack,sha
from install import encode
def main():
    p=argparse.ArgumentParser();p.add_argument('--citadel',type=Path,required=True);a=p.parse_args()
    native=json.loads((ROOT/'audit/native.json').read_text());static=json.loads((ROOT/'audit/static.json').read_text());pins=json.loads((ROOT/'inputs.json').read_text())
    assert native['status']==static['status']=='passed'
    assert sha(a.citadel)==native['citadel_sha256']==pins['citadel_sha256']
    bundle=ROOT/'dist/bundle';(bundle/'mods').mkdir(parents=True,exist_ok=True);rows=[]
    for name,mid,old in [(AM,'alexsmobs',[pins['alexsmobs_sha256']]),(MOD,'empirecraft_wildlife',[]),(SPAWN,'spawn',[pins['spawn_sha256']]),(a.citadel.name,'citadel',[pins['citadel_sha256']])]:
        source=a.citadel if mid=='citadel' else ROOT/'dist'/name
        if mid!='citadel':assert sha(source)==native['sha256'][name]==static['sha256'][name]
        with zipfile.ZipFile(source) as z:version=next(m['version'] for m in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'] if m['modId']==mid)
        rows.append(dict(id=mid,file=name,version=version,sha256=sha(source),replace_sha256=[*old,sha(source)],sides=['client','server']))
        shutil.copy2(source,bundle/'mods'/name)
    full=json.loads((ROOT/'audit/native-full.json').read_text())
    assert full['status']=='passed' and full['sha256']==native['sha256'] and full['citadel_sha256']==pins['citadel_sha256']
    release=dict(version='1.0.0',mods=rows,native=native,native_full_pack=full,static=static)
    (ROOT/'release.json').write_bytes(encode(release));(bundle/'release.json').write_bytes(encode(release))
    for name in ['install.py','README.uk.md']:shutil.copy2(ROOT/name,bundle/name)
    files={f.relative_to(bundle).as_posix():f.read_bytes() for f in bundle.rglob('*') if f.is_file()}
    # Spawn's audio assets make a combined ZIP exceed GitHub's 100 MiB per-file limit.
    pack(ROOT/'dist/Empirecraft-Alexs-Wildlife-1.0.0.zip',{n:raw for n,raw in files.items() if n!='mods/'+SPAWN})
    pack(ROOT/'dist/Empirecraft-Spawn-Ant-Replacement-1.0.0.zip',{'mods/'+SPAWN:files['mods/'+SPAWN]})
    print('WILDLIFE_RELEASE_PASS')
if __name__=='__main__':main()
