"""Verified client-only cache for initial installs without private GitHub access."""
import argparse
import base64
import hashlib
from pathlib import Path
import sys
from urllib.parse import urlsplit, unquote
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from empirecraft_launcher.security import verify_manifest, atomic_write
from tools.channel import git, REPO
ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--destination", type=Path, required=True)
    p.add_argument("--oculus", type=Path, required=True)
    p.add_argument("--repository", type=Path, default=REPO)
    a = p.parse_args()
    manifest = verify_manifest((ROOT/"resources/bootstrap.json").read_bytes(), base64.b64decode((ROOT/"resources/trusted-key.txt").read_text()))
    items = {i["sha256"]: i for data in manifest["profiles"].values() for i in data["files"]}
    for sha, item in items.items():
        u = urlsplit(item["url"])
        if u.hostname == "raw.githubusercontent.com":
            parts = unquote(u.path).split("/", 4)
            if parts[1:3] not in (["2sparta", "Empirecraft"], ["2sparta", "EmpireCraftLauncher"]) or parts[3] != manifest["commit"]:
                raise SystemExit("Unexpected Git source")
            body = git("show", parts[3] + ":" + parts[4], repo=a.repository)
        elif item["path"] == "mods/oculus-mc1.20.1-1.8.0.jar":
            body = a.oculus.read_bytes()
        else:
            raise SystemExit("Unknown offline source")
        if len(body) != item["size"] or hashlib.sha256(body).hexdigest() != sha:
            raise SystemExit("Offline cache integrity failure: " + item["path"])
        atomic_write(a.destination/sha, body)
    print("Verified offline objects:", len(items))
if __name__ == "__main__": main()
