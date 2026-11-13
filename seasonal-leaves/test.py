#!/usr/bin/env python3
import shutil, subprocess, json
from pathlib import Path
root=Path(__file__).resolve().parent
out=root.parent/'work/seasonal-leaves/unit';out.mkdir(parents=True,exist_ok=True)
compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
subprocess.run([*compiler,'--release','17','-d',str(out),str(root/'src/main/java/ua/empirecraft/leaves/SeasonCurve.java'),str(root/'tests/SeasonCurveTest.java'),str(root/'src/main/java/ua/empirecraft/leaves/VegetationPolicy.java'),str(root/'tests/VegetationPolicyTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'SeasonCurveTest'],check=True)

subprocess.run(['java','-cp',str(out),'VegetationPolicyTest'],check=True)

profiles=json.loads((root/'src/main/resources/vegetation-profiles.json').read_text())['renewal_plants']
ids=[p['id'] for p in profiles]
assert len(ids)==33 and len(set(ids))==33
assert {'nirvana:wild_hemp','farmersrespite:wild_coffee_bush','rusticdelight:wild_coffee'}<=set(ids)
for p in profiles: assert p['seasons']==['spring','summer','autumn'],p['id']
print('PROFILE_DATA_PASS 33 distinct native renewal species and all rare IDs')

subprocess.run([*compiler,'--release','17','-d',str(out),str(root/'src/main/java/ua/empirecraft/leaves/ScanBudget.java'),str(root/'tests/ScanBudgetTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'ua.empirecraft.leaves.ScanBudgetTest'],check=True)

subprocess.run([*compiler,'--release','17','-cp',str(out),'-d',str(out),str(root/'src/main/java/ua/empirecraft/leaves/WeatherPolicy.java'),str(root/'src/main/java/ua/empirecraft/leaves/SeasonVisualPolicy.java'),str(root/'tests/SeasonEffectsTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'SeasonEffectsTest'],check=True)
