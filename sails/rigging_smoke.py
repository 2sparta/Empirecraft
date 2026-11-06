#!/usr/bin/env python3
"""Run the destructive rig fixture only in a disposable Forge/VS/Create/Copycats world."""
import argparse,json,subprocess,tempfile,shutil
from pathlib import Path
import build
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--runtime',type=Path,required=True)
p.add_argument('--mods-dir',type=Path,default=build.REPO/'server/mods')
a=p.parse_args()
logs=build.ROOT/'build/rigging-world-logs';logs.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='empirecraft-rigging-') as temp:
 root=Path(temp);(root/'libraries').symlink_to((a.runtime/'libraries').resolve(),target_is_directory=True);(root/'mods').mkdir()
 for pattern in ['architectury-9.2.14-forge.jar','kotlinforforge-4.12.0-all*.jar','valkyrienskies-120-2.4.11-empirecraft.1.jar','create-1.20.1-6.0.8-medieval.4.jar','copycats-3.0.10*.jar','empirecraft-ship-physics-1.2.2.jar','empirecraft-no-end-1.0.0.jar']:
  source,=list(a.mods_dir.glob(pattern));shutil.copyfile(source,root/'mods'/source.name)
 for source in [build.ROOT/'build/empirecraft-rigging-smoketest.jar',build.REPO/'server/mods'/('vs_sails-1.20.1-'+build.VERSION+'-forge.jar')]:shutil.copyfile(source,root/'mods'/source.name)
 (root/'eula.txt').write_text('eula=true\n')
 settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=2),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
 (root/'server.properties').write_text('level-name=rigging-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
 (root/'config/vs_sails').mkdir(parents=True);(root/'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nmagic-ballast-righting-force=0\n')
 log=logs/'forge-copycats.log'
 with log.open('w') as output:
  result=subprocess.run(['java','-Xmx1G','@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
 print('Forge process exit:',result.returncode)
 for extra in (root/'logs').glob('*.log'):
  shutil.copyfile(extra,logs/extra.name)
 contents=log.read_text()
 debug=logs/'debug.log'
 if debug.exists():contents+='\n'+debug.read_text()
 for line in contents.splitlines():
  if 'RIGGING_SMOKE_' in line:print(line)
 if result.returncode or 'RIGGING_SMOKE_PASS' not in contents or 'RIGGING_SMOKE_FAIL' in contents or 'Exception in thread "Physics thread"' in contents:
  print(contents[-12000:]);raise SystemExit('Rigging world failed: '+str(log))
print('RIGGING_NATIVE_WORLD_PASS')
