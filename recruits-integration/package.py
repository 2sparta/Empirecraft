#!/usr/bin/env python3
"""Package the three built server components with instructions and SHA-256 checksums."""
import hashlib, zipfile
from build import ROOT, REPO, archive


def main():
    paths={
        'server/plugins/empirecraft-feudal-1.11.0.jar':REPO/'feudal/dist/empirecraft-feudal-1.11.0.jar',
        'server/plugins/TownyRecruitsBridge-2.0.0.jar':ROOT/'dist/TownyRecruitsBridge-2.0.0.jar',
        'server/mods/empirecraft-recruits-integration-1.0.0.jar':ROOT/'dist/empirecraft-recruits-integration-1.0.0.jar',
    }
    contents={name:path.read_bytes() for name,path in paths.items()}
    for name,path in paths.items():
        with zipfile.ZipFile(path) as jar:
            assert jar.testzip() is None,name
            assert not any('SmokePlugin' in entry or '/guardtest/' in entry or entry.endswith('Tests.class') for entry in jar.namelist()),name
    contents['SHA256SUMS']=(''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(contents.items()))).encode()
    contents['README-UA.md']=(ROOT/'README.md').read_bytes()
    archive(ROOT/'dist/Empirecraft-Recruits-Towny-1.0.0.zip',contents)


if __name__=='__main__':main()
