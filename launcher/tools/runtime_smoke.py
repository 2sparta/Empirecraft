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
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from empirecraft_launcher.security import verify_manifest
from empirecraft_launcher.game import install_runtime, make_command
from empirecraft_launcher.updater import resources, install_pack
from empirecraft_launcher.profiles import apply_options

args = argparse.ArgumentParser()
args.add_argument('--boot-pack', action='store_true')
options = args.parse_args()
manifest = verify_manifest((resources()/'bootstrap.json').read_bytes(), base64.b64decode((resources()/'trusted-key.txt').read_text()))
with tempfile.TemporaryDirectory(prefix='empirecraft-runtime-') as folder:
    root = Path(folder)
    java = install_runtime(root, manifest, print)
    game = root/'instances/potato'
    game.mkdir(parents=True)
    if options.boot_pack:
        game = install_pack(root, manifest, 'potato')
        for old in (game/'mods').glob('empirecraft-launcher-client-*.jar'):
            old.unlink()
        addon = Path(__file__).resolve().parents[1]/'native/dist/empirecraft-launcher-client-0.1.1.jar'
        shutil.copy2(addon, game/'mods'/addon.name)
        apply_options(game, 'potato', {})
        # Unattended fixture: dismiss vanilla's first-run accessibility dialog.
        with (game/'options.txt').open('a', encoding='utf-8') as settings:
            settings.write('onboardAccessibility:false\n')
    command = make_command(root, game, java, 'LauncherSmoke', 'potato', 4096)
    assert command[-2:] == ['--quickPlayMultiplayer', 'p2.foxter.top:25628']
    assert (root/'runtime/.forge-complete.json').exists()
    print('OFFICIAL_JAVA17_FORGE_INSTALL_PASS', flush=True)
    if options.boot_pack:
        # Boot to the local menu. Never connect a test client to the live server.
        command = command[:-2] + ['--width','854','--height','480']
        output = root/'client-startup.log'
        with output.open('wb') as log:
            child = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic()+360
                ready = None
                while time.monotonic() < deadline:
                    path = game/'logs/latest.log'
                    text = path.read_text(errors='replace') if path.exists() else ''
                    if child.poll() is not None:
                        raise RuntimeError('Client exited: '+str(child.returncode)+'\n'+output.read_text(errors='replace')[-14000:]+'\n'+text[-18000:])
                    if any(marker in text for marker in ['IllegalClassLoadError','Failed to initialize mod containers','Reported exception thrown!']):
                        raise RuntimeError(text[-18000:])
                    if 'Empirecraft home screen ready' in text:
                        ready = ready or time.monotonic()
                        if time.monotonic()-ready >= 10:
                            print('FULL_PACK_MAIN_MENU_PASS', flush=True)
                            break
                    time.sleep(1)
                else:
                    raise RuntimeError('Timed out waiting for main menu\n'+output.read_text(errors='replace')[-16000:])
            finally:
                child.terminate()
                try: child.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    child.kill();child.wait()
