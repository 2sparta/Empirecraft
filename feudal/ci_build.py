#!/usr/bin/env python3
"""Build against the checked-in Mohist/Towny runtime with JDK 21, targeting Java 17."""
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
towny = repo / 'server/plugins/Towny-0.101.2.0.jar'
api = repo / 'server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar'
assert towny.is_file() and api.is_file(), 'Include pinned Towny and server/libraries in the CI checkout'
(repo / 'feudal/lib').mkdir(exist_ok=True)
shutil.copyfile(towny, repo / 'feudal/lib' / towny.name)
subprocess.run([sys.executable, str(repo / 'feudal/build.py'), '--server-api', str(api),
                '--api-libraries', str(repo / 'server/libraries'), *sys.argv[1:]], check=True)
