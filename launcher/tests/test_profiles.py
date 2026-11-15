from empirecraft_launcher.profiles import apply_options, recommended_ram, PROFILES
from empirecraft_launcher.game import offline_uuid, make_command, SERVER
from empirecraft_launcher.diagnostics import gc_summary
import pytest

def test_profile_options_keep_controls_and_gameplay_hud(tmp_path):
    (tmp_path / "options.txt").write_text("key_key.forward:key.keyboard.z\nlang:uk_ua\n")
    apply_options(tmp_path, "potato", {})
    text = (tmp_path / "options.txt").read_text()
    assert "renderDistance:5" in text and "particles:2" in text and "maxFps:60" in text
    assert "key_key.forward:key.keyboard.z" in text and "lang:uk_ua" in text
    config = (tmp_path / "config/legendarysurvivaloverhaul/legendarysurvivaloverhaul-client.toml").read_text()
    assert '"Temperature Display Mode" = "SYMBOL"' in config and '"Heat Temperature Overlay" = false' in config
    assert not (tmp_path / "config/legendarysurvivaloverhaul/legendarysurvivaloverhaul-common.toml").exists()

def test_high_keeps_user_shader_choice(tmp_path):
    apply_options(tmp_path, "high", {})
    iris = tmp_path / "config/iris.properties"
    iris.write_text("enableShaders=true\nshaderPack=local.zip\n")
    apply_options(tmp_path, "high", {})
    assert "local.zip" in iris.read_text()

def test_ram_and_offline_identity():
    assert 2048 <= recommended_ram("potato", 8192) <= 4096
    assert recommended_ram("high", 8192) < 8192 - 2048
    assert offline_uuid("Notch") == "b50ad385-829d-3141-a216-7e7d7539ba7f"
    assert offline_uuid("notch") != offline_uuid("Notch")

def test_command_has_fixed_server_and_no_shell_arguments(tmp_path, monkeypatch):
    import minecraft_launcher_lib as mll
    observed = {}
    def fake(version, path, options):
        observed.update(options)
        return ["java", "-jar", "test.jar"]
    monkeypatch.setattr(mll.command, "get_minecraft_command", fake)
    cmd = make_command(tmp_path, tmp_path, "java17", "Tester", "potato", 4096)
    assert cmd[-2:] == ["--quickPlayMultiplayer", SERVER]
    assert observed["gameDirectory"] == str(tmp_path)
    assert any(x == "-Xmx4096M" for x in observed["jvmArguments"])
    assert not any("javaagent" in x or "AlwaysPreTouch" in x for x in cmd)
    with pytest.raises(ValueError): make_command(tmp_path, tmp_path, "java17", "x;evil", "potato", 4096)

def test_gc_summary(tmp_path):
    path = tmp_path / "gc.log"
    path.write_text("[gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 100M->70M(4096M) 125.15ms\n")
    assert gc_summary(path)["pauses_over_100ms"] == 1
