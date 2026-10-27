#!/usr/bin/env python3
"""Refresh only Feudal payloads in existing installers; never rebuild or write other mods."""
from pathlib import Path
import hashlib
import json
import zipfile

REPO = Path(__file__).resolve().parent.parent
PLUGIN = 'empirecraft-feudal-1.6.0.jar'

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def refresh(relative, replacements, timestamp, source_prefix=None):
    path = REPO / relative
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        before = {name: archive.read(name) for name in archive.namelist()}
        assert len(before) == len(archive.namelist()), 'Duplicate archive entries'
    after = dict(before)
    if source_prefix:
        after = {name: raw for name, raw in after.items() if not name.startswith(source_prefix)}
    after.update(replacements)
    if 'SHA256SUMS.txt' in after:
        after['SHA256SUMS.txt'] = ''.join(sha(raw) + '  ' + name + '\n'
            for name, raw in sorted(after.items()) if name != 'SHA256SUMS.txt').encode()
    allowed = set(replacements) | {'SHA256SUMS.txt'}
    changed = {name for name in before.keys() | after.keys() if before.get(name) != after.get(name)}
    assert all(name in allowed or source_prefix and name.startswith(source_prefix) for name in changed)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, raw in sorted(after.items()):
            info = zipfile.ZipInfo(name, timestamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist()) == set(after)
        for name, raw in after.items():
            assert archive.read(name) == raw
    path.with_suffix(path.suffix + '.sha256').write_text(sha(path.read_bytes()) + '  ' + path.name + '\n')
    print('Refreshed Feudal entries:', relative, sorted(changed))

def main():
    plugin = (REPO / 'server/plugins' / PLUGIN).read_bytes()
    checksum = (sha(plugin) + '  ' + PLUGIN + '\n').encode()
    assert (REPO / 'server/plugins' / (PLUGIN + '.sha256')).read_bytes() == checksum
    with zipfile.ZipFile(REPO / 'server/plugins' / PLUGIN) as archive:
        assert 'ua/empirecraft/feudal/PoliticsDebug.class' in archive.namelist()
        assert 'empirecraft.feudal.debug' in archive.read('plugin.yml').decode()
    report_path = REPO / 'validation/currency-market-1.6.0.json'
    report = json.loads(report_path.read_bytes())
    assert report['plugin_version'] == '1.6.0'
    report['plugin_sha256'] = sha(plugin)
    verification = (json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode()
    report_path.write_bytes(verification)
    for side in ['client', 'server']:
        replacements = {'verification.json': verification}
        if side == 'server':
            replacements.update({'plugins/' + PLUGIN: plugin, 'plugins/' + PLUGIN + '.sha256': checksum})
        refresh('feudal/dist/Empirecraft-Currency-Market-1.6.0-' + side + '.zip', replacements, (2026, 10, 27, 0, 0, 0))
    refresh('chat-channels/dist/Empirecraft-ChatPlus-1.0.0.zip', {
        'server/plugins/' + PLUGIN: plugin, 'server/plugins/' + PLUGIN + '.sha256': checksum,
    }, (2026, 10, 25, 0, 0, 0))
    sources = {path.relative_to(REPO).as_posix(): path.read_bytes()
        for path in (REPO / 'feudal/src').rglob('*') if path.is_file()}
    sources['feudal/build.py'] = (REPO / 'feudal/build.py').read_bytes()
    refresh('chat-channels/dist/Empirecraft-ChatPlus-1.0.0-sources.zip', sources,
        (2026, 10, 25, 0, 0, 0), source_prefix='feudal/src/')

if __name__ == '__main__':
    main()
