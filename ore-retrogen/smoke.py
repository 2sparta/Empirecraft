#!/usr/bin/env python3
"""Launch three isolated Forge lifetimes to check old chunks and restart persistence."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import build


def prepare(root,server,create,sulfur):
    (root/'libraries').symlink_to((server/'libraries').resolve(),target_is_directory=True)
    (root/'mods').mkdir()
    for path in [create,sulfur,build.REPO/'server/mods'/build.NAME,build.ROOT/'build/empirecraft-ores-test.jar']:
        shutil.copyfile(path,root/'mods'/path.name)
    (root/'eula.txt').write_text('eula=true\n')
    settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:stone',height=95),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
    (root/'server.properties').write_text('level-name=ore-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    pack=root/'ore-test/datapacks/test';(pack/'data/minecraft/dimension').mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps(dict(pack=dict(pack_format=15,description='Isolated ore-retrogen fixture'))))
    (pack/'data/minecraft/dimension/world_europe.json').write_text(json.dumps(dict(type='minecraft:overworld',generator=dict(type='minecraft:flat',settings=settings))))
    (root/'config').mkdir()
    cfg=(build.REPO/'server/config/empirecraft_ore_retrogen-common.toml').read_text().replace('["minecraft:overworld", "minecraft:world_europe"]','["minecraft:world_europe"]')
    (root/'config/empirecraft_ore_retrogen-common.toml').write_text(cfg.replace('enabled = true','enabled = false'))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server-home',type=Path,required=True,help='Installed Forge 47.4.23 libraries (originals are never changed).')
    p.add_argument('--toolchain',type=Path,default=build.REPO/'sails/build')
    p.add_argument('--create',type=Path,default=build.REPO/'mods/create-1.20.1-6.0.8-medieval.2.jar')
    p.add_argument('--sulfur',type=Path,default=build.REPO/'mods/sulfur_gunpowder-1.0.0-forge-1.20.1.jar')
    a=p.parse_args()
    subprocess.run([sys.executable,build.ROOT/'build.py','--fixture','--toolchain',a.toolchain,'--server-home',a.server_home],check=True)
    logs=build.ROOT/'build/smoke-logs';logs.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='empirecraft-ore-test-') as folder:
        root=Path(folder);prepare(root,a.server_home,a.create,a.sulfur)
        for phase in ['baseline','retro','restart']:
            if phase=='retro':
                path=root/'config/empirecraft_ore_retrogen-common.toml';path.write_text(path.read_text().replace('enabled = false','enabled = true'))
            output=logs/(phase+'.log')
            with output.open('w') as log:
                result=subprocess.run(['java','-Xmx2G','-Dempirecraft.ores.phase='+phase,'@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,stdout=log,stderr=subprocess.STDOUT,timeout=180)
            text=output.read_text()
            if result.returncode or 'ORE_SMOKE_PASS phase='+phase not in text or 'ORE_SMOKE_FAIL' in text:
                raise SystemExit('Forge smoke failed: '+str(output))
            print(next(line for line in text.splitlines() if 'ORE_SMOKE_PASS' in line))
    print('Three Forge lifetimes passed; logs:',logs)
if __name__=='__main__':main()
