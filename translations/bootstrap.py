#!/usr/bin/env python3
"""One-time translation draft generation; the delivered pack is fully offline.

Uses exact installed English strings, community translation memories, and cached
machine drafts. Not called by the final build or by Minecraft.
"""
import concurrent.futures
import copy
import json
import re
import subprocess
import time
import urllib.parse
import urllib.request
from pathlib import Path
from prepare import HERE, WORK, main as prepare

PROTECTED = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\$\([^)]*\)|§.|\{[^{}\n]*\}|https?://[^\s<>]+|\b[a-z0-9_]+:[a-z0-9_./]+\b|\n|\\n')
MARKER = re.compile(r'⟦(\d{6})⟧')
TEXT_FIELDS = {'name', 'description', 'text', 'title', 'subtitle', 'landing_text', 'header', 'caption'}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + '\n')


def tokens(text):
    return sorted(PROTECTED.findall(text))


def community():
    sources = json.loads((HERE / 'community-sources.json').read_text())
    names = [p.stem for p in (HERE / 'upstream').glob('*.json') if not p.name.startswith('_')]
    for source in sources:
        target = WORK / source['directory']
        if not (target / '.git').exists():
            subprocess.run(['git', 'clone', '-q', '--depth=1', '--filter=blob:none', '--no-checkout', source['url'], str(target)], check=True)
            subprocess.run(['git', '-C', str(target), 'fetch', '-q', '--depth=1', '--filter=blob:none', 'origin', source['commit']], check=True)
            subprocess.run(['git', '-C', str(target), 'sparse-checkout', 'init', '--cone'], check=True)
            subprocess.run(['git', '-C', str(target), 'sparse-checkout', 'set', *[source['assets'] + '/' + n for n in names]], check=True)
            subprocess.run(['git', '-C', str(target), 'checkout', '-q', '--detach', 'FETCH_HEAD'], check=True)
        license_files = list(target.glob('*LICENSE*')) + list(target.glob('*License*'))
        for license_file in license_files:
            if license_file.is_file():
                destination = HERE / 'licenses' / (source['directory'] + '-' + license_file.name)
                destination.parent.mkdir(exist_ok=True)
                destination.write_bytes(license_file.read_bytes())


def translate_batch(batch, locale):
    blocks, protected = [], {}
    for index, text in enumerate(batch):
        def protect(match):
            marker = 'ZXQ' + str(len(protected)).zfill(5) + 'QXZ'
            protected[marker] = match.group()
            return marker
        blocks.append('⟦' + str(index).zfill(6) + '⟧\n' + PROTECTED.sub(protect, text))
    query = '\n'.join(blocks)
    params = urllib.parse.urlencode({'client': 'gtx', 'sl': 'en', 'tl': locale[:2], 'dt': 't', 'q': query})
    request = urllib.request.Request('https://translate.googleapis.com/translate_a/single?' + params, headers={'User-Agent': 'Mozilla/5.0'})
    for attempt in range(5):
        try:
            response = json.loads(urllib.request.urlopen(request, timeout=40).read())
            text = ''.join(part[0] for part in response[0] if part[0])
            pieces = MARKER.split(text)
            mapping = {int(pieces[i]): pieces[i+1].strip() for i in range(1, len(pieces), 2)}
            if len(mapping) != len(batch):
                raise ValueError('Translation response lost segment markers')
            result = {}
            for index, original in enumerate(batch):
                translated = mapping[index]
                for marker, value in protected.items():
                    translated = re.sub(re.escape(marker), lambda m: value, translated, flags=re.I)
                if 'ZXQ' in translated or tokens(original) != tokens(translated):
                    raise ValueError('Protected formatting changed')
                # Prefix/suffix spaces can be significant in constructed labels.
                translated = re.match(r'^\s*', original).group() + translated.strip() + re.search(r'\s*$', original).group()
                result[original] = translated
            return result
        except ValueError:
            if len(batch) > 1:
                middle = len(batch) // 2
                return translate_batch(batch[:middle], locale) | translate_batch(batch[middle:], locale)
            # For difficult markup, translate literal spans separately and copy
            # every protected token directly. The model cannot mutate tokens.
            original = batch[0]
            parts, position = [], 0
            def literal(text):
                if not text.strip() or not re.search('[a-zA-Z]', text):
                    return text
                params = urllib.parse.urlencode({'client': 'gtx', 'sl': 'en', 'tl': locale[:2], 'dt': 't', 'q': text.strip()})
                answer = json.loads(urllib.request.urlopen('https://translate.googleapis.com/translate_a/single?' + params, timeout=40).read())
                value = ''.join(part[0] for part in answer[0] if part[0]).strip()
                return re.match(r'^\s*', text).group() + value + re.search(r'\s*$', text).group()
            for match in PROTECTED.finditer(original):
                parts.extend([literal(original[position:match.start()]), match.group()])
                position = match.end()
            parts.append(literal(original[position:]))
            translated = ''.join(parts)
            assert tokens(original) == tokens(translated), repr(original)
            return {original: translated}
        except Exception as error:
            if attempt == 4:
                if len(batch) > 1:
                    middle = len(batch) // 2
                    return translate_batch(batch[:middle], locale) | translate_batch(batch[middle:], locale)
                raise
            print(locale, 'retry', attempt + 1, type(error).__name__, flush=True)
            time.sleep(min(4 * 2**attempt, 40))


