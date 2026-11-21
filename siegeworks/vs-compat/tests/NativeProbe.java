package ua.empirecraft.siegevsprobe;
import java.util.*;
import java.lang.reflect.*;
import com.mojang.authlib.GameProfile;
import me.mss1r.siegeworks.entity.base.*;
import me.mss1r.axiomata.ballistics.*;
import me.mss1r.siegeworks.gameplay.ballistics.SiegeBallisticsEnvironment;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaterniond;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.mod.common.*;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ua.empirecraft.siegeworks.vs.*;
@Mod("empirecraft_siege_vs_probe")public final class NativeProbe {
 MinecraftServer server;ServerLevel level;LoadedServerShip ship;long id;int ticks,checks;AbstractSiegeEntity gun;BlockPos wall;Vec3 localGun;org.joml.Vector3d base;Entity shot;float relativeYaw;Object universe,claim;Class<?> townBlock;
 void check(boolean ok,String s){checks++;if(!ok)throw new AssertionError(s);}
 public NativeProbe(){net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(()->org.valkyrienskies.mod.common.ValkyrienSkiesMod.getApi().registerAttachment(Motor.class)));MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e)->{server=e.getServer();level=server.overworld();var observer=FakePlayerFactory.getMinecraft(level);observer.setPos(15,112,25);try{var ch=net.minecraft.network.Connection.class.getDeclaredField("f_129468_");ch.setAccessible(true);var transport=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("f_9742_");transport.setAccessible(true);ch.set(transport.get(observer.connection),new io.netty.channel.embedded.EmbeddedChannel());}catch(Exception failure){throw new RuntimeException(failure);}level.addNewPlayer(observer);try{var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11196_");f.setAccessible(true);((List)f.get(server.getPlayerList())).add(observer);}catch(Exception x){throw new RuntimeException(x);}});MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void build(){Set<BlockPos> blocks=new HashSet<>();for(int x=0;x<12;x++)for(int z=0;z<16;z++){var p=new BlockPos(x,100,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}
 for(int y=101;y<105;y++)for(int z=1;z<15;z++){var p=new BlockPos(10,y,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}var s=ShipAssembler.assembleToShip(level,blocks,1);s.setStatic(true);id=s.getId();}
 void ready(){ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);check(ship!=null,"Real VS hull loaded");var a=ship.getShipAABB();for(int x=a.minX()>>4;x<=(a.maxX()-1)>>4;x++)for(int z=a.minZ()>>4;z<=(a.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
 wall=new BlockPos(a.minX()+10,a.minY()+2,a.minZ()+6);localGun=new Vec3(a.minX()+3,a.minY()+1.001,a.minZ()+5);
 gun=(AbstractSiegeEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("siegeworks:culverin")).create(level);check(gun!=null&&gun.isFullyBuilt(),"Native completed culverin constructs");var p=Frames.world(ship,localGun);gun.setPos(p);gun.setTrackedYaw(-90);gun.setYRot(-90);level.addFreshEntity(gun);base=new org.joml.Vector3d(ship.getTransform().getPositionInWorld());}
 void fire(int row,String entity){wall=new BlockPos(wall.getX(),wall.getY(),ship.getShipAABB().minZ()+row);level.setBlock(wall,Blocks.OAK_PLANKS.defaultBlockState(),2);
 shot=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("siegeworks:"+entity)).create(level);check(shot instanceof SiegeProjectile,"Native projectile constructs: "+entity);var ball=(SiegeProjectile)shot;ball.setOwner(gun);ball.reachBlocksAs(null);ball.setPos(Frames.world(ship,new Vec3(wall.getX()-2,wall.getY()+.5,wall.getZ()+.5)));ball.setDeltaMovement(Frames.direction(ship,new Vec3(4,0,0),false));level.addFreshEntity(ball);}
 Object call(Object o,String name,Class<?>[] args,Object...v)throws Exception{return (o instanceof Class<?> c?c:o.getClass()).getMethod(name,args).invoke(o instanceof Class<?>?null:o,v);}
 void protect()throws Exception{var b=Class.forName("org.bukkit.Bukkit");Object manager=call(b,"getPluginManager",new Class<?>[]{}),plugin=call(manager,"getPlugin",new Class<?>[]{String.class},"Towny");var cl=plugin.getClass().getClassLoader();var u=Class.forName("com.palmergames.bukkit.towny.TownyUniverse",true,cl);universe=call(u,"getInstance",new Class<?>[]{});Object world=call(universe,"getWorld",new Class<?>[]{String.class},"test");townBlock=Class.forName("com.palmergames.bukkit.towny.object.TownBlock",true,cl);Class<?> tw=Class.forName("com.palmergames.bukkit.towny.object.TownyWorld",true,cl);var at=BlockPos.containing(Frames.world(ship,wall.getCenter()));claim=townBlock.getConstructor(int.class,int.class,tw).newInstance(at.getX()>>4,at.getZ()>>4,world);call(universe,"addTownBlock",new Class<?>[]{townBlock},claim);check(!ua.empirecraft.siegeworks.Blocks.allowed(level,wall),"Claim checks actual hull world position, not storage coordinates");}
 void stress(){
  shot.getPersistentData().putLong("ECImpactShip",ship.getId());
  SiegeBallisticsEnvironment.IMPACTS.drive(level,((SiegeProjectile)shot).getPhysicsProfile(),.1,new Vec3(40,120,40),new BlockPos(40,120,40),Vec3.ZERO,null,0,ImpactSource.of((SiegeProjectile)shot));
  check(!shot.getPersistentData().contains("ECImpactShip"),"Next terrain contact clears the previous hull frame");
  check(Frames.current()==null,"No leaked impact frame");try(var a=Frames.enter(ship)){check(Frames.current()==ship,"Frame entered");try(var b=Frames.enter(null)){check(Frames.current()==null,"World fragment frame");}check(Frames.current()==ship,"Nested frame restored");}check(Frames.current()==null,"Outer frame restored");
  int before=level.getChunkSource().getLoadedChunksCount();long start=System.nanoTime();int hits=0;for(int i=0;i<1000;i++){Vec3 a=Frames.world(ship,new Vec3(wall.getX()-2,wall.getY()+.5,wall.getZ()+.5)),z=Frames.world(ship,new Vec3(wall.getX()+2,wall.getY()+.5,wall.getZ()+.5));if(ShipRaycast.first(level,gun,a,z,true,s->false)!=null)hits++;}
  check(hits==1000,"Bounded rotated hull sweeps");check(level.getChunkSource().getLoadedChunksCount()==before,"Sweeps do not generate/load chunks");System.out.println("SIEGE_VS_SWEEPS count=1000 milliseconds="+(System.nanoTime()-start)/1000000.0);
 }
 public static final class Motor implements org.valkyrienskies.core.api.ships.ShipPhysicsListener {
  public volatile boolean active=true;public double speed=1,yaw=.12;
  @Override public void physTick(org.valkyrienskies.core.api.ships.PhysShip body,org.valkyrienskies.core.api.world.PhysLevel world){if(!active||body.isStatic())return;
   var v=body.getVelocity();body.applyInvariantForce(new org.joml.Vector3d((speed-v.x())*body.getMass()*2,(10-v.y()*2)*body.getMass(),-v.z()*body.getMass()*2));
   var r=body.getTransform().getShipToWorldRotation();var acceleration=r.transformInverse(new org.joml.Vector3d(0,yaw,0).sub(body.getAngularVelocity())).mul(2);body.applyInvariantTorque(r.transform(body.getMomentOfInertia().transform(acceleration)));
  }
 }
 void tick(TickEvent.ServerTickEvent e){if(server==null||e.phase!=TickEvent.Phase.END)return;try{ticks++;
  if(ticks==2)build();if(ticks==20)ready();
  if(ticks==55){check(Decks.contact(gun)!=null,"Culverin supported by VS deck");fire(6,"cannon_ball");}
  if(ticks==75){check(!level.getBlockState(wall).is(Blocks.OAK_PLANKS),"Real cannonball penetrates ship wood");check(Math.abs(shot.getX())<10000,"Projectile never teleports into storage");if(!shot.isRemoved())shot.discard();}
  if(ticks==85){System.out.println("SIEGE_VS_BEFORE_MOTION local="+Frames.local(ship,gun.position())+" expected="+localGun+" yaw="+gun.getYRot()+" tracked="+gun.getTrackedYaw()+" observer="+level.players().stream().map(p->p.position().toString()).toList());localGun=Frames.local(ship,gun.position());relativeYaw=gun.getTrackedYaw()+(float)Math.toDegrees(Math.atan2(ship.getShipToWorld().m20(),ship.getShipToWorld().m22()));ship.setAttachment(Motor.class,new Motor());ship.setStatic(false);}
  if(ticks>=85&&ticks<=165&&ticks%10==0){var drag=((org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider)gun).getDraggingInformation();System.out.println("SIEGE_VS_STEP tick="+ticks+" local="+Frames.local(ship,gun.position())+" carrier="+drag.getAddedMovementLastTick()+" velocity="+gun.getDeltaMovement()+" entityTick="+gun.tickCount+" contact="+(Decks.contact(gun)!=null));}
  if(ticks==165){ship.getAttachment(Motor.class).active=false;ship.setStatic(true);check(ship.getTransform().getPositionInWorld().distance(base)>.5,"Real physics translates the hull");System.out.println("SIEGE_VS_MOTION pos="+ship.getTransform().getPositionInWorld()+" vel="+ship.getVelocity()+" gun="+gun.position());Vec3 now=Frames.local(ship,gun.position());check(now.distanceTo(localGun)<.8,"Culverin follows moving/turning deck: drift="+now.distanceTo(localGun));check(Decks.contact(gun)!=null,"Stable deck contact after movement");float expectedYaw=relativeYaw-(float)Math.toDegrees(Math.atan2(ship.getShipToWorld().m20(),ship.getShipToWorld().m22()));check(Math.abs(net.minecraft.util.Mth.wrapDegrees(gun.getTrackedYaw()-expectedYaw))<3,"Aim rotates with the deck: tracked="+gun.getTrackedYaw()+" body="+gun.getYRot()+" expected="+expectedYaw);fire(9,"cannon_ball");}
  if(ticks==185){check(!level.getBlockState(wall).is(Blocks.OAK_PLANKS),"Cannonball damages rotated hull");if(!shot.isRemoved())shot.discard();fire(12,"trebuchet_projectile");}
  if(ticks==205){check(!level.getBlockState(wall).is(Blocks.OAK_PLANKS),"Catapult projectile damages rotated hull");if(!shot.isRemoved())shot.discard();fire(6,"cannon_ball");protect();}
  if(ticks==225){check(level.getBlockState(wall).is(Blocks.OAK_PLANKS),"Shot cannot break claimed moving hull");call(universe,"removeTownBlock",new Class<?>[]{townBlock},claim);check(ua.empirecraft.siegeworks.Blocks.allowed(level,wall),"Removed claim immediately releases hull protection");stress();localGun=Frames.local(ship,gun.position());relativeYaw=gun.getTrackedYaw()+(float)Math.toDegrees(Math.atan2(ship.getShipToWorld().m20(),ship.getShipToWorld().m22()));var reverse=new Motor();reverse.speed=-.7;reverse.yaw=-.18;ship.setAttachment(Motor.class,reverse);ship.setStatic(false);}
  if(ticks==305){ship.getAttachment(Motor.class).active=false;ship.setStatic(true);}
  if(ticks==310){double drift=Frames.local(ship,gun.position()).distanceTo(localGun);float expected=relativeYaw-(float)Math.toDegrees(Math.atan2(ship.getShipToWorld().m20(),ship.getShipToWorld().m22()));check(drift<.25,"Reverse travel preserves deck position: drift="+drift);check(Math.abs(net.minecraft.util.Mth.wrapDegrees(gun.getTrackedYaw()-expected))<3,"Reverse turn preserves aim");check(Decks.contact(gun)!=null,"Deck contact survives reverse travel");System.out.println("SIEGE_VS_REVERSE drift="+drift+" aimError="+Math.abs(net.minecraft.util.Mth.wrapDegrees(gun.getTrackedYaw()-expected)));gun.setPos(Frames.world(ship,localGun).add(60,0,0));}
  if(ticks==313){check(Decks.contact(gun)==null,"Off-deck engine is not glued to its old ship");System.out.println("SIEGE_VS_NATIVE_PASS checks="+checks+" cannon=true catapult=true rotated=true movingDeck=true reverseTravel=true liveClaims=true noForcedChunks=true");server.halt(false);}
 }catch(Throwable x){System.out.println("SIEGE_VS_NATIVE_FAIL "+x);x.printStackTrace();server.halt(false);}}
}
