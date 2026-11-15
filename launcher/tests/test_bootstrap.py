import base64
from pathlib import Path
import re
from empirecraft_launcher.security import verify_manifest
from empirecraft_launcher.updater import resources

def test_shipped_channel_signature_content_and_profile_differences():
    m = verify_manifest((resources()/"bootstrap.json").read_bytes(), base64.b64decode((resources()/"trusted-key.txt").read_text()))
    profiles = {p: {f["path"]: f for f in data["files"]} for p,data in m["profiles"].items()}
    removed = set(profiles["balanced"]) - set(profiles["potato"])
    assert len(removed) == 2
    assert any("dynamic-fps" in p for p in removed)
    assert any("sound-physics" in p for p in removed)
    assert set(profiles["high"]) - set(profiles["balanced"]) == {"mods/oculus-mc1.20.1-1.8.0.jar"}
    for profile in profiles.values():
        for fragment in ["TerraFirmaCraft", "empirecraft-casting-1.3.0", "admanticheat", "embeddium", "modernfix", "ferritecore", "entityculling", "ImmediatelyFast", "empirecraft-launcher-client"]:
            assert any(fragment in path for path in profile), fragment
        assert not any("sulfur_gunpowder" in p or p.startswith("server/") for p in profile)
        for f in profile.values():
            if f["url"].startswith("https://raw.githubusercontent.com/"):
                assert re.search(r"/Empirecraft/[0-9a-f]{40}/", f["url"])
