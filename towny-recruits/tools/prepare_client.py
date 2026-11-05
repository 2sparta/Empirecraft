#!/usr/bin/env python3
"""Prepare real Minecraft 1.20.1 client SRG compile dependencies (never packaged in the mod)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile

CLIENT_SHA1 = '0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838'
MAPPINGS_SHA1 = '6c48521eed01fe2e8ecdadbd5ae348415f3c47da'

def download(url, path, expected):
    if not path.exists() or hashlib.sha1(path.read_bytes()).hexdigest() != expected:
        with urllib.request.urlopen(url, timeout=60) as response:
            path.write_bytes(response.read())
    if hashlib.sha1(path.read_bytes()).hexdigest() != expected:
        raise SystemExit('Checksum mismatch: ' + str(path))

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args(); a.output.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', timeout=60) as r:
        versions = json.load(r)
    url = next(v['url'] for v in versions['versions'] if v['id'] == '1.20.1')
    with urllib.request.urlopen(url, timeout=60) as r: meta = json.load(r)
    assert meta['downloads']['client']['sha1'] == CLIENT_SHA1
    assert meta['downloads']['client_mappings']['sha1'] == MAPPINGS_SHA1
    vanilla, official = a.output/'client-1.20.1.bin', a.output/'client-mappings.txt'
    download(meta['downloads']['client']['url'], vanilla, CLIENT_SHA1)
    download(meta['downloads']['client_mappings']['url'], official, MAPPINGS_SHA1)
    for lib in meta['libraries']:
        if lib['name'] in ('org.lwjgl:lwjgl:3.3.1', 'org.lwjgl:lwjgl-glfw:3.3.1'):
            entry = lib['downloads']['artifact']
            download(entry['url'], a.output/Path(entry['path']).name, entry['sha1'])
    # The hybrid server's merged mapping omits unpatched client-only classes.
    # Keep MCP's SRG members, and substitute Mojang's complete client class names.
    names = {}
    for line in official.read_text().splitlines():
        if line and not line.startswith((' ', '#')) and ' -> ' in line:
            name, obf = line.rstrip(':').split(' -> '); names[obf.replace('.', '/')] = name.replace('.', '/')
    mcp = next(a.server.rglob('mcp_config-1.20.1-20230612.114412.zip'))
    lines = ['tsrg2 obf srg']
    with zipfile.ZipFile(mcp) as z:
        for line in z.read('config/joined.tsrg').decode().splitlines()[1:]:
            if line.startswith('\t\t'): continue
            words = line.split()
            if not words: continue
            lines.append('\t'+' '.join(words[:-1]) if line.startswith('\t') else words[0]+' '+names.get(words[0], words[1]))
    mapping = a.output/'client-srg.tsrg'; mapping.write_text('\n'.join(lines)+'\n')
    fart = next(a.server.rglob('ForgeAutoRenamingTool-0.1.22-all.jar'))
    java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    target = a.output/'client-1.20.1-srg.jar'
    log = a.output/'remap.log'
    with log.open('w') as output:
        subprocess.run([java, '-jar', str(fart), '--input', str(vanilla), '--output', str(target),
            '--names', str(mapping), '--ann-fix', '--ids-fix', '--src-fix', '--record-fix', '--strip-sigs'],
            stdout=output, stderr=subprocess.STDOUT, check=True)
    print('Verified real client SRG dependencies: ' + str(a.output))

if __name__ == '__main__': main()
