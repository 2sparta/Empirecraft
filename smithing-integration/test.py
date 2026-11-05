#!/usr/bin/env python3
from pathlib import Path
import argparse,json,subprocess,tempfile,tomllib,zipfile
from audit import extract,ROOT,REPO
from runtime_test import run as run_rhino

def main():
 p=argparse.ArgumentParser();p.add_argument('--jars',type=Path,default=REPO/'mods');args=p.parse_args()
 data=extract(args.jars);(ROOT/'build').mkdir(exist_ok=True)
 # CI can enumerate the poster addon before the core create-*.jar provider.
 with tempfile.TemporaryDirectory(prefix='provider-order-',dir=ROOT/'build') as directory:
  folder=Path(directory)
  for source in sorted(args.jars.glob('*.jar'),key=lambda p:(not p.name.startswith('create-drafting-'),p.name)):
   (folder/source.name).symlink_to(source.resolve())
  assert extract(folder)==data,'Provider selection depends on addon/filesystem order'
 assert 'create' in data['versions'],'The core Create provider must be audited'
 (ROOT/'build/fixtures.json').write_text(json.dumps(data))
 subprocess.run(['node',str(ROOT/'tests/integration.cjs')],check=True)
 runtime=run_rhino(args.jars)
 server=tomllib.loads((REPO/'server/config/overgeared-common.toml').read_text())
 client=tomllib.loads((REPO/'config/overgeared-common.toml').read_text())
 assert server==client
 assert {'armor','shield'} <= set(server['Blueprint & Tool Types']['availableToolTypes'])
 blocks=list((REPO/'server/kubejs/data/overgeared/loot_modifiers').glob('*.json'))
 assert len(blocks)==14
 for path in blocks:
  value=json.loads(path.read_text());assert value['item']=='overgeared:steel_ingot'
  assert {'condition':'minecraft:random_chance','chance':0.0} in value['conditions']
 # No changes to native mod binaries; no ADM hash changes are required.
 result=json.loads((ROOT/'build/results.json').read_text())
 result.update(versions=data['versions'],jar_sha256=data['jar_sha256'],disabled_steel_loot_modifiers=len(blocks),bad_upstream_json=data['bad_upstream_json'],integration_version='1.0.1',runtime=runtime,scope='Installed JAR JSON fixtures, real Rhino/Fastutil and independent Node event contracts; no full Mohist/client launch')
 (ROOT/'audit/compatibility.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 print('PASS client/server blueprint configuration, native loot overrides and exact-JAR audit')
if __name__=='__main__':main()
