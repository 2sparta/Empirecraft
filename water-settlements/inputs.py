"""Immutable upstream inputs, including after the patched jars are installed."""
from pathlib import Path
import hashlib,subprocess
REPO=Path(__file__).resolve().parent.parent
BASE='2c5e06013b9186c9eab0cb78578466173d503aa5'
def read_base(relative,expected):
    path=REPO/relative
    raw=path.read_bytes() if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==expected else subprocess.check_output(['git','show',BASE+':'+relative],cwd=REPO)
    assert hashlib.sha256(raw).hexdigest()==expected,relative
    return raw
def materialize(relative,expected,destination):
    raw=read_base(relative,expected);destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(raw);return destination
