#!/usr/bin/env python3
"""Build a reproducible, version-pinned medieval Create JAR and server overlay using JDK 17+."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from policy import allowed, recipe_reason, CORE, DISABLED_TYPES

ROOT = Path(__file__).resolve().parent
SOURCE_SHA256 = '6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0'
VERSION = '6.0.8-medieval.2'
NAME = 'create-1.20.1-' + VERSION + '.jar'
EXPORTS = [f'--add-exports=java.base/jdk.internal.org.objectweb.asm{suffix}=ALL-UNNAMED'
           for suffix in ('', '.tree', '.tree.analysis')]

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding='utf-8')

def pack(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    with zipfile.ZipFile(temporary, 'w') as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 24, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    with temporary.open('rb') as stream:
        os.fsync(stream.fileno())
    os.replace(temporary, path)

def ingredient(value):
    return {'tag': value[1:]} if value.startswith('#') else {'item': value}

def manual_recipes():
    recipes = {}
    def shaped(name, result, count, pattern, keys):
        recipes[name] = {'type': 'minecraft:crafting_shaped', 'category': 'misc',
                         'pattern': pattern, 'key': {k: ingredient(v) for k, v in keys.items()},
                         'result': {'item': result, 'count': count}}
    def shapeless(name, result, count, inputs):
        recipes[name] = {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                         'ingredients': list(map(ingredient, inputs)), 'result': {'item': result, 'count': count}}
    shapeless('brass', 'create:brass_ingot', 4, ['minecraft:copper_ingot'] * 3 + ['create:zinc_ingot'])
    shapeless('andesite_casing', 'create:andesite_casing', 1, ['#minecraft:logs', 'create:andesite_alloy'])
    for material, ingot, sheet in (
        ('iron', 'minecraft:iron_ingot', 'create:iron_sheet'),
        ('copper', 'minecraft:copper_ingot', 'create:copper_sheet'),
        ('gold', 'minecraft:gold_ingot', 'create:golden_sheet'),
        ('brass', 'create:brass_ingot', 'create:brass_sheet'),
    ):
        shapeless(material + '_sheet_by_hand', sheet, 1, [ingot, ingot])
    shaped('brass_casing', 'create:brass_casing', 1, ['PPP', 'PBP', 'PPP'], {'P': '#minecraft:planks', 'B': 'create:brass_ingot'})
    shaped('sequenced_gearshift', 'create:sequenced_gearshift', 1, [' C ', 'GBG', ' R '],
           {'C': 'minecraft:clock', 'G': 'create:cogwheel', 'B': 'create:brass_casing', 'R': 'minecraft:redstone'})
    shaped('adjustable_chain_gearshift', 'create:adjustable_chain_gearshift', 1, [' G ', 'CRC', ' A '],
           {'G': 'create:cogwheel', 'C': 'minecraft:chain', 'R': 'minecraft:redstone', 'A': 'create:andesite_casing'})
    shaped('encased_chain_drive', 'create:encased_chain_drive', 2, ['CCC', 'SAS'],
           {'C': 'minecraft:chain', 'S': 'create:shaft', 'A': 'create:andesite_casing'})
    shaped('contraption_controls', 'create:contraption_controls', 1, [' L ', 'CAC', ' R '],
           {'L': 'minecraft:lever', 'C': 'create:cogwheel', 'A': 'create:andesite_casing', 'R': 'minecraft:redstone'})
    shaped('redstone_contact', 'create:redstone_contact', 2, ['IQR', 'AAA'],
           {'I': 'minecraft:iron_ingot', 'Q': 'minecraft:quartz', 'R': 'minecraft:redstone', 'A': 'create:andesite_alloy'})
    return recipes

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='Unmodified Create 1.20.1 6.0.8 JAR, pinned by SHA-256')
    parser.add_argument('--output', type=Path, default=ROOT / 'dist')
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    if hashlib.sha256(args.source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise SystemExit('Unsupported source JAR: SHA-256 does not match tested Create 6.0.8.')
    build = ROOT / 'build'
    if build.exists(): shutil.rmtree(build)
    classes, patched, tool = (build / x for x in ('classes', 'patched', 'tool'))
    for directory in (classes, patched, tool): directory.mkdir(parents=True)
    subprocess.run([args.java, 'com.sun.tools.javac.Main', '--release', '17', '-d', str(classes),
                    str(ROOT / 'java/MedievalPolicy.java')], check=True)
    subprocess.run([args.java, 'com.sun.tools.javac.Main', *EXPORTS, '-source', '17', '-target', '17',
                    '-d', str(tool), str(ROOT / 'java/PatchCreate.java')], check=True)
    subprocess.run([args.java, *EXPORTS, '-cp', str(tool), 'PatchCreate', str(args.source), str(patched)], check=True)
    with zipfile.ZipFile(args.source) as original:
        files = {entry.filename: original.read(entry) for entry in original.infolist() if not entry.is_dir()}
    language = json.loads(files['assets/create/lang/en_us.json'])
    ids = {key.split('.')[2] for key in language if re.fullmatch(r'(?:item|block)\.create\.[a-z0-9_]+', key)}
    ids.update(Path(n).stem for n in files if re.fullmatch(r'assets/create/models/item/[^/]+\.json', n))
    ids.update(CORE)
    allow = sorted('create:' + name for name in ids if allowed('create:' + name))
    deny = sorted('create:' + name for name in ids if not allowed('create:' + name))
    files['empirecraft/medieval-create-allowed.txt'] = ('\n'.join(allow) + '\n').encode()
    deleted, kept = {}, {}
    removed_types = Counter()
    for name in list(files):
        if name.startswith('data/create/recipes/') and name.endswith('.json'):
            recipe = json.loads(files[name])
            reason = recipe_reason(recipe)
            if reason:
                deleted[name] = reason
                removed_types[recipe['type']] += 1
                del files[name]
            else:
                kept[name] = recipe
        # Deleted recipes must not leave recipe advancements pointing at absent IDs.
        if name.startswith('data/create/advancements/recipes/'):
            del files[name]
    for name, recipe in manual_recipes().items():
        assert recipe_reason(recipe) is None, (name, recipe_reason(recipe))
        files['data/empirecraft/recipes/medieval_create/' + name + '.json'] = json.dumps(recipe, indent=2).encode()
    for path in classes.rglob('*.class'):
        files[path.relative_to(classes).as_posix()] = path.read_bytes()
    for path in patched.rglob('*.class'):
        files[path.relative_to(patched).as_posix()] = path.read_bytes()
    metadata = files['META-INF/mods.toml'].decode()
    metadata = metadata.replace('version = "6.0.8"', f'version = "{VERSION}"')
    metadata = metadata.replace('displayName = "Create"', 'displayName = "Create: Medieval Empirecraft"')
    files['META-INF/mods.toml'] = metadata.encode()
    manifest = files['META-INF/MANIFEST.MF'].decode()
    manifest = re.sub(r'(?m)^Implementation-Version:[^\r\n]*', 'Implementation-Version: ' + VERSION, manifest)
    files['META-INF/MANIFEST.MF'] = manifest.encode()
    # Preserve upstream licensing and clearly identify this modified distribution.
    files['META-INF/EMPIRECRAFT-MEDIEVAL.txt'] = (f'Modified Create {VERSION}. Source SHA-256: {SOURCE_SHA256}\n'
        'Medieval profile source: https://github.com/2sparta/Empirecraft/tree/codex/medieval-create/medieval-create\n'
        'Registry IDs are preserved for world compatibility; disabled content is guarded at runtime.\n').encode()
    files['META-INF/EMPIRECRAFT-LICENSE.txt'] = (ROOT / 'LICENSE').read_bytes()
    jar = args.output / NAME
    pack(jar, files)
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    write(jar.with_suffix('.jar.sha256'), digest + '  ' + NAME + '\n')
    report = {'source_sha256': SOURCE_SHA256, 'version': VERSION, 'sha256': digest,
              'allowed_ids': allow, 'disabled_ids': deny, 'removed_recipes': deleted,
              'kept_recipe_count': len(kept), 'manual_recipe_count': len(manual_recipes()),
              'removed_recipe_types': dict(removed_types),
              'patched_methods': (patched / 'patches.txt').read_text().splitlines(),
              'verification': {'asm_basic_verifier': 'passed', 'minecraft_startup': 'not run', 'graphical_client': 'not run'}}
    write(args.output / 'validation.json', json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    # KubeJS is already part of Empirecraft. Remove addon recipes too, on every reload.
    js = '// Generated by medieval-create/build.py; requires existing KubeJS 2001.6.5.\n'
    js += 'ServerEvents.recipes(event => {\n'
    js += '  const disabled = ' + json.dumps(deny) + ';\n'
    js += '  disabled.forEach(id => { event.remove({output: id}); event.remove({input: id}); });\n'
    js += '  const types = ' + json.dumps(sorted(DISABLED_TYPES)) + ';\n'
    js += '  types.forEach(type => event.remove({type: type}));\n'
    js += '  event.remove({type: "create:milling", output: /.*(?:iron|gold|copper|zinc|brass|silver|tin|lead|nickel|alumin|osmium|uranium|platinum|quicksilver|experience|raw_|crushed_).*/});\n'
    js += '});\n'
    write(args.output / 'overlay/kubejs/server_scripts/zz_medieval_create.js', js)
    overlay = {'mods/' + NAME: jar.read_bytes(),
               'kubejs/server_scripts/zz_medieval_create.js': js.encode(),
               'tools/update_adm.py': (ROOT / 'update_adm.py').read_bytes(),
               'README-UK.md': (ROOT / 'README.md').read_bytes(),
               'validation.json': json.dumps(report, indent=2).encode()}
    pack(args.output / 'Empirecraft-Create-Medieval-1.0.1.zip', overlay)
    bundle=args.output/'Empirecraft-Create-Medieval-1.0.1.zip'
    write(bundle.with_suffix('.zip.sha256'),hashlib.sha256(bundle.read_bytes()).hexdigest()+'  '+bundle.name+'\n')
    print(json.dumps({'jar': str(jar), 'sha256': digest, 'kept_recipes': len(kept),
                      'removed_recipes': len(deleted), 'manual_recipes': len(manual_recipes()),
                      'allowed_ids': len(allow), 'disabled_ids': len(deny)}))

if __name__ == '__main__': main()

