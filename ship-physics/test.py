#!/usr/bin/env python3
"""Run bounded spread and pinned VS contact compatibility regressions after build.py."""
import os, shlex, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent
BUILD=ROOT/'build'
def main():
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    flags=shlex.split((BUILD/'javac.args').read_text())
    cp=os.pathsep.join([str(BUILD/'classes'),flags[flags.index('-cp')+1]])
    classes=BUILD/'tests';classes.mkdir(exist_ok=True)
    (ROOT.parent/'work').mkdir(exist_ok=True)
    sources=[ROOT/'tests/ImpactSpreadTest.java',ROOT/'tests/ContactCompatibilityTest.java',ROOT/'tests/MassBoundsTest.java']
    subprocess.run([str(java),'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
    for name in ['ImpactSpreadTest','ContactCompatibilityTest']:
        subprocess.run([str(java),'-ea','-cp',os.pathsep.join([str(classes),cp]),name],check=True,cwd=ROOT.parent/'work')
    # This test needs the patched production class rather than the unmodified named input.
    production=ROOT.parent/'mods/empirecraft-ship-physics-1.2.2.jar'
    subprocess.run([str(java),'-ea','-cp',os.pathsep.join([str(classes),str(production),cp]),'MassBoundsTest'],check=True,cwd=ROOT.parent/'work')
if __name__=='__main__':main()
