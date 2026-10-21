"""Materialize pinned large JARs and verify them without third-party packages."""
import hashlib
import json
import shutil
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def valid(path, digest):
    return path.is_file() and hashlib.file_digest(path.open('rb'), 'sha512').hexdigest() == digest

def target(relative):
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT) or path.suffix != '.jar':
        raise ValueError(f'Invalid JAR destination: {relative}')
    return path

def main():
    entries = json.loads((ROOT / 'large-mods.json').read_text(encoding='utf-8'))['files']
    for entry in entries:
        paths = [target(p) for p in entry['destinations']]
        source = next((p for p in paths if valid(p, entry['sha512'])), None)
        temporary = None
        if source is None:
            handle = tempfile.NamedTemporaryFile(dir=ROOT, suffix='.download', delete=False)
            temporary = Path(handle.name)
            with handle as out:
                if entry.get('url'):
                    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'EmpireCraft-pinned-mods/1.0'})
                    with urllib.request.urlopen(request, timeout=120) as response:
                        shutil.copyfileobj(response, out)
                else:
                    for relative in entry['parts']:
                        part = (ROOT / relative).resolve()
                        if not part.is_relative_to(ROOT / '.github/mod-upload'):
                            raise ValueError('Invalid upload part path')
                        with part.open('rb') as stream:
                            shutil.copyfileobj(stream, out)
            if not valid(temporary, entry['sha512']):
                temporary.unlink(missing_ok=True)
                raise ValueError(f'Checksum mismatch: {entry["id"]}')
            source = temporary
        for path in paths:
            if not valid(path, entry['sha512']):
                path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, path)
        if temporary:
            temporary.unlink()
        for relative in entry.get('parts', []):
            (ROOT / relative).unlink(missing_ok=True)
        print(f'Verified {entry["id"]}: {len(paths)} destination(s)')

if __name__ == '__main__':
    main()
