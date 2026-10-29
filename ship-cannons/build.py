#!/usr/bin/env python3
"""Build against the pinned Empirecraft Forge/VS runtime; no bundled game classes."""
import argparse, hashlib, json, os, shutil, subprocess, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
SHARED = REPO / 'sails/build'
VERSION = '1.2.0'

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def jar(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as out:
        for name, data in sorted(entries.items()):
            item = zipfile.ZipInfo(name, (2026, 10, 28, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            out.writestr(item, data)

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--server-home', type=Path, default=REPO/'server')
    ap.add_argument('--prepare-only', action='store_true')
    args = ap.parse_args()
    build = ROOT/'build'; build.mkdir(parents=True, exist_ok=True)
    SHARED.mkdir(parents=True,exist_ok=True)
    downloads=json.loads((REPO/'sails/build-inputs.json').read_text())['downloads']
    for name in ['tiny-remapper.jar','mapping-io.jar','client-mappings.txt','annotations.jar','client-original.jar']:
        item=downloads[name];path=SHARED/name
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:
            temporary=path.with_suffix(path.suffix+'.download')
            with urllib.request.urlopen(item['url'],timeout=60) as response:temporary.write_bytes(response.read())
            if temporary.stat().st_size!=item['size'] or hashlib.sha256(temporary.read_bytes()).hexdigest()!=item['sha256']:
                temporary.unlink(missing_ok=True);raise SystemExit('Checksum mismatch: '+name)
            os.replace(temporary,path)
    libraries = args.server_home/'libraries'
    def need(pattern, root=libraries):
        values = list(root.rglob(pattern))
        if len(values) != 1:
            raise SystemExit(f'Expected one {pattern} in {root}, found {len(values)}')
        return values[0]
    java = Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java')
    compiler = [str(java), 'com.sun.tools.javac.Main']
    srgutils = need('srgutils-0.5.4.jar')
    asm = sorted((libraries/'org/ow2/asm').rglob('*.jar'))
    remapper = os.pathsep.join(map(str, [SHARED/'tiny-remapper.jar', SHARED/'mapping-io.jar', *asm]))
    mapping = SHARED/'named-srg.tiny'
    if not mapping.exists():
        tools = build/'tools'; tools.mkdir(exist_ok=True)
        run(*compiler, '--release', '17', '-cp', srgutils, '-d', tools, REPO/'sails/tools/CreateMappings.java')
        with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as z:
            (SHARED/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
        run(java, '-cp', os.pathsep.join(map(str, [tools, srgutils])), 'CreateMappings', SHARED/'client-mappings.txt', SHARED/'joined.tsrg', mapping, SHARED/'client-named.tiny')
    kotlin = need('kotlinforforge-4.12.0-all*.jar', REPO/'mods')
    mods = [need('valkyrienskies-120-2.4.11-empirecraft.1.jar', REPO/'mods'), need('create-1.20.1-6.0.8-medieval.4.jar', REPO/'mods'), need('copycats-3.0.10*recipes.1.jar', REPO/'mods'), need('architectury-9.2.14-forge.jar', REPO/'mods'), kotlin]
    for item in json.loads((ROOT/'build-inputs.json').read_text())['runtime']:
        path=need(item['pattern'],REPO/'mods' if item['kind'] in {'mod','compat'} else libraries)
        if hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:
            raise SystemExit('Changed pinned runtime input: '+path.name)
    nested = build/'nested'; nested.mkdir(exist_ok=True)
    for mod in mods:
        with zipfile.ZipFile(mod) as z:
            for name in z.namelist():
                if name.endswith('.jar'):
                    (nested/Path(name).name).write_bytes(z.read(name))
    # Forge's MixinExtras bundle contains its common API in a second nested JAR.
    # Resolve transitive embedded libraries too, rather than compiling against a partial API.
    pending=list(sorted(nested.glob('*.jar')));seen=set()
    while pending:
        archive=pending.pop();digest=hashlib.sha256(archive.read_bytes()).hexdigest()
        if digest in seen:continue
        seen.add(digest)
        if len(seen)>512:raise SystemExit('Too many nested runtime libraries')
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not name.endswith('.jar'):continue
                data=z.read(name);target=nested/Path(name).name
                if not target.exists() or target.read_bytes()!=data:
                    target.write_bytes(data)
                if hashlib.sha256(data).hexdigest() not in seen:pending.append(target)
    deps = [*sorted(libraries.rglob('*.jar')), *sorted(nested.glob('*.jar'))]
    named = build/'runtime-named.jar'
    if not named.exists():
        inputs = [need('server-1.20.1-20230612.114412-srg.jar'), need('forge-1.20.1-47.4.23-server.jar'), need('forge-1.20.1-47.4.23-universal.jar'), *mods]
        entries = {}
        for path in inputs:
            with zipfile.ZipFile(path) as z:
                entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
        jar(build/'runtime-srg.jar', entries)
        run(java, '-Xmx2G', '-cp', remapper, 'dev.architectury.tinyremapper.Main', build/'runtime-srg.jar', named, mapping, 'srg', 'named', '--ignoreConflicts')
    client = build/'client-named.jar'
    if not client.exists():
        run(java, '-Xmx2G', '-cp', remapper, 'dev.architectury.tinyremapper.Main', SHARED/'client-original.jar', client, SHARED/'client-named.tiny', 'official', 'named', '--ignoreConflicts')
    client_srg=build/'client-srg.jar'
    if not client_srg.exists():run(java,'-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',client,client_srg,mapping,'named','srg',named,'--ignoreConflicts')
    extra=[need(item['pattern'],REPO/'mods') for item in json.loads((ROOT/'build-inputs.json').read_text())['runtime'] if item['kind']=='compat']
    entries={}
    for path in extra:
        with zipfile.ZipFile(path) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    jar(build/'compat-srg.jar',entries)
    (build/'compat-named.jar').unlink(missing_ok=True)
    run(java,'-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',build/'compat-srg.jar',build/'compat-named.jar',mapping,'srg','named',build/'runtime-srg.jar',client_srg,*deps,'--ignoreConflicts')
    cp = os.pathsep.join(map(str, [build/'compat-named.jar',named, client, SHARED/'annotations.jar', *deps]))
    (build/'classpath.txt').write_text(cp)
    if args.prepare_only:
        print('Pinned compilation runtime prepared'); return
    classes = build/'classes'
    shutil.rmtree(classes, ignore_errors=True); classes.mkdir()
    sources = sorted((ROOT/'src/main/java').rglob('*.java'))
    flags = ['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sources)]
    argfile=build/'javac.args'
    argfile.write_text('\n'.join('"'+x.replace('\\','\\\\').replace('"','\\"')+'"' for x in flags))
    run(*compiler, '@'+str(argfile))
    jar(build/'patch-named.jar', {str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    (build/'patch-srg.jar').unlink(missing_ok=True)
    run(java, '-Xmx1G', '-cp', remapper, 'dev.architectury.tinyremapper.Main', build/'patch-named.jar', build/'patch-srg.jar', mapping, 'named', 'srg', named, client, '--ignoreConflicts')
    with zipfile.ZipFile(build/'patch-srg.jar') as z:
        entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    resources=ROOT/'src/main/resources'
    entries.update({str(p.relative_to(resources)):p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    entries['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes()
    output=build/f'empirecraft-ship-cannons-{VERSION}.jar'; jar(output, entries)
    assert zipfile.ZipFile(output).testzip() is None
    checksum=hashlib.sha256(output.read_bytes()).hexdigest()
    for folder in [REPO/'mods',REPO/'server/mods']:
        folder.mkdir(parents=True,exist_ok=True);shutil.copyfile(output,folder/output.name)
        (folder/(output.name+'.sha256')).write_text(checksum+'  '+output.name+'\n')
    print(output.name, checksum)

if __name__ == '__main__': main()
