#!/usr/bin/env python3
"""The same pinned build, real-world migration and packaging gate locally and in CI."""
import argparse,subprocess,sys
from pathlib import Path
from build import ROOT,REPO
def run(*command):subprocess.run(list(map(str,command)),cwd=REPO,check=True)
def main():
 p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);a=p.parse_args()
 runtime=ROOT/'build/runtime';toolchain=ROOT/'build/toolchain'
 run(sys.executable,'seasonal-leaves/prepare_runtime.py',runtime)
 run(sys.executable,'seasonal-leaves/toolchain.py',toolchain,runtime)
 run(sys.executable,'content-trim/build.py','--upstream',a.upstream.resolve(),'--classpath-dir',toolchain,'--classpath-dir',runtime/'libraries')
 run(sys.executable,'content-trim/update_metadata.py','--check')
 run(sys.executable,'content-trim/test.py')
 run(sys.executable,'content-trim/smoke.py','--upstream',a.upstream.resolve(),'--runtime',runtime,'--toolchain',toolchain)
 run(sys.executable,'content-trim/package.py')
if __name__=='__main__':main()
