"""Actual official downloads + Forge processors, with no pre-existing .minecraft."""
import base64
from pathlib import Path
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from empirecraft_launcher.security import verify_manifest
from empirecraft_launcher.game import install_runtime, make_command
from empirecraft_launcher.updater import resources

manifest = verify_manifest((resources()/"bootstrap.json").read_bytes(), base64.b64decode((resources()/"trusted-key.txt").read_text()))
with tempfile.TemporaryDirectory(prefix="empirecraft-runtime-") as folder:
    root = Path(folder)
    java = install_runtime(root, manifest, print)
    game = root/"instances/potato"
    game.mkdir(parents=True)
    command = make_command(root, game, java, "LauncherSmoke", "potato", 4096)
    assert command[-2:] == ["--quickPlayMultiplayer", "p2.foxter.top:25628"]
    assert (root/"runtime/.forge-complete.json").exists()
    print("OFFICIAL_JAVA17_FORGE_INSTALL_PASS")
