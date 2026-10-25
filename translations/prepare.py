#!/usr/bin/env python3
"""One-time workspace helper to import exact-key community translations."""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORK = HERE.parents[1]


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def main():
    catalog = {p.stem: load(p) for p in sorted((HERE / 'upstream').glob('*.json')) if not p.name.startswith('_')}
    for entry in load(HERE / 'upstream' / '_invalid.json'):
        if entry['path'].endswith('/en_us.json'):
            ns = entry['path'].split('/')[1]
            catalog.setdefault(ns, {})['en_us'] = json.loads('{' + entry['raw'].strip().rstrip(',') + '}')
            (HERE / 'upstream' / (ns + '.json')).write_text(json.dumps(catalog[ns], ensure_ascii=False, indent=2) + '\n')
    roots = [
        ('StarmanMine142', WORK / 'community-starman' / 'all' / 'assets', 'uk_ua'),
        ('Tenwoc', WORK / 'community-tenwoc' / 'assets', 'uk_ua'),
        ('Deflecta', WORK / 'community-ru' / 'Набор ресурсов' / '1.20' / 'assets', 'ru_ru'),
        ('RTF', WORK / 'community-rtf' / '1.20.x' / 'assets', 'ru_ru'),
    ]
    provenance = {}
    result = {}
    for ns, data in sorted(catalog.items()):
        en = data.get('en_us', {})
        result[ns] = {lang: dict(data.get(lang, {})) for lang in ['uk_ua', 'ru_ru']}
        for author, root, lang in roots:
            path = root / ns / 'lang' / (lang + '.json')
            if not path.exists():
                continue
            try:
                values = load(path)
            except ValueError:
                continue
            for key, text in values.items():
                if key in en and isinstance(text, str) and (key not in result[ns][lang] or result[ns][lang][key] == en[key]):
                    result[ns][lang][key] = text
                    provenance[ns + '/' + lang + '/' + key] = author
    # Reuse only exact English text with an existing actual translation.
    memories = {lang: {} for lang in ['uk_ua', 'ru_ru']}
    for ns, data in catalog.items():
        for key, english in data.get('en_us', {}).items():
            for lang in memories:
                translated = result[ns][lang].get(key)
                if translated and translated != english:
                    memories[lang].setdefault(english, translated)
    gaps = {lang: {} for lang in memories}
    for ns, data in catalog.items():
        for key, english in data.get('en_us', {}).items():
            for lang in memories:
                if key not in result[ns][lang] or result[ns][lang][key] == english:
                    if english in memories[lang]:
                        result[ns][lang][key] = memories[lang][english]
                    else:
                        gaps[lang].setdefault(english, []).append([ns, key])
    (HERE / 'working.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    (HERE / 'gaps.json').write_text(json.dumps(gaps, ensure_ascii=False, indent=2) + '\n')
    (HERE / 'community-provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n')
    for lang, missing in gaps.items():
        print(lang, len(missing), 'unique strings', sum(len(x) for x in missing.values()), 'keys')
        (HERE / (lang + '-gaps.txt')).write_text('\n'.join(f'{ns}: {len([1 for refs in missing.values() for n,k in refs if n==ns])}' for ns in catalog))


if __name__ == '__main__':
    main()
