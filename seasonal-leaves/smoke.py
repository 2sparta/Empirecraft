#!/usr/bin/env python3
"""Two actual Forge launches in a disposable world. Never uses a live server world."""
import argparse, os, shutil, subprocess, tempfile
from pathlib import Path
import build
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--runtime',type=Path,required=True,help='Installed Forge 47.4.23 directory (libraries only are reused)')
p.add_argument('--vegetation-only',action='store_true')
a=p.parse_args()
runtime=a.runtime.resolve()
with tempfile.TemporaryDirectory(prefix='empirecraft-leaves-') as tmp:
    root=Path(tmp);(root/'libraries').symlink_to(runtime/'libraries',target_is_directory=True)
    (root/'mods').mkdir();(root/'config').mkdir()
    for name in ['SereneSeasons.jar','GlitchCore.jar']:
        shutil.copyfile(build.ROOT/'build'/name,root/'mods'/name)
    for source in [build.REPO/'server/mods/empirecraft-seasonal-leaves-1.1.0.jar',build.ROOT/'build/empirecraft-leaves-test.jar']:
        shutil.copyfile(source,root/'mods'/source.name)
    (root/'eula.txt').write_text('eula=true\n')
    (root/'server.properties').write_text('level-name=leaves-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25591\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nsync-chunk-writes=true\n')
    (root/'config/empirecraft_seasonal_leaves-common.toml').write_text('enabled=true\nincludePersistentLeaves=false\npositionsPerLevelTick=65536\nleafAttemptsPerLevelTick=128\nrescanDelayTicks=20\nmaxSavedLeavesPerChunk=32768\n')
    phases=[('winter',False),('spring',False)] if a.vegetation_only else [('winter',False),('spring',False),('winter',True),('spring',True)]
    if not a.vegetation_only:
        with (root/'config/empirecraft_seasonal_leaves-common.toml').open('a') as f:f.write('[vegetation]\nenabled=false\n')
    if a.vegetation_only:
        with (root/'config/empirecraft_seasonal_leaves-common.toml').open('a') as f:f.write('[vegetation]\nrequireWilderness=false\nregrowGroundCover=false\npositionsPerLevelTick=65536\nplantsPerLevelTick=128\nrescanDelayTicks=20\nmaxGroundCoverPerChunk=8\nrareCooldownDays=0\n')
    for phase,persistent in phases:
        config=root/'config/empirecraft_seasonal_leaves-common.toml'
        value=config.read_text().replace('includePersistentLeaves = false','includePersistentLeaves = '+str(persistent).lower()).replace('includePersistentLeaves=false','includePersistentLeaves='+str(persistent).lower())
        config.write_text(value)
        log=build.ROOT/'build'/f'smoke-{"vegetation-" if a.vegetation_only else ""}{phase}-{str(persistent).lower()}.log'
        command=['java','-XX:ActiveProcessorCount=2','-Xms256M','-Xmx1400M',f'-Dleaves.spring={str(phase=="spring").lower()}',f'-Dleaves.persistent={str(persistent).lower()}',f'-Dvegetation.test={str(a.vegetation_only).lower()}','@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui']
        with log.open('w') as out:
            result=subprocess.run(command,cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text()
        marker=f'VEGETATION_SMOKE_PASS phase={"summer" if phase=="spring" else "winter"}' if a.vegetation_only else f'LEAVES_SMOKE_PASS phase={phase}'
        if result.returncode or marker not in text or 'LEAVES_SMOKE_FAIL' in text or 'VEGETATION_SMOKE_FAIL' in text or (a.vegetation_only and phase=='spring' and 'VEGETATION_GROWTH_PASS' not in text):
            raise SystemExit('FAIL: '+str(log)+'\n'+text[-8000:])
        print('PASS',phase,'persistent='+str(persistent))
