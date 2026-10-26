#!/usr/bin/env python3
"""Boot a clean Forge world, then upgrade a world containing removed Spawn content."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
from build import ROOT,REPO,NAME,ORIGINAL
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path,required=True);a=p.parse_args()
logs=ROOT/'build/world-logs';logs.mkdir(parents=True,exist_ok=True)
for scenario in ['fresh','upgrade']:
    with tempfile.TemporaryDirectory(prefix='spawn-native-'+scenario+'-') as temp:
        root=Path(temp);(root/'libraries').symlink_to((a.runtime/'libraries').resolve(),target_is_directory=True);(root/'mods').mkdir()
        for pattern in ['geckolib-forge-*.jar','FarmersDelight-*.jar']:
            source,=list((REPO/'server/mods').glob(pattern));shutil.copyfile(source,root/'mods'/source.name)
        shutil.copyfile(ROOT/'build/empirecraft-spawn-smoketest.jar',root/'mods/empirecraft-spawn-smoketest.jar')
        (root/'eula.txt').write_text('eula=true\n')
        settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:stone',height=60),dict(block='minecraft:dirt',height=2),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        phases=[('fresh',False)] if scenario=='fresh' else [('baseline',True),('upgraded',False)]
        for phase,baseline in phases:
            source=ROOT/'build'/ORIGINAL if baseline else REPO/'server/mods'/NAME
            shutil.copyfile(source,root/'mods/spawn.jar')
            flags=['-Dempirecraft.spawn.baseline=true'] if baseline else ['-Dempirecraft.spawn.resume=true'] if phase=='upgraded' else []
            log=logs/(phase+'.log')
            with log.open('w') as out:result=subprocess.run(['java','-Xmx2G',*flags,'@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=240)
            contents=log.read_text();expected='SPAWN_SMOKE_BASELINE' if baseline else 'SPAWN_SMOKE_PASS'
            for line in contents.splitlines():
                if 'SPAWN_SMOKE_' in line:print(phase,line,flush=True)
            if result.returncode or expected not in contents or 'SPAWN_SMOKE_FAIL' in contents:
                print(contents[-18000:]);raise SystemExit('Forge Spawn test failed: '+str(log))
print('PASS actual Forge fresh-world and saved-world deletion tests')
