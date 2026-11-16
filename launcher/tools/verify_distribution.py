"""Verify a signed public channel against both its Git snapshot and the client source."""
import argparse
import base64
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from empirecraft_launcher.security import verify_manifest
from tools.channel import build, git


def assert_same_contents(expected, actual):
    for profile in ('potato', 'balanced', 'high'):
        def files(pack):
            return {f['path']: (f['size'], f['sha256']) for f in pack['profiles'][profile]['files']}
        a, b = files(expected), files(actual)
        if a != b:
            missing = sorted(a.keys()-b.keys())
            extra = sorted(b.keys()-a.keys())
            changed = sorted(k for k in a.keys() & b.keys() if a[k] != b[k])
            raise ValueError(f'Stale {profile} distribution: missing={missing}, extra={extra}, changed={changed}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--source-ref', required=True)
    parser.add_argument('--distribution', type=Path, required=True)
    parser.add_argument('--channel', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    key = base64.b64decode(git('show', args.source_ref+':launcher/resources/trusted-key.txt', repo=args.source).strip())
    manifest = verify_manifest(args.channel.read_bytes(), key)
    source = build(args.source_ref, repo=args.source, url_repository='2sparta/Empirecraft')
    snapshot = build(manifest['commit'], repo=args.distribution, url_repository='2sparta/EmpireCraftLauncher')
    assert_same_contents(source, manifest)
    assert_same_contents(snapshot, manifest)
    report = {'source_commit': source['commit'], 'public_snapshot': manifest['commit'],
        'signature_verified': True, 'all_profiles_match_source': True,
        'profile_files': {name: len(data['files']) for name, data in manifest['profiles'].items()}}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))

if __name__ == '__main__': main()
