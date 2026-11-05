#!/usr/bin/env python3
"""Offline validation and reproducible Minecraft 1.20.1 resource-pack build."""
import copy
import hashlib
import json
import re
import zipfile
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACK = HERE / 'pack'
LOCALES = ['uk_ua', 'ru_ru']
FORMAT = re.compile(r'%[A-Z][A-Z0-9_]*%|%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z%])|\$\([^)]*\)|§.|\{[^{}\n]*\}|https?://[^\s<>]+|\b[a-z][a-z0-9_]*:[a-z0-9_./]+\b')
TEXT_FIELDS = {'name', 'description', 'text', 'title', 'subtitle', 'landing_text', 'header', 'caption'}


def load(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


def formatting(text):
    result = Counter()
    implicit = 0
    for match in FORMAT.finditer(text):
        token = match.group()
        if match.group(2) and match.group(2) != '%':
            if match.group(1):
                index = int(match.group(1))
            else:
                implicit += 1
                index = implicit
            result[('argument', index, match.group(2))] += 1
        else:
            if token.startswith(('https://', 'http://')):
                token = token.rstrip('.,;!')
            result[('literal', token)] += 1
    return result


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + '\n')


def check_book(source, translated, context, field=''):
    if isinstance(source, dict):
        assert isinstance(translated, dict) and set(source) == set(translated), context
        for key in source:
            check_book(source[key], translated[key], context + '/' + key, key)
    elif isinstance(source, list):
        assert isinstance(translated, list) and len(source) == len(translated), context
        for index, (before, after) in enumerate(zip(source, translated)):
            check_book(before, after, context + '/' + str(index), field)
    elif isinstance(source, str) and field in TEXT_FIELDS:
        assert isinstance(translated, str), context
        assert not re.search(r'ZXQ\d+QXZ|⟦\d{6}⟧', translated, re.I), context
        assert formatting(source) == formatting(translated), context + ': formatting changed'
        assert not source.strip() or translated.strip(), context + ': empty translation'
    else:
        assert source == translated, context + ': non-text data changed'


def main():
    assert load(PACK / 'pack.mcmeta')['pack']['pack_format'] == 15
    invalid = load(HERE / 'upstream' / '_invalid.json')
    assert not invalid, 'Unresolved malformed upstream language resources'
    stats, identities, changes = [], {}, {lang: 0 for lang in LOCALES}
    for source in sorted((HERE / 'upstream').glob('*.json')):
        if source.name.startswith('_'):
            continue
        original = load(source)
        english = original.get('en_us', {})
        if not english:
            continue
        row = {'namespace': source.stem, 'english_keys': len(english)}
        for locale in LOCALES:
            path = PACK / 'assets' / source.stem / 'lang' / (locale + '.json')
            overlay = load(path) if path.exists() else {}
            effective = original.get(locale, {}) | overlay
            missing = sorted(set(english) - set(effective))
            assert not missing, (source.stem, locale, 'missing keys', missing[:10])
            assert all(isinstance(v, str) for v in overlay.values()), path
            changed = {key: value for key, value in overlay.items() if value != original.get(locale, {}).get(key)}
            for key, value in changed.items():
                if key not in english:
                    continue
                assert not english[key].strip() or value.strip(), (source.stem, locale, key, 'empty')
                assert formatting(english[key]) == formatting(value), (source.stem, locale, key, english[key], value, 'formatting')
                assert not re.search(r'ZXQ\d+QXZ|⟦\d{6}⟧', value, re.I), (path, key, 'draft marker')
            row[locale] = {'covered': len(english), 'missing': 0, 'overlay_keys': len(changed)}
            changes[locale] += len(changed)
            identities[source.stem + '/' + locale] = {key: english[key] for key in english if effective.get(key) == english[key] and re.search('[a-zA-Z]', english[key])}
        stats.append(row)
    books = load(HERE / 'upstream' / '_books.json')
    book_count = 0
    for path, source in books.items():
        if '/en_us/' not in path:
            continue
        for locale in LOCALES:
            target = PACK / path.replace('/en_us/', '/' + locale + '/')
            assert target.exists(), str(target)
            check_book(source, load(target), str(target))
            book_count += 1
    inventory = load(HERE / 'upstream' / '_inventory.json')
    report = {
        'minecraft': '1.20.1', 'pack_format': 15,
        'unique_jars_including_nested': len(inventory['jars']),
        'namespaces_with_english': len(stats),
        'english_keys': sum(row['english_keys'] for row in stats),
        'overlay_keys': changes, 'localized_book_files': book_count,
        'missing': {locale: 0 for locale in LOCALES},
        'namespaces': stats,
        'limitations': ['Machine translation drafts were used for missing prose; manual review focuses on gameplay terms and formatting.', 'Original proper names, identifiers, abbreviations and credits can remain in Latin script.', 'Hard-coded text without localization keys and text rendered inside textures is outside resource-pack coverage.', 'Full graphical client has not been launched.'],
    }
    dump(HERE / 'audit' / 'coverage.json', report)
    dump(HERE / 'audit' / 'unchanged-source-text.json', {k: v for k, v in identities.items() if v})
    # Never redistribute unchanged original language resources in the overlay.
    entries = {}
    for path in sorted(PACK.rglob('*')):
        if not path.is_file():
            continue
        name = path.relative_to(PACK).as_posix()
        if re.fullmatch(r'assets/[^/]+/lang/(uk_ua|ru_ru)\.json', name):
            _, ns, _, filename = name.split('/')
            original = load(HERE / 'upstream' / (ns + '.json')).get(filename[:-5], {})
            values = {k: v for k, v in load(path).items() if original.get(k) != v}
            if not values:
                continue
            entries[name] = (json.dumps(values, ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode()
        else:
            if name in books and path.suffix == '.json' and load(path) == books[name]:
                continue
            entries[name] = path.read_bytes()
    for name in ['README.md', 'CREDITS.md']:
        entries[name] = (HERE / name).read_bytes()
    for path in sorted((HERE / 'licenses').glob('*')):
        if path.is_file():
            entries['licenses/' + path.name] = path.read_bytes()
    entries['coverage.json'] = (HERE / 'audit' / 'coverage.json').read_bytes()
    dist = HERE / 'dist'
    dist.mkdir(exist_ok=True)
    destination = dist / 'Empirecraft-Translations-uk-ru-1.1.0.zip'
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
        assert 'pack.mcmeta' in archive.namelist()
        assert not any(name.startswith('/') or '..' in Path(name).parts for name in archive.namelist())
        for name in archive.namelist():
            if name.endswith('.json') or name == 'pack.mcmeta':
                json.loads(archive.read(name).decode('utf-8'))
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix('.zip.sha256').write_text(digest + '  ' + destination.name + '\n')
    print(json.dumps({k: v for k, v in report.items() if k not in ['namespaces', 'limitations']}, ensure_ascii=False, indent=2))
    print(destination.name, digest)


if __name__ == '__main__':
    main()
