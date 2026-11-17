import hashlib,json,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
checks=0
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp);bundle=t/'bundle'
 with zipfile.ZipFile(ROOT/'dist/Empirecraft-Copycats-Calendar-Climate-1-install.zip') as z:z.extractall(bundle)
 r=json.loads((bundle/'release.json').read_text())
 for case in ['missing-adm','existing-adm','client']:
  game=t/case;(game/'mods').mkdir(parents=True);(game/'world').mkdir();(game/'world/level.dat').write_bytes(b'WORLD')
  for name in ['chippedexttras-1.4-empirecraft.2.jar','empirecraft-calendar-1.2.3.jar','empirecraft-tfc-integration-1.0.3.jar']:(game/'mods'/name).write_bytes((REPO/'disabled-mods/replaced-2026-11-01/server/mods'/name).read_bytes())
  p=game/'config/sereneseasons/seasons.toml';p.parent.mkdir(parents=True);p.write_text('[dimension_settings]\nwhitelisted_dimensions = ["minecraft:custom_world"]\n[time_settings]\nday_duration = 24000\nsub_season_duration = 8\n[weather_settings]\nchange_weather_frequency = false\n')
  (game/'config/sereneseasons/fertility.toml').write_bytes(b'# preserve custom crop settings\n')
  if case=='existing-adm':
   p=game/'config/admanticheat/idfile/admanticheat_id_list.json';p.parent.mkdir(parents=True);p.write_bytes((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes())
  args=[sys.executable,str(bundle/'update_installation.py'),('--client' if case=='client' else '--server'),str(game)]
  subprocess.run(args,capture_output=True,check=True)
  assert not list((game/'mods').glob('chippedexttras-*'));checks+=1
  assert not (game/'mods/empirecraft-calendar-1.2.3.jar').exists();checks+=1
  assert (game/'mods'/r['file']).read_bytes()==(bundle/('client' if case=='client' else 'server')/'mods'/r['file']).read_bytes();checks+=1
  config=(game/'config/sereneseasons/seasons.toml').read_text();assert '57600' in config and 'sub_season_duration = 30' in config;checks+=1
  assert (game/'config/sereneseasons/fertility.toml').read_bytes()==b'# preserve custom crop settings\n';checks+=1
  assert 'custom_world' in config and 'change_weather_frequency = false' in config;checks+=1
  assert (game/'config/admanticheat/idfile/admanticheat_id_list.json').exists()==(case!='client');checks+=1
  assert not (game/'mods/empirecraft-tfc-integration-1.0.3.jar').exists();checks+=1
  assert (game/'mods/empirecraft-tfc-integration-1.0.4.jar').read_bytes()==(REPO/'mods/empirecraft-tfc-integration-1.0.4.jar').read_bytes();checks+=1
  before={str(p.relative_to(game)):p.read_bytes() for p in game.rglob('*') if p.is_file()};subprocess.run(args,capture_output=True,check=True);after={str(p.relative_to(game)):p.read_bytes() for p in game.rglob('*') if p.is_file()};assert before==after;checks+=1
  assert (game/'world/level.dat').read_bytes()==b'WORLD';checks+=1
 (ROOT/'audit/installer.json').write_text(json.dumps(dict(status='passed',checks=checks),indent=2)+'\n');print('TRANSITION_INSTALLER_PASS',checks)
