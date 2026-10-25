#!/usr/bin/env python3
"""Package the existing wind/fishing datapack and matching server/client season configs."""
import hashlib
import json
import tomllib
from pathlib import Path
import build


def main():
    server = build.REPO / 'server/config/sereneseasons/seasons.toml'
    client = build.REPO / 'config/sereneseasons/seasons.toml'
    configs = [tomllib.loads(path.read_text()) for path in [server, client]]
    if configs[0] != configs[1]:
        raise SystemExit('Server/client season configuration differs')
    if configs[0]['dimension_settings']['whitelisted_dimensions'] != ['minecraft:overworld', 'minecraft:world_europe']:
        raise SystemExit('Expected explicit Overworld and world_europe season allowlist')
    if configs[0]['time_settings']['day_duration'] != 57600 or configs[0]['time_settings']['sub_season_duration'] != 30:
        raise SystemExit('Calendar timings do not match Empirecraft 1.1.0')
    expected = build.OUTPUT.with_suffix('.zip.sha256').read_text().split()[0]
    if build.digest(build.OUTPUT) != expected:
        raise SystemExit('Datapack checksum mismatch')
    entries = {
        'server/world/datapacks/empirecraft-multiverse.zip': build.OUTPUT.read_bytes(),
        'server/config/sereneseasons/seasons.toml': server.read_bytes(),
        'client/config/sereneseasons/seasons.toml': client.read_bytes(),
        'INSTALL-UA.txt': (
            'Empirecraft: world_europe — вітер, риболовля та сезони\n\n'
            'На зупиненому сервері скопіюйте вміст server/ у корінь сервера.\n'
            'Скопіюйте вміст client/ у корінь кожної клієнтської збірки.\n'
            'Повністю перезапустіть сервер і клієнти.\n'
            'Пакет даних встановлюється в основний world/datapacks; правила адресовані minecraft:world_europe.\n'
            'Для першого оновлення вітру зачекайте до 300 ігрових тактів (~15 секунд за 20 TPS).\n'
            'Сезони використовують календар Empirecraft: 48 хвилин на добу, 30 днів на місяць.\n'
            'Копії JAR модів до цього архіву не входять; встановлений набір модів залишається чинним.\n'
        ).encode('utf-8')
    }
    output = build.ROOT / 'dist/Empirecraft-World-Europe-Fix.zip'
    build.write_pack(output, entries)
    sha = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.zip.sha256').write_text(sha + '  ' + output.name + '\n')
    print(json.dumps({'file': str(output), 'sha256': sha, 'entries': sorted(entries)}))


if __name__ == '__main__':
    main()
