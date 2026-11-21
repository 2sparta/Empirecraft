"""Verify the installed overlay preserves the existing recipes, assets and block authority."""
import hashlib,json,subprocess,tomllib,zipfile,io
from pathlib import Path
from build import R,REPO,BASE,NAME

def main():
    report=json.loads((R/'build.json').read_text())
    assert report['base_commit']==BASE
    base=subprocess.check_output(['git','show',BASE+':mods/'+NAME.replace('empirecraft.2','empirecraft.1')],cwd=REPO)
    assert hashlib.sha256(base).hexdigest()==report['base_sha256']
    candidate=(R/'dist'/NAME).read_bytes()
    assert hashlib.sha256(candidate).hexdigest()==report['sha256']
    with zipfile.ZipFile(io.BytesIO(base)) as old,zipfile.ZipFile(io.BytesIO(candidate)) as new:
        assert new.testzip() is None
        preserved=[n for n in old.namelist() if not n.endswith('/') and n not in ['META-INF/mods.toml','META-INF/MANIFEST.MF','ua/empirecraft/siegeworks/Blocks.class']]
        for name in preserved:assert old.read(name)==new.read(name),name
        mods=tomllib.loads(new.read('META-INF/mods.toml').decode())
        assert any(m['modId']=='siegeworks' and m['version']=='0.1.0-beta.11' for m in mods['mods'])
        assert any(d['modId']=='valkyrienskies' and d['mandatory'] and d['side']=='BOTH' for d in mods['dependencies']['siegeworks'])
        assert 'empirecraft_siegeworks_vs.mixins.json' in new.read('META-INF/MANIFEST.MF').decode()
        assert not any('probe' in n.lower() or 'TEST-ONLY' in n for n in new.namelist())
    for side in ['mods','server/mods']:
        files=list((REPO/side).glob('siegeworks-*.jar'))
        assert [p.name for p in files]==[NAME],side
        assert files[0].read_bytes()==candidate,side
        assert files[0].with_suffix('.jar.sha256').read_text().split()[0]==report['sha256']
    native=json.loads((R/'native.json').read_text())
    assert native['status']=='passed' and native['artifact_sha256']==report['sha256']
    result=dict(status='passed',unchanged_base_entries=len(preserved),recipes_and_models_preserved=True,paired_bytes=True,sha256=report['sha256'])
    (R/'static.json').write_text(json.dumps(result,indent=2)+'\n')
    print('SIEGE_VS_ARTIFACT_PASS',len(preserved))
if __name__=='__main__':main()
