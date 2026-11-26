#!/usr/bin/env python3
import argparse,json,os,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent
p=argparse.ArgumentParser();p.add_argument('--compiler',default='javac');a=p.parse_args()
def run(args):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    print(result.stdout,end='')
    if result.returncode:raise SystemExit(result.returncode)
b=R/'build';tests=b/'test-classes';tests.mkdir(exist_ok=True)
libs=sorted((REPO/'server/libraries').rglob('*.jar'))
candidate=b/'libs/empirecraft-earth-0.1.3.jar'
asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'),reverse=True)
cp=[tests,*asm,candidate,*sorted((REPO/'mods').glob('*.jar')),*sorted((REPO/'server/mods').glob('*.jar')),*libs]
source=[R/'tests/GeographyTest.java',R/'tests/MixinTest.java',REPO/'seasonal-calendar/src/test/java/guardtest/OfflineMixinService.java',REPO/'seasonal-calendar/src/test/java/guardtest/OfflineProperties.java']
run([a.compiler,'-source','17','-target','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(tests),*map(str,source)])
services=tests/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
(services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
run(['java','-ea','-cp',os.pathsep.join(map(str,cp)),'ua.empirecraft.earth.GeographyTest',str(R/'src/main/resources/earth-defaults.json')])
for side in ['SERVER','CLIENT']:
    run(['java','-ea','-cp',os.pathsep.join(map(str,cp)),'guardtest.MixinTest',side])
# Verify all configured ore/rock pairs exist in the exact installed TFC resource registry.
with zipfile.ZipFile(REPO/'mods/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar') as z:
    files=set(z.namelist());data=json.loads((R/'src/main/resources/earth-defaults.json').read_text())
    ores={o for f in data['features'] if f['layer']=='deposit' for o in f['values']['ores']}
    rocks={'granite'}|{r for f in data['features'] if f['layer']=='geology' for r in f['values']['rocks']}
    graded={'hematite','magnetite','limonite','cassiterite','garnierite','native_copper','native_gold','native_silver','sphalerite'}
    for ore in ores:
        for rock in rocks:
            path=f'assets/tfc/blockstates/ore/{"normal_" if ore in graded else ""}{ore}/{rock}.json'
            assert path in files,path
print('PASS configured TFC ore/block IDs')
