#!/usr/bin/env python3
"""Prepare only the named compilation inputs; never rebuild unrelated ship mods."""
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent

def prepare(out, server):
    out.mkdir(parents=True, exist_ok=True)
    pins = json.loads((REPO/'sails/build-inputs.json').read_text())['downloads']
    libraries = server/'libraries'
    def need(name):
        paths = list(libraries.rglob(name))
        if len(paths) != 1: raise SystemExit('Expected one runtime input: '+name)
        return paths[0]
    def fetch(name, local=None):
        path = out/name
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != pins[name]['sha256']:
            if local: shutil.copyfile(local,path)
            else:
                with urllib.request.urlopen(pins[name]['url'],timeout=90) as response: path.write_bytes(response.read())
        if hashlib.sha256(path.read_bytes()).hexdigest() != pins[name]['sha256']: raise SystemExit('Input checksum mismatch: '+name)
        return path
    for name in ['tiny-remapper.jar','mapping-io.jar','client-mappings.txt','client-original.jar']: fetch(name)
    fetch('mcp-config.zip',need('mcp_config-1.20.1-20230612.114412.zip'))
    with zipfile.ZipFile(out/'mcp-config.zip') as z: (out/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    srgutils = need('srgutils-0.5.4.jar')
    subprocess.run([*compiler,'--release','17','-cp',str(srgutils),'-d',str(out),str(REPO/'sails/tools/CreateMappings.java')],check=True)
    subprocess.run(['java','-cp',os.pathsep.join(map(str,[out,srgutils])),'CreateMappings',str(out/'client-mappings.txt'),str(out/'joined.tsrg'),str(out/'named-srg.tiny'),str(out/'client-named.tiny')],check=True)
    inputs = [need('server-1.20.1-20230612.114412-srg.jar'),need('forge-1.20.1-47.4.23-server.jar'),need('forge-1.20.1-47.4.23-universal.jar')]
    entries = {}
    for path in inputs:
        with zipfile.ZipFile(path) as z: entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    with zipfile.ZipFile(out/'runtime-srg.jar','w',zipfile.ZIP_DEFLATED) as z:
        for n,data in sorted(entries.items()): z.writestr(n,data)
    asm = sorted((libraries/'org/ow2/asm').rglob('*.jar'))
    remapper = os.pathsep.join(map(str,[out/'tiny-remapper.jar',out/'mapping-io.jar',*asm]))
    for source,target,mapping,namespace in [('runtime-srg.jar','runtime-named.jar','named-srg.tiny','srg'),('client-original.jar','client-named.jar','client-named.tiny','official')]:
        (out/target).unlink(missing_ok=True)
        subprocess.run(['java','-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(out/source),str(out/target),str(out/mapping),namespace,'named','--ignoreConflicts'],check=True)
    deps = [out/'runtime-named.jar',out/'client-named.jar',*sorted(libraries.rglob('*.jar'))]
    (out/'javac.args').write_text('-cp\n'+shlex.quote(os.pathsep.join(map(str,deps)))+'\n')

if __name__ == '__main__': prepare(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve())

