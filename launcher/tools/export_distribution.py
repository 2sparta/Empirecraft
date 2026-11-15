"""Prepare client-only files for owner review. Does not create/publish a repository."""
import argparse
import json
from pathlib import Path
import re
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.channel import build, git, REPO

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--ref", default="HEAD")
    p.add_argument("--destination", type=Path, required=True)
    a = p.parse_args()
    if a.destination.resolve() == REPO.resolve():
        raise SystemExit("Distribution must be separate from source repository")
    manifest = build(a.ref)
    # Balanced is the complete shared pack. High's Oculus remains on its official CDN.
    files = manifest["profiles"]["balanced"]["files"]
    audit = []
    for item in files:
        source = item["url"].split(manifest["commit"]+"/", 1)[1]
        from urllib.parse import unquote
        body = git("show", manifest["commit"]+":"+unquote(source))
        if not item["path"].startswith("mods/") and re.search(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{30,}|discord(?:app)?\.com/api/webhooks/\d+/", body):
            raise SystemExit("Review possible secret in "+item["path"])
        path = a.destination/item["path"]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(body)
        audit.append({k: item[k] for k in ("path","size","sha256")})
    a.destination.mkdir(parents=True, exist_ok=True)
    (a.destination/"CLIENT-FILES.json").write_text(json.dumps({"source_commit": manifest["commit"], "files": audit},indent=2)+"\n")
    (a.destination/"README.md").write_text("# Empirecraft client distribution\n\nClient files only. No server files, player data, credentials or release signing keys.\n\nThe stable channel is Ed25519-signed. Minecraft, Java and Oculus are downloaded separately from their official sources. Mods retain their individual licences.\n")
    # Only the two named public launchers may be exported, never arbitrary ZIPs.
    downloads = a.destination/"downloads"
    downloads.mkdir(exist_ok=True)
    for platform in ("Windows", "Linux"):
        filename = f"Empirecraft-Launcher-0.1.0-{platform}-x64.zip"
        source = REPO/"launcher/dist"/filename
        if source.is_file():
            import shutil
            shutil.copy2(source, downloads/filename)
            shutil.copy2(source.with_suffix(".zip.sha256"), downloads/(filename+".sha256"))
    print("Client files prepared for review:",len(audit),a.destination)
if __name__ == "__main__": main()
