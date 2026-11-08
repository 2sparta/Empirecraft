#!/usr/bin/env python3
"""Compare old/new real scanning code with 14k queued work entries and test datapack cache reloads."""
import subprocess,sys
from pathlib import Path
if __name__=='__main__':
    subprocess.run([sys.executable,str(Path(__file__).with_name('growth_smoke.py')),'--performance',*sys.argv[1:]],check=True)
