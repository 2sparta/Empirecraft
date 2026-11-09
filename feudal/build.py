#!/usr/bin/env python3
"""Build with JDK 21+, a Java 17 bytecode target, Python 3 and pinned API jars. No Gradle or Maven required."""
import argparse, hashlib, io, os, pathlib, shutil, subprocess, sys, urllib.request, urllib.error, zipfile
from release import PLUGIN
ROOT = pathlib.Path(__file__).resolve().parent
LIB = ROOT / 'lib'
BUILD = ROOT / 'build'
APIS = [
 ('spigot-api.jar', 'https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.20.1-R0.1-SNAPSHOT/spigot-api-1.20.1-R0.1-20230921.163938-66-shaded.jar', '465c8fce2efa60da5517c312dcc57c957908c608f23f92b14216caecc0e85713'),
 ('h2.jar', 'https://repo.maven.apache.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar', 'b9d8f19358ada82a4f6eb5b174c6cfe320a375b5a9cb5a4fe456d623e6e55497'),
 ('Towny-0.101.2.0.jar', 'https://github.com/TownyAdvanced/Towny/releases/download/0.101.2.0/Towny.Advanced.0.101.2.0.zip', 'c21dd5c8e63401fe5f14be77a33e96afa727b9c8214a7f85c2c4f67b761645a6')
]
def java_tool(name):
    # Towny's scheduler requires Java 21 at runtime; compilation can still target 17.
    if name == 'java' and os.environ.get('FEUDAL_RUNTIME_JAVA'):
        return [os.environ['FEUDAL_RUNTIME_JAVA']]
    home = os.environ.get('JAVA_HOME')
    binary = pathlib.Path(home) / 'bin' / (name + ('.exe' if os.name == 'nt' else '')) if home else shutil.which(name)
    if binary and pathlib.Path(binary).exists():
        return [str(binary)]
    # Some packaged JDKs include jdk.compiler but omit the javac launcher.
    if name == 'javac':
        java = java_tool('java')
        probe = subprocess.run([*java, 'com.sun.tools.javac.Main', '-version'], capture_output=True)
        if probe.returncode == 0:
            return [*java, 'com.sun.tools.javac.Main']
    raise SystemExit('Потрібен JDK 17+ (java та компілятор javac).')
