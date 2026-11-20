from pathlib import Path
import subprocess
import pytest
from tools.channel import build

@pytest.mark.parametrize("conflict", [False, True])
def test_native_artifact_in_source_and_installed_mod_folder(tmp_path, conflict):
    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    name = "empirecraft-launcher-client-test.jar"
    installed = tmp_path/"mods"/name
    source = tmp_path/"launcher/native/dist"/name
    installed.parent.mkdir(parents=True)
    source.parent.mkdir(parents=True)
    installed.write_bytes(b"release artifact")
    source.write_bytes(b"different artifact" if conflict else b"release artifact")
    subprocess.run(["git", "-C", str(tmp_path), "add", "."], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "fixture"], check=True)
    if conflict:
        with pytest.raises(ValueError, match="Conflicting pack inputs"):
            build("HEAD", tmp_path, "2sparta/EmpireCraftLauncher")
    else:
        manifest = build("HEAD", tmp_path, "2sparta/EmpireCraftLauncher")
        for profile in manifest["profiles"].values():
            assert sum(f["path"] == "mods/"+name for f in profile["files"]) == 1

def test_installed_addon_excludes_archived_source_binary(tmp_path):
    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    installed=tmp_path/"mods/empirecraft-launcher-client-2.jar"
    archived=tmp_path/"launcher/native/dist/empirecraft-launcher-client-1.jar"
    for p,body in [(installed,b"new addon"),(archived,b"old addon")]:
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_bytes(body)
    subprocess.run(["git","-C",str(tmp_path),"add","."],check=True)
    subprocess.run(["git","-C",str(tmp_path),"-c","user.name=Test","-c","user.email=test@example.invalid","commit","-qm","fixture"],check=True)
    m=build("HEAD",tmp_path,"2sparta/EmpireCraftLauncher")
    for profile in m["profiles"].values():
        native=[f["path"] for f in profile["files"] if "empirecraft-launcher-client-" in f["path"]]
        assert native == ["mods/empirecraft-launcher-client-2.jar"]


def test_only_current_source_version_is_exported(tmp_path):
    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    for ver in ("0.2.0", "0.3.0"):
        p = tmp_path / f"launcher/native/dist/empirecraft-launcher-client-{ver}.jar"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(ver.encode())
    meta = tmp_path / "launcher/native/src/main/resources/META-INF/mods.toml"
    meta.parent.mkdir(parents=True)
    meta.write_text('[[mods]]\nmodId="empirecraft_launcher_client"\nversion="0.3.0"\n')
    subprocess.run(["git", "-C", str(tmp_path), "add", "."], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "fixture"], check=True)
    m = build("HEAD", tmp_path, "2sparta/EmpireCraftLauncher")
    for profile in m["profiles"].values():
        assert [f["path"] for f in profile["files"] if 'launcher-client' in f["path"]] == ["mods/empirecraft-launcher-client-0.3.0.jar"]
