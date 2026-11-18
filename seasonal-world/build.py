#!/usr/bin/env python3
"""Build the new paired modules on the exact installed November 18 artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
sys.path.insert(0, str(REPO / 'gameplay-interactions'))
from build import compile_classes, entries, pack

RELEASES = {
    'sails': ('vs_sails', '0.3.2-empirecraft.19.1', '0.3.2-empirecraft.19.2',
              'vs_sails-1.20.1-0.3.2-empirecraft.19.1-forge.jar', 'cc154f1316fa82b02deec6b8474ef9d59b00d8644974c9141fda861c4d8669c5'),
    'bridge': ('empirecraft_townymap', '1.13.1', '1.14.0',
               'empirecraft-towny-journeymap-1.13.1.jar', 'c97a4cf0d64e0038ef72e43d9a86ff1e2f20d04078c88bdef9c61c3781b213c8'),
    'gameplay-interactions': ('empirecraft_interactions', '1.0.0', '1.1.0',
                              'empirecraft-interactions-1.0.0.jar', 'fa04b7017579287716dc6269315227f4aea4fc3d79fa7d33ffa946511c52e491'),
    'armory': ('empirecraft_armory', '1.1.1', '1.2.0',
               'empirecraft-armory-1.1.1.jar', 'd747c12718237f55d0a1139d807bf3992dde4bbc3f45e6a62ab2a96eb9801879'),
}
JAVA = {
    'sails': ('com/quintonc/vs_sails/wind/', ('ServerWindManager', 'OceanWind')),
    'bridge': ('ua/empirecraft/townymap/', (
        'BanditTerritories', 'BridgeService', 'ClimateObservations', 'EarthMapClimate',
        'MapMode', 'TownyAccess', 'CurrencyNetwork', 'SeasonalForecast', 'client/StrategyMapUi',
    )),
    'gameplay-interactions': ('ua/empirecraft/interactions/', (
        'AnimalThreats', 'AnimalFamiliarity', 'TableAccess', 'GameplayInteractions', 'mixin/TableOwnershipMixin',
    )),
    'armory': ('ua/empirecraft/armory/', ('ArmorProvenance', 'Parts', 'WorkshopEntity')),
}
ARCHIVE = REPO / 'disabled-mods/replaced-2026-11-01/mods'
OUTPUT = ROOT / 'build/libs'


def nested_jars(target, mods):
    target.mkdir(exist_ok=True, parents=True)
    for prefix in ('journeymap-forge-', 'valkyrienskies-', 'blockandboard-', 'kotlinforforge-'):
        jar, = [p for p in mods if p.name.startswith(prefix)]
        for name, raw in entries(jar).items():
            if name.endswith('.jar'):
                path = target / Path(name).name
                path.write_bytes(raw)
    return sorted(target.glob('*.jar'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--module', choices=RELEASES, action='append')
    args = parser.parse_args()
    bases = {}
    for module, (_, _, _, name, expected) in RELEASES.items():
        archive = ARCHIVE / name
        path = archive if archive.exists() else REPO / 'mods' / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            parser.error('Unexpected base artifact ' + name)
        bases[module] = path

    mods = sorted((REPO / 'mods').glob('*.jar'))
    libs = REPO / 'server/libraries'
    ROOT.joinpath('build').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT / 'build') as scratch:
        work = Path(scratch)
        cp = [args.sdk / 'runtime-named.jar', *bases.values(), *nested_jars(work/'nested',mods),
              *sorted(libs.rglob('*.jar')), *mods, *sorted((REPO/'server/mods').glob('empirecraft-*.jar'))]
        for module, (_, old, new, name, _) in RELEASES.items():
            if args.module and module not in args.module:
                continue
            folder = work / module
            folder.mkdir()
            package, selection = JAVA[module]
            sources = [REPO/module/'src/main/java'/f'{package}{source}.java' for source in selection]
            compiled = compile_classes(folder, args.sdk, libs, cp, sources)
            files = entries(bases[module])
            files.update(compiled)
            path = 'META-INF/mods.toml'
            files[path] = files[path].replace(f'version="{old}"'.encode(), f'version="{new}"'.encode())
            files['META-INF/MANIFEST.MF'] = files['META-INF/MANIFEST.MF'].replace(
                f'Implementation-Version: {old}'.encode(), f'Implementation-Version: {new}'.encode())
            if module == 'sails':
                resources = REPO/module/'src/main/resources'
                files.update({p.relative_to(resources).as_posix(): p.read_bytes()
                              for p in (resources/'data/vs_sails/vs_sails/wind').glob('*.json')})
            elif module == 'gameplay-interactions':
                resources = REPO/module/'src/main/resources'
                files.update({p.relative_to(resources).as_posix(): p.read_bytes()
                              for p in (resources/'assets').rglob('*.json')})
            elif module == 'armory':
                sys.path.insert(0, str(REPO/'armory'))
                from resources import generate
                generated = generate()
                for lang in ('uk_ua', 'en_us'):
                    key = f'assets/empirecraft_armory/lang/{lang}.json'
                    translations = json.loads(files[key])
                    translations.update({k: v for k, v in json.loads(generated[key]).items() if k.startswith('armory.origin.')})
                    files[key] = (json.dumps(translations, ensure_ascii=False, indent=2)+'\n').encode()
            target = args.output / name.replace(old, new)
            pack(target, files)
            print(target, flush=True)


if __name__ == '__main__':
    main()
