"""Explicit species decisions and geographic envelopes; never infer ranges from a biome name."""
import json, zipfile, re, subprocess, shutil
from pathlib import Path
from content import DISABLED_BLOCKS
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def make(source):
    with zipfile.ZipFile(source) as z:
        lang=json.loads(z.read('assets/alexsmobs/lang/en_us.json'))
    javap=REPO.parent/'.empirecraft/jdk-17/bin/javap'
    fields=subprocess.run([str(javap) if javap.exists() else shutil.which('javap'),'-classpath',str(source),'-p','com.github.alexthe666.alexsmobs.entity.AMEntityRegistry'],check=True,capture_output=True,text=True).stdout
    registered={name.lower() for name in re.findall(r'public static final net.minecraftforge.registries.RegistryObject<.*> ([A-Z0-9_]+);',fields)}
    # Lon/lat polygons use the Earth addon projection. These are game range approximations.
    regions={
      'australia':[[113,-22],[114,-34],[130,-39],[141,-39],[154,-29],[153,-11],[142,-10],[135,-12],[130,-10],[122,-14],[113,-22]],
      'tasmania':[[144,-44],[149,-44],[149,-40],[144,-40],[144,-44]],
      'eastern_australia':[[140,-39],[154,-39],[154,-17],[145,-17],[140,-39]],
      'new_guinea':[[130,-11],[152,-11],[152,0],[130,0],[130,-11]],
      'north_america':[[-168,15],[-80,7],[-52,45],[-60,72],[-168,72],[-168,15]],
      'central_america':[[-115,15],[-100,28],[-85,21],[-77,7],[-92,6],[-115,15]],
      'south_america':[[-82,12],[-65,12],[-34,-6],[-40,-24],[-67,-56],[-82,-20],[-82,12]],
      'africa':[[-18,15],[-6,37],[12,38],[35,32],[52,12],[40,-35],[18,-36],[8,-5],[-18,15]],
      'central_africa':[[8,-7],[32,-7],[32,8],[8,8],[8,-7]],
      'east_africa':[[22,-12],[40,-12],[40,16],[22,16],[22,-12]],
      'ethiopian_highlands':[[35,6],[41,6],[41,15],[35,15],[35,6]],
      'asia_mountains':[[60,25],[105,25],[105,52],[60,52],[60,25]],
      'north_africa_asia':[[-18,15],[65,15],[65,48],[-18,48],[-18,15]],
      'europe':[[-11,35],[45,35],[45,71],[-11,71],[-11,35]],
      'komodo':[[119,-9.5],[121,-9.5],[121,-8],[119,-8],[119,-9.5]],
      'pupfish': [[-116.4,36.3],[-116.1,36.3],[-116.1,36.6],[-116.4,36.6],[-116.4,36.3]],
      'indo_pacific':[[30,-35],[180,-35],[180,35],[30,35],[30,-35]],
    }
    rules={}
    def animal(name,ranges,habitat='land',zones=(),min_depth=0,max_depth=512,weight=4,minimum=1,maximum=3):
        rules['alexsmobs:'+name]=dict(regions=ranges,habitat=habitat,zones=list(zones),min_depth=min_depth,max_depth=max_depth,weight=weight,min=minimum,max=maximum)
    animal('roadrunner',['north_america','central_america'],zones=['desert','dry_steppe','steppe','subtropical'])
    animal('hummingbird',['north_america','central_america','south_america'],zones=['rainforest','mixed_forest','subtropical','savanna'])
    animal('gorilla',['central_africa'],zones=['rainforest','savanna'])
    animal('hammerhead_shark',[],'marine',min_depth=3,max_depth=120)
    animal('komodo_dragon',['komodo'],zones=['savanna','subtropical','rainforest'])
    animal('capuchin_monkey',['central_america','south_america'],zones=['rainforest','savanna'])
    animal('raccoon',['north_america','central_america'],zones=['mixed_forest','subtropical','taiga'])
    animal('blobfish',['australia','tasmania'],'marine',min_depth=25)
    animal('shoebill',['east_africa'],'wetland')
    animal('snow_leopard',['asia_mountains'],zones=['montane_forest','mixed_forest','subtropical','steppe','dry_steppe','taiga','tundra'])
    rules['alexsmobs:snow_leopard']['min_elevation']=100
    animal('crow',['north_america','europe','asia_mountains','north_africa_asia'],zones=['mixed_forest','forest_steppe','taiga','steppe','subtropical','mediterranean'])
    animal('emu',['australia'],zones=['savanna','desert','dry_steppe','steppe','subtropical'])
    animal('platypus',['eastern_australia','tasmania'],'fresh')
    animal('tasmanian_devil',['tasmania'],zones=['mixed_forest','subtropical'])
    animal('kangaroo',['australia','tasmania'],zones=['savanna','desert','dry_steppe','steppe','subtropical','mixed_forest'])
    animal('cachalot_whale',[],'marine',min_depth=10,weight=2,maximum=2)
    animal('orca',[],'marine',min_depth=5,weight=2,maximum=3)
    animal('leafcutter_ant',['central_america','south_america'],zones=['rainforest','savanna','subtropical'],weight=2,maximum=4)
    animal('bald_eagle',['north_america'],zones=['mixed_forest','taiga','subtropical','tundra'])
    animal('frilled_shark',[],'marine',min_depth=25,weight=2)
    animal('seagull',[],'coast',weight=5)
    animal('toucan',['central_america','south_america'],zones=['rainforest','savanna'])
    animal('maned_wolf',['south_america'],zones=['savanna','steppe','subtropical'])
    animal('anteater',['central_america','south_america'],zones=['rainforest','savanna','subtropical'])
    animal('gelada_monkey',['ethiopian_highlands'],zones=['montane_forest','savanna','steppe'])
    animal('jerboa',['north_africa_asia'],zones=['desert','dry_steppe','steppe'])
    animal('bison',['north_america'],zones=['steppe','mixed_forest','taiga'])
    animal('devils_hole_pupfish',['pupfish'],'fresh',max_depth=30,weight=2)
    animal('flying_fish',[],'marine',min_depth=0,max_depth=20)
    animal('potoo',['central_america','south_america'],zones=['rainforest','savanna','subtropical'])
    animal('mudskipper',['indo_pacific'],'coast')
    animal('sugar_glider',['eastern_australia','new_guinea'],zones=['rainforest','mixed_forest','subtropical','savanna'])
    animal('skunk',['north_america','central_america','south_america'],zones=['mixed_forest','subtropical','steppe','savanna'])
    helpers=set('cachalot_echo tossed_item shark_tooth_arrow mud_ball fart emu_egg'.split())
    entities={k.removeprefix('entity.alexsmobs.'):v for k,v in lang.items() if k.startswith('entity.alexsmobs.') and '.' not in k.removeprefix('entity.alexsmobs.')}
    duplicates={'grizzly_bear':'naturalist:bear','gazelle':'tfc:gazelle','crocodile':'tfc:crocodile','rattlesnake':'naturalist:rattlesnake','lobster':'tfc:lobster','moose':'tfc:moose','seal':'spawn:seal','elephant':'naturalist:elephant','tiger':'tfc:tiger','rhinoceros':'naturalist:rhino','catfish':'naturalist:catfish','blue_jay':'naturalist:bluejay','mimic_octopus':'spawn:octopus','terrapin':'tfc:turtle','alligator_snapping_turtle':'tfc:turtle','rain_frog':'dragnlivestock:o_frog','comb_jelly':'tfc:jellyfish','giant_squid':'minecraft:squid','banana_slug':'naturalist:snail','caiman':'naturalist:alligator','anaconda':'naturalist:snake'}
    insects=set('fly crimson_mosquito cockroach tarantula_hawk centipede_head centipede_body centipede_tail triops mantis_shrimp'.split())
    removed={ 'alexsmobs:'+name: ('duplicate: '+duplicates[name] if name in duplicates else 'oversized arthropod' if name in insects else 'fantasy mob or helper of a removed mob') for name in sorted(registered) if 'alexsmobs:'+name not in rules and name not in helpers }
    # All unused animal drops/buckets and magical equipment are unavailable in recipes, creative and JEI.
    keep_items=set('animal_dictionary tab_icon roadrunner_feather shark_tooth shark_tooth_arrow banana ancient_dart komodo_spit sopa_de_macaco komodo_spit_bottle poison_bottle raccoon_tail frontier_cap blobfish blobfish_bucket fish_oil acacia_blossom emu_egg boiled_emu_egg emu_feather platypus_bucket fedora kangaroo_meat cooked_kangaroo_meat kangaroo_burger kangaroo_hide ambergris cachalot_whale_tooth gongylidia leafcutter_ant_pupa falconry_glove falconry_glove_inventory falconry_glove_hand falconry_hood serrated_shark_tooth frilled_shark_bucket bison_fur devils_hole_pupfish_bucket flying_fish flying_fish_bucket fish_bones mudskipper_bucket stink_bottle banner_pattern_australia_0 banner_pattern_australia_1 banner_pattern_new_mexico banner_pattern_brazil music_disc_thime music_disc_daze'.split())
    hidden=[]
    for key in lang:
        if not key.startswith('item.alexsmobs.') or '.' in key.removeprefix('item.alexsmobs.'):continue
        name=key.removeprefix('item.alexsmobs.')
        if name.startswith('spawn_egg_'):
            if 'alexsmobs:'+name.removeprefix('spawn_egg_') in rules:continue
        elif name in keep_items:continue
        hidden.append('alexsmobs:'+name)
    hidden+=['spawn:'+n for n in ['ant_spawn_egg','ant_pupa','ant_farm','ant_mound','anthill','rotten_log_anthill']]
    whale=json.loads((REPO/'recipe-whales/whale-loot.json').read_text())
    cachalot=dict(whale['fin_whale']['loot']);cachalot.pop('wonderoussea:blaeen_bristles');cachalot['alexsmobs:cachalot_whale_tooth']=[2,6]
    orca=dict(whale['minke_whale']['loot']);orca.pop('wonderoussea:blaeen_bristles')
    return dict(schema_version=1,ranges_are_approximations=True,regions=regions,animals=rules,helpers=['alexsmobs:'+v for v in sorted(helpers)],removed=removed,other_removed=['spawn:ant'],hidden_items=sorted(set(hidden) | DISABLED_BLOCKS),whale_loot={'alexsmobs:cachalot_whale':cachalot,'alexsmobs:orca':orca})

if __name__=='__main__':
    import sys
    out=ROOT/'src/main/resources/wildlife-policy.json'
    out.write_text(json.dumps(make(Path(sys.argv[1])),ensure_ascii=False,indent=2)+'\n')
