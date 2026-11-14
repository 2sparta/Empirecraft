#!/usr/bin/env python3
"""Compatibility entrypoint for the current fleet/client build; outputs the ready Bridge JAR."""
from pathlib import Path
import shutil
import subprocess
import sys

def main():
    repo=Path(__file__).resolve().parent.parent
    subprocess.run([sys.executable,str(repo/'menu-update/build.py'),*sys.argv[1:]],check=True)
    source=repo/'menu-update/dist/empirecraft-towny-journeymap-1.11.1.jar'
    target=repo/'bridge/build/libs'/source.name
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source,target)
    print(target)

if __name__=='__main__':main()
