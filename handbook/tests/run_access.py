#!/usr/bin/env python3
"""Test recipe lookup and vanilla give in a copy of a generated Mohist fixture."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parent

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--fixture',type=Path,required=True)
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--sdk',type=Path,required=True)
    p.add_argument('--book',type=Path)
    p.add_argument('--runtime-java',type=Path,default=Path('/usr/bin/java'))
    p.add_argument('--work',type=Path,default=REPO.parent/'work/handbook-writing-access')
    a=p.parse_args()
    fixture=a.fixture.resolve()
    assert str(fixture).startswith('/workspace/work/') and (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file()
    work=a.work.resolve();assert str(work).startswith('/workspace/work/')
    work.mkdir(parents=True,exist_ok=True)
    runtime=work/'runtime'
    assert not runtime.exists(),'Use a fresh scratch runtime for each run'
    shutil.copytree(fixture,runtime,symlinks=True)
    classes=work/'classes';classes.mkdir(exist_ok=True)
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    cp=[a.sdk/'runtime-named.jar',*libs,*sorted((REPO/'server/mods').glob('*.jar'))]
    java=a.java_home/'bin/java'
    subprocess.run([str(a.java_home/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),
        '-d',str(classes),str(ROOT/'tests/HandbookAccessProbe.java')],check=True)
    named=work/'probe-named.jar';srg=work/'probe-srg.jar'
    with zipfile.ZipFile(named,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    remapper=[a.sdk/'tiny-remapper.jar',a.sdk/'mapping-io.jar',*asm]
    subprocess.run([str(java),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',
        str(named),str(srg),str(a.sdk/'named-srg.tiny'),'named','srg',str(a.sdk/'runtime-named.jar'),*map(str,libs),'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(srg,'a') as z:
        z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_handbook_probe"\nversion="1.0.0"\n')
    shutil.copyfile(srg,runtime/'mods/empirecraft-handbook-probe-TEST-ONLY.jar')
    # Test the current installed handbook, regardless of the fixture's historical copy.
    for old in (runtime/'mods').glob('empirecraft-handbook-1*.jar'):old.unlink()
    book=a.book or next((REPO/'server/mods').glob('empirecraft-handbook-*.jar'))
    shutil.copyfile(book,runtime/'mods'/book.name)
    for old in (runtime/'mods').glob('empirecraft-layered-armor-*.jar'):old.unlink()
    armor=next((REPO/'server/mods').glob('empirecraft-layered-armor-*.jar'))
    shutil.copyfile(armor,runtime/'mods'/armor.name)
    script=REPO/'server/kubejs/server_scripts/zzzzzzzzz_empirecraft_paper_books.js'
    shutil.copyfile(script,runtime/'kubejs/server_scripts'/script.name)
    shutil.copytree(REPO/'server/kubejs/data/empirecraft_guidebook',runtime/'kubejs/data/empirecraft_guidebook',dirs_exist_ok=True)
    # Model the reported missing recipe IDs before the late recovery pass runs.
    (runtime/'kubejs/server_scripts/handbook_missing_recipes_TEST_ONLY.js').write_text(
        '// priority: 1000\nServerEvents.recipes(event => {\n'+
        "  ['minecraft:paper','minecraft:book','empirecraft_guidebook:handbook'].forEach(id => event.remove({id:id}))\n})\n")
    log=work/'native.log'
    with log.open('w') as out:
        result=subprocess.run([str(a.runtime_java),'@launch.args'],cwd=runtime,stdout=out,stderr=subprocess.STDOUT,timeout=600)
    raw=log.read_text(errors='replace')
    markers=[line for line in raw.splitlines() if 'HANDBOOK_ACCESS_' in line]
    print('\n'.join(markers))
    assert result.returncode==0 and any('HANDBOOK_ACCESS_PASS' in line for line in markers) and 'HANDBOOK_ACCESS_FAIL' not in raw,str(log)
    report={'status':'passed','runtime':'Mohist / Forge 47.4.23','book':book.name,
        'sha256':hashlib.sha256(book.read_bytes()).hexdigest(),'markers':markers,
        'positions':13,'grids':['2x2','3x3'],'command':'give @s patchouli:guide_book{"patchouli:book":"empirecraft_guidebook:handbook"} 1',
        'recipe_command':True,'paper_and_book':True,'tfc_paper_finishing':True,'knife_damage':True,
        'fault_injection':'Early KubeJS pass removes paper, book and handbook IDs; late shipped pass restores them.',
        'script_sha256':hashlib.sha256(script.read_bytes()).hexdigest(),
        'scope':'Actual RecipeManager selection and result NBT; native command parse, execution and inventory. No graphical client or production world.'}
    (ROOT/'audit/access.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':main()
