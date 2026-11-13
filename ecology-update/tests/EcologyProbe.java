package ua.empirecraft.ecologytest;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHandler;
import ua.empirecraft.fauna.*;
import ua.empirecraft.calendar.SeasonalPlants;
import ua.empirecraft.leaves.SeasonalVegetation;
import ua.empirecraft.leaves.VegetationPolicy;
import ua.empirecraft.worldrules.*;

/** Disposable-world tests. No mock plants, mobs, sound handlers or placement implementations. */
@Mod("empirecraft_ecology_test")
public final class EcologyProbe {
  MinecraftServer server;ServerLevel level;int ticks,started=-1,checks,season=1;boolean failed;
  final List<PathfinderMob> flock=new ArrayList<>();final Map<UUID,Vec3> original=new HashMap<>();
  double spread;long beforeNearMiss;Entity bullet;
  final JsonArray plantReport=new JsonArray();
  final boolean restart=Integer.getInteger("ecology.phase",1)==2;
  public EcologyProbe(){
    MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e)->{server=e.getServer();level=server.overworld();});
    MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::season);
    MinecraftForge.EVENT_BUS.addListener(this::tick);
  }
  void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
  void season(TickEvent.LevelTickEvent e){
    if(level!=null&&e.level==level&&e.phase==TickEvent.Phase.END)SeasonHandler.getSeasonSavedData(level).seasonCycleTicks=SeasonHelper.getSeasonState(level).getSubSeasonDuration()*season;
  }
  void selectSeason(int value){season=value;SeasonHandler.getSeasonSavedData(level).seasonCycleTicks=SeasonHelper.getSeasonState(level).getSubSeasonDuration()*value;}
  Block block(String id){var result=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));check(result!=null&&result!=Blocks.AIR,"registered plant/block "+id);return result;}
  Item item(String id){var result=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));check(result!=null&&result!=Items.AIR,"registered item "+id);return result;}
  void tick(TickEvent.ServerTickEvent e){
    if(e.phase!=TickEvent.Phase.END||server==null||failed)return;
    try{
      ticks++;if(ticks>1800)throw new AssertionError("ecology fixture timed out");
      if(started<0){
        if(ticks<40||FaunaMod.session==null||!FaunaMod.session.population.censusReady||!FaunaMod.session.population.land().ready())return;
        if(restart){restartChecks();finish();return;}
        started=ticks;setup();return;
      }
      int t=ticks-started;
      if(t==20){
        var sound=ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("musketmod:musket_fire"));check(sound!=null,"real musket sound exists");
        level.playSound(null,104,72,136,sound,SoundSource.PLAYERS,1,1);
      }
      if(t==25)for(var b:flock)check(FaunaMod.birds.startled(b),"real gunfire startles each flock member");
      if(t==75){
        System.out.println("ECOLOGY_SCATTER "+flock.stream().map(b->b.getUUID()+" at "+b.position()+" from "+original.get(b.getUUID())+" nav="+b.getNavigation().getClass().getSimpleName()+" done="+b.getNavigation().isDone()).toList());
        for(var b:flock)check(b.getX()>original.get(b.getUUID()).x,"bird actually flies away from the shot");
        spread=spread();check(spread>8,"birds scatter in different directions: "+spread);
        check(FaunaMod.birds.sameFlock(flock.get(0),flock.get(1)),"scatter retains flock identity");
      }
      if(t==100){long before=FaunaMod.birds.alarms;level.explode(null,104,72,136,.1f,Level.ExplosionInteraction.NONE);check(FaunaMod.birds.alarms>before,"real explosion reaches the flock alarm handler");}
      if(t==110){
        beforeNearMiss=FaunaMod.birds.nearMisses;var position=flock.get(0).getEyePosition();
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("musketmod:bullet"));check(type!=null,"real bullet entity exists");
        bullet=type.create(level);bullet.setPos(position.x-300,position.y,position.z);level.addFreshEntity(bullet);
        bullet.setPos(position.x+300,position.y,position.z);
      }
      if(t==115){check(FaunaMod.birds.nearMisses>beforeNearMiss,"fast bullet crossing the flock triggers a near-miss alarm");if(bullet!=null)bullet.discard();}
      if(t==650){
        for(var b:flock)check(!FaunaMod.birds.startled(b),"birds settle after the noise ends");
        check(FaunaMod.birds.sameFlock(flock.get(0),flock.get(2)),"quiet flock keeps its surviving members");
        check(spread()<32,"flock gathers again after scatter: "+spread());
        Files.writeString(Path.of("ecology-flight.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("scatterSpread",spread,"quietSpread",spread(),"alarms",FaunaMod.birds.alarms,"nearMisses",FaunaMod.birds.nearMisses)));
        finish();
      }
    }catch(Throwable error){failed=true;System.out.println("ECOLOGY_NATIVE_FAIL");error.printStackTrace();server.halt(false);}
  }
  double spread(){double result=0;for(var a:flock)for(var b:flock)result=Math.max(result,a.distanceTo(b));return result;}
  void setup()throws Exception{
    level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
    for(int x=5;x<=11;x++)for(int z=5;z<=11;z++)level.setChunkForced(x,z,true);
    auditPlants();flaxHarvest();forwardSpawns();
    for(int i=0;i<3;i++){
      var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("naturalist:sparrow"));
      var bird=(PathfinderMob)type.create(level);bird.moveTo(132+i*3,72,136,0,0);
      check(level.addFreshEntity(bird),"real songbird joins the wilderness");flock.add(bird);original.put(bird.getUUID(),bird.position());
    }
    check(FaunaMod.birds.sameFlock(flock.get(0),flock.get(2)),"neighboring native songbirds form a flock");
  }
  void forwardSpawns(){
    var p=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes("EcologyForward".getBytes()),"EcologyForward"));
    p.moveTo(256,64,256,90,0);level.players().add(p);
    try{
      FaunaMod.session.nearby.tick(0);p.setPos(257,64,256);FaunaMod.session.nearby.tick(5);
      check(FaunaMod.session.nearby.ahead(p,new BlockPos(300,64,256)),"movement ahead is independent of a backwards camera");
      check(!FaunaMod.session.nearby.ahead(p,new BlockPos(210,64,256)),"behind position is rejected");
      check(!FaunaMod.session.nearby.naturalPosition(level,new BlockPos(210,64,256)),"vanilla generation shares the forward policy");
    }finally{level.players().remove(p);}
  }
  void preparePlot(int cx,int cz,String climate,boolean cactus){
    var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
        climate.equals("cold")?Biomes.SNOWY_PLAINS:climate.equals("hot")?Biomes.JUNGLE:Biomes.PLAINS);
    for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++){
      var chunk=level.getChunk(x,z);chunk.fillBiomesFromNoise((a,b,c,s)->biome,level.getChunkSource().randomState().sampler());
    }
    for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
      for(int y=64;y<=69;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
      level.setBlock(new BlockPos(x,63,z),(cactus?Blocks.SAND:Blocks.GRASS_BLOCK).defaultBlockState(),2);
      if(climate.equals("wet")&&x%16<8)level.setBlock(new BlockPos(x,64,z),Blocks.WATER.defaultBlockState(),2);
      else if(climate.equals("wet")&&x%4==1&&z%4==1)level.setBlock(new BlockPos(x,63,z),Blocks.WATER.defaultBlockState(),2);
      if(cactus&&x%3==1&&z%3==1)level.setBlock(new BlockPos(x,64,z),Blocks.CACTUS.defaultBlockState(),2);
    }
  }
  void auditPlants()throws Exception{
    var rows=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/ecology-plants.json"))).getAsJsonObject().getAsJsonArray("renewal_plants");
    var originalPlants=List.copyOf(RulesConfig.PLANTS.get());var missing=new ArrayList<String>();var used=new HashSet<Long>();long originalTime=level.getGameTime();
    Method plant=NaturalRenewal.class.getDeclaredMethod("plant",ServerLevel.class,int.class,int.class,long.class,RenewalData.class);plant.setAccessible(true);
    try{
      for(var value:rows){
        var row=value.getAsJsonObject();String id=row.get("id").getAsString(),climate=row.get("climate").getAsString();Block b=block(id);
        int cx=32,cz=32;
        if(VegetationPolicy.RARE.contains(id)){
          search:for(int dx=0;dx<128;dx++)for(int dz=0;dz<128;dz++)if(!used.contains(ChunkPos.asLong(32+dx,32+dz))
              &&VegetationPolicy.rareChunk(level.getSeed(),32+dx,32+dz,128)){cx=32+dx;cz=32+dz;break search;}
        }
        used.add(ChunkPos.asLong(cx,cz));preparePlot(cx,cz,climate,id.equals("ecologics:prickly_pear"));
        if(id.endsWith("mushroom_colony"))for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)
          level.setBlock(new BlockPos(x,63,z),Blocks.MYCELIUM.defaultBlockState(),2);
        if(id.equals("fruitsdelight:wild_pineapple"))for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)
          level.setBlock(new BlockPos(x,63,z),Blocks.SAND.defaultBlockState(),2);
        if(id.startsWith("vinery:jungle_grape_bush"))for(int x=cx*16+1;x<cx*16+15;x+=3)for(int z=cz*16+1;z<cz*16+15;z+=3)
          for(int y=64;y<=68;y++)level.setBlock(new BlockPos(x,y,z),Blocks.JUNGLE_LOG.defaultBlockState(),2);
        boolean seasons=true;for(int time:new int[]{1,4,7}){selectSeason(time);seasons&=SeasonalVegetation.allowRenewal(level,cx,cz,b.defaultBlockState());}
        selectSeason(10);check(!SeasonalPlants.canAppear(level),"winter appearance is disabled for "+id);selectSeason(4);
        check(seasons,"wild plant available in spring/summer/autumn: "+id);
        RulesConfig.PLANTS.set(List.of(id+"|"+climate));var renewal=new NaturalRenewal();renewal.initialize();
        boolean found=false;BlockPos at=null;BlockState state=null;
        for(int trial=0;trial<512&&!found;trial++){
          // Advance only the disposable world's scheduling clock between native renewal attempts.
          // Keep its probability, one-position budget, terrain checks and Mixins intact.
          ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+720001);
          plant.invoke(renewal,level,cx,cz,ChunkPos.asLong(cx,cz),new RenewalData());
          for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)for(int y=64;y<=66;y++){
            var pos=new BlockPos(x,y,z);var s=level.getBlockState(pos);if(s.is(b)){found=true;at=pos;state=s;}
          }
        }
        var result=new JsonObject();result.addProperty("id",id);result.addProperty("climate",climate);result.addProperty("appeared",found);
        var loot=new JsonArray();if(found)for(var stack:Block.getDrops(state,level,at,null))loot.add(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        result.add("loot",loot);plantReport.add(result);if(!found)missing.add(id);
      }
    }finally{RulesConfig.PLANTS.set(originalPlants);((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(originalTime);selectSeason(4);}
    Files.writeString(Path.of("ecology-plants.json"),new GsonBuilder().setPrettyPrinting().create().toJson(plantReport));
    check(missing.isEmpty(),"plants with no native appearance route: "+missing);
  }
  void flaxHarvest(){
    var crop=block("supplementaries:flax");var state=crop.defaultBlockState();
    for(var property:state.getProperties())if(property instanceof IntegerProperty age&&age.getName().equals("age"))state=state.setValue(age,Collections.max(age.getPossibleValues()));
    var pos=new BlockPos(560,64,528);level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),2);
    check(Block.getDrops(state,level,pos,null).stream().anyMatch(stack->stack.is(item("supplementaries:flax"))),"mature cultivated flax yields lining fiber");
    var spring=TagKey.create(Registries.BLOCK,new ResourceLocation("sereneseasons:spring_crops"));
    var summer=TagKey.create(Registries.BLOCK,new ResourceLocation("sereneseasons:summer_crops"));
    check(state.is(spring)&&!state.is(summer),"cultivated flax retains its original seasonal tags");
    var player=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes("EcologyFarmer".getBytes()),"EcologyFarmer"));
    for(Block soil:List.of(Blocks.FARMLAND,block("tfc:farmland/loam"))){
      level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);level.setBlock(pos.below(),soil.defaultBlockState(),2);
      player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("supplementaries:flax_seeds"),2));
      var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
      player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
      check(level.getBlockState(pos).is(crop),"real flax seeds plant on "+ForgeRegistries.BLOCKS.getKey(soil));
    }
  }
  void restartChecks()throws Exception{
    check(Files.isRegularFile(Path.of("ecology-plants.json")),"plant availability report survives restart");
    check(ForgeRegistries.BLOCKS.containsKey(new ResourceLocation("supplementaries:wild_flax")),"flax remains registered after restart");
    selectSeason(4);check(SeasonalVegetation.allowRenewal(level,32,32,block("supplementaries:wild_flax").defaultBlockState()),"summer flax availability survives restart");
  }
  void finish(){System.out.println("ECOLOGY_NATIVE_PASS phase="+(restart?2:1)+" assertions="+checks);server.halt(false);server=null;}
}
