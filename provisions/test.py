#!/usr/bin/env python3
"""Verify the config migration preserves custom settings and is idempotent."""
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile
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
repo=Path(__file__).resolve().parents[1]
plugins=repo/'server/plugins'
if (plugins/'empirecraft-feudal-1.4.0.jar').exists():
    assert sorted(p.name for p in plugins.glob('empirecraft-feudal-*.jar'))==['empirecraft-feudal-1.4.0.jar'], 'Remove superseded feudal plugin JARs'
    with ZipFile(plugins/'empirecraft-feudal-1.4.0.jar') as jar:
        assert jar.testzip() is None
        assert {'ua/empirecraft/feudal/PoliticsService.class', 'ua/empirecraft/feudal/ProvisionService.class', 'ua/empirecraft/feudal/PoliticalNativeGuard.class', 'ua/empirecraft/feudal/ProvisionController.class', 'org/h2/Driver.class'} <= set(jar.namelist()), 'Unified plugin must contain politics, provisions and its database'
        assert 'version: 1.4.0' in jar.read('plugin.yml').decode()
        defaults=jar.read('config.yml').decode()
        assert '\npolitics:\n' in defaults and '\nprovisions:\n' in defaults
print('PASS config migration preserves custom values/backups, fresh installation and idempotence')
