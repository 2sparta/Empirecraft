#!/usr/bin/env python3
"""A server-only policy must not change the client's existing calendar binary or seasonal rules."""
import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
def main():
    row=next(m for m in json.loads((REPO/'modpack-lock.json').read_text())['mods'] if m['id']=='empirecraft_calendar')
    jar=REPO/'server/mods'/row['file']
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==row['sha256']
    assert jar.read_bytes()==(REPO/'mods'/row['file']).read_bytes()
    resources=REPO/'seasonal-calendar/src/main/resources'
    with zipfile.ZipFile(jar) as z:
        for path in [resources/'empirecraft-fauna.json',*(resources/'data/sereneseasons/tags').rglob('*.json')]:
            assert z.read(path.relative_to(resources).as_posix())==path.read_bytes(),'Seasonal resource changed; rebuild/review the calendar explicitly: '+str(path)
    print('PASS audited additions leave the current Calendar 1.1.1 binary and seasonal assignments unchanged')
if __name__=='__main__':main()
