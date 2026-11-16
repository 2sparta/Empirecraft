from __future__ import annotations
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from urllib.parse import urljoin
import uuid
import psutil
import requests
from . import __version__
from .security import checked_url, download, SecurityError, atomic_write
from .profiles import recommended_ram

SERVER = "p2.foxter.top:25628"
VERSION = "1.20.1-forge-47.4.23"

def offline_uuid(name: str) -> str:
    value = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    value[6] = (value[6] & 15) | 48
    value[8] = (value[8] & 63) | 128
    return str(uuid.UUID(bytes=bytes(value)))

@contextmanager
def official_network():
    """Bound every upstream-library request, including redirect destinations."""
    original = requests.sessions.Session.request
    def guarded(session, method, url, **kwargs):
        if method.upper() != "GET":
            raise SecurityError("Інсталятор може лише завантажувати офіційні файли")
        kwargs["timeout"] = (15, 60)
        kwargs["allow_redirects"] = False
        for _ in range(5):
            checked_url(url, official=True)
            result = original(session, method, url, **kwargs)
            if result.status_code in (301, 302, 303, 307, 308):
                url = urljoin(url, result.headers["Location"])
                result.close()
                continue
            result.raise_for_status()
            return result
        raise SecurityError("Забагато перенаправлень офіційного інсталятора")
    requests.sessions.Session.request = guarded
    try:
        yield
    finally:
        requests.sessions.Session.request = original

def java17(path: str) -> str:
    probe = str(Path(path).with_name("java.exe")) if Path(path).name.lower() == "javaw.exe" else path
    p = subprocess.run([probe, "-version"], capture_output=True, text=True, timeout=15, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    version = p.stderr + p.stdout
    if p.returncode or not re.search(r'version "17\.', version) or "64-Bit" not in version:
        raise RuntimeError("Потрібна 64-бітна Java 17. Скористайся автоматичним встановленням.")
    return path

def install_runtime(root: Path, manifest: dict, status=lambda s: None, *, repair: bool = False) -> str:
    import minecraft_launcher_lib as mll
    mc = root / "runtime"
    mc.mkdir(parents=True, exist_ok=True)
    callback = {"setStatus": lambda s: status("Minecraft / Java · " + str(s))}
    with official_network():
        # Mojang's runtime manifest includes hashes for every Java runtime file.
        java = mll.runtime.get_executable_path("java-runtime-gamma", mc)
        if repair or not java or not Path(java).is_file():
            status("Встановлюю офіційну Java 17…")
            mll.runtime.install_jvm_runtime("java-runtime-gamma", mc, callback=callback, max_workers=3)
            java = mll.runtime.get_executable_path("java-runtime-gamma", mc)
        if not java:
            raise RuntimeError("Не вдалося встановити Java 17")
        java17(str(java))
        marker = mc / ".forge-complete.json"
        if repair or not marker.exists():
            installer = download(manifest["installer"], root / "cache", official=True)
            original_download = mll.forge.download_file
            def pinned_download(url, path, *args, **kwargs):
                if url == manifest["installer"]["url"]:
                    Path(path).parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(installer, path)
                    return True
                return original_download(url, path, *args, **kwargs)
            mll.forge.download_file = pinned_download
            try:
                mll.forge.install_forge_version("1.20.1-47.4.23", mc, callback=callback, java=java)
            finally:
                mll.forge.download_file = original_download
            if not (mc / "versions" / VERSION / (VERSION + ".json")).is_file():
                raise RuntimeError("Інсталятор Forge не завершився")
            atomic_write(marker, json.dumps({"installer": manifest["installer"]["sha256"]}).encode())
    windowless = Path(java).with_name("javaw.exe")
    return str(windowless) if os.name == "nt" and windowless.is_file() else str(java)

def make_command(root: Path, game: Path, java: str, name: str, profile: str, ram: int) -> list[str]:
    import minecraft_launcher_lib as mll
    if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", name):
        raise ValueError("Нік: 3–16 латинських літер, цифр або _")
    total = psutil.virtual_memory().total // 1024**2
    if total < 6144:
        raise RuntimeError("Для цієї збірки потрібно щонайменше 6 ГБ фізичної RAM; бажано 8 ГБ або більше.")
    ram = max(2048, min(int(ram), int(total - 2048), 12288))
    logs = game / "logs"
    logs.mkdir(exist_ok=True)
    gc = "logs/gc.log"
    options = {
        "username": name, "uuid": offline_uuid(name), "token": "0",
        "gameDirectory": str(game), "executablePath": java,
        "launcherName": "Empirecraft", "launcherVersion": __version__,
        "jvmArguments": ["-Xms512M", f"-Xmx{ram}M", "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", f"-Xlog:gc*,safepoint:file={gc}:time,uptime,level,tags:filecount=3,filesize=8M", f"-Dempirecraft.launcher.profile={profile}"],
    }
    command = mll.command.get_minecraft_command(VERSION, root / "runtime", options)
    return command

def run_game(command: list[str], game: Path, status=lambda s: None) -> int:
    from .diagnostics import monitor
    # No shell, no free-form JVM flags, no access tokens in a logged command.
    with (game / "logs/launcher-game.log").open("ab") as log:
        child = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        status("Гра працює. Показники записуються локально; лаунчер майже не використовує CPU.")
        try:
            monitor(child, game / "logs/performance.json")
        except Exception as exc:
            # Keep backend/reporting failures separate from the game's result.
            # The Minecraft process must remain running and keep the root lock.
            try:
                log.write(("\n[Empirecraft] Optional diagnostics unavailable: " + str(exc)[:500] + "\n").encode("utf-8", errors="replace"))
                log.flush()
            except OSError:
                pass
        finally:
            # Retain the instance lock if writing an optional report fails.
            result = child.wait()
        return result
