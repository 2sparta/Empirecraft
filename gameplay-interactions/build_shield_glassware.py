#!/usr/bin/env python3
"""Build the pavise and glassware update against the installed Forge runtime."""

import argparse
import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory

from build import ROOT, REPO, compile_classes, entries, pack


BASE = 'empirecraft-interactions-1.1.0.jar'
BASE_HASH = '102b9144942ee983d0752b850cf2dceb0320463b5b3cc5dcf13c6c772f75fec3'
NAME = 'empirecraft-interactions-1.2.0.jar'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--client-named', type=Path, required=True, help='Minecraft 1.20.1 client in Mojang mappings')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    archive = REPO / 'disabled-mods/replaced-2026-11-01/mods' / BASE
    original = archive if archive.is_file() else REPO / 'mods' / BASE
    if hashlib.sha256(original.read_bytes()).hexdigest() != BASE_HASH:
        parser.error(f'Unexpected base artifact {original}')

    libs = REPO / 'server/libraries'
    mods = sorted((REPO / 'mods').glob('*.jar'))
    classpath = [args.sdk / 'runtime-named.jar', args.client_named, original, *sorted(libs.rglob('*.jar')), *mods]
    package = ROOT / 'src/main/java/ua/empirecraft/interactions'
    sources = [
        'GameplayInteractions', 'PaviseShapes', 'Glassware', 'BoilGlassWaterRecipe', 'mixin/PaviseShapeMixin',
        'mixin/PaviseDamageMixin', 'mixin/client/PaviseCrackRenderMixin', 'mixin/client/PaviseDamageStages',
        'mixin/KettleGlasswareMixin', 'mixin/GlassBottleDrinkMixin', 'mixin/GlasswareNameMixin',
    ]
    files = entries(original)
    with TemporaryDirectory(prefix='empirecraft-glassware-') as scratch:
        files.update(compile_classes(Path(scratch), args.sdk, libs, classpath,
                                     [package / (source + '.java') for source in sources]))
    resources = ROOT / 'src/main/resources'
    for path in [resources / 'META-INF/mods.toml', resources / 'empirecraft_interactions.mixins.json',
                 *sorted((resources / 'data').rglob('*.json'))]:
        files[path.relative_to(resources).as_posix()] = path.read_bytes()
    pack(args.output / NAME, files)


if __name__ == '__main__':
    main()
