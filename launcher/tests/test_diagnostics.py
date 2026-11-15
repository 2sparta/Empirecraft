"""Failures in optional Windows counters must not change the game lifecycle."""
import json
from pathlib import Path
import subprocess
import sys
import pytest
from empirecraft_launcher import diagnostics, game

PDH_ERROR = "PdhAddEnglishCounterW failed. Performance counters may be disabled."

def child_process():
    return subprocess.Popen([sys.executable, "-c", "import time; time.sleep(0.35)"])

def test_disabled_pdh_swap_counter_keeps_other_metrics_and_child_alive(tmp_path, monkeypatch):
    calls = []
    def disabled():
        calls.append(True)
        raise RuntimeError(PDH_ERROR)
    monkeypatch.setattr(diagnostics.psutil, "swap_memory", disabled)
    child = child_process()
    try:
        diagnostics.monitor(child, tmp_path/"performance.json", interval=0.01)
        assert child.wait(timeout=10) == 0
    finally:
        if child.poll() is None:
            child.kill()
            child.wait()
    report = json.loads((tmp_path/"performance.json").read_text())
    assert len(calls) == 1  # No repeated queries to a disabled PDH counter.
    assert PDH_ERROR in report["unavailable_metrics"]["swap"]
    assert report["samples"]
    assert all(s["swap_used_mb"] is None for s in report["samples"])
    assert any(s["java_rss_mb"] > 0 for s in report["samples"])
    assert any(s["system_available_mb"] > 0 for s in report["samples"])

@pytest.mark.parametrize("exit_code", [0, 7])
def test_unexpected_monitor_failure_preserves_real_child_exit(tmp_path, monkeypatch, exit_code):
    def broken(*args, **kwargs):
        raise RuntimeError(PDH_ERROR)
    monkeypatch.setattr(diagnostics, "monitor", broken)
    (tmp_path/"logs").mkdir()
    result = game.run_game([sys.executable, "-c", f"import sys; print('child completed'); sys.exit({exit_code})"], tmp_path)
    assert result == exit_code
    text = (tmp_path/"logs/launcher-game.log").read_text()
    assert "child completed" in text
    assert "Optional diagnostics unavailable" in text

def test_report_write_failure_is_nonfatal(tmp_path, monkeypatch):
    def unwritable(*args, **kwargs):
        raise OSError("read-only diagnostic destination")
    monkeypatch.setattr(diagnostics, "atomic_write", unwritable)
    child = child_process()
    try:
        diagnostics.monitor(child, tmp_path/"report.json", interval=0.01)
        assert child.wait(timeout=10) == 0
    finally:
        if child.poll() is None:
            child.kill()
            child.wait()
