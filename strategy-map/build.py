#!/usr/bin/env python3
"""Build the strategic JourneyMap overlay against genuine APIs already present in the repository."""
import argparse,hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
BUILD=ROOT/'build'
VERSION='1.13.1'
FEUDAL_VERSION='1.20.1'
MAIN=['Claims','BanditNames','BanditTerritories','ClaimIndex','ClaimKnowledge','OverlaySync','TownyAccess','BridgeService',
      'MapMode','MapHistoryNetwork','ClimateObservations','EarthMapClimate','LocalSurvey','ShipObservation','ShipObservationStore','StrategyNetwork','SpyglassService','EmpireTownyMap']
CLIENT=['ClientAccess','StrategyLayout','MapCardSelection','HeraldryIcon','RouteMapLayer','ShipMapLayer','RecruitsMapTools','StrategyMapUi','MapModeButton','MapClickPlugin','FleetWaypoints']
METHODS={'getUUID':'m_20148_','getWorldPath':'m_129843_','getPlayerList':'m_6846_','getPlayers':'m_11314_',
         'isRunning':'m_130010_','serverLevel':'m_284548_','getBlockX':'m_146903_','getBlockZ':'m_146907_',
         'location':'m_135782_','tryParse':'m_135820_','asLong':'m_121882_','writeUtf':'m_130072_',
         'readUtf':'m_130136_','writeEnum':'m_130068_','readEnum':'m_130066_','writeVarInt':'m_130130_',
         'readVarInt':'m_130242_','isUsingItem':'m_6117_','getUseItem':'m_21211_','getEyePosition':'m_146892_',
         'getViewVector':'m_20252_','getViewDistance':'m_11312_','hasChunkAt':'m_46805_','getChunkSource':'m_7726_','getChunkNow':'m_7131_','getMinBuildHeight':'m_141937_',
         'getNoiseBiome':'m_203495_','getFluidState':'m_6425_','getBlockState':'m_8055_',
         'unwrapKey':'m_203543_','getHeight':'m_5885_'}

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def readjar(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            entry=zipfile.ZipInfo(name,(2026,11,17,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,raw)
    path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def classes(path):return {p.relative_to(path).as_posix():p.read_bytes() for p in path.rglob('*.class')}

def runtime_source(path):
    text=path.read_text()
    if path.stem not in ['ClientAccess','HeraldryIcon','RecruitsMapTools']:
        for old,new in METHODS.items():text=re.sub(r'\.'+old+r'\(','.'+new+'(',text)
        text=text.replace('player.getX()','player.m_20185_()').replace('player.getZ()','player.m_20189_()')
        for old,new in [('getX','m_123341_'),('getY','m_123342_'),('getZ','m_123343_')]:text=re.sub(r'\.'+old+r'\(','.'+new+'(',text)
        text=text.replace('.dimension().m_135782_()', '.m_46472_().m_135782_()')
        text=text.replace('LevelResource.ROOT','LevelResource.f_78182_').replace('Registries.DIMENSION','Registries.f_256858_').replace('ResourceKey.create(','ResourceKey.m_135785_(')
        text=text.replace('BlockPos.containing(', 'BlockPos.m_274446_(')
        text=text.replace('Items.SPYGLASS','net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("minecraft","spyglass"))')
        if path.stem=='ClimateObservations':
            text=(text.replace('.getType()', '.m_76152_()').replace('.is(FluidTags.WATER)', '.m_205070_(FluidTags.f_13131_)')
                      .replace('.is(BiomeTags.IS_OCEAN)', '.m_203656_(BiomeTags.f_207603_)')
                      .replace('.is(BiomeTags.IS_RIVER)', '.m_203656_(BiomeTags.f_207605_)')
                      .replace('.getBlock()', '.m_60734_()').replace('.getPath()', '.m_135815_()'))
        text=text.replace('.m_21211_().is(','.m_21211_().m_150930_(').replace('eye.add(', 'eye.m_82549_(').replace('look.scale(', 'look.m_82490_(')
        text=text.replace('player.getServer()','player.m_20194_()').replace('player.connection.connection','player.f_8906_.f_9742_')
        text=text.replace('hit.getType()','hit.m_6662_()').replace('hit.getBlockPos()','hit.m_82425_()')
    if path.stem=='RecruitsMapTools':
        text=text.replace('position.getX()','position.m_123341_()').replace('position.getZ()','position.m_123343_()')
    return text

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk',type=Path,default=Path('/workspace/.empirecraft/jdk-17'))
    args=parser.parse_args();BUILD.mkdir(exist_ok=True);logs=BUILD/'logs';logs.mkdir(exist_ok=True)
    def run(command,label):
        result=subprocess.run(list(map(str,command)),capture_output=True,text=True);(logs/(label+'.log')).write_text(result.stdout+result.stderr)
        if result.returncode:raise RuntimeError(label+'\n'+result.stdout[-4000:]+result.stderr[-10000:])
        print(label+'_PASS',flush=True);return result.stdout
    base=ROOT/'native-inputs/empirecraft-towny-journeymap-1.12.0.jar'
    assert sha(base)=='302081b2f7fc1eacad55ecaab9133d7296f583c8a5315dda5f2c35abb26107a1'
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));deps=BUILD/'deps';deps.mkdir(exist_ok=True)
    for name in ['valkyrienskies-120-2.4.11-empirecraft.1.jar','journeymap-forge-1.20.1-6.0.7.jar']:
        for path,raw in readjar(REPO/'mods'/name).items():
            if path.endswith('.jar') and path.startswith(('META-INF/jarjar/','META-INF/jars/')):(deps/Path(path).name).write_bytes(raw)
    runtime=REPO/'server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar'
    srg=REPO/'server/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar'
    cp=os.pathsep.join(map(str,[base,runtime,srg,*sorted(deps.glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar'))]))
    (BUILD/'classpath.txt').write_text(cp)
    source=REPO/'bridge/src/main/java/ua/empirecraft/townymap';mapped=BUILD/'mapped';mapped.mkdir(exist_ok=True)
    paths=[source/(n+'.java') for n in MAIN]+[source/'client'/(n+'.java') for n in CLIENT]
    fingerprint={p.relative_to(REPO).as_posix():sha(p) for p in paths};generated=[]
    for path in paths:
        text=runtime_source(path)
        target=mapped/path.relative_to(source);target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text);generated.append(target)
    output=BUILD/'classes';shutil.rmtree(output,ignore_errors=True);output.mkdir()
    compiler=[args.jdk/'bin/javac','--release','17','-proc:none','-encoding','UTF-8']
    run([*compiler,'-cp',cp,'-d',output,*generated],'bridge-compile')
    files=readjar(base);prefixes=['ua/empirecraft/townymap/'+n for n in MAIN]+['ua/empirecraft/townymap/client/'+n for n in CLIENT]
    changed=lambda n:any(n==prefix+'.class' or n.startswith(prefix+'$') for prefix in prefixes)
    files={n:raw for n,raw in files.items() if not changed(n)};files.update(classes(output))
    files['META-INF/mods.toml']=(REPO/'bridge/src/main/resources/META-INF/mods.toml').read_bytes()
    for name,size in [('ship',32),('route',16)]:
        image=Image.new('RGBA',(size,size));draw=ImageDraw.Draw(image)
        if name=='ship':draw.polygon([(16,1),(26,25),(16,21),(6,25)],fill=(255,255,255,255));draw.rectangle((14,16,18,29),fill=(255,255,255,255))
        else:draw.ellipse((2,2,13,13),fill=(229,194,119,255),outline=(35,45,43,255),width=2)
        path=BUILD/(name+'.png');image.save(path);files['assets/empirecraft_townymap/textures/map/'+name+'.png']=path.read_bytes()
    bridge=ROOT/f'dist/empirecraft-towny-journeymap-{VERSION}.jar';archive(bridge,files)
    preserved=[n for n in readjar(base) if n.endswith('.class') and not changed(n)]
    assert all(files[n]==readjar(base)[n] for n in preserved)
    pluginbase=ROOT/'native-inputs/empirecraft-feudal-1.19.4.jar'
    api=deps/'bukkit-api.jar';forge=REPO/'server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-universal.jar'
    archive(api,{n:raw for n,raw in readjar(forge).items() if n.startswith('org/bukkit/') and not n.startswith('org/bukkit/craftbukkit/') and n.endswith('.class')})
    pluginclasses=BUILD/'plugin';shutil.rmtree(pluginclasses,ignore_errors=True);pluginclasses.mkdir()
    pluginpaths=[REPO/'feudal/src/main/java/ua/empirecraft/feudal'/(n+'.java') for n in ['MapProfiles','RealmCommands','FeudalPlugin']]
    fingerprint.update({p.relative_to(REPO).as_posix():sha(p) for p in pluginpaths})
    plugincp=os.pathsep.join(map(str,[api,pluginbase,*sorted((REPO/'server/plugins').glob('*.jar')),*libs]))
    run([*compiler,'-cp',plugincp,'-d',pluginclasses,*pluginpaths],'feudal-compile')
    files=readjar(pluginbase);files.update(classes(pluginclasses));files['plugin.yml']=(REPO/'feudal/src/main/resources/plugin.yml').read_bytes()
    plugin=ROOT/f'dist/empirecraft-feudal-{FEUDAL_VERSION}.jar';archive(plugin,files)
    army=REPO/'towny-recruits/src/main/java/ua/townyrecruits/client/JourneyMapArmyLayer.java';armyclasses=BUILD/'army';armyclasses.mkdir(exist_ok=True)
    fingerprint[army.relative_to(REPO).as_posix()]=sha(army)
    armybase=ROOT/'native-inputs/TownyRecruits-2.0.2.jar'
    run([*compiler,'-cp',str(armybase)+os.pathsep+cp,'-d',armyclasses,army],'army-compile')
    files=readjar(armybase);files.update(classes(armyclasses));files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'version="2.0.2"',b'version="2.0.3"')
    armyjar=ROOT/'dist/TownyRecruits.jar';archive(armyjar,files)
    tools=BUILD/'tools';tools.mkdir(exist_ok=True);asm=sorted((REPO/'server/libraries/org/ow2/asm').glob('*/9.7/*.jar'));asmcp=os.pathsep.join(map(str,asm))
    run([*compiler,'-cp',asmcp,'-d',tools,ROOT/'tools/MigrateRecruitsMap.java'],'migration-compile')
    migration=run([args.jdk/'bin/java','-cp',str(tools)+os.pathsep+asmcp,'MigrateRecruitsMap',ROOT/'native-inputs/recruits-1.20.1-1.15.2.jar',BUILD/'recruits-migrated.jar'],'migration')
    files=readjar(BUILD/'recruits-migrated.jar');metadata=files['META-INF/mods.toml'].decode();metadata=re.sub(r'''(?m)^version\s*=\s*["']1\.15\.2["']''','version="1.15.2-empirecraft.1"',metadata)
    metadata+='\n[[dependencies.recruits]]\nmodId="empirecraft_townymap"\nmandatory=true\nversionRange="[1.13.0,1.14)"\nordering="AFTER"\nside="CLIENT"\n'
    files['META-INF/mods.toml']=metadata.encode();recruits=ROOT/'dist/recruits-1.20.1-1.15.2-empirecraft.1.jar';archive(recruits,files)
    for path in [ROOT/'tools/MigrateRecruitsMap.java',ROOT/'build.py',REPO/'bridge/src/main/resources/META-INF/mods.toml',REPO/'feudal/src/main/resources/plugin.yml']:
        fingerprint[path.relative_to(REPO).as_posix()]=sha(path)
    assert all(sha(REPO/name)==digest for name,digest in fingerprint.items())
    report=dict(status='compiled',bridge_preserved_classes=len(preserved),source_sha256=fingerprint,
                artifacts={p.name:sha(p) for p in [bridge,plugin,armyjar,recruits]},migration=migration.strip().splitlines(),
                graphical_client_tested=False,production_world_opened=False)
    (ROOT/'dist/build-validation.json').write_text(json.dumps(report,indent=2)+'\n');print('STRATEGY_MAP_BUILD_PASS')

if __name__=='__main__':main()
