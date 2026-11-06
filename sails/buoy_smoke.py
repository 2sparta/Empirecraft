#!/usr/bin/env python3
"""Run the actual buoy-world fixture in an isolated standard Forge installation."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import build

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--runtime',type=Path,required=True)
p.add_argument('--baseline',type=Path)
p.add_argument('--launch-args',type=Path,help='Use an already verified local Mohist launch argument file')
a=p.parse_args()
logs=build.ROOT/'build/buoy-world-logs';logs.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='empirecraft-buoy-water-') as temp:
    root=Path(temp);(root/'libraries').symlink_to((a.runtime/'libraries').resolve(),target_is_directory=True)
    if a.launch_args:(root/'mohist-config').mkdir();(root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
    (root/'mods').mkdir()
    for pattern in ['architectury-9.2.14-forge.jar','kotlinforforge-4.12.0-all*.jar','valkyrienskies-120-2.4.11-empirecraft.1.jar','empirecraft-no-end-1.0.0.jar']:
        source,=list((build.REPO/'server/mods').glob(pattern));shutil.copyfile(source,root/'mods'/source.name)
    for source in [build.ROOT/'build/empirecraft-buoy-smoketest.jar',build.REPO/'server/mods'/('vs_sails-1.20.1-'+build.VERSION+'-forge.jar')]:
        shutil.copyfile(source,root/'mods'/source.name)
    (root/'eula.txt').write_text('eula=true\n')
    settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:stone',height=79),dict(block='minecraft:water',height=4)],features=False,lakes=False,structure_overrides=[])
    (root/'server.properties').write_text('level-name=buoy-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    pack=root/'buoy-test/datapacks/test';(pack/'data/minecraft/dimension').mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps(dict(pack=dict(pack_format=15,description='Isolated buoy water test'))))
    (pack/'data/minecraft/dimension/world_europe.json').write_text(json.dumps(dict(type='minecraft:overworld',generator=dict(type='minecraft:flat',settings=settings))))
    (root/'config/vs_sails').mkdir(parents=True)
    (root/'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nmagic-ballast-righting-force=0\n')
    name='vs_sails-1.20.1-'+build.VERSION+'-forge.jar'
    phases=[('fresh',[])] if not a.baseline else [('baseline',['-Dempirecraft.buoy.baseline=true']),('upgraded',['-Dempirecraft.buoy.resume=true'])]
    for phase,flags in phases:
        target=root/'mods'/name
        source=a.baseline if phase=='baseline' else build.REPO/'server/mods'/name
        shutil.copyfile(source,target)
        log=logs/(phase+'.log')
        with log.open('w') as output:
            launch=['@'+str(a.launch_args.resolve())] if a.launch_args else ['@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui']
            result=subprocess.run(['java','-Xmx2G',*flags,*launch],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        contents=log.read_text()
        for line in contents.splitlines():
            if line.startswith(('BUOY_TRACE','BUOY_SMOKE_')):print(phase,line,flush=True)
        expected='BUOY_SMOKE_BASELINE' if phase=='baseline' else 'BUOY_SMOKE_PASS'
        if result.returncode or expected not in contents or 'BUOY_SMOKE_FAIL' in contents or 'Exception in thread "Physics thread"' in contents:
            print(contents[-14000:]);raise SystemExit('Native Forge buoy test failed: '+str(log))
print('BUOY_NATIVE_WORLD_PASS')
