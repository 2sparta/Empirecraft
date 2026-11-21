"""Fresh official installation; optional real graphical boot of the client pack."""
import argparse
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from empirecraft_launcher.security import verify_manifest, digest, safe_path
from empirecraft_launcher.game import install_runtime, make_command
from empirecraft_launcher.updater import resources, install_pack
from empirecraft_launcher.profiles import apply_options

args = argparse.ArgumentParser()
args.add_argument('--boot-pack', action='store_true')
args.add_argument('--profile', choices=['potato','balanced','high'], default='potato')
args.add_argument('--menu-capture', type=Path, help='Capture and exercise the real X11 game menu')
args.add_argument('--gameplay-probe', type=Path, help='Exercise the offline kitchen fixture and collect logs/screenshots')
args.add_argument('--official-cache', type=Path, help='CI-only cache of a completed official runtime; no worlds or modpack data')
args.add_argument('--seed-pack-cache', type=Path, help='Seed the regular SHA-256 download cache from a matching local client snapshot')
args.add_argument('--overlay', type=Path, help='Trusted integration fixture copied only into the disposable game directory')
args.add_argument('--replace-mod-prefix', action='append', default=[])
options = args.parse_args()
manifest = verify_manifest((resources()/'bootstrap.json').read_bytes(), base64.b64decode((resources()/'trusted-key.txt').read_text()))
with tempfile.TemporaryDirectory(prefix='empirecraft-runtime-') as folder:
    root = Path(folder)
    official_cache = options.official_cache.resolve() if options.official_cache else None
    if official_cache and official_cache.exists():
        pin = official_cache/'.forge-complete.json'
        if not pin.is_file() or json.loads(pin.read_text()).get('installer') != manifest['installer']['sha256']:
            raise ValueError('Official CI runtime cache pin differs from the signed installer')
        shutil.copytree(official_cache, root/'runtime', symlinks=True)
    java = install_runtime(root, manifest, print)
    if official_cache and not official_cache.exists():
        official_cache.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(root/'runtime', official_cache, symlinks=True)
        print('OFFICIAL_CI_RUNTIME_CACHED', flush=True)
    game = root/'instances'/options.profile
    game.mkdir(parents=True)
    if options.boot_pack:
        if options.seed_pack_cache:
            seed = options.seed_pack_cache.resolve()
            cache = root/'cache'; cache.mkdir(exist_ok=True)
            seeded = 0
            for item in manifest['profiles'][options.profile]['files']:
                source = seed/Path(str(safe_path(item['path'])))
                if source.is_file() and not source.is_symlink() and source.resolve().is_relative_to(seed) and source.stat().st_size == item['size'] and digest(source) == item['sha256']:
                    shutil.copy2(source, cache/item['sha256']); seeded += 1
                elif item['url'].startswith('https://raw.githubusercontent.com/'):
                    raise ValueError('Local CI snapshot differs from the signed file: '+item['path'])
            print('SIGNED_PACK_CACHE_SEEDED', seeded, flush=True)
        game = install_pack(root, manifest, options.profile)
        for old in (game/'mods').glob('empirecraft-launcher-client-*.jar'):
            old.unlink()
        addon = Path(__file__).resolve().parents[1]/'native/dist/empirecraft-launcher-client-0.3.0.jar'
        shutil.copy2(addon, game/'mods'/addon.name)
        apply_options(game, options.profile, {})
        # Unattended fixture: log nonfatal Forge warnings instead of awaiting a click.
        # Forge still displays and fails on actual mod-loading errors.
        (game/'config/forge-client.toml').write_text('[client]\nshowLoadWarnings = false\n')
        # An unversioned options file goes through vanilla's legacy DataFixer,
        # which enables the first-run accessibility screen again.
        version = manifest['minecraft']
        with ZipFile(root / 'runtime' / 'versions' / version / f'{version}.jar') as vanilla:
            options_version = json.loads(vanilla.read('version.json'))['world_version']
        settings = game / 'options.txt'
        fixture = {'version': options_version, 'onboardAccessibility': 'false',
                   'guiScale': 2, 'lang': 'uk_ua'}
        lines = [line for line in settings.read_text(encoding='utf-8').splitlines()
                 if line.partition(':')[0] not in fixture]
        lines.extend(f'{key}:{value}' for key, value in fixture.items())
        settings.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    if options.overlay:
        if not options.boot_pack or not options.overlay.is_dir():
            raise ValueError('An overlay needs --boot-pack and an existing fixture directory')
        for prefix in options.replace_mod_prefix:
            if not prefix or any(c in prefix for c in '/\\*?[]'):
                raise ValueError('A replacement prefix must be a plain filename prefix')
            for old in (game/'mods').glob(prefix+'*.jar'):
                old.unlink()
        shutil.copytree(options.overlay, game, dirs_exist_ok=True)
    command = make_command(root, game, java, 'LauncherSmoke', options.profile, 4096)
    assert '--quickPlayMultiplayer' not in command
    assert (root/'runtime/.forge-complete.json').exists()
    print('OFFICIAL_JAVA17_FORGE_INSTALL_PASS', flush=True)
    if options.boot_pack:
        # Boot to the local menu. Never connect a test client to the live server.
        command += ['--width','854','--height','480']
        command.insert(1, '-Dempirecraft.launcher.smoke=true')
        output = root/'client-startup.log'
        with output.open('wb') as log:
            child = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic()+(1200 if options.gameplay_probe else 360)
                ready = None
                gameplay_finished = None
                while time.monotonic() < deadline:
                    path = game/'logs/latest.log'
                    text = path.read_text(errors='replace') if path.exists() else ''
                    if child.poll() is not None:
                        raise RuntimeError('Client exited: '+str(child.returncode)+'\n'+output.read_text(errors='replace')[-14000:]+'\n'+text[-18000:])
                    if any(marker in text for marker in ['IllegalClassLoadError','Failed to initialize mod containers','Reported exception thrown!']):
                        raise RuntimeError(text[-18000:])
                    if 'Empirecraft smoke screen: net.minecraftforge.client.gui.LoadingErrorScreen' in text:
                        lines = output.read_text(errors='replace').splitlines()
                        diagnostic = set()
                        for index, line in enumerate(lines):
                            if any(word in line.lower() for word in ['error', 'exception', 'loading warning', 'modloading', 'failure']):
                                diagnostic.update(range(max(0,index-2),min(len(lines),index+12)))
                        raise RuntimeError('Forge loading screen diagnostics\n'+'\n'.join(lines[i] for i in sorted(diagnostic) if not any(skip in lines[i] for skip in ['ModelBakery', 'ModelManager', 'Missing textures', 'minecraft:textures/'])))
                    if options.gameplay_probe:
                        if 'KITCHEN_CLIENT_GAMEPLAY_PASS' in text:
                            gameplay_finished = gameplay_finished or time.monotonic()
                            if time.monotonic()-gameplay_finished > 120:
                                options.gameplay_probe.mkdir(parents=True, exist_ok=True)
                                dump = subprocess.run(['jstack', '-l', str(child.pid)], capture_output=True, text=True, timeout=20)
                                (options.gameplay_probe/'thread-dump.txt').write_text(dump.stdout+'\n'+dump.stderr)
                                raise RuntimeError('Offline gameplay completed; world exit stalled. See thread-dump.txt')
                        if 'KITCHEN_CLIENT_FAIL' in text:
                            raise RuntimeError(text[-22000:])
                        if 'KITCHEN_CLIENT_PASS' in text:
                            time.sleep(3)
                            final_log = path.read_text(errors='replace')
                            if child.poll() is not None or any((game/'crash-reports').glob('*.txt')) or any(marker in final_log for marker in ['Reported exception thrown!', 'Encountered an unexpected exception', 'Exception ticking world']):
                                raise RuntimeError('Gameplay marker followed by a crash\n'+final_log[-22000:])
                            print(next(line for line in text.splitlines() if 'KITCHEN_CLIENT_PASS' in line), flush=True)
                            break
                    if not options.gameplay_probe and 'Empirecraft home screen ready' in text:
                        ready = ready or time.monotonic()
                        if time.monotonic()-ready >= 10:
                            print('FULL_PACK_MAIN_MENU_PASS', flush=True)
                            if options.menu_capture:
                                from menu_capture import capture_menu
                                capture_menu(options.menu_capture, game)
                            break
                    time.sleep(1)
                else:
                    raise RuntimeError(('Timed out waiting for offline gameplay' if options.gameplay_probe else 'Timed out waiting for main menu')+'\n'+output.read_text(errors='replace')[-16000:])
            finally:
                child.terminate()
                try: child.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    child.kill();child.wait()
                if options.gameplay_probe:
                    options.gameplay_probe.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(output, options.gameplay_probe/'client-startup.log')
                    for folder in ['logs', 'screenshots', 'crash-reports']:
                        if (game/folder).is_dir():
                            shutil.copytree(game/folder, options.gameplay_probe/folder, dirs_exist_ok=True)
                if options.menu_capture:
                    options.menu_capture.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(output, options.menu_capture/'client-startup.log')
                    if (game/'logs/latest.log').exists():
                        shutil.copy2(game/'logs/latest.log', options.menu_capture/'latest.log')
