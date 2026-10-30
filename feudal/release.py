"""Current artifact names, read from the descriptors shipped with the sources."""
from pathlib import Path
import re

REPO = Path(__file__).resolve().parent.parent
PLUGIN_VERSION = re.search(r'^version: (\S+)$', (REPO/'feudal/src/main/resources/plugin.yml').read_text(), re.M)[1]
PLUGIN = f'empirecraft-feudal-{PLUGIN_VERSION}.jar'

def bridge_version():
    return re.search(r'^version="([^"]+)"$', (REPO/'bridge/src/main/resources/META-INF/mods.toml').read_text(), re.M)[1]

def bridge_name():
    return f'empirecraft-towny-journeymap-{bridge_version()}.jar'
