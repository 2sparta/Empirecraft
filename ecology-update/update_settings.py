#!/usr/bin/env python3
"""Add missing new defaults without overwriting existing server tuning."""
import argparse,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def apply(folder):
    path=folder/'empirecraft-fauna.json'
    defaults=ROOT/'fauna-defaults.json'
    if not defaults.exists():defaults=ROOT.parent/'fauna/build/defaults/empirecraft-fauna.json'
    if path.exists():
        settings=json.loads(path.read_text())
        for key,value in json.loads(defaults.read_text()).items():settings.setdefault(key,value)
        path.write_text(json.dumps(settings,ensure_ascii=False,indent=2)+'\n')
    path=folder/'vs_sails/config.cfg'
    if path.exists():
        text=path.read_text();keys={line.split('=',1)[0].strip() for line in text.splitlines() if '=' in line and not line.lstrip().startswith('#')}
        for key,value in [('puddle-drying','true'),('puddle-dry-delay-seconds','60'),('puddle-dry-interval-seconds','10')]:
            if key not in keys:text+='\n'+key+' = '+value+'\n'
        path.write_text(text)
def main():
    p=argparse.ArgumentParser();p.add_argument('config',type=Path);a=p.parse_args();apply(a.config)
    print('Missing gameplay defaults added; existing settings preserved.')
if __name__=='__main__':main()
