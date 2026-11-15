import hashlib
from pathlib import Path
import pytest
from empirecraft_launcher import updater as u

def pack(body=b"jar"):
    files = [{"path": "mods/mod.jar", "size": len(body), "sha256": hashlib.sha256(body).hexdigest(), "url": "https://cdn.modrinth.com/mod.jar"}]
    return {"sequence": 1, "_digest": "a", "profiles": {k: {"files": files} for k in ("potato", "balanced", "high")}}

def test_atomic_update_preserves_personal_data_removes_old_mods_and_repairs(tmp_path, monkeypatch):
    source = tmp_path / "source"
    source.write_bytes(b"jar")
    monkeypatch.setattr(u, "download", lambda *a, **kw: source)
    root = tmp_path / "data"
    root.mkdir()
    game = u.install_pack(root, pack(), "potato")
    (game / "options.txt").write_text("lang:uk_ua\n")
    (game / "mods/foreign.jar").write_bytes(b"foreign")
    (game / "journeymap").mkdir()
    (game / "journeymap/map.json").write_text("personal")
    game = u.install_pack(root, pack(), "potato")
    assert (game / "journeymap/map.json").read_text() == "personal"
    assert not (game / "mods/foreign.jar").exists()
    assert (game / "options.txt").read_text() == "lang:uk_ua\n"
    (game / "mods/mod.jar").write_bytes(b"bad")
    u.install_pack(root, pack(), "potato")
    assert (game / "mods/mod.jar").read_bytes() == b"jar"
    monkeypatch.setattr(u, "download", lambda *a, **kw: pytest.fail("no reinstallation for verified profile"))
    u.install_pack(root, pack(), "potato")

def test_failed_update_keeps_playable_instance_and_cleans_stage(tmp_path, monkeypatch):
    root = tmp_path / "data"
    game = root / "instances/potato/mods"
    game.mkdir(parents=True)
    (game / "old.jar").write_bytes(b"old")
    def fail(*a, **kw): raise IOError("network interrupted")
    monkeypatch.setattr(u, "download", fail)
    with pytest.raises(IOError): u.install_pack(root, pack(), "potato")
    assert (game / "old.jar").read_bytes() == b"old"
    assert not list((root / "instances").glob(".stage-*"))

def test_recover_and_symlink_rejection(tmp_path):
    backup = tmp_path / "instances/potato.previous"
    backup.mkdir(parents=True)
    (backup / "preserved").write_text("yes")
    u.recover(tmp_path)
    assert (tmp_path / "instances/potato/preserved").is_file()
    if hasattr(Path, "symlink_to"):
        link = tmp_path / "instances/potato/evil"
        try: link.symlink_to(tmp_path, target_is_directory=True)
        except OSError: pytest.skip("symlink privilege unavailable")
        with pytest.raises(u.SecurityError): u.install_pack(tmp_path, pack(), "potato")

def test_exclusive_instance_lock(tmp_path):
    with u.instance_lock(tmp_path):
        with pytest.raises(RuntimeError):
            with u.instance_lock(tmp_path): pass
