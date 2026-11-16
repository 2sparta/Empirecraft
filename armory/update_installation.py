#!/usr/bin/env python3
"""Install the paired workshop addon, preserving unrelated local Overgeared settings."""
import re,tomllib,zipfile
from pathlib import Path
import install_core as core
BASE_PLAN=core.plan
SECTIONS=['Default (No Blueprint)','Poorly Forged','Well Forged','Expertly Forged','Perfectly Forged','Masterwork']
VALUES=dict(zoneStartingSize='65',zoneShrinkFactor='1.0',minPerfectZone='50',arrowSpeed='1.0',arrowSpeedIncrease='0.0',maxArrowSpeed='1.0')
def simplify(text):
 pattern=r'(\["?(?:Default \(No Blueprint\)|Poorly Forged|Well Forged|Expertly Forged|Perfectly Forged|Masterwork)"?\][\s\S]*?)(?=\n\[|\Z)'
 def change(match):
  result=match[0]
  for key,value in VALUES.items():
   result,n=re.subn(r'(?m)^(\s*'+key+r'\s*=\s*).+$',lambda m:m[1]+value,result)
   if n!=1:raise ValueError('Missing/duplicate minigame setting '+key)
  return result
 result,n=re.subn(pattern,change,text)
 if n!=6:raise ValueError('Expected six Overgeared minigame sections; local config requires review')
 data=tomllib.loads(result)
 for section in SECTIONS:
  for key,value in VALUES.items():assert data[section][key]==float(value)
 return result
def plan(root,side,bundle,release):
 root=Path(root).resolve();required={'overgeared','empirecraft_forging','tfc'};found=set()
 for path in (root/'mods').glob('*.jar'):
  with zipfile.ZipFile(path) as jar:
   if 'META-INF/mods.toml' in jar.namelist():found.update(m['modId'] for m in tomllib.loads(jar.read('META-INF/mods.toml').decode())['mods'])
 if required-found:raise ValueError('Install on the current Empirecraft pack first; missing '+', '.join(sorted(required-found)))
 root,writes,retire=BASE_PLAN(root,side,bundle,release)
 config=root/'config/overgeared-common.toml'
 if config.exists():writes[config]=simplify(config.read_text(encoding='utf-8-sig')).encode()
 return root,writes,retire
core.plan=plan
if __name__=='__main__':core.main()
