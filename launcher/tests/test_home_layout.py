"""Exercise geometry used by the actual Java screen at several GUI scales."""
from pathlib import Path
import shutil
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1] / 'native'

def test_character_and_controls_fit_supported_gui_sizes(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('Java 17+ is needed for the native layout harness')
    subprocess.run([javac, '--release', '17', '-d', str(tmp_path),
        str(ROOT/'src/main/java/ua/empirecraft/launcherclient/HomeLayout.java'),
        str(ROOT/'tests/HomeLayoutTest.java')], check=True)
    result = subprocess.run([java, '-cp', str(tmp_path), 'HomeLayoutTest'],
                            check=True, capture_output=True, text=True)
    assert 'HOME_LAYOUT_PASS 8 sizes' in result.stdout
