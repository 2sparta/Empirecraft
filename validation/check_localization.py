#!/usr/bin/env python3
"""Check shipped EN/RU catalogs against literals, formatting and command data."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def catalog(folder, language):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            assert key not in result, ('duplicate translation', key)
            result[key] = value
        return result
    return json.loads((folder / (language + '.json')).read_text(), object_pairs_hook=unique)

def main():
    server = ROOT / 'feudal/src/main/resources/empirecraft-feudal/lang'
    client = ROOT / 'bridge/src/main/resources/assets/empirecraft_townymap/locales'
    total = 0
    for folder, sources, function in (
        (server, ROOT / 'feudal/src/main/java', r'LocalText\.tr'),
        (client, ROOT / 'bridge/src/main/java/ua/empirecraft/townymap/client', r'(?:ClientText\.)?tr'),
    ):
        en, ru = catalog(folder, 'en'), catalog(folder, 'ru')
        assert en.keys() == ru.keys(), 'EN/RU catalog coverage differs'
        for source in en:
            for language, values in [('en', en), ('ru', ru)]:
                value = values[source]
                assert isinstance(value, str) and value.strip(), (language, source)
                for pattern in (r'[()\[\]]', r'§.', r'\{[a-zA-Z_]+\}', r'%[sd]', r'(?<![A-Za-z])[0-9]+(?:\.[0-9]+)?'):
                    assert re.findall(pattern, source) == re.findall(pattern, value), (language, 'format/number', source, value)
                assert re.match(r'^\s*', source)[0] == re.match(r'^\s*', value)[0], (language, 'leading space', source)
                assert re.search(r'\s*$', source)[0] == re.search(r'\s*$', value)[0], (language, 'trailing space', source)
                if language == 'ru':
                    # These are actual, backward-compatible parser prefixes in help examples.
                    prose = value.replace('місто:', '').replace('держава:', '')
                    assert not re.search('[іїєґІЇЄҐ]', prose), ('untranslated Russian text', source, value)
        for path in sources.rglob('*.java'):
            for literal in re.findall(function + r'\(\s*("(?:\\.|[^"\\])*")', path.read_text()):
                source = json.loads(literal)
                if re.search('[А-Яа-яІіЇїЄєҐґ]', source):
                    assert source in en, ('missing translation', path, source)
        total += len(en)
    print('PASS EN/RU localization:', total, 'catalog entries; literal coverage, numeric values, formatting and whitespace')

if __name__ == '__main__':
    main()
