#!/usr/bin/env python3
"""Test the bounded placement algorithm without a Minecraft runtime."""
from pathlib import Path
import shutil
import subprocess
import sys
ROOT=Path(__file__).resolve().parent
out=ROOT/'build/pure-tests';out.mkdir(parents=True,exist_ok=True)
compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
subprocess.run([*compiler,'--release','17','-d',str(out),str(ROOT/'src/main/java/ua/empirecraft/ores/OrePass.java'),str(ROOT/'tests/OrePassTest.java')],check=True)
subprocess.run(['java','-ea','-cp',str(out),'ua.empirecraft.ores.OrePassTest'],check=True)

subprocess.run([sys.executable,str(ROOT/'tests/test_adm.py')],check=True)
