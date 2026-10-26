package ua.empirecraft.shipcannons.test;

import com.mojang.authlib.GameProfile;
import net.mehvahdjukaar.supplementaries.common.block.blocks.CannonBlock;
import net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile;
import net.mehvahdjukaar.supplementaries.common.entities.CannonBallEntity;
import net.mehvahdjukaar.supplementaries.common.entities.SlingshotProjectileEntity;
import net.mehvahdjukaar.supplementaries.reg.ModRegistry;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.util.*;

/** Destructive, isolated fixture. Never install this JAR on a real server. */
@Mod("empirecraft_cannon_test")
public final class CannonSmokeMod {
    MinecraftServer server;ServerLevel level;LoadedServerShip ship;ServerPlayer player;long shipId;
    int tick,assertions;final List<CannonBlockTile> cannons=new ArrayList<>();
    final List<Entity> shots=new ArrayList<>();final Map<UUID,Vec3> origins=new HashMap<>();
    final Map<UUID,Vec3> beforeShift=new HashMap<>();
    final boolean baseline=Boolean.getBoolean("empirecraft.cannon.baseline");
    Vector3d expected;
    public CannonSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::joined);}
    void check(boolean value,String name){assertions++;if(!value)throw new AssertionError(name);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation("minecraft:world_europe")));if(level==null)throw new AssertionError("fixture world missing");}
    void joined(EntityJoinLevelEvent e){
        if(e.getLevel()!=level)return;Entity b=e.getEntity();
        if(b instanceof CannonBallEntity || b instanceof SlingshotProjectileEntity || b instanceof PrimedTnt || b instanceof AbstractArrow){
            shots.add(b);origins.put(b.getUUID(),VSGameUtilsKt.toWorldCoordinates(level,b.position()));
            System.out.println("CANNON_SPAWN "+b.getClass().getSimpleName()+" pos="+b.position()+" vel="+b.getDeltaMovement());
        }
    }
    void teleport(Vector3d position,Quaterniond rotation){VSGameUtilsKt.getShipObjectWorld(level).teleportShip(ship,new ShipTeleportDataImpl(position,rotation,new Vector3d(),new Vector3d(),ship.getChunkClaimDimension(),1.0,ship.getTransform().getPositionInShip()));}
    void setup() throws Exception {
        for(int step=-2;step<=18;step++)for(int dx=-1;dx<=1;dx++)for(int dz=-2;dz<=2;dz++)level.setChunkForced(-step+dx,-(int)(step*.58)+dz,true);
        player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("44f5fa32-a32f-45de-90f9-fb5e16d29722"),"CannonProbe"));
        player.setPos(5,184,5);level.addNewPlayer(player);
        for(var f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(f.getType()==List.class){f.setAccessible(true);try{((List)f.get(server.getPlayerList())).add(player);break;}catch(UnsupportedOperationException ignored){}}
        Set<BlockPos> blocks=new HashSet<>();
        for(int x=0;x<12;x++)for(int z=0;z<5;z++){var p=new BlockPos(x,180,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
        for(int n=0;n<4;n++){var p=new BlockPos(1+n*3,181,0);level.setBlockAndUpdate(p,ModRegistry.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING,Direction.UP));blocks.add(p);}
        ServerShip built=ShipAssembler.assembleToShip(level,blocks,1);check(built!=null,"ship assembled");built.setStatic(true);shipId=built.getId();
    }
    void aimAndFire() throws Exception {
        var b=ship.getShipAABB();
        for(int x=b.minX();x<b.maxX();x++)for(int y=b.minY();y<b.maxY();y++)for(int z=b.minZ();z<b.maxZ();z++)if(level.getBlockEntity(new BlockPos(x,y,z)) instanceof CannonBlockTile c)cannons.add(c);
        cannons.sort(Comparator.comparingInt(c->c.getBlockPos().getX()));check(cannons.size()==4,"four cannon ammunition paths");
        Vec3 center=VSGameUtilsKt.toWorldCoordinates(level,cannons.get(0).getBlockPos().getCenter());player.setPos(center.x,center.y+1,center.z);
        var first=cannons.get(0);first.setPlayerWhoMayEdit(player.getUUID());check(first.isEditingPlayer(player),"world-space owner retained");check(first.stillValid(player),"world-space cannon inventory valid");
        Item[] ammo={ModRegistry.CANNONBALL_ITEM.get(),Items.COBBLESTONE,Items.TNT,Items.ARROW};
        for(int n=0;n<4;n++){var c=cannons.get(n);level.setChunkForced(c.getBlockPos().getX()>>4,c.getBlockPos().getZ()>>4,true);c.setFuel(new ItemStack(Items.GUNPOWDER,64));c.setProjectile(new ItemStack(ammo[n],16));c.setAttributes(0,10,(byte)2,false,null);c.ignite(null);}
        double pitch=Math.toRadians(10);expected=new Vector3d(0,Math.sin(pitch),-Math.cos(pitch));ship.getTransform().getShipToWorldRotation().transform(expected);
        System.out.println("CANNON_AIM worldDirection="+expected);
    }
    void earlyCheck(){
        check(shots.stream().anyMatch(x->x instanceof CannonBallEntity),"real cannonball fired");
        check(shots.stream().anyMatch(x->x instanceof SlingshotProjectileEntity),"real ordinary item fired");
        check(shots.stream().anyMatch(x->x instanceof PrimedTnt),"real TNT fired");
        check(shots.stream().anyMatch(x->x instanceof AbstractArrow),"real arrow fired");
        for(Entity shot:shots){
            System.out.println("CANNON_EARLY "+shot.getClass().getSimpleName()+" age="+shot.tickCount+" ship="+(VSGameUtilsKt.getShipManaging(shot)!=null)+" pos="+shot.position());
            if(baseline && shot instanceof SlingshotProjectileEntity)continue;
            check(VSGameUtilsKt.getShipManaging(shot)==null,"world transition: "+shot.getClass().getSimpleName());
            var v=shot.getDeltaMovement();double dot=v.x*expected.x+v.z*expected.z;
            check(dot>0,"launch follows rotated barrel: "+shot.getClass().getSimpleName());
        }
    }
    void lateCheck(){
        for(Entity shot:shots){if(shot instanceof PrimedTnt)continue;
            System.out.println("CANNON_LATE "+shot.getClass().getSimpleName()+" age="+shot.tickCount+" removed="+shot.isRemoved()+" ship="+(VSGameUtilsKt.getShipManaging(shot)!=null)+" pos="+shot.position());
            if(baseline && shot instanceof SlingshotProjectileEntity)continue;
            check(!shot.isRemoved(),"projectile still flying");check(shot.tickCount>=70,"ticks beyond source chunk");check(VSGameUtilsKt.getShipManaging(shot)==null,"no ship attachment");
            check(shot.position().distanceTo(origins.get(shot.getUUID()))>20,"travels away from ship");beforeShift.put(shot.getUUID(),shot.position());
        }
        var transform=ship.getTransform();teleport(new Vector3d(transform.getPositionInWorld()).add(0,50,0),new Quaterniond(transform.getShipToWorldRotation()));
    }
    void finish(){
        for(Entity shot:shots){Vec3 before=beforeShift.get(shot.getUUID());if(before!=null)check(shot.getY()<before.y+10,"free shot does not follow a relocated ship");}
        var first=cannons.get(0);player.setPos(400,80,400);check(!first.isEditingPlayer(player),"distant editor rejected");check(first.getPlayerWhoMayEdit()==null,"distant edit ownership cleared");check(!first.stillValid(player),"distant inventory rejected");
        System.out.println("CANNON_SMOKE_"+(baseline?"BASELINE":"PASS")+" assertions="+assertions);server.halt(false);server=null;
    }
    void tick(TickEvent.ServerTickEvent e){if(server==null||e.phase!=TickEvent.Phase.END)return;try{
        tick++;
        if(tick==20)setup();
        if(tick==55){ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);check(ship!=null,"ship loaded");teleport(new Vector3d(ship.getTransform().getPositionInWorld()),new Quaterniond().rotateY(Math.toRadians(60)).rotateX(.12).rotateZ(-.08));}
        if(tick==75)aimAndFire();
        if(tick==140)earlyCheck();
        if(tick==200)lateCheck();
        if(tick==215)finish();
    }catch(Throwable ex){System.out.println("CANNON_SMOKE_FAIL "+ex);ex.printStackTrace();server.halt(false);server=null;}}
}
