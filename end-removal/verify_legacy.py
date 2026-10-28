#!/usr/bin/env python3
"""The End overlay supersedes the older Chipped binary while preserving its Nether exclusions."""
import hashlib,json,zipfile
from build import ROOT,REPO,NAMES,MOD

def main():
    pins=json.loads((ROOT/'inputs.json').read_text());report=json.loads((ROOT/'audit/build.json').read_text())
    for old,new in NAMES.items():
        raw=(REPO/'mods'/new).read_bytes();assert raw==(REPO/'server/mods'/new).read_bytes()
        assert hashlib.sha256(raw).hexdigest()==report['native'][new]['sha256']
        with zipfile.ZipFile(REPO/'mods'/new) as archive:
            assert archive.testzip() is None
            if old.startswith('chipped-forge'):
                previous=archive.read('META-INF/empirecraft-trim.json')
                assert hashlib.sha256(previous).hexdigest()==pins['legacy_trim_metadata_sha256']
                legacy=set(json.loads(previous)['removed']);retired=set(json.loads(archive.read('META-INF/empirecraft-no-end.json'))['removed'])
                assert len(legacy)>1000 and len(retired)==139 and not legacy&retired
                assert not any('assets/'+identity.split(':')[0]+'/models/item/'+identity.split(':')[1]+'.json' in archive.namelist() for identity in legacy|retired)
    assert (REPO/'mods'/MOD).read_bytes()==(REPO/'server/mods'/MOD).read_bytes()
    print('PASS End overlay preserves the exact previous Chipped Nether exclusion manifest and all removed item assets')

if __name__=='__main__':main()
