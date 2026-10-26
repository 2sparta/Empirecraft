#!/usr/bin/env python3
"""Run buoy and cannon regressions in disposable Forge/VS worlds, including an old-save migration."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
SIX='vs_sails-1.20.1-0.3.2-empirecraft.6-forge.jar'
FIVE='vs_sails-1.20.1-0.3.2-empirecraft.5-forge.jar'
NEW='empirecraft-ship-cannons-1.0.1.jar'
OLD='empirecraft-ship-cannons-1.0.0.jar'

def prepare(root,server,mods,fixture,water):
    root.mkdir(parents=True,exist_ok=True);(root/'libraries').symlink_to((server/'libraries').resolve(),target_is_directory=True)
    (root/'mods').mkdir()
    for pattern in ['architectury-9.2.14-forge.jar','kotlinforforge-4.12.0-all*.jar','valkyrienskies-120-2.4.11-empirecraft.1.jar','moonlight-1.20-2.16.37-forge.jar','supplementaries-1.20-3.1.44-forge.jar']:
        matches=list(mods.glob(pattern))
        if len(matches)!=1:raise ValueError('Expected one pinned dependency: '+pattern)
        shutil.copyfile(matches[0],root/'mods'/matches[0].name)
    shutil.copyfile(fixture,root/'mods'/fixture.name)
    (root/'eula.txt').write_text('eula=true\n')
    layers=[dict(block='minecraft:bedrock',height=1)]
    if water:layers += [dict(block='minecraft:stone',height=79),dict(block='minecraft:water',height=4)]
    settings=dict(biome='minecraft:plains',layers=layers,features=False,lakes=False,structure_overrides=[])
    (root/'server.properties').write_text('level-name=ship-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25591\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    pack=root/'ship-test/datapacks/test';(pack/'data/minecraft/dimension').mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps(dict(pack=dict(pack_format=15,description='Isolated ship regression fixture'))))
    (pack/'data/minecraft/dimension/world_europe.json').write_text(json.dumps(dict(type='minecraft:overworld',generator=dict(type='minecraft:flat',settings=settings))))

def versions(root,sails,cannons):
    for p in (root/'mods').glob('vs_sails-*.jar'):p.unlink()
    for p in (root/'mods').glob('empirecraft-ship-cannons-*.jar'):p.unlink()
    for p in [sails,cannons]:shutil.copyfile(p,root/'mods'/p.name)

def run(root,name,flags,success,logs):
    path=logs/(name+'.log')
    print('Running',name,flush=True)
    with path.open('w') as output:
        result=subprocess.run(['java','-Xmx2G',*flags,'@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
    text=path.read_text()
    if result.returncode or success not in text or '_SMOKE_FAIL' in text or 'Exception in thread "Physics thread"' in text:raise SystemExit('Failed '+name+': '+str(path))
    for line in text.splitlines():
        if line.startswith(('BUOY_TRACE','BUOY_SMOKE_','CANNON_EARLY','CANNON_LATE','CANNON_SMOKE_')):print(line,flush=True)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server-home',type=Path,required=True)
    p.add_argument('--mods-dir',type=Path,required=True,help='Pinned VS/Moonlight/Supplementaries dependencies and old Sails/cannon JARs for baselines.')
    p.add_argument('--buoy-new-only',action='store_true',help='Run the focused new-body regression after a Sails-only change.')
    a=p.parse_args();a.mods_dir=a.mods_dir.resolve()
    logs=ROOT/'build/smoke-logs';logs.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='empirecraft-ship-tests-') as temp:
        if a.buoy_new_only:
            fresh=Path(temp)/'buoys-new';prepare(fresh,a.server_home,a.mods_dir,REPO/'sails/build/empirecraft-buoy-smoketest.jar',True)
            versions(fresh,REPO/'mods'/SIX,REPO/'mods'/NEW)
            run(fresh,'buoy-new-final',[],'BUOY_SMOKE_PASS',logs)
            print('SHIP_SAFETY_FOCUSED_PASS',flush=True);return
        buoy=Path(temp)/'buoys';prepare(buoy,a.server_home,a.mods_dir,REPO/'sails/build/empirecraft-buoy-smoketest.jar',True)
        versions(buoy,a.mods_dir/FIVE,a.mods_dir/OLD)
        run(buoy,'buoy-baseline',['-Dempirecraft.buoy.baseline=true'],'BUOY_SMOKE_BASELINE',logs)
        versions(buoy,REPO/'mods'/SIX,REPO/'mods'/NEW)
        run(buoy,'buoy-old-save',['-Dempirecraft.buoy.resume=true'],'BUOY_SMOKE_PASS',logs)
        fresh=Path(temp)/'buoys-new';prepare(fresh,a.server_home,a.mods_dir,REPO/'sails/build/empirecraft-buoy-smoketest.jar',True)
        versions(fresh,REPO/'mods'/SIX,REPO/'mods'/NEW)
        run(fresh,'buoy-new',[],'BUOY_SMOKE_PASS',logs)
        for baseline in [True,False]:
            cannon=Path(temp)/('cannon-baseline' if baseline else 'cannon-patched')
            prepare(cannon,a.server_home,a.mods_dir,ROOT/'build/empirecraft-cannon-test.jar',False)
            versions(cannon,REPO/'mods'/SIX,a.mods_dir/OLD if baseline else REPO/'mods'/NEW)
            run(cannon,'cannon-baseline' if baseline else 'cannon-patched',['-Dempirecraft.cannon.baseline=true'] if baseline else [],'CANNON_SMOKE_BASELINE' if baseline else 'CANNON_SMOKE_PASS',logs)
    print('SHIP_SAFETY_SMOKE_PASS',flush=True)
if __name__=='__main__':main()
