#!/usr/bin/env python3
"""Disable Towny command travel while preserving other settings and respawn rules."""
import argparse
import re
import shutil
from pathlib import Path

FLAGS = ('allow_town_spawn', 'allow_town_spawn_travel', 'allow_town_spawn_travel_nation',
         'allow_town_spawn_travel_ally', 'allow_nation_spawn', 'allow_nation_spawn_travel',
         'allow_nation_spawn_travel_ally')
SPAWN_PERMISSION = re.compile(r'^\s*-\s+[\'"]?towny\.(?:town|nation)\.spawn(?:\.[\w*]+)*[\'"]?\s*(?:#.*)?$', re.M)


def update(settings):
    config = settings / 'config.yml'
    perms = settings / 'townyperms.yml'
    original = config.read_text()
    revised = original
    for flag in FLAGS:
        pattern = re.compile(r'^([ \t]*' + re.escape(flag) + r':[ \t]*)([\'"]?)(true|false)\2([ \t]*(?:#.*)?)$', re.M)
        revised, count = pattern.subn(lambda m: m[1] + m[2] + 'false' + m[2] + m[4], revised)
        if count != 1:
            raise ValueError('Expected exactly one Towny setting: ' + flag)
    old_perms = perms.read_text()
    new_perms = SPAWN_PERMISSION.sub('', old_perms)
    for path, before, after in ((config, original, revised), (perms, old_perms, new_perms)):
        if before != after:
            backup = path.with_name(path.name + '.before-city-markets')
            if not backup.exists():
                shutil.copyfile(path, backup)
            path.write_text(after)
    print('PASS seven travel flags disabled; positive Towny spawn grants removed; respawn settings preserved')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('settings', type=Path, help='Path to server/plugins/Towny/settings')
    update(parser.parse_args().settings)
