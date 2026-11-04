#!/usr/bin/env python3
"""Run native Towny routing and H2-backed trade currency regressions after feudal/build.py."""
import os, subprocess
from pathlib import Path

repo=Path(__file__).resolve().parents[1]
classpath=[repo/'feudal/build/classes',repo/'feudal/build/test-classes',*sorted((repo/'feudal/lib').glob('*.jar')), *sorted((repo/'server/libraries').rglob('*.jar'))]
assert (classpath[1]/'ua/empirecraft/feudal/TradeTownyTests.class').is_file(), 'Run feudal/build.py first'
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
subprocess.run([java,'-cp',os.pathsep.join(map(str,classpath)),'ua.empirecraft.feudal.TradeTownyTests'],check=True)
