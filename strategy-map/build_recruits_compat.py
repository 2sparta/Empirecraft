#!/usr/bin/env python3
"""Rebuild the installed Recruits JAR with support for the seasonal map bridge."""

import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile


HERE = Path(__file__).resolve().parent
BASE_JAR = HERE / 'dist/recruits-1.20.1-1.15.2-empirecraft.1.jar'
BASE_SHA256 = '483fb3e4e7d12b6665b4d46e767dc428d3b21fa905528f6e1ada9ca40a53b02f'
RELEASE_NAME = 'recruits-1.20.1-1.15.2-empirecraft.2.jar'
DESCRIPTOR = 'META-INF/mods.toml'


def build(output):
    if hashlib.sha256(BASE_JAR.read_bytes()).hexdigest() != BASE_SHA256:
        raise ValueError(f'Unexpected Recruits input: {BASE_JAR}')
    with ZipFile(BASE_JAR) as original:
        descriptor = original.read(DESCRIPTOR)
        for old, new in (
            (b'version="1.15.2-empirecraft.1"', b'version="1.15.2-empirecraft.2"'),
            (b'versionRange="[1.13.0,1.14)"', b'versionRange="[1.13.0,1.15)"'),
        ):
            if descriptor.count(old) != 1:
                raise ValueError(f'Unexpected Recruits descriptor: {old!r}')
            descriptor = descriptor.replace(old, new)
        with ZipFile(output, 'w') as updated:
            for entry in original.infolist():
                data = descriptor if entry.filename == DESCRIPTOR else original.read(entry)
                updated.writestr(entry, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output_dir', type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / RELEASE_NAME
    build(output)
    checksum = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.jar.sha256').write_text(f'{checksum}  {output.name}\n')
    print(output)


if __name__ == '__main__':
    main()
