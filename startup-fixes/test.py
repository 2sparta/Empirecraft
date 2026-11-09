#!/usr/bin/env python3
"""Exercise the optional world-reference migration against disposable data."""
import tempfile
from pathlib import Path
from repair_worlds import repair


def main():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        config = root / 'plugins/Multiverse-Core/worlds.yml'
        config.parent.mkdir(parents=True)
        original = 'DIM1:\n  auto-load: true\n  pvp: true\nmine_4:\n  auto-load: true\n  alias: mine\nworld_real:\n  auto-load: true\nworld:\n  auto-load: true\n'
        config.write_text(original)
        # Preserve an existing world and a world recoverable from its backup.
        (root / 'mine_4').mkdir();(root / 'mine_4/level.dat').write_bytes(b'world')
        (root / 'world_real').mkdir();(root / 'world_real/level.dat_old').write_bytes(b'backup')
        assert repair(root) == ['DIM1']
        assert config.read_text() == original.replace('DIM1:\n  auto-load: true', 'DIM1:\n  auto-load: false')
        assert config.with_name('worlds.yml.before-startup-fixes-1').read_text() == original
        assert repair(root) == []
        assert (root / 'mine_4/level.dat').read_bytes() == b'world'
        assert (root / 'world_real/level.dat_old').read_bytes() == b'backup'
        (root / 'mine_4/level.dat').unlink()
        assert repair(root) == ['mine_4']
        assert config.with_name('worlds.yml.before-startup-fixes-1').read_text() == original
        assert 'world:\n  auto-load: true' in config.read_text()
    print('PASS: missing reference disabled; valid/recoverable worlds and all other settings preserved; backup and repeat execution verified')


if __name__ == '__main__':
    main()
