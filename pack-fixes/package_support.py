"""Carry the required compatibility mod whenever a legacy update embeds the current ADM."""
from pathlib import Path

def add_updates(files,repo,as_paths=False):
    jars=list((repo/'mods').glob('empirecraft-pack-fixes-*.jar'))
    if not jars:return
    assert len(jars)==1
    jar=jars[0]
    def add(name,path):files[name]=path if as_paths else path.read_bytes()
    for side,folder in [('client',repo),('server',repo/'server')]:
        add(side+'/mods/'+jar.name,jar)
        add(side+'/mods/'+jar.name+'.sha256',jar.with_suffix('.jar.sha256'))
        for path in sorted((folder/'kubejs/data/minecraft/tags').rglob('*.json')):
            add(side+'/'+str(path.relative_to(folder)),path)
    add('PACK-FIXES-UA.md',repo/'pack-fixes/README.md')
    add('REMOVE-OLD-PACK-FIXES.txt',repo/'pack-fixes/REMOVE-OLD-PACK-FIXES.txt')

    # New armor is required by the current ADM; carry its paired client/server payload.
    layers=list((repo/'mods').glob('empirecraft-layered-armor-*.jar'))
    if layers:
        assert len(layers)==1
        for side in ['client','server']:
            add(side+'/mods/'+layers[0].name,layers[0])
            add(side+'/mods/'+layers[0].name+'.sha256',layers[0].with_suffix('.jar.sha256'))
            base=repo if side=='client' else repo/'server'
            recipe=base/'kubejs/server_scripts/zzzzzzzz_empirecraft_stone_recipes.js'
            add(side+'/kubejs/server_scripts/'+recipe.name,recipe)
        add('LAYERED-ARMOR-UA.md',repo/'layered-armor/README.md')

    survival=list((repo/'mods').glob('legendarysurvivaloverhaul-*-empirecraft.*.jar'))
    if survival:
        assert len(survival)==1
        for side in ['client','server']:
            add(side+'/mods/'+survival[0].name,survival[0])
            add(side+'/mods/'+survival[0].name+'.sha256',survival[0].with_suffix('.jar.sha256'))
        add('SURVIVAL-UA.md',repo/'survival-overhaul/README.md')
        add('reference-configs/legendarysurvivaloverhaul/legendarysurvivaloverhaul-common.toml',repo/'survival-overhaul/defaults/legendarysurvivaloverhaul-common.toml')
        source=repo/'survival-overhaul/dist/Empirecraft-Survival-Update-1-sources.zip'
        if source.exists():add(source.name,source)
