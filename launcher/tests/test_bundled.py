import hashlib
from pathlib import Path
import pytest
from empirecraft_launcher import security as s

def test_full_bundle_installs_without_github_and_still_verifies_bytes(tmp_path, monkeypatch):
    body = b"checked mod bytes"
    sha = hashlib.sha256(body).hexdigest()
    bundle = tmp_path/"bundle"
    bundle.mkdir()
    file = bundle/sha
    file.write_bytes(body)
    item = {"url": "https://raw.githubusercontent.com/2sparta/Empirecraft/commit/file.jar", "size": len(body), "sha256": sha}
    def denied(*a, **kw): raise ConnectionError("private GitHub inaccessible")
    monkeypatch.setattr(s, "response", denied)
    assert s.download(item, tmp_path/"cache", bundled=bundle) == file
    file.write_bytes(b"changed mod bytes")
    with pytest.raises(ConnectionError): s.download(item, tmp_path/"cache", bundled=bundle)
