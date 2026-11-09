#!/usr/bin/env python3
"""Disable only the three invalid autoload references reported in the supplied log.

This optional migration preserves the entries and makes a backup. A real level.dat
or level.dat_old always wins: existing worlds are never disabled or recreated.
"""
import argparse
import re
import shutil
from pathlib import Path

STALE = ('DIM1', 'mine_4', 'world_real')


def repair(server):
    server = Path(server).resolve()
    config = server / 'plugins/Multiverse-Core/worlds.yml'
    if not config.is_file():
        return []
    text = config.read_text(encoding='utf-8')
    changed = []
    for name in STALE:
        if any((server / name / file).is_file() for file in ('level.dat', 'level.dat_old')):
            continue
        # Multiverse 5's root mapping format; do not guess other YAML layouts.
        pattern = re.compile(r'(?m)^' + re.escape(name) + r':[^\n]*\n(?:(?:[ \t]+[^\n]*|)[\n]|[ \t]+[^\n]*$)*')
        match = pattern.search(text)
        if match is None:
            continue
        section, count = re.subn(r'(?m)^(  auto-load: )true([ \t]*(?:#.*)?)$', r'\g<1>false\2', match.group(), count=1)
        if count:
            text = text[:match.start()] + section + text[match.end():]
            changed.append(name)
    if changed:
        backup = config.with_name('worlds.yml.before-startup-fixes-1')
        if not backup.exists():
            shutil.copy2(config, backup)
        config.write_text(text, encoding='utf-8')
    return changed


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('server', type=Path, help='Stopped server directory')
    args = parser.parse_args()
    print('Disabled missing world autoload entries:', ', '.join(repair(args.server)) or 'none')
