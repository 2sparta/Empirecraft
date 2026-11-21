#!/usr/bin/env python3
"""Check the shipped ship-map overlay, its evidence and paired client/server artifacts."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent


def digest(data):
    return hashlib.sha256(data).hexdigest()


def archive(data):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        assert jar.testzip() is None
        return {name: jar.read(name) for name in jar.namelist() if not name.endswith('/')}


def main():
    report = json.loads((ROOT / 'build.json').read_text())
    for path, expected in report['sources_sha256'].items():
        assert digest((REPO / path).read_bytes()) == expected, ('Source changed after build', path)
    name = 'empirecraft-towny-journeymap-1.14.1.jar'
    raw = (ROOT / 'dist' / name).read_bytes()
    sha = digest(raw)
    assert sha == report['sha256']
    current = archive(raw)
    base_raw = subprocess.check_output(['git', 'show', report['base_commit'] + ':mods/empirecraft-towny-journeymap-1.14.0.jar'], cwd=REPO)
    assert digest(base_raw) == report['base_sha256']
    base = archive(base_raw)
    spec = importlib.util.spec_from_file_location('ship_map_build', ROOT / 'build.py')
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    prefixes = ['ua/empirecraft/townymap/' + value for value in builder.JAVA]
    preserved = [p for p in base if p.endswith('.class') and not any(p == x + '.class' or p.startswith(x + '$') for x in prefixes)]
    assert len(preserved) == report['preserved_classes']
    assert all(current[p] == base[p] for p in preserved)
    assert not any(p.startswith(('net/minecraft/', 'net/minecraftforge/', 'org/valkyrienskies/')) for p in current)
    assert not any('Probe' in p or '/test/' in p for p in current)
    assert b'version="1.14.1"' in current['META-INF/mods.toml']
    manifest = current['META-INF/MANIFEST.MF'].decode().replace('\r\n ', '')
    for config, field, classes in [
        ('empirecraft-ship-map.mixins.json', 'client', ['ShipSpyglassKeys', 'ShipSpyglassMouse']),
        ('empirecraft-ship-map-common.mixins.json', 'mixins', ['ShipClientSettings']),
    ]:
        value = json.loads(current[config])
        assert config in manifest and value['required'] and value['injectors']['defaultRequire'] == 1
        assert value[field] == classes
    for filename in ['tests.json', 'native.json']:
        value = json.loads((ROOT / filename).read_text())
        assert value['status'] == 'passed' and value['artifact_sha256'] == sha, filename
    value = json.loads((ROOT / 'client.json').read_text())
    assert value['status'] == 'passed' and value['artifact_sha256'] == sha
    assert value['probe_sha256'] == digest((ROOT / 'tests/ClientProbe.java').read_bytes())
    assert value['checks'] >= 19 and not value['production_world_opened']
    assert all((ROOT / 'audit' / name).is_file() for name in value['screenshots'])
    book_report = json.loads((ROOT / 'book.json').read_text())
    book_name = 'empirecraft-handbook-1.0.7.jar'
    book_raw = (ROOT / 'dist' / book_name).read_bytes()
    assert digest(book_raw) == book_report['sha256']
    book = archive(book_raw)
    previous = archive(subprocess.check_output(['git', 'show', report['base_commit'] + ':mods/empirecraft-handbook-1.0.6.jar'], cwd=REPO))
    changed = set(book_report['replaced_legacy_entries']) | {'META-INF/mods.toml', 'empirecraft-handbook-catalog.json'}
    assert all(book[p] == data for p, data in previous.items() if p not in changed)
    for locale in ['uk_ua', 'en_us']:
        entry = json.loads(book[f'assets/empirecraft_guidebook/patchouli_books/handbook/{locale}/entries/map/ship_contacts.json'])
        assert entry['category'] == 'empirecraft_guidebook:map' and len(entry['pages']) == book_report['pages']
        layout_spec = importlib.util.spec_from_file_location('ship_map_book_layout', REPO / 'handbook/layout.py')
        layout = importlib.util.module_from_spec(layout_spec)
        layout_spec.loader.exec_module(layout)
        assert all(layout.lines(page.get('text', '')) <= layout.MAX_LINES for page in entry['pages'])
    for filename, content, old in [(name, raw, 'empirecraft-towny-journeymap-1.14.0.jar'), (book_name, book_raw, 'empirecraft-handbook-1.0.6.jar')]:
        for folder in ['mods', 'server/mods']:
            assert (REPO / folder / filename).read_bytes() == content, ('Side differs', folder, filename)
            assert (REPO / folder / (filename + '.sha256')).read_text() == digest(content) + '  ' + filename + '\n'
            assert not (REPO / folder / old).exists()
    print(f'SHIP_MAP_STATIC_PASS preservedClasses={len(preserved)} tests=100 pairedArtifacts=2 sha256={sha}')


if __name__ == '__main__':
    main()
