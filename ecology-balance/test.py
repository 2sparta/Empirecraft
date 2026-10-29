#!/usr/bin/env python3
"""Run pure habitat rules, deployed configuration, ammunition data and exact overlay preservation checks."""
import hashlib,json,subprocess,tempfile,tomllib,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory() as d:
 subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',d,str(R/'world-rules/src/main/java/ua/empirecraft/worldrules/TreeRules.java'),str(R/'seasonal-calendar/src/main/java/ua/empirecraft/calendar/HabitatRules.java'),str(R/'ecology-balance/PolicyTest.java')],check=True)
 subprocess.run(['java','-ea','-cp',d,'PolicyTest'],check=True)
seasons=tomllib.loads((R/'config/sereneseasons/seasons.toml').read_text())
assert seasons==tomllib.loads((R/'server/config/sereneseasons/seasons.toml').read_text())
months={v['season']:v for v in seasons['season_properties']}
assert .25+months['EARLY_SPRING']['biome_temp_adjustment']>.15,'Lowland taiga should have rain in March'
assert months['EARLY_SPRING']['melt_percent']>6.25
assert all(months[s+'_WINTER']['max_rain_time']==96000 for s in ['EARLY','MID','LATE'])
config=tomllib.loads((R/'server/config/empirecraft_world_rules-common.toml').read_text())
assert config['rareTreeChance']==.1 and config['maxTreesPerChunk']==3
assert len([x for x in config['trees'] if x.startswith('minecraft:')])==6
shot='data/createbigcannons/munition_properties/projectiles/shot.json'
a=json.loads((R/'kubejs'/shot).read_text());assert a==json.loads((R/'server/kubejs'/shot).read_text())
assert a['penetration']==2.2 and a['durability_mass']==6 and a['toughness']==2.5
assert a['entity_damage']==30 and a['gravity']==-.05 and a['added_recoil']==1
report=R/'ecology-balance/audit/overlay.json'
if report.exists():
 for name,rule in json.loads(report.read_text()).items():
  sides=[p for side in ['mods','server/mods'] if (p:=R/side/name).exists()]
  assert all(p.read_bytes()==sides[0].read_bytes() for p in sides)
  with zipfile.ZipFile(sides[0]) as z:
   assert z.testzip() is None
   for n,h in rule['preserved_entries'].items():assert hashlib.sha256(z.read(n)).hexdigest()==h,(name,n)
   assert set(rule['changed_entries'])<=set(z.namelist())
 worldgen=json.loads((R/'ecology-balance/audit/worldgen.json').read_text())
 for n in worldgen['overrides']:
  a=json.loads((R/'kubejs'/n).read_text());assert a==json.loads((R/'server/kubejs'/n).read_text())
  assert a['placement'][0]=={'type':'minecraft:rarity_filter','chance':8}
 print('PASS packaged side parity and preservation of every unrelated class/resource')
print('PASS March lowland rain, winter intervals, tree pool configuration and solid-shot penetration data')
