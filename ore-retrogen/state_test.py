#!/usr/bin/env python3
"""Run capability persistence checks against the real named Forge/Minecraft classes."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import build

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--toolchain',type=Path,default=build.ROOT/'build/toolchain')
p.add_argument('--server-home',type=Path,default=build.REPO/'server')
a = p.parse_args()
deps = build.toolchain(a.toolchain.resolve(),a.server_home.resolve())
out = build.ROOT/'build/state-tests'; out.mkdir(parents=True,exist_ok=True)
cp = os.pathsep.join([str(build.ROOT/'build/named.jar'),*deps])
compiler = [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
subprocess.run([*compiler,'--release','17','-proc:none','-cp',cp,'-d',str(out),str(build.ROOT/'tests/ChunkStateTest.java')],check=True)
subprocess.run(['java','-DLog4jContextSelector=org.apache.logging.log4j.core.selector.ClassLoaderContextSelector','-ea','-cp',os.pathsep.join([str(out),cp]),'ua.empirecraft.ores.ChunkStateTest',str(out/'chunk-state.nbt')],check=True)