def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-api', type=pathlib.Path, help='Compile against an installed server API jar instead of downloading Spigot')
    parser.add_argument('--api-libraries', type=pathlib.Path, help='Dependency jars for the installed server API')
    parser.add_argument('--output-dir', type=pathlib.Path, help='Build artifacts here without replacing server plugins')
    options = parser.parse_args()
    api = options.server_api.resolve() if options.server_api else LIB / 'spigot-api.jar'
    if options.server_api:
        if not api.is_file(): parser.error('--server-api must be a jar file')
        print('Installed server API:', api, 'SHA-256:', hashlib.sha256(api.read_bytes()).hexdigest())
    LIB.mkdir(exist_ok=True)
    for name, url, sha in APIS:
        path = LIB / name
        if not path.exists():
            print('Завантаження:', name, flush=True)
            mirrors = [url]
            if name == 'spigot-api.jar':
                mirrors.append(url.replace('https://hub.spigotmc.org/nexus/content/repositories/snapshots', 'https://repo.papermc.io/repository/maven-public'))
            if name == 'h2.jar':
                mirrors.append(url.replace('repo.maven.apache.org/maven2', 'repo1.maven.org/maven2'))
            for attempt in range(3):
                for mirror in mirrors:
                    try:
                        request = urllib.request.Request(mirror, headers={'User-Agent': 'EmpirecraftFeudal-build/1.0'})
                        with urllib.request.urlopen(request, timeout=120) as response:
                            data = response.read()
                        break
                    except (urllib.error.URLError, TimeoutError):
                        if attempt == 2 and mirror == mirrors[-1]:
                            raise
                else:
                    continue
                break
            if url.endswith('.zip'):
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    entry = next(n for n in archive.namelist() if n.endswith('/' + name))
                    data = archive.read(entry)
            if hashlib.sha256(data).hexdigest() != sha:
                raise SystemExit('SHA-256 залежності не збігається: ' + name)
            path.write_bytes(data)
        if hashlib.sha256(path.read_bytes()).hexdigest() != sha:
            raise SystemExit('Пошкоджена залежність: ' + str(path))
    classes = BUILD / 'classes'
    tests = BUILD / 'test-classes'
    for path in (classes, tests):
        if path.exists(): shutil.rmtree(path)
        path.mkdir(parents=True)
    classpath = os.pathsep.join(str(p) for p in [api, *(LIB / name for name, _, _ in APIS if name != 'spigot-api.jar'), *(sorted(options.api_libraries.resolve().rglob('*.jar')) if options.api_libraries else [])])
    run(*java_tool('javac'), '-proc:none', '--release', '17', '-encoding', 'UTF-8', '-cp', classpath, '-d', classes, *sorted((ROOT / 'src/main/java').rglob('*.java')))
    # Standalone integration runners also use build/classes as their runtime classpath.
    shutil.copytree(ROOT / 'src/main/resources', classes, dirs_exist_ok=True)
    run(*java_tool('javac'), '-proc:none', '--release', '17', '-encoding', 'UTF-8', '-cp', str(classes) + os.pathsep + classpath, '-d', tests, *sorted((ROOT / 'src/test/java').rglob('*.java')))
    suites = ('FeudalTests', 'CurrencyTests', 'RealmCommerceTests', 'GoldInventoryTests', 'ItemPlanningTests',
              'ProvisionTests', 'PoliticsTests', 'PoliticsDebugTests', 'PoliticalRegressionTests',
              'PoliticsProvisionTests', 'ChatTitleTests', 'CurrencyMarketTests', 'CurrencyScreenTests',
              'CurrencyExchangeTests', 'CommandLanguageTests', 'PhysicalGoldTests', 'TradeTownyTests',
              'KingdomTests', 'ProvisionClaimTests', 'CityMarketTests', 'MarketExtensionTests', 'MarketMailTests', 'MarketNoticeTests', 'MarketFishingTests', 'ContractTests', 'FleetTests', 'ShipRegistrationTests', 'RecruitMarketTests', 'RecruitTownyRuntimeTests', 'RuntimeCompatibilityTests', 'DiscordTests', 'LocalizationTests')
    for suite in suites:
        # Proxy-based Bukkit tests need the genuine upstream interfaces without
        # starting Forge registries. Main compilation above still uses the exact
        # installed hybrid API; native smoke tests boot the real server separately.
        run(*java_tool('java'), '-cp', os.pathsep.join((str(classes), str(tests), str(ROOT / "src/main/resources"), str(LIB/'spigot-api.jar'), classpath)), 'ua.empirecraft.feudal.' + suite)
    target = (options.output_dir.resolve() if options.output_dir else ROOT.parent / 'server/plugins') / PLUGIN
    target.parent.mkdir(parents=True, exist_ok=True)
    for old in target.parent.glob('empirecraft-feudal-*.jar'):
        if old != target:
            old.unlink(); old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
        for base in (classes, ROOT / 'src/main/resources'):
            for file in sorted(base.rglob('*')):
                if file.is_file() and (base != classes or file.suffix == '.class'):
                    info = zipfile.ZipInfo(file.relative_to(base).as_posix(), (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o644 << 16
                    jar.writestr(info, file.read_bytes())
        # Bundle the database so the server needs no runtime network access.
        with zipfile.ZipFile(LIB / 'h2.jar') as dependency:
            for entry in sorted(dependency.namelist()):
                if entry.endswith('/') or entry == 'META-INF/MANIFEST.MF' or entry.endswith(('.SF', '.RSA', '.DSA')) or entry.endswith('module-info.class'):
                    continue
                info = zipfile.ZipInfo(entry, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                jar.writestr(info, dependency.read(entry))
    checksum = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.jar.sha256').write_text(checksum + '  ' + target.name + '\n', encoding='utf-8')
    print('Готово:', target, '\nSHA-256:', checksum)
if __name__ == '__main__': main()
