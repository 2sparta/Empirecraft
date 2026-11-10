#!/usr/bin/env python3
import os,shlex,subprocess
from pathlib import Path
import build
ROOT=build.ROOT;out=build.build.BUILD
sdk=build.build.SDK
java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac')
flags=shlex.split((sdk/'javac.args').read_text());cp=os.pathsep.join(map(str,[out/'mod-classes',*flags[flags.index('-cp')+1].split(os.pathsep)]))
classes=out/'tests';classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(javac),'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tests/EquineRulesTest.java')],check=True)
subprocess.run([str(java),'-ea','-cp',str(classes)+os.pathsep+cp,'ua.empirecraft.equine.EquineRulesTest'],check=True)
