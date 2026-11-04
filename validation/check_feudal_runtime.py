#!/usr/bin/env python3
"""Keep the public API of the actually installed 1.11 runtime in subsequent source builds."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    version = re.search(r'^version: (\S+)$', (ROOT / 'feudal/src/main/resources/plugin.yml').read_text(), re.M)[1]
    jar = args.jar or ROOT / 'server/plugins' / ('empirecraft-feudal-' + version + '.jar')
    baseline = json.loads((ROOT / 'validation/feudal-1.11.0-api.json').read_text())
    javap = str(Path(os.environ['JAVA_HOME']) / 'bin/javap') if os.environ.get('JAVA_HOME') else shutil.which('javap')
    assert javap, 'A complete JDK with javap is required'
    output = subprocess.run([javap, '-public', '-s', '-classpath', str(jar), *baseline['classes']],
                            check=True, capture_output=True, text=True).stdout
    actual = set()
    owner = declaration = ''
    for line in output.splitlines():
        header = re.search(r'(?:class|interface|enum) (ua\.empirecraft\.feudal\.[\w.$]+)', line)
        if header:
            owner = header[1]
        if line.strip().startswith('public '):
            declaration = line.strip()
        if line.strip().startswith('descriptor:'):
            descriptor = line.strip().split(':', 1)[1].strip()
            name = declaration.split('(')[0].split()[-1].rstrip(';')
            if name == owner:
                name = '<init>'
            actual.add(owner + '|' + name + '|' + descriptor)
    missing = set(baseline['public_members']) - actual
    assert not missing, 'Lost installed public API: ' + repr(sorted(missing))
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None
        assert 'version: ' + version + '\n' in archive.read('plugin.yml').decode()
        assert all(name.replace('.', '/') + '.class' in archive.namelist() for name in baseline['classes'])
    print('PASS current descriptor and all ' + str(len(baseline['public_members'])) + ' installed public API members')


if __name__ == '__main__':
    main()
