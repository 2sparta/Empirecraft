#!/usr/bin/env python3
"""Run actual Towny event regressions on Java 21, as required by its scheduler API."""
from pathlib import Path
import os
import subprocess

repo=Path(__file__).resolve().parents[1]
assert (repo/'feudal/build/test-classes/ua/empirecraft/feudal/ProvisionClaimTests.class').exists(), 'Run python3 feudal/build.py first'
classpath=os.pathsep.join(map(str,[repo/'feudal/build/classes',repo/'feudal/build/test-classes',*sorted((repo/'feudal/lib').glob('*.jar'))]))
java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else 'java'
subprocess.run([str(java),'-cp',classpath,'ua.empirecraft.feudal.ProvisionClaimTests'],check=True)