def translate_locale(locale, needed):
    path = HERE / 'drafts' / (locale + '.json')
    cache = json.loads(path.read_text()) if path.exists() else {}
    todo = [text for text in needed if text not in cache]
    batches, batch, length = [], [], 0
    for text in todo:
        if batch and length + len(text) + 15 > 2600:
            batches.append(batch)
            batch, length = [], 0
        batch.append(text)
        length += len(text) + 15
    if batch:
        batches.append(batch)
    def run_batch(batch):
        time.sleep(1.5)
        return translate_batch(batch, locale)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        for index, result in enumerate(executor.map(run_batch, batches)):
            cache.update(result)
            dump(path, cache)
            print(locale, 'batch', index + 1, '/', len(batches), 'strings', len(cache), flush=True)
    return cache


def main():
    community()
    prepare()
    working = json.loads((HERE / 'working.json').read_text())
    gaps = json.loads((HERE / 'gaps.json').read_text())
    books = json.loads((HERE / 'upstream' / '_books.json').read_text())
    # Covers raw Patchouli book prose as well as text in lang/*.json.
    def gather(value, field=''):
        if isinstance(value, dict):
            for key, child in value.items():
                yield from gather(child, key)
        elif isinstance(value, list):
            for child in value:
                yield from gather(child, field)
        elif isinstance(value, str) and field in TEXT_FIELDS and re.search('[a-zA-Z]', value):
            if not re.fullmatch(r'[a-z0-9_]+[.:][a-z0-9_./]+', value):
                yield value
    book_strings = {text for path, book in books.items() if '/en_us/' in path for text in gather(book)}
    for locale in gaps:
        for text in book_strings:
            gaps[locale].setdefault(text, [])
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        caches = dict(zip(gaps, executor.map(lambda locale: translate_locale(locale, gaps[locale]), gaps)))
    for locale, pending in gaps.items():
        for text, refs in pending.items():
            for ns, key in refs:
                working[ns][locale][key] = caches[locale][text]
    for ns, locales in working.items():
        for locale, values in locales.items():
            dump(HERE / 'pack' / 'assets' / ns / 'lang' / (locale + '.json'), values)
    def rewrite(value, locale, field=''):
        if isinstance(value, dict):
            return {key: rewrite(child, locale, key) for key, child in value.items()}
        if isinstance(value, list):
            return [rewrite(child, locale, field) for child in value]
        if isinstance(value, str) and field in TEXT_FIELDS and value in caches[locale]:
            return caches[locale][value]
        return value
    for path, book in books.items():
        if '/en_us/' in path:
            for locale in gaps:
                dump(HERE / 'pack' / path.replace('/en_us/', '/' + locale + '/'), rewrite(book, locale))
    print('Draft translations generated; linguistic review and offline validation required.', flush=True)


if __name__ == '__main__':
    main()
