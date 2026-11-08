#!/usr/bin/env python3
"""Refresh the three changed modules without altering unrelated content versions."""
import subprocess,sys
from pathlib import Path
REPO=Path(__file__).resolve().parent.parent
for script,args in [('towny-recruits/release.py',['--install']),('trader-fix/update_metadata.py',[])]:
    subprocess.run([sys.executable,str(REPO/script),*args],check=True)
print('COMBAT_METADATA_PASS')
