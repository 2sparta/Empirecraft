#!/usr/bin/env python3
"""Build the coordinated firearm update from the checkout's installed JARs."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
BUILD = ROOT / 'build/firearms'
SIDES = [REPO / 'mods', REPO / 'server/mods']
EXPORTS = ['--add-exports=java.base/jdk.internal.org.objectweb.asm' + suffix + '=ALL-UNNAMED'
           for suffix in ('', '.tree')]
sys.path.insert(0, str(REPO / 'native-content'))
from build import archive, entries


BASE_REF = '787ee683a2753dc2b4cbc8b3a155987e3f65747e'


def baseline(pattern):
    path = installed(pattern)
    raw = subprocess.check_output(['git', 'show', f'{BASE_REF}:mods/{path.name}'], cwd=REPO)
    return entries(raw)


def java(tool):
    return str(Path(os.environ['JAVA_HOME']) / 'bin' / tool) if 'JAVA_HOME' in os.environ else tool


def installed(pattern):
    matches = list(SIDES[0].glob(pattern))
    if len(matches) != 1:
        raise ValueError(f'Expected one {pattern}, found {matches}')
    return matches[0]


def compile_java(sources, destination, classpath='', options=()):
    destination.mkdir(parents=True, exist_ok=True)
    subprocess.run([java('javac'), '-source', '17', '-target', '17', '-proc:none', '-encoding', 'UTF-8',
                    *options, '-cp', classpath, '-d', str(destination), *map(str, sources)], check=True)


def installed_classpath():
    jars = [*SIDES[0].glob('*.jar'), *SIDES[1].glob('*.jar'), *(REPO / 'server/libraries').rglob('*.jar')]
    jars = sorted(set(jars), key=lambda path: (not path.name.endswith('-47.4.23-server.jar'), str(path)))
    return os.pathsep.join(map(str, jars))


def dump(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()


def translations(tyw):
    source = json.loads(tyw['assets/thirtyyearswar/lang/en_us.json'])
    for lang in ('uk_ua', 'ru_ru'):
        raw = (ROOT / 'lang' / f'{lang}.json').read_bytes()
        data = json.loads(raw)
        assert data.keys() == source.keys(), lang
        for key, line in data.items():
            tokens = r'%(?:\d+\$)?[sd]|%%'
            assert sorted(re.findall(tokens, line)) == sorted(re.findall(tokens, source[key])), key
        tyw[f'assets/thirtyyearswar/lang/{lang}.json'] = raw


def aim_overlay(tyw, classpath):
    stubs = []
    for name, content in json.loads((ROOT / 'src/aim-client-abi.json').read_text()).items():
        package, _ = name.rsplit('/', 1)
        path = BUILD / 'client-abi' / (name + '.java')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f'package {package.replace("/", ".")};\n{content}\n')
        stubs.append(path)
    classes = BUILD / 'aim'
    compile_java([*stubs, ROOT / 'src/AimOverlay.java'], classes, classpath)
    name = 'ua/empirecraft/thirtyyearswar/AimOverlay.class'
    tyw[name] = (classes / name).read_bytes()


def trim_boomsticks(data):
    compiled = BUILD / 'patch'
    compile_java([ROOT / 'src/TrimBoomsticks.java'], compiled, options=EXPORTS)
    work = BUILD / 'boomsticks'
    for name, raw in data.items():
        if name.endswith('.class'):
            path = work / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
    subprocess.run([java('java'), *EXPORTS, '-cp', str(compiled), 'TrimBoomsticks', str(work)], check=True)
    for path in work.rglob('*.class'):
        data[path.relative_to(work).as_posix()] = path.read_bytes()
    retired = re.compile(r'(?:arquebus|handgonne|round_ball|upgrade_kit)')
    for name in list(data):
        if name.startswith(('assets/', 'data/')) and retired.search(name):
            del data[name]
        elif name.startswith(('assets/', 'data/')) and name.endswith('.json'):
            raw = data[name]
            if not retired.search(raw.decode(errors='replace')):
                continue
            if '/lang/' in name:
                text = raw.decode('utf-8-sig')
                # A shipped German entry lacks its trailing comma; discard retired keys before parsing.
                text = re.sub(r'^\s*"[^"\n]*(?:arquebus|handgonne|round_ball|upgrade_kit)[^"\n]*"\s*:[^\n]*\n', '', text, flags=re.M)
                text = re.sub(r',\s*}', '\n}', text)
                data[name] = dump(json.loads(text, strict=False))
            elif name.endswith('/sounds.json'):
                value = json.loads(raw)
                data[name] = dump({key: item for key, item in value.items() if not retired.search(key)})
            elif '/recipes/' in name or '/advancements/' in name:
                del data[name]


def remove_dependency(data, mod, dep):
    text = data['META-INF/mods.toml'].decode()
    pattern = rf'\[\[dependencies\.{mod}\]\]\s+modId\s*=\s*"{dep}".*?(?=\[\[|\Z)'
    text, count = re.subn(pattern, '', text, flags=re.S)
    assert count == 1, (mod, dep)
    data['META-INF/mods.toml'] = text.encode()


def build_updates():
    cp = installed_classpath()
    tyw = baseline('epic-knights-thirty-years-war-*-empirecraft.1.jar')
    translations(tyw)
    aim_overlay(tyw, cp)

    medieval = baseline('medieval_boomsticks-*.jar')
    trim_boomsticks(medieval)

    components = baseline('tfc_muskets-*.jar')
    remove_dependency(components, 'tfc_muskets', 'musketmod')

    tfc = baseline('empirecraft-tfc-integration-*.jar')
    policy_path = 'empirecraft-tfc-policy.json'
    policy = json.loads(tfc[policy_path])
    removed_recipes = {
        *policy['guns'], 'musketmod:cartridge', 'empirecraft_tfc:blunderbuss_barrel',
    }
    for recipe_id in removed_recipes:
        namespace, recipe = recipe_id.split(':', 1)
        tfc.pop(f'data/{namespace}/recipes/{recipe}.json', None)
        policy['overrides'].pop(recipe_id, None)
    tfc[policy_path] = dump(policy)

    casting = baseline('empirecraft-casting-*.jar')
    for index, retired in {
        'thermal-recipes.json': ['arquebus/barrel_blank', 'arquebus/barrel', 'arquebus/ammunition', 'arquebus/assembly'],
        'content-recipes.json': ['ammo/musket_cartridge'],
    }.items():
        recipes = json.loads(casting[index])
        for name in retired:
            recipes.pop(f'empirecraft_casting:{name}', None)
            casting.pop(f'data/empirecraft_casting/recipes/{name}.json', None)
        casting[index] = dump(recipes)
    for locale, pages in [('uk_ua', 6), ('en_us', 5)]:
        path = f'assets/empirecraft_guidebook/patchouli_books/handbook/{locale}/entries/metals/heat_and_arquebus.json'
        guide = json.loads(casting[path])
        guide['pages'] = guide['pages'][:pages]
        casting[path] = dump(guide)

    recruits = baseline('TownyRecruits.jar')
    remove_dependency(recruits, 'townyrecruits', 'musketmod')
    remove_dependency(recruits, 'townyrecruits', 'medieval_boomsticks')
    recruits['META-INF/mods.toml'] += b'\n[[dependencies.townyrecruits]]\nmodId="thirtyyearswar"\nmandatory=true\nversionRange="[1.3,)"\nordering="AFTER"\nside="BOTH"\n'
    mixins = json.loads(recruits['townyrecruits.mixins.json'])
    mixins['mixins'].remove('ProjectileMixin')
    recruits['townyrecruits.mixins.json'] = dump(mixins)
    del recruits['ua/townyrecruits/mixin/ProjectileMixin.class']
    classes = BUILD / 'recruits'
    compile_java([REPO / 'towny-recruits/src/main/java/ua/townyrecruits/GunGoal.java'], classes, cp)
    recruits['ua/townyrecruits/GunGoal.class'] = (classes / 'ua/townyrecruits/GunGoal.class').read_bytes()

    return {
        'empirecraft_casting': (installed('empirecraft-casting-*.jar'), archive(casting)),
        'thirtyyearswar': (installed('epic-knights-thirty-years-war-*-empirecraft.1.jar'), archive(tyw)),
        'medieval_boomsticks': (installed('medieval_boomsticks-*.jar'), archive(medieval)),
        'tfc_muskets': (installed('tfc_muskets-*.jar'), archive(components)),
        'empirecraft_tfc': (installed('empirecraft-tfc-integration-*.jar'), archive(tfc)),
        'townyrecruits': (installed('TownyRecruits.jar'), archive(recruits)),
        'empirecraft_forging': (installed('empirecraft-forging-*.jar'), installed('empirecraft-forging-*.jar').read_bytes()),
    }


def install(updates):
    for path, raw in updates.values():
        for side in SIDES:
            target = side / path.name
            target.write_bytes(raw)
            target.with_suffix('.jar.sha256').write_text(hashlib.sha256(raw).hexdigest() + '  ' + path.name + '\n')
    backup = REPO / 'disabled-mods/firearms-2026-11-18'
    for side, label in zip(SIDES, ('client', 'server')):
        for path in side.glob('musketmod-*.jar*'):
            target = backup / label / 'mods' / path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            path.rename(target)


if __name__ == '__main__':
    updates = build_updates()
    if '--install' in sys.argv:
        install(updates)
    else:
        BUILD.mkdir(parents=True, exist_ok=True)
        for path, raw in updates.values():
            (BUILD / path.name).write_bytes(raw)
