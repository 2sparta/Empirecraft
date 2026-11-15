from __future__ import annotations
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager
import base64
import json
import os
from pathlib import Path
import shutil
import sys
import uuid
import requests
from .security import SecurityError, atomic_write, verify_manifest, read_url, download, digest

CHANNEL = "https://raw.githubusercontent.com/2sparta/EmpireCraftLauncher/main/channel/stable.json"

def resources() -> Path:
    return Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent)) / "resources"

def data_root() -> Path:
    base = Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData/Local"))) if sys.platform == "win32" else Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local/share")))
    return base / "Empirecraft"

@contextmanager
def instance_lock(root: Path):
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    stream = (root / ".lock").open("a+b")
    try:
        stream.seek(0)
        if sys.platform == "win32":
            import msvcrt
            stream.write(b"0")
            stream.flush()
            stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        stream.close()
        raise RuntimeError("Інший лаунчер або гра вже працює")
    try:
        yield
    finally:
        stream.close()

def load_manifest(root: Path, status=lambda s: None) -> dict:
    public_key = base64.b64decode((resources() / "trusted-key.txt").read_text().strip(), validate=True)
    state_path = root / "trusted-state.json"
    previous = json.loads(state_path.read_text()) if state_path.exists() else None
    cached = root / "channel.json"
    # Cached signatures are rechecked on every start, including offline starts.
    try:
        raw = read_url(CHANNEL)
    except (requests.RequestException, OSError):
        status("Канал недоступний. Перевіряю останній підписаний пакет…")
        raw = cached.read_bytes() if cached.exists() else (resources() / "bootstrap.json").read_bytes()
    manifest = verify_manifest(raw, public_key, previous=previous)
    atomic_write(cached, raw)
    atomic_write(state_path, json.dumps({"sequence": manifest["sequence"], "digest": manifest["_digest"]}).encode())
    return manifest

def reject_symlinks(root: Path):
    # Managed roots must never resolve into another Minecraft installation.
    if root.is_symlink() or any(p.is_symlink() for p in root.parents):
        raise SecurityError("Папка лаунчера не може бути символьним посиланням")

def install_pack(root: Path, manifest: dict, profile: str, status=lambda s: None) -> Path:
    reject_symlinks(root)
    instances = root / "instances"
    instances.mkdir(parents=True, exist_ok=True)
    reject_symlinks(instances)
    game = instances / profile
    backup = instances / (profile + ".previous")
    if backup.exists():
        reject_symlinks(backup)
    if game.exists():
        reject_symlinks(game)
        # Do not follow links while preserving user-owned maps/settings.
        if any(p.is_symlink() for p in game.rglob("*")):
            raise SecurityError("У профілі знайдено символьне посилання")
        items = manifest["profiles"][profile]["files"]
        expected_mods = {Path(i["path"]).name for i in items if i["path"].startswith("mods/")}
        actual_mods = {p.name for p in (game / "mods").iterdir()} if (game / "mods").is_dir() else set()
        # Fast path still verifies JAR hashes: never trust only the installed marker.
        if actual_mods == expected_mods and all((game / i["path"]).is_file() and (game / i["path"]).stat().st_size == i["size"] and digest(game / i["path"]) == i["sha256"] for i in items):
            status("Файли збірки перевірено; повторне завантаження не потрібне.")
            return game
    stage = instances / (".stage-" + uuid.uuid4().hex)
    stage.mkdir()
    try:
        items = manifest["profiles"][profile]["files"]
        cache = root / "cache"
        cache.mkdir(exist_ok=True)
        reject_symlinks(cache)
        # Mechanical disks benefit from low concurrency; never spawn a thread per mod.
        with ThreadPoolExecutor(max_workers=3) as pool:
            jobs = {pool.submit(download, item, cache, bundled=resources()/"offline-cache"): item for item in items}
            for count, future in enumerate(as_completed(jobs), 1):
                item = jobs[future]
                try:
                    source = future.result()
                except BaseException:
                    for pending in jobs:
                        pending.cancel()
                    raise
                target = stage / item["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)  # Never hardlink user-modifiable JARs into cache.
                status(f"Перевірено {count}/{len(items)} · {Path(item['path']).name}")
        if game.exists():
            for source in game.iterdir():
                # Only managed folders are replaced; personal data stays local.
                if source.name in {"mods", "config", "defaultconfigs", "kubejs", "resourcepacks", ".pack.json"}:
                    continue
                target = stage / source.name
                if source.is_dir():
                    shutil.copytree(source, target)
                else:
                    shutil.copy2(source, target)
            # Preserve mod user settings that are not shipped as managed config.
            for folder in ("config", "resourcepacks"):
                if (game / folder).is_dir():
                    for source in (game / folder).rglob("*"):
                        target = stage / source.relative_to(game)
                        if source.is_file() and not target.exists():
                            target.parent.mkdir(parents=True, exist_ok=True)
                            shutil.copy2(source, target)
        atomic_write(stage / ".pack.json", json.dumps({"sequence": manifest["sequence"], "digest": manifest["_digest"], "profile": profile}).encode())
        if backup.exists():
            shutil.rmtree(backup)
        if game.exists():
            game.rename(backup)
        try:
            stage.rename(game)
        except BaseException:
            if backup.exists() and not game.exists():
                backup.rename(game)
            raise
        return game
    finally:
        if stage.exists():
            shutil.rmtree(stage)

def recover(root: Path):
    for profile in ("potato", "balanced", "high"):
        game = root / "instances" / profile
        backup = game.with_name(profile + ".previous")
        if not game.exists() and backup.is_dir() and not backup.is_symlink():
            backup.rename(game)
