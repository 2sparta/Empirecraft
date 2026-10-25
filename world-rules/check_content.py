#!/usr/bin/env python3
"""Check preserved native fletching recipes, installed artifacts and real script engines."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--overgeared',type=Path,required=True)
    p.add_argument('--create',type=Path,required=True)
    p.add_argument('--rhino',type=Path,required=True)
    p.add_argument('--runtime',type=Path,action='append',required=True)
    args=p.parse_args()
    paths=['minecraft/recipes/arrow2.json',*[f'overgeared/recipes/{m}_arrow.json' for m in ('iron','steel','diamond')]]
    jar=REPO/'server/mods/empirecraft-world-rules-1.0.2.jar'
    with zipfile.ZipFile(args.overgeared) as native,zipfile.ZipFile(jar) as built:
        for name in paths:
            original=json.loads(native.read('data/'+name))
            del original['result_tipped'];del original['result_lingering']
            data=(ROOT/'src/main/resources/data'/name).read_bytes()
            assert json.loads(data)==original,'Normal arrow materials/count changed: '+name
            assert built.read('data/'+name)==data,'Rebuild native recipe overrides: '+name
            assert original['result']['count']==4
        for material in ('iron','steel','diamond'):
            assert f'assets/overgeared/models/item/{material}_arrow.json' in native.namelist()
    with zipfile.ZipFile(args.create) as create:
        doors={n.split('/')[-1][:-5] for n in create.namelist() if n.startswith('assets/create/blockstates/') and n.endswith(('_door.json','_trapdoor.json'))}
        assert doors=={'andesite_door','brass_door','copper_door','framed_glass_door','framed_glass_trapdoor','train_door','train_trapdoor'}
    row=next(r for r in json.loads((REPO/'modpack-lock.json').read_text())['mods'] if r['id']=='empirecraft_world_rules')
    raw=jar.read_bytes()
    assert row['file']==jar.name and row['version']=='1.0.2' and row['sides']==['server']
    assert row['size']==len(raw) and row['sha256']==hashlib.sha256(raw).hexdigest() and row['sha512']==hashlib.sha512(raw).hexdigest()
    assert row['git_sha']==hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    assert jar.with_suffix('.jar.sha256').read_text().split()[0]==row['sha256']
    scripts=[ROOT/'tests/content-fixture.js',REPO/'server/kubejs/server_scripts/zzzzz_empirecraft_content.js',REPO/'kubejs/client_scripts/empirecraft_content.js']
    node='\n'.join(f'require("vm").runInThisContext(require("fs").readFileSync({json.dumps(str(file))},"utf8"));' for file in scripts)+'\nconsole.log(checkContentScripts());'
    subprocess.run(['node','-e',node],check=True)
    assert hashlib.sha256(args.rhino.read_bytes()).hexdigest()=='fed2211429301bf043864183cab9ab8e92d4cc4dbb9e488ce6c75217c54584a6','Use the shipped Rhino version'
    jars=[args.rhino,*sorted({p.resolve() for root in args.runtime for p in root.rglob('*.jar')})]
    if not any(j.name.startswith('commons-lang3-') for j in jars):
        commons=ROOT/'build/commons-lang3-3.12.0.jar'
        if not commons.exists():
            with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar',timeout=30) as response:
                commons.write_bytes(response.read())
        assert hashlib.sha256(commons.read_bytes()).hexdigest()=='d919d904486c037f8d193412da0c92e22a9fa24230b9d67a57855c5c31c7e94e'
        jars.append(commons)
    cp=os.pathsep.join(str(f.resolve()) for f in jars)
    output=ROOT/'build/content-tests';output.mkdir(parents=True,exist_ok=True)
    java_home=os.environ.get('JAVA_HOME');java=str(Path(java_home)/'bin/java') if java_home else 'java'
    javac=[str(Path(java_home)/'bin/javac')] if java_home else ([shutil.which('javac')] if shutil.which('javac') else [java,'com.sun.tools.javac.Main'])
    subprocess.run([*javac,'--release','17','-proc:none','-cp',cp,'-d',str(output),str(ROOT/'tests/ContentRhinoTest.java')],check=True)
    subprocess.run([java,'-cp',str(output)+os.pathsep+cp,'ContentRhinoTest',*map(str,scripts)],check=True)
    print('PASS 4 exact native arrow recipes, 7 native Create doors, server-only lock/checksum, Node and shipped Rhino')

if __name__=='__main__':main()
