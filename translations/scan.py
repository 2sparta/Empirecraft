#!/usr/bin/env python3
"""Inventory language resources from the exact installed client/server JARs."""
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'translations' / 'upstream'
LANG = re.compile(r'^assets/([^/]+)/lang/(en_us|uk_ua|ru_ru)\.(json|lang)$')


def read_json(raw):
    text = raw.decode('utf-8-sig')
    if not text.lstrip().startswith('{'):
        text = '{' + text.strip().rstrip(',') + '}'
    # A few mods ship comments/trailing commas in their language JSON.
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        text = re.sub(r',\s*([}\]])', r'\1', text)
        return json.loads(text)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    catalog, jars, seen, invalid, books = {}, [], set(), [], {}
    def scan(raw, label):
        digest = hashlib.sha256(raw).hexdigest()
        if digest in seen:
            return
        seen.add(digest)
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            resources = []
            for name in sorted(z.namelist()):
                if re.match(r'^(assets|data)/[^/]+/patchouli_books/.+/(en_us|uk_ua|ru_ru)/.+\.json$', name):
                    books[name] = read_json(z.read(name))
                match = LANG.match(name)
                if match:
                    ns, locale, ext = match.groups()
                    if ext == 'json':
                        try:
                            data = read_json(z.read(name))
                        except (ValueError, UnicodeError) as error:
                            invalid.append({'jar': label, 'path': name, 'error': str(error), 'raw': z.read(name).decode('utf-8-sig', errors='replace')})
                            continue
                    else:
                        data = dict(line.split('=', 1) for line in z.read(name).decode('utf-8-sig').splitlines() if '=' in line and not line.startswith('#'))
                    data = {k: v for k, v in data.items() if isinstance(v, str)}
                    catalog.setdefault(ns, {}).setdefault(locale, {}).update(data)
                    resources.append({'path': name, 'keys': len(data)})
                if name.endswith('.jar') and ('jarjar/' in name or 'jars/' in name):
                    scan(z.read(name), label + '!/' + name)
            jars.append({'path': label, 'sha256': digest, 'resources': resources})
    # Server-only mods first, client resources have final precedence.
    for folder in ['server/mods', 'mods']:
        for jar in sorted((ROOT / folder).glob('*.jar')):
            scan(jar.read_bytes(), str(jar.relative_to(ROOT)))
    for ns, data in sorted(catalog.items()):
        (OUT / (ns + '.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    report = {'jars': jars, 'namespaces': {ns: {lang: len(values) for lang, values in data.items()} for ns, data in sorted(catalog.items())}}
    (OUT / '_inventory.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    (OUT / '_invalid.json').write_text(json.dumps(invalid, ensure_ascii=False, indent=2) + '\n')
    (OUT / '_books.json').write_text(json.dumps(books, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report['namespaces'], ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
