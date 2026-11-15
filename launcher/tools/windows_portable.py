"""Cross-package a real Windows runtime; no untested PyInstaller cross-builds."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = {"url": "https://github.com/astral-sh/python-build-standalone/releases/download/20261110/cpython-3.12.16%2B20261110-x86_64-pc-windows-msvc-install_only_stripped.tar.gz", "sha256": "355ee61b8207b91574491b1c6dc9b0ebb4408fc56bfafe498f3ef439314b52dc"}
def sha(path):
    with path.open("rb") as f: return hashlib.file_digest(f,"sha256").hexdigest()

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--runtime", type=Path, required=True)
    p.add_argument("--wheels", type=Path, required=True)
    p.add_argument("--lock-wheels", action="store_true")
    p.add_argument("--offline-cache", type=Path)
    a = p.parse_args()
    if sha(a.runtime) != RUNTIME["sha256"]:
        raise SystemExit("Python runtime hash mismatch")
    hashes = {f.name: sha(f) for f in sorted(a.wheels.glob("*.whl"))}
    lock = ROOT / "windows-wheels.lock.json"
    if a.lock_wheels:
        lock.write_text(json.dumps(hashes, indent=2) + "\n")
    if not lock.exists() or json.loads(lock.read_text()) != hashes:
        raise SystemExit("Windows wheel set differs from reviewed hash lock")
    if len(hashes) < 8:
        raise SystemExit("Incomplete dependencies")
    folder = ROOT / "build/windows-portable/Empirecraft"
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True)
    with tarfile.open(a.runtime) as archive:
        archive.extractall(folder, filter="data")
    site = folder / "python/Lib/site-packages"
    site.mkdir(parents=True, exist_ok=True)
    for name in hashes:
        with zipfile.ZipFile(a.wheels / name) as archive:
            for member in archive.infolist():
                path = Path(member.filename)
                if path.is_absolute() or ".." in path.parts or "\\" in member.filename:
                    raise SystemExit("Unsafe wheel member")
            archive.extractall(site)
    app = folder / "application"
    shutil.copytree(ROOT / "empirecraft_launcher", app / "empirecraft_launcher", ignore=shutil.ignore_patterns("__pycache__"))
    shutil.copytree(ROOT / "resources", app / "resources")
    if a.offline_cache:
        shutil.copytree(a.offline_cache, app / "resources/offline-cache")
    shutil.copy2(ROOT / "run.py", app / "run.py")
    shutil.copy2(ROOT / "README.md", folder / "README.md")
    shutil.copy2(ROOT / "LICENSE", folder / "LICENSE-launcher.txt")
    shutil.copy2(lock, folder / lock.name)
    (folder / "python-runtime-source.json").write_text(json.dumps(RUNTIME, indent=2) + "\n")
    env = dict(os.environ, ZIG_GLOBAL_CACHE_DIR=str(ROOT / "build/zig-cache"), ZIG_LOCAL_CACHE_DIR=str(ROOT / "build/zig-local"))
    subprocess.run([sys.executable, "-m", "ziglang", "cc", "-target", "x86_64-windows-gnu", "-municode", "-Wl,--subsystem,windows", "-Os", "-s", str(ROOT / "native/windows-bootstrap.c"), "-o", str(folder / "Empirecraft.exe"), "-luser32", "-lkernel32"], env=env, check=True)
    assert (folder / "Empirecraft.exe").read_bytes()[:2] == b"MZ"
    assert (folder / "python/DLLs/_tkinter.pyd").exists()
    suffix = "-Full" if a.offline_cache else ""
    target = ROOT / f"dist/Empirecraft-Launcher-0.1.0-Windows-x64{suffix}.zip"
    target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=7) as archive:
        for path in sorted(folder.rglob("*")):
            if path.is_file(): archive.write(path, "Empirecraft/" + path.relative_to(folder).as_posix())
    target.with_suffix(".zip.sha256").write_text(sha(target) + "  " + target.name + "\n")
    print(target, target.stat().st_size)
if __name__ == "__main__":
    main()
