#!/usr/bin/env python3
"""Build against the installed Forge/Mohist 1.20.1 APIs, without downloading an SDK.

Only the menu controllers, icon resolver and menu preferences are patched into the
pinned Feudal JAR. All other plugin entries are retained byte for byte.
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
WORK=REPO/'work/town-menu-art/build'
DIST=ROOT/'dist'
BASE_NAME='empirecraft-feudal-1.20.2.jar'
PLUGIN_NAME='empirecraft-feudal-1.20.3.jar'
MOD_NAME='empirecraft-menu-art-1.1.0.jar'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def readjar(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def jar(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            i=zipfile.ZipInfo(name,(2026,11,21,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
    path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def resources(path):return {p.relative_to(path).as_posix():p.read_bytes() for p in path.rglob('*') if p.is_file()}
def run(command,name):
    result=subprocess.run(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (WORK/(name+'.log')).write_text(result.stdout)
    if result.returncode:print(result.stdout[-7000:]);raise SystemExit(name+' failed')
    print(name+'_PASS',flush=True)
    return result.stdout

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk17',type=Path,default=REPO.parent/'.empirecraft/jdk-17')
    a=p.parse_args();WORK.mkdir(parents=True,exist_ok=True);DIST.mkdir(exist_ok=True)
    baseline=ROOT/'native-inputs'/BASE_NAME
    # The menu-language release is an immutable input; the current installation may already be upgraded.
    expected=json.loads((ROOT/'inputs-1.1.json').read_text())
    assert sha(baseline)==expected['feudal_sha256'],'Feudal input checksum mismatch'
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    forge=next(p for p in libs if p.name=='forge-1.20.1-47.4.23-universal.jar')
    assert sha(forge)==expected['forge_sha256'],'Forge input checksum mismatch'
    api=WORK/'bukkit-api.jar'
    jar(api,{n:v for n,v in readjar(forge).items() if n.startswith('org/bukkit/') and not n.startswith('org/bukkit/craftbukkit/') and n.endswith('.class')})
    compiler=['java','com.sun.tools.javac.Main','-source','17','-target','17','--system',a.jdk17,'-proc:none','-encoding','UTF-8']
    pluginclasses=WORK/'plugin';clientclasses=WORK/'client';tests=WORK/'tests'
    for path in (pluginclasses,clientclasses,tests):
        shutil.rmtree(path,ignore_errors=True);path.mkdir()
    cp=os.pathsep.join(map(str,[api,baseline,*sorted((REPO/'server/plugins').glob('*.jar')),*libs]))
    src=REPO/'feudal/src/main/java/ua/empirecraft/feudal'
    run([*compiler,'-cp',cp,'-d',pluginclasses,*[src/n for n in ['TownMenu.java','ProvisionController.java','MenuIcons.java','CommandPreferences.java','CommandLanguage.java']]],'plugin-compile')
    original=readjar(baseline);files=dict(original);patch=resources(pluginclasses)
    allowed=('ua/empirecraft/feudal/TownMenu','ua/empirecraft/feudal/ProvisionController','ua/empirecraft/feudal/MenuIcons','ua/empirecraft/feudal/CommandPreferences','ua/empirecraft/feudal/CommandLanguage')
    controlled=lambda n:any(n==stem+'.class' or (n.startswith(stem+'$') and n.endswith('.class')) for stem in allowed)
    assert all(controlled(n) for n in patch),sorted(patch)
    for n in list(files):
        if controlled(n):del files[n]
    files.update(patch)
    descriptor=(REPO/'feudal/src/main/resources/plugin.yml').read_bytes()
    assert b'version: 1.20.3\n' in descriptor
    files['plugin.yml']=descriptor
    jar(DIST/PLUGIN_NAME,files)
    unchanged=[n for n in original if not controlled(n) and n!='plugin.yml']
    assert all(files[n]==original[n] for n in unchanged)
    jei=next((REPO/'mods').glob('jei-*.jar'))
    clientcp=os.pathsep.join(map(str,[*libs,jei]))
    run([*compiler,'-cp',clientcp,'-d',clientclasses,*sorted((ROOT/'src/main/java').rglob('*.java'))],'client-compile')
    mod=resources(clientclasses);mod.update({n:v for n,v in resources(ROOT/'src/main/resources').items() if '/textures/gui/' not in n})
    mod['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\n\r\n'
    jar(DIST/MOD_NAME,mod)
    testcp=os.pathsep.join(map(str,[pluginclasses,clientclasses,baseline,api,*sorted((REPO/'server/plugins').glob('*.jar')),*libs,jei]))
    run([*compiler,'-cp',testcp,'-d',tests,*sorted((ROOT/'tests').glob('*.java'))],'test-compile')
    markers=[]
    for suite in ['ua.empirecraft.feudal.MenuSkinTests','ua.empirecraft.feudal.MenuIconsTests','ua.empirecraft.menuart.ItemLinkageTests']:
        result=run(['java','-ea','-cp',str(tests)+os.pathsep+testcp,suite,DIST/MOD_NAME],suite.rsplit('.',1)[1]);markers+=result.splitlines()
    run([*compiler,'-cp',testcp,'-d',tests,*sorted((REPO/'feudal/src/test/java').rglob('*.java'))],'regression-compile')
    regression={}
    for suite in ['ProvisionTests','PoliticsProvisionTests','ProvisionClaimTests','ItemPlanningTests','GoldInventoryTests','LocalizationTests','CommandLanguageTests','ContractTests','FleetTests','RecruitTownyRuntimeTests']:
        result=run(['java','-ea','-cp',str(tests)+os.pathsep+testcp,'ua.empirecraft.feudal.'+suite],suite)
        regression[suite]=sum(line.startswith('✓ ') for line in result.splitlines())
    report={'status':'passed','forge_sha256':sha(forge),'baseline_feudal_sha256':sha(baseline),'unchanged_plugin_entries':len(unchanged),'patched_classes':sorted(patch),'tests':markers,'regression':regression,'graphical_client_tested':False,'production_world_opened':False,'sha256':{n:sha(DIST/n) for n in [PLUGIN_NAME,MOD_NAME]}}
    (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/build.json').write_text(json.dumps(report,indent=2)+'\n')
    print('TOWN_HALL_BUILD_PASS',flush=True)
if __name__=='__main__':main()
