"""Signed, bounded, HTTPS-only pack downloads. No code from manifest fields."""
from __future__ import annotations
import base64
import hashlib
import json
import os
import re
import tempfile
import time
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit, unquote
import requests
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

MAX_MANIFEST = 6 * 1024 * 1024
MAX_FILE = 512 * 1024 * 1024
HOSTS = frozenset({"raw.githubusercontent.com", "cdn.modrinth.com"})
OFFICIAL_HOSTS = HOSTS | frozenset({"launchermeta.mojang.com", "piston-meta.mojang.com", "piston-data.mojang.com", "libraries.minecraft.net", "resources.download.minecraft.net", "maven.minecraftforge.net", "files.minecraftforge.net", "launcher.mojang.com", "maven.neoforged.net"})

class SecurityError(ValueError):
    pass

def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def safe_path(name: str) -> PurePosixPath:
    if not isinstance(name, str) or len(name) > 240 or "\\" in name or ":" in name or "%" in name or any(ord(c) < 32 for c in name):
        raise SecurityError("Недопустимий шлях у пакеті")
    parts = name.split("/")
    if any(p in ("", ".", "..") or p.endswith((".", " ")) or re.fullmatch(r"(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])(\..*)?", p) for p in parts):
        raise SecurityError("Небезпечний шлях у пакеті")
    path = PurePosixPath(name)
    if path.is_absolute() or parts[0] not in {"mods", "config", "defaultconfigs", "kubejs", "resourcepacks"}:
        raise SecurityError("Файл поза дозволеними папками пакета")
    if parts[0] == "mods" and (len(parts) != 2 or not name.endswith(".jar")):
        raise SecurityError("Некоректний файл моду")
    return path

def checked_url(url: str, official: bool = False) -> str:
    p = urlsplit(url)
    hosts = OFFICIAL_HOSTS if official else HOSTS
    if p.scheme != "https" or p.hostname not in hosts or p.port not in (None, 443) or p.username or p.password or p.fragment:
        raise SecurityError("Адреса завантаження не входить до дозволених HTTPS джерел")
    if any(x in unquote(p.path).split("/") for x in ("..", ".")):
        raise SecurityError("Недопустима адреса завантаження")
    if p.hostname == "raw.githubusercontent.com" and not p.path.startswith("/2sparta/Empirecraft/"):
        raise SecurityError("Дозволений лише репозиторій Empirecraft")
    return url

def response(url: str, *, official: bool = False):
    # requests' automatic redirects would otherwise bypass the host boundary.
    for _ in range(5):
        checked_url(url, official)
        r = requests.get(url, stream=True, timeout=(15, 45), allow_redirects=False, headers={"User-Agent": "EmpirecraftLauncher/0.1"})
        if r.status_code in (301, 302, 303, 307, 308):
            from urllib.parse import urljoin
            target = urljoin(url, r.headers.get("Location", ""))
            r.close()
            url = target
            continue
        r.raise_for_status()
        return r
    raise SecurityError("Забагато перенаправлень")

def read_url(url: str, limit: int = MAX_MANIFEST) -> bytes:
    with response(url) as r:
        body = bytearray()
        for part in r.iter_content(64 * 1024):
            body.extend(part)
            if len(body) > limit:
                raise SecurityError("Завеликий файл")
        return bytes(body)

def atomic_write(path: Path, data: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=".empirecraft-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
    finally:
        Path(tmp).unlink(missing_ok=True)

def verify_manifest(raw: bytes, public_key: bytes, *, previous: dict | None = None, now: int | None = None) -> dict:
    if len(raw) > MAX_MANIFEST:
        raise SecurityError("Завеликий маніфест")
    try:
        envelope = json.loads(raw)
        payload = base64.b64decode(envelope["payload"], validate=True)
        signature = base64.b64decode(envelope["signature"], validate=True)
        Ed25519PublicKey.from_public_bytes(public_key).verify(signature, payload)
        data = json.loads(payload)
    except Exception as exc:
        raise SecurityError("Невірний цифровий підпис оновлення") from exc
    now = int(time.time()) if now is None else now
    if data.get("schema") != 1 or data.get("server") != "p2.foxter.top:25628" or data.get("minecraft") != "1.20.1" or data.get("forge") != "47.4.23":
        raise SecurityError("Несумісний пакет")
    if not isinstance(data.get("sequence"), int) or not isinstance(data.get("expires"), int) or not data["sequence"] <= now + 86400 < data["expires"]:
        raise SecurityError("Підписаний канал прострочений або має неправильну дату")
    payload_hash = hashlib.sha256(payload).hexdigest()
    if previous and (data["sequence"] < previous["sequence"] or data["sequence"] == previous["sequence"] and payload_hash != previous["digest"]):
        raise SecurityError("Спроба відкотити або підмінити оновлення")
    profiles = data.get("profiles")
    if not isinstance(profiles, dict) or set(profiles) != {"potato", "balanced", "high"}:
        raise SecurityError("Некоректні профілі")
    for profile in profiles.values():
        files = profile.get("files", [])
        if not 1 <= len(files) <= 15000:
            raise SecurityError("Некоректна кількість файлів")
        seen = set()
        total = 0
        for item in files:
            path = str(safe_path(item["path"]))
            folded = path.casefold()
            if folded in seen or not re.fullmatch("[0-9a-f]{64}", item.get("sha256", "")) or type(item.get("size")) is not int or not 0 <= item["size"] <= MAX_FILE:
                raise SecurityError("Некоректний опис файлу")
            seen.add(folded)
            total += item["size"]
            checked_url(item["url"])
        if total > 8 * 1024**3:
            raise SecurityError("Завеликий пакет")
        if not any(p.startswith("mods/empirecraft-launcher-client-") for p in seen):
            raise SecurityError("У пакеті відсутній клієнт Empirecraft")
    installer = data.get("installer", {})
    if installer.get("url") != "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar" or installer.get("sha256") != "75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a" or installer.get("size") != 8836161:
        raise SecurityError("Невідомий інсталятор Forge")
    data["_digest"] = payload_hash
    return data

def download(item: dict, cache: Path, *, official: bool = False) -> Path:
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / item["sha256"]
    if target.is_file() and not target.is_symlink() and target.stat().st_size == item["size"] and digest(target) == item["sha256"]:
        return target
    fd, temp = tempfile.mkstemp(prefix="download-", dir=cache)
    try:
        total, h = 0, hashlib.sha256()
        with os.fdopen(fd, "wb") as out, response(item["url"], official=official) as r:
            for chunk in r.iter_content(256 * 1024):
                total += len(chunk)
                if total > item["size"]:
                    raise SecurityError("Розмір завантаження не збігається")
                h.update(chunk)
                out.write(chunk)
        if total != item["size"] or h.hexdigest() != item["sha256"]:
            raise SecurityError("Контрольна сума завантаження не збігається")
        os.replace(temp, target)
        return target
    finally:
        Path(temp).unlink(missing_ok=True)
