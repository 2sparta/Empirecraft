#!/usr/bin/env python3
"""Append provision defaults only when missing; preserve the server's existing settings."""
import argparse
from pathlib import Path
import re
import shutil

DEFAULTS='''
# Провізія: 8 голоду / Minecraft-чанк / реальну добу; весна 1, літо 0.5, осінь 0.8, зима 1.5.
provisions:
  loss-after-hours: 48
  loss-every-hours: 12
  loss-fraction: 0.10
  collapse-after-hours: 96
'''

def update(path):
    path=Path(path)
    if path.exists():
        old=path.read_text(encoding='utf-8-sig')
        if re.search(r'^provisions\s*:',old,re.MULTILINE):return False
        backup=path.with_name(path.name+'.before-provisions')
        if not backup.exists():shutil.copy2(path,backup)
        result=old.rstrip()+'\n'+DEFAULTS
    else:
        # The JAR supplies all other defaults when this is a fresh installation.
        result=DEFAULTS.lstrip()
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+'.provisions.tmp');temp.write_text(result,encoding='utf-8');temp.replace(path)
    return True

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True,type=Path);args=parser.parse_args()
    print('Added provision defaults.' if update(args.config) else 'Existing provision settings preserved.')
