import base64
import copy
import json
import hashlib
from pathlib import Path
import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from empirecraft_launcher import security as s

KEY = Ed25519PrivateKey.generate()
PUBLIC = KEY.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
FILE = {"path": "mods/empirecraft-launcher-client-test.jar", "url": "https://raw.githubusercontent.com/2sparta/Empirecraft/abc/mod.jar", "size": 3, "sha256": hashlib.sha256(b"jar").hexdigest()}
def manifest():
    from tools.channel import INSTALLER
    return {"schema": 1, "minecraft": "1.20.1", "forge": "47.4.23", "server": "p2.foxter.top:25628", "sequence": 100000, "expires": 300000, "profiles": {k: {"files": [copy.deepcopy(FILE)]} for k in ("potato", "balanced", "high")}, "installer": INSTALLER}
def sign(data, key=KEY):
    payload = json.dumps(data).encode()
    return json.dumps({"payload": base64.b64encode(payload).decode(), "signature": base64.b64encode(key.sign(payload)).decode()}).encode()

def test_signature_tampering_and_other_key_rejected():
    raw = sign(manifest())
    assert s.verify_manifest(raw, PUBLIC, now=100000)["server"] == "p2.foxter.top:25628"
    with pytest.raises(s.SecurityError):
        s.verify_manifest(sign(manifest(), Ed25519PrivateKey.generate()), PUBLIC, now=100000)
    obj = json.loads(raw)
    obj["payload"] = base64.b64encode(json.dumps({**manifest(), "server": "evil.example"}).encode()).decode()
    with pytest.raises(s.SecurityError):
        s.verify_manifest(json.dumps(obj).encode(), PUBLIC, now=100000)

def test_rollback_equivocation_expiry_and_future_rejected():
    data = manifest()
    first = s.verify_manifest(sign(data), PUBLIC, now=100000)
    state = {"sequence": first["sequence"], "digest": first["_digest"]}
    assert s.verify_manifest(sign(data), PUBLIC, previous=state, now=100000)
    for sequence in (99999, 100001):
        changed = copy.deepcopy(data)
        changed["sequence"] = sequence
        if sequence == 100001:
            state["sequence"] = sequence
        with pytest.raises(s.SecurityError):
            s.verify_manifest(sign(changed), PUBLIC, previous=state, now=100000)
    for now in (300000, -100000):
        with pytest.raises(s.SecurityError):
            s.verify_manifest(sign(data), PUBLIC, now=now)

@pytest.mark.parametrize("path", ["../evil", "/mods/x.jar", "C:/mods/a.jar", "mods/../a.jar", "mods\\x.jar", "mods/a.jar:stream", "mods/NUL.jar", "config/con/a", "config/a.", "config/a ", "mods/%2e%2e/a.jar", "mods//a.jar", "mods/x/x.jar", "options.txt", "config/a\x00b"])
def test_paths(path):
    with pytest.raises(s.SecurityError):
        s.safe_path(path)

@pytest.mark.parametrize("url", ["http://cdn.modrinth.com/a", "https://cdn.modrinth.com.evil.test/a", "https://evil.test/a", "https://user:pass@cdn.modrinth.com/a", "https://cdn.modrinth.com:444/a", "https://raw.githubusercontent.com/attacker/repo/a", "https://cdn.modrinth.com/%2e%2e/a"])
def test_urls(url):
    with pytest.raises(s.SecurityError):
        s.checked_url(url)

def test_duplicate_case_insensitive_paths_rejected():
    data = manifest()
    data["profiles"]["high"]["files"].append({**FILE, "path": FILE["path"].upper().replace("MODS/", "mods/").replace(".JAR", ".jar")})
    with pytest.raises(s.SecurityError):
        s.verify_manifest(sign(data), PUBLIC, now=100000)

def test_download_integrity_cache_and_partial_cleanup(tmp_path, monkeypatch):
    class Fake:
        def __enter__(self): return self
        def __exit__(self, *a): pass
        def iter_content(self, n): yield b"wrong"
    monkeypatch.setattr(s, "response", lambda *a, **kw: Fake())
    with pytest.raises(s.SecurityError): s.download(FILE, tmp_path)
    assert list(tmp_path.iterdir()) == []
    def good(self, n): yield b"jar"
    Fake.iter_content = good
    result = s.download(FILE, tmp_path)
    monkeypatch.setattr(s, "response", lambda *a, **kw: pytest.fail("valid cache must not use network"))
    assert s.download(FILE, tmp_path) == result
    result.write_bytes(b"bad")
    with pytest.raises(BaseException): s.download(FILE, tmp_path)

def test_redirect_checked_before_second_request(monkeypatch):
    calls = []
    class Redirect:
        status_code = 302
        headers = {"Location": "https://evil.test/payload"}
        def close(self): pass
    def fake(url, **kwargs):
        calls.append(url)
        assert kwargs["allow_redirects"] is False
        return Redirect()
    monkeypatch.setattr(s.requests, "get", fake)
    with pytest.raises(s.SecurityError): s.response(FILE["url"])
    assert len(calls) == 1
