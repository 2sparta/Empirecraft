#!/usr/bin/env python3
"""Check connection overlays and prevent optional-mod tag failures from returning."""
import copy
import json
import subprocess
import sys
from repair_tags import ROOT, REPO, TAGS, RELATIVE, helper


def main():
    subprocess.run([sys.executable, str(ROOT/'repair_tags.py'), '--check'], check=True)
    cases = [
        ('minecraft:oak_fence', 'minecraft:oak_fence'),
        ('#minecraft:wooden_fences', '#minecraft:wooden_fences'),
        ('mod:fence', {'id': 'mod:fence', 'required': False}),
        ('#mod:wooden_fences', {'id': '#mod:wooden_fences', 'required': False}),
        ({'id': 'mod:wall', 'required': True}, {'id': 'mod:wall', 'required': False}),
        ({'id': 'minecraft:cobblestone_wall', 'required': False}, {'id': 'minecraft:cobblestone_wall', 'required': False}),
    ]
    for value, expected in cases:
        before = copy.deepcopy(value)
        actual = helper.optional_mod_reference(value)
        assert actual == expected and value == before
        assert helper.optional_mod_reference(actual) == actual
    for name in TAGS:
        data = json.loads((REPO/'server/kubejs'/RELATIVE/(name+'.json')).read_text())
        assert data['replace'] is True, 'Must override the stale embedded NoEnd snapshot'
        for value in data['values']:
            identity = value if isinstance(value, str) else value['id']
            if not identity.removeprefix('#').startswith('minecraft:'):
                assert isinstance(value, dict) and value['required'] is False, (name, value)
                assert not identity.removeprefix('#').startswith('mcw'), 'Retired Macaw entries must stay absent from active overlays'
    fences = json.loads((REPO/'server/kubejs'/RELATIVE/'fences.json').read_text())['values']
    walls = json.loads((REPO/'server/kubejs'/RELATIVE/'walls.json').read_text())['values']
    assert '#minecraft:wooden_fences' in fences
    assert {'minecraft:cobblestone_wall', 'minecraft:stone_brick_wall'} <= set(v for v in walls if isinstance(v, str))
    assert 'minecraft:end_stone_brick_wall' not in [v if isinstance(v, str) else v['id'] for v in walls]
    print('PASS vanilla membership, optional mod/tag references, idempotence and retained End restrictions')


if __name__ == '__main__':
    main()
