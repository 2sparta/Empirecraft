package com.empirecraft.sails.test;

import com.quintonc.vs_sails.ship.*;
import com.quintonc.vs_sails.registration.SailsBlocks;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

/** Real finite-water/door/anchor integration; used exclusively in a disposable ocean world. */
@Mod("empirecraft_flood_smoketest")
public final class FloodSmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks;long bellId,lockId;boolean resume;
    BlockPos bottom,side,outer,inner,bellFloor,mainFloor,anchor,capstan;long trapped,lockWater;
    net.minecraftforge.common.util.FakePlayer player;ArmorStand passenger;
    public FloodSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.overworld();resume=Files.exists(Path.of("flood-save.txt"));com.quintonc.vs_sails.protection.ProtectionConfig.get().enabled=false;}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    LoadedServerShip ship(long id){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
    SailsShipControl control(long id){return SailsShipControl.getOrCreate(ship(id),level);}
    long water(long id){var s=control(id).floodState;return s.reserve+s.water.values().stream().mapToLong(Integer::longValue).sum();}
    void force(LoadedServerShip body){var b=body.getShipAABB();for(int x=b.minX()>>4;x<=(b.maxX()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);}
    void door(BlockPos pos,Direction facing,boolean open){
        for(var half:DoubleBlockHalf.values()){
            var p=half==DoubleBlockHalf.LOWER?pos:pos.above();var previous=level.getBlockState(p);
            BlockState state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,facing).setValue(DoorBlock.OPEN,open).setValue(DoorBlock.HALF,half);
            if(previous.is(Blocks.OAK_DOOR))state=state.setValue(BlockStateProperties.WATERLOGGED,previous.getValue(BlockStateProperties.WATERLOGGED)).setValue(DoorWater.FINITE,previous.getValue(DoorWater.FINITE));
            level.setBlock(p,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }
    long assemble(int x0,int x1,boolean airlock){
        Set<BlockPos> blocks=new HashSet<>();int top=airlock?66:67;
        for(int x=x0;x<=x1;x++)for(int y=63;y<=top;y++)for(int z=16;z<=22;z++){
            BlockPos p=new BlockPos(x,y,z);boolean wall=x==x0||x==x1||y==63||y==top||z==16||z==22||airlock&&x==x0+4;
            level.setBlock(p,wall?Blocks.OAK_PLANKS.defaultBlockState():Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            if(wall)blocks.add(p);
        }
        if(airlock){
            door(new BlockPos(x0,64,19),Direction.EAST,false);door(new BlockPos(x0+4,64,19),Direction.EAST,false);
        }else{
            var a=new BlockPos(x0+2,68,19);var c=a.above();
            level.setBlockAndUpdate(a,SailsBlocks.ANCHOR_BLOCK.get().defaultBlockState());level.setBlockAndUpdate(c,SailsBlocks.CAPSTAN_BLOCK.get().defaultBlockState());blocks.add(a);blocks.add(c);
        }
        var body=ShipAssembler.assembleToShip(level,blocks,1);body.setStatic(true);return body.getId();
    }
    void positions(){
        var b=ship(bellId).getShipAABB();bottom=new BlockPos(b.minX()+3,b.minY(),b.minZ()+3);side=new BlockPos(b.minX(),b.minY()+1,b.minZ()+3);bellFloor=bottom.above();
        anchor=new BlockPos(b.minX()+2,b.minY()+5,b.minZ()+3);capstan=anchor.above();
        b=ship(lockId).getShipAABB();outer=new BlockPos(b.minX(),b.minY()+1,b.minZ()+3);inner=outer.east(4);mainFloor=inner.east(3);
    }
    void setupPlayer(){
        player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.fromString("e93ab403-e73d-47ea-91dd-a97c1e188d67"),"FloodTester"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(48,100,20);level.addNewPlayer(player);
        for(var f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(f.getType()==List.class){try{f.setAccessible(true);((List)f.get(server.getPlayerList())).add(player);break;}catch(ReflectiveOperationException|UnsupportedOperationException ignored){}}
        for(int x=-1;x<6;x++)for(int z=-1;z<4;z++)level.setChunkForced(x,z,true);
    }
    void assertFinite(long id){
        for(var entry:control(id).floodState.water.entrySet()){
            var pos=BlockPos.of(entry.getKey());var f=level.getFluidState(pos);
            check(!f.isSource(),"ship water must never be a source at "+pos);
            check(f.getAmount()==entry.getValue(),"world fluid keeps exact stored volume at "+pos);
        }
    }
    int drain(long id){int removed=0;for(var pos:new ArrayList<>(control(id).floodState.water.keySet()))removed+=ShipFlooding.bail(level,BlockPos.of(pos),8);return removed;}
    void buckets(){
        check(drain(bellId)==trapped,"all isolated bell water can be bailed without creating water");check(water(bellId)==0,"bell becomes dry after bailing");
        check(ShipFlooding.pour(level,bellFloor,3),"pour exactly three eighths into the ship");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
        var hit=new BlockHitResult(Vec3.atCenterOf(bellFloor),Direction.UP,bellFloor,false);
        java.util.function.Consumer<FillBucketEvent> cancel=e->e.setCanceled(true);
        MinecraftForge.EVENT_BUS.addListener(cancel);check(FiniteBuckets.use(level,player,InteractionHand.MAIN_HAND,hit),"cancelled bucket action handled");MinecraftForge.EVENT_BUS.unregister(cancel);
        check(water(bellId)==3 && player.getMainHandItem().is(Items.BUCKET),"Forge bucket cancellation preserves water and item");
        check(FiniteBuckets.use(level,player,InteractionHand.MAIN_HAND,hit),"player collects partial puddle");
        check(FiniteBuckets.contents(player.getMainHandItem())==3,"partial puddle yields a 3/8 bucket");check(!player.getMainHandItem().is(Items.WATER_BUCKET),"partial bucket cannot masquerade as a full vanilla bucket");check(water(bellId)==0,"partial pickup removes exactly three units");
        player.getCooldowns().removeCooldown(player.getMainHandItem().getItem());player.setShiftKeyDown(true);
        check(FiniteBuckets.use(level,player,InteractionHand.MAIN_HAND,hit),"sneaking pours the partial bucket");player.setShiftKeyDown(false);
        check(water(bellId)==3 && player.getMainHandItem().is(Items.BUCKET),"partial pour conserves three units");
        var picked=FiniteBuckets.pickupAutomated(level,bellFloor);check(FiniteBuckets.contents(picked)==3 && water(bellId)==0,"automatic pickup also preserves partial quantity");
        ShipFlooding.pour(level,bellFloor,8);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));player.getCooldowns().removeCooldown(Items.BUCKET);
        check(FiniteBuckets.use(level,player,InteractionHand.MAIN_HAND,hit) && player.getMainHandItem().is(Items.WATER_BUCKET),"a full measure gives the ordinary water bucket");
        var dispenser=bellFloor.south();level.setBlock(dispenser,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.NORTH),18);
        ShipFlooding.pour(level,bellFloor,5);var source=new net.minecraft.core.BlockSourceImpl(level,dispenser);
        var returned=DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET).dispense(source,new ItemStack(Items.BUCKET));
        check(FiniteBuckets.contents(returned)==5 && water(bellId)==0,"registered dispenser behavior collects exact partial measure");
        var empty=DispenserBlock.DISPENSER_REGISTRY.get(returned.getItem()).dispense(source,returned);
        check(empty.is(Items.BUCKET) && water(bellId)==5,"registered dispenser pours the same partial measure");
        check(drain(bellId)==5,"automated round trip creates no extra water");level.setBlockAndUpdate(dispenser,Blocks.AIR.defaultBlockState());
    }
    void orientations(){
        // Same physically passable shape can be encoded with OPEN=false or OPEN=true.
        var closed=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.OPEN,false);
        var rotated=closed.setValue(DoorBlock.FACING,Direction.NORTH);
        var shape=closed.getCollisionShape(level,outer);var rotatedShape=rotated.getCollisionShape(level,outer);
        check(shape.max(Direction.Axis.X)-shape.min(Direction.Axis.X)<.25,"closed corridor door is a slab across X");
        check(rotatedShape.max(Direction.Axis.X)-rotatedShape.min(Direction.Axis.X)>.9,"rotated OPEN=false door leaves X passage");
        check(rotated.hasProperty(BlockStateProperties.WATERLOGGED),"vanilla doors acquire waterlogging");
        var p=new BlockPos(8,100,8);level.setBlockAndUpdate(p,Blocks.WATER.defaultBlockState());
        var placement=new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_DOOR),new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false));
        var placed=Blocks.OAK_DOOR.getStateForPlacement(placement);check(placed!=null && placed.getValue(BlockStateProperties.WATERLOGGED),"placing a door underwater no longer creates an air pocket");
        var trap=Blocks.OAK_TRAPDOOR.getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_TRAPDOOR),new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false)));
        check(trap!=null && trap.getValue(BlockStateProperties.WATERLOGGED),"underwater trapdoors remain wet");
        var at=bellFloor.east();level.setBlock(at,Blocks.OAK_TRAPDOOR.defaultBlockState(),18);ShipFlooding.pour(level,at,3);
        check(level.getBlockState(at).getValue(DoorWater.FINITE)==3 && level.getFluidState(at).getAmount()==3 && !level.getFluidState(at).isSource(),"finite waterlogged trapdoor keeps partial volume");
        check(FiniteBuckets.contents(((SimpleWaterloggedBlock)Blocks.OAK_TRAPDOOR).pickupBlock(level,at,level.getBlockState(at)))==3,"waterlogged-block pickup cannot produce a full bucket from partial water");
        level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
    }
    void anchors(){
        var body=ship(bellId);var c=control(bellId);var p=body.getShipToWorld().transformPosition(new Vector3d(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5));
        c.anchorCapstan=capstan.asLong();c.anchorBlock=anchor.asLong();c.anchorDimension=body.getChunkClaimDimension();c.anchorX=p.x;c.anchorY=p.y-12;c.anchorZ=p.z;c.anchorLength=12;c.anchorProgress=1;c.anchorDeployed=true;c.anchorWasStatic=false;c.anchorLocked=true;
        AnchorMechanics.tick(c,level);check(!body.isStatic() && !c.anchorLocked,"legacy owned anchor migrates from static lock to dynamic tether");
        c.sailSafetyHeld=true;c.anchorLocked=true;body.setStatic(true);AnchorMechanics.tick(c,level);check(body.isStatic(),"anchor migration preserves a separate safety hold");c.sailSafetyHeld=false;
        c.anchorWasStatic=true;c.anchorLocked=true;AnchorMechanics.tick(c,level);check(body.isStatic(),"anchor migration preserves a ship that was already static");
        c.releaseAnchor();check(body.isStatic(),"raising anchor preserves the external static state");
        // Keep this saved flood fixture static; native sea-motion tests cover dynamic rocking.
    }
    void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || server==null)return;
        try{
            ticks++;
            if(ticks==5){setupPlayer();if(!resume){bellId=assemble(32,38,false);lockId=assemble(56,66,true);}else{String[] s=Files.readString(Path.of("flood-save.txt")).split(",");bellId=Long.parseLong(s[0]);lockId=Long.parseLong(s[1]);lockWater=Long.parseLong(s[2]);}}
            if(ticks==20){check(ship(bellId)!=null && ship(lockId)!=null,"real assembled ships loaded");force(ship(bellId));force(ship(lockId));positions();if(!resume){control(bellId).onServerTick();control(lockId).onServerTick();}}
            if(resume){
                if(ticks==100){check(FloodZones.contains(level,ship(bellId),bellFloor),"bare saved hull restores its managed compartment without helm interaction");check(water(lockId)==lockWater,"finite airlock water persists across real server restart");check(water(bellId)==0,"bailed ship stays dry across restart");assertFinite(lockId);System.out.println("FLOOD_SMOKE_PASS phase=restart assertions="+checks+" savedUnits="+lockWater);server.halt(false);server=null;}return;
            }
            if(ticks==80){
                check(FloodZones.contains(level,ship(bellId),bellFloor),"dry underwater deck has managed water exclusion");check(water(bellId)==0 && water(lockId)==0,"sealed ships begin dry under the ocean: bell="+water(bellId)+" lock="+water(lockId)+" bellCells="+control(bellId).floodState.water+" lockCells="+control(lockId).floodState.water);
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.1,bellFloor.getZ()+.5));passenger=new ArmorStand(level,p.x,p.y,p.z);passenger.setNoGravity(true);level.addFreshEntity(passenger);
                level.setBlockAndUpdate(bottom,Blocks.AIR.defaultBlockState());door(outer,Direction.EAST,true);
            }
            if(ticks==180){check(water(bellId)==0,"bottom-only hole cannot displace trapped air in diving bell");check(!passenger.isInWater(),"external ocean does not mark a passenger in the dry bell as swimming");check(water(lockId)>0,"open outer door admits ocean water");check(ShipFlooding.volume(level,mainFloor)==0,"closed inner door protects the dry room");level.setBlockAndUpdate(side,Blocks.AIR.defaultBlockState());}
            if(ticks==340){
                check(water(bellId)>0,"side breach lets air escape and floods the compartment; units="+water(bellId));assertFinite(bellId);assertFinite(lockId);
                check(ShipFlooding.volume(level,bellFloor)>0,"the passenger test cell inside the cabin contains finite water");
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.01,bellFloor.getZ()+.5));passenger.setPos(p.x,p.y,p.z);
                level.setBlockAndUpdate(bottom,Blocks.OAK_PLANKS.defaultBlockState());level.setBlockAndUpdate(side,Blocks.OAK_PLANKS.defaultBlockState());door(outer,Direction.EAST,false);
            }
            if(ticks==379 || ticks==719){
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.01,bellFloor.getZ()+.5));passenger.setPos(p.x,p.y,p.z);passenger.setDeltaMovement(Vec3.ZERO);
            }
            if(ticks==380){trapped=water(bellId);lockWater=water(lockId);check(trapped>0 && lockWater>0,"closing the hull and airlock retains water");check(passenger.isInWater(),"real finite interior water remains visible to entity fluid physics after sealing the hull");System.out.println("FLOOD_VOLUME_TRACE bell="+trapped+" lock="+lockWater);}
            if(ticks==440){
                check(water(bellId)==trapped && water(lockId)==lockWater,"isolated water volume stays constant instead of making sources");
                check(ShipFlooding.volume(level,mainFloor)==0,"main room stays dry with both doors geometrically closed");
                door(inner,Direction.NORTH,false); // OPEN=false, but collision plane leaves the passage clear.
            }
            if(ticks==680){
                check(water(lockId)==lockWater,"opening inner door redistributes the existing quantity only");check(ShipFlooding.volume(level,mainFloor)>0,"rotated false-closed door actually passes water into the main room");assertFinite(lockId);
                long upper=control(lockId).floodState.water.entrySet().stream().filter(v->BlockPos.of(v.getKey()).getY()>mainFloor.getY()).mapToLong(v->v.getValue()).sum();check(upper==0,"airlock water spreads into a floor puddle after entering larger room");
                buckets();orientations();anchors();
                check(water(bellId)==0,"bucket and trapdoor tests leave no residual generated water");
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.01,bellFloor.getZ()+.5));passenger.setPos(p.x,p.y,p.z);
            }
            if(ticks==720){
                check(!passenger.isInWater(),"bailing the interior clears entity swimming despite external ocean");
                Files.writeString(Path.of("flood-save.txt"),bellId+","+lockId+","+lockWater);
                System.out.println("FLOOD_SMOKE_PASS phase=airlock-bell-buckets-doors-anchor assertions="+checks+" airlockUnits="+lockWater);server.halt(false);server=null;
            }
        }catch(Throwable failure){System.out.println("FLOOD_SMOKE_FAIL tick="+ticks);failure.printStackTrace();server.halt(false);server=null;}
    }
}
