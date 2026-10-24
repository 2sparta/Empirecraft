#!/usr/bin/env python3
"""Verify the config migration preserves custom settings and is idempotent."""
from pathlib import Path
from tempfile import TemporaryDirectory
from provision_config import update

with TemporaryDirectory() as folder:
    path=Path(folder)/'config.yml'
    old='sync-ticks: 333\ncustom:\n  keep: "Унікальне значення"\n'
    path.write_text(old)
    assert update(path)
    result=path.read_text()
    assert result.startswith(old)
    assert 'collapse-after-hours: 96' in result
    assert path.with_name('config.yml.before-provisions').read_text()==old
    assert not update(path) and path.read_text()==result
    custom=old+'provisions:\n  collapse-after-hours: 120\n'
    path.write_text(custom)
    assert not update(path) and path.read_text()==custom
    fresh=Path(folder)/'fresh/config.yml';assert update(fresh);assert not update(fresh)
print('PASS config migration preserves custom values/backups, fresh installation and idempotence')
