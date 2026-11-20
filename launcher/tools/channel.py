"""Build a signed channel from immutable Git blobs. No workspace/server secrets."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import tomllib
from urllib.parse import quote
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat, PrivateFormat, NoEncryption

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
OCULUS = {"path": "mods/oculus-mc1.20.1-1.8.0.jar", "url": "https://cdn.modrinth.com/data/GchcoXML/versions/iQ1SwGc3/oculus-mc1.20.1-1.8.0.jar", "size": 2851119, "sha256": "0945df0cba0f62b3901dd80c3268e5311b770ece78c78037a45db12ac0425fef"}
INSTALLER = {"url": "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar", "size": 8836161, "sha256": "75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a"}

def git(*args, repo=REPO):
    return subprocess.check_output(["git", "-C", str(repo), *args])

def build(ref: str, repo=REPO, url_repository=None) -> dict:
    if url_repository is None:
        remote = git("remote", "get-url", "origin", repo=repo).decode().strip().removesuffix(".git")
        url_repository = next((name for name in ("2sparta/Empirecraft", "2sparta/EmpireCraftLauncher") if remote.endswith(name)), None)
        if url_repository is None:
            raise ValueError("Specify the approved URL repository explicitly")
    commit = git("rev-parse", ref + "^{commit}", repo=repo).decode().strip()
    common = {}
    tree = git("ls-tree", "-r", "-z", commit, "mods", "config", "defaultconfigs", "kubejs", "launcher/native/dist", repo=repo)
    installed_native = {entry.split(b"\t", 1)[1].decode() for entry in tree.split(b"\0") if entry and entry.split(b"\t", 1)[1].startswith(b"mods/empirecraft-launcher-client-")}
    source_native = None
    metadata_path = "launcher/native/src/main/resources/META-INF/mods.toml"
    if not installed_native and git("ls-tree", commit, metadata_path, repo=repo).strip():
        metadata = tomllib.loads(git("show", commit+":"+metadata_path, repo=repo).decode())
        version = metadata["mods"][0]["version"]
        source_native = "launcher/native/dist/empirecraft-launcher-client-"+version+".jar"
        if not git("ls-tree", commit, source_native, repo=repo).strip():
            raise ValueError("Current launcher addon has not been built: "+source_native)
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        meta, name = entry.split(b"\t", 1)
        mode, kind, blob = meta.decode().split()
        path = name.decode()
        if mode != "100644" or kind != "blob":
            raise ValueError("Links/executables are not pack inputs: " + path)
        if path.endswith(".sha256") or path.startswith("mods/") and not path.endswith(".jar"):
            continue
        if path.startswith("launcher/native/dist/"):
            if not path.endswith(".jar"):
                continue
            if source_native and path != source_native:
                continue
            target = "mods/" + Path(path).name
            if installed_native and target not in installed_native:
                continue  # Archived build outputs must not add a second production addon.
        else:
            target = path
        body = git("cat-file", "blob", blob, repo=repo)
        item = {"path": target, "url": f"https://raw.githubusercontent.com/{url_repository}/{commit}/{quote(path, safe='/')}", "size": len(body), "sha256": hashlib.sha256(body).hexdigest()}
        if target in common:
            if common[target]["sha256"] != item["sha256"] or common[target]["size"] != item["size"]:
                raise ValueError("Conflicting pack inputs for " + target)
            continue
        common[target] = item
    profiles = {}
    for profile in ("potato", "balanced", "high"):
        files = [f for f in common.values() if profile != "potato" or not Path(f["path"]).name.startswith(("sound-physics-remastered-", "dynamic-fps-"))]
        if profile == "high":
            files = [*files, OCULUS]
        profiles[profile] = {"files": sorted(files, key=lambda f: f["path"])}
    now = int(time.time())
    return {"schema": 1, "sequence": now, "expires": now + 180 * 86400, "minecraft": "1.20.1", "forge": "47.4.23", "server": "p2.foxter.top:25628", "commit": commit, "installer": INSTALLER, "profiles": profiles}

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--ref", default="HEAD")
    p.add_argument("--output", type=Path, default=ROOT / "channel/stable.json")
    p.add_argument("--generate-key", type=Path)
    p.add_argument("--repository", type=Path, default=REPO)
    p.add_argument("--url-repository", choices=["2sparta/Empirecraft", "2sparta/EmpireCraftLauncher"])
    a = p.parse_args()
    if a.generate_key:
        if a.generate_key.exists():
            raise SystemExit("Refusing to replace a private key")
        key = Ed25519PrivateKey.generate()
        a.generate_key.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        fd = os.open(a.generate_key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as f:
            f.write(base64.b64encode(key.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())))
        public = base64.b64encode(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
        target = ROOT / "resources/trusted-key.txt"
        if target.exists() and target.read_bytes().strip() != public:
            raise SystemExit("Existing trust root differs; explicit key rotation required")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(public + b"\n")
        print("Private key created outside repository; public trust root saved.")
        return
    secret = os.environ.get("LAUNCHER_SIGNING_KEY")
    if not secret:
        key_file = os.environ.get("EMPIRECRAFT_SIGNING_KEY_FILE")
        if key_file:
            secret = Path(key_file).read_text().strip()
    if not secret:
        raise SystemExit("Set LAUNCHER_SIGNING_KEY or EMPIRECRAFT_SIGNING_KEY_FILE. Never commit a private key.")
    key = Ed25519PrivateKey.from_private_bytes(base64.b64decode(secret.strip(), validate=True))
    trusted = base64.b64decode((ROOT / "resources/trusted-key.txt").read_text().strip(), validate=True)
    if key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw) != trusted:
        raise SystemExit("Signing key does not match embedded trust root")
    payload = json.dumps(build(a.ref, a.repository, a.url_repository), ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
    envelope = {"payload": base64.b64encode(payload).decode(), "signature": base64.b64encode(key.sign(payload)).decode()}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(envelope, separators=(",", ":")) + "\n")
    print("Signed immutable pack", json.loads(payload)["commit"], "bytes", len(payload))
if __name__ == "__main__":
    main()
