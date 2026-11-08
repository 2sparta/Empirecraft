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
import org.joml.Quaterniond;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

/** Real finite-water/door/anchor integration; used exclusively in a disposable ocean world. */
@Mod("empirecraft_flood_smoketest")
public final class FloodSmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks;long bellId,lockId,tallId;boolean resume;
    BlockPos tallPour;
    BlockPos bottom,side,outer,inner,bellFloor,mainFloor,anchor,capstan;long trapped,lockWater;
    net.minecraftforge.common.util.FakePlayer player;ArmorStand passenger;
    BlockPos overboard,overflowCell;long heelWater,raisedWater;double leftMean;
    public FloodSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.overworld();resume=Files.exists(Path.of("flood-save.txt"));com.quintonc.vs_sails.protection.ProtectionConfig.get().enabled=false;}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    LoadedServerShip ship(long id){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
    SailsShipControl control(long id){return SailsShipControl.getOrCreate(ship(id),level);}
    long water(long id){var s=control(id).floodState;return s.reserve+s.water.values().stream().mapToLong(Integer::longValue).sum()+s.excess.values().stream().mapToLong(Integer::longValue).sum();}
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
    long tallShip(){
        // Same 70 x 51 x 21 bounds as the repository's ship3 schematic. The
        // old graph exhausted 65,536 cells on empty sky around this mast.
        Set<BlockPos> blocks=new HashSet<>();
        for(int x=100;x<170;x++)for(int z=16;z<37;z++){
            var floor=new BlockPos(x,84,z);level.setBlock(floor,Blocks.OAK_PLANKS.defaultBlockState(),18);blocks.add(floor);
            if(x==100 || x==169 || z==16 || z==36){var rail=floor.above();level.setBlock(rail,Blocks.OAK_PLANKS.defaultBlockState(),18);blocks.add(rail);}
        }
        for(int y=85;y<=134;y++){var p=new BlockPos(135,y,26);level.setBlock(p,Blocks.OAK_LOG.defaultBlockState(),18);blocks.add(p);}
        var body=ShipAssembler.assembleToShip(level,blocks,1);body.setStatic(true);return body.getId();
    }
    void positions(){
        var b=ship(bellId).getShipAABB();bottom=new BlockPos(b.minX()+3,b.minY(),b.minZ()+3);side=new BlockPos(b.minX(),b.minY()+1,b.minZ()+3);bellFloor=bottom.above();
        anchor=new BlockPos(b.minX()+2,b.minY()+5,b.minZ()+3);capstan=anchor.above();
        b=ship(lockId).getShipAABB();outer=new BlockPos(b.minX(),b.minY()+1,b.minZ()+3);inner=outer.east(4);mainFloor=inner.east(3);
        overflowCell=inner.east(2).above(2);
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
    void pose(double y,double roll,double pitch){
        var body=ship(bellId);var p=body.getTransform().getPositionInWorld();
        VSGameUtilsKt.getShipObjectWorld(level).teleportShip(body,new ShipTeleportDataImpl(new Vector3d(p.x(),y,p.z()),
                new Quaterniond().rotateZ(Math.toRadians(roll)).rotateX(Math.toRadians(pitch)),new Vector3d(),new Vector3d(),body.getChunkClaimDimension(),1.0,body.getTransform().getPositionInShip()));
    }
    double meanX(){double sum=0;long q=0;for(var e:control(bellId).floodState.water.entrySet()){sum+=BlockPos.of(e.getKey()).getX()*e.getValue();q+=e.getValue();}return sum/q;}
    void overboard(){
        ShipFlooding.pour(level,bellFloor,3);overboard=side.west();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));player.getCooldowns().removeCooldown(Items.WATER_BUCKET);
        var hit=new BlockHitResult(Vec3.atCenterOf(side),Direction.WEST,side,false);
        check(FiniteBuckets.use(level,player,InteractionHand.MAIN_HAND,hit) && player.getMainHandItem().is(Items.BUCKET),"player pours a full bucket over the actual hull edge");
        check(ShipFlooding.pour(level,overboard,5),"partial overboard pour accepts its exact quantity");
        var saved=control(bellId).floodState;
        saved.interior=java.util.stream.LongStream.concat(Arrays.stream(saved.interior),java.util.stream.LongStream.of(overboard.asLong())).toArray();
        ShipFlooding.changed(level,side); // Reproduce Sails 15's saved exterior-water mask.
    }
    void immersion(){
        var body=ship(bellId);var up=body.getShipToWorld();boolean sampled=false;
        for(var e:control(bellId).floodState.water.entrySet())if(e.getValue()<8){
            var p=BlockPos.of(e.getKey());int q=e.getValue();double x=up.m01(),y=up.m11(),z=up.m21();
            var center=FloodGeometry.centroid(FloodGeometry.mesh(x,y,z,FloodGeometry.depths(x,y,z)[q]));
            var wet=up.transformPosition(new Vector3d(p.getX()+center.x(),p.getY()+center.y(),p.getZ()+center.z()));
            var dry=up.transformPosition(new Vector3d(p.getX()+.5+Math.signum(x)*.49,p.getY()+.5+Math.signum(y)*.49,p.getZ()+.5+Math.signum(z)*.49));
            check(FloodImmersion.wet(level,body,wet.x,wet.y,wet.z),"water centroid is submerged at a real combined roll and pitch");
            check(!FloodImmersion.wet(level,body,dry.x,dry.y,dry.z),"upper corner of the same partly filled voxel stays above the angled surface");
            check(FloodImmersion.height(level,body,new AABB(wet.x-.01,wet.y,wet.z-.01,wet.x+.01,wet.y+.02,wet.z+.01))>0,"Forge body immersion sees the same tilted water polyhedron");
            sampled=true;break;
        }
        check(sampled,"tilted native water has partial surface cells");
    }
    void waterloggedPacket()throws Exception{
        var at=bellFloor.east();level.setBlockAndUpdate(at,Blocks.OAK_SLAB.defaultBlockState());
        check(ShipFlooding.pour(level,at,3) && level.getFluidState(at).getAmount()==3,"ordinary waterlogged slabs retain an exact partial measure");
        var mask=Arrays.stream(FloodNetwork.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Mask")).findFirst().orElseThrow();
        var ctor=mask.getDeclaredConstructor(String.class,long.class,FloodZones.Region.class,Map.class);ctor.setAccessible(true);
        var write=FloodNetwork.class.getDeclaredMethod("write",mask,net.minecraft.network.FriendlyByteBuf.class);write.setAccessible(true);
        var read=FloodNetwork.class.getDeclaredMethod("read",net.minecraft.network.FriendlyByteBuf.class);read.setAccessible(true);
        var volumes=mask.getDeclaredMethod("volumes");volumes.setAccessible(true);
        var packet=ctor.newInstance(VSGameUtilsKt.getDimensionId(level),bellId,FloodZones.Region.of(new long[]{at.asLong()}),Map.of(at.asLong(),3));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{write.invoke(null,packet,buffer);Object decoded=read.invoke(null,buffer);check(volumes.invoke(decoded).equals(Map.of(at.asLong(),3)),"real network codec keeps partial water in blocks without a finite block-state property");}
        finally{buffer.release();}
        check(FiniteBuckets.contents(FiniteBuckets.pickupAutomated(level,at))==3,"waterlogged slab bailing yields only the three stored eighths");level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
    }
    void trace()throws Exception{
        var maps=ShipFlooding.class.getDeclaredField("LEVELS");maps.setAccessible(true);Object context=((Map)((Map)maps.get(null)).get(level)).get(bellId);
        var field=context.getClass().getDeclaredField("topology");field.setAccessible(true);var t=(FloodTopology.Result)field.get(context);
        var ports=FloodModel.class.getDeclaredField("ports");ports.setAccessible(true);
        int n=t.index().get(bellFloor.asLong());
        System.out.println("FLOOD_CELL_TRACE state="+level.getBlockState(bellFloor)+" inside="+t.model().graph.inside(n)+" amount="+t.model().amount(n)+" ports="+ports.get(t.model()));
        for(int other:t.model().graph.edges[n])System.out.println("FLOOD_EDGE_TRACE pos="+BlockPos.of(t.cells()[other])+" inside="+t.model().graph.inside(other)+" amount="+t.model().amount(other)+" floor="+t.model().graph.floor(other));
    }
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
            if(ticks==5){setupPlayer();if(!resume){bellId=assemble(32,38,false);lockId=assemble(56,66,true);tallId=tallShip();}else{String[] s=Files.readString(Path.of("flood-save.txt")).split(",");bellId=Long.parseLong(s[0]);lockId=Long.parseLong(s[1]);lockWater=Long.parseLong(s[2]);}}
            if(ticks==20){check(ship(bellId)!=null && ship(lockId)!=null,"real assembled ships loaded");force(ship(bellId));force(ship(lockId));positions();if(!resume){
                control(bellId).onServerTick();control(lockId).onServerTick();force(ship(tallId));control(tallId).onServerTick();
                var b=ship(tallId).getShipAABB();tallPour=new BlockPos(b.minX()+20,b.minY()+1,b.minZ()+10);
                check(ShipFlooding.pour(level,tallPour,8),"a full bucket can be poured on a tall ship's deck");
            }}
            if(resume){
                if(ticks==100){check(FloodZones.contains(level,ship(bellId),bellFloor),"bare saved hull restores its managed compartment without helm interaction");check(water(lockId)==lockWater,"finite airlock water persists across real server restart");check(control(lockId).floodState.excess.getOrDefault(overflowCell.asLong(),0)==8,"a full sealed cell restores its own pending bucket at its saved position");check(water(bellId)==0,"bailed ship stays dry across restart");assertFinite(lockId);System.out.println("FLOOD_SMOKE_PASS phase=restart assertions="+checks+" savedUnits="+lockWater);server.halt(false);server=null;}return;
            }
            if(ticks==80){
                check(FloodZones.contains(level,ship(bellId),bellFloor),"dry underwater deck has managed water exclusion");check(water(bellId)==0 && water(lockId)==0,"sealed ships begin dry under the ocean: bell="+water(bellId)+" lock="+water(lockId)+" bellCells="+control(bellId).floodState.water+" lockCells="+control(lockId).floodState.water);
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.1,bellFloor.getZ()+.5));passenger=new ArmorStand(level,p.x,p.y,p.z);passenger.setNoGravity(true);level.addFreshEntity(passenger);
                level.setBlockAndUpdate(bottom,Blocks.AIR.defaultBlockState());door(outer,Direction.EAST,true);
            }
            if(ticks==180){check(water(bellId)==0,"bottom-only hole cannot displace trapped air in diving bell");check(!passenger.isInWater(),"external ocean does not mark a passenger in the dry bell as swimming");check(water(lockId)>0,"open outer door admits ocean water");check(ShipFlooding.volume(level,mainFloor)==0,"closed inner door protects the dry room");level.setBlockAndUpdate(side,Blocks.AIR.defaultBlockState());}
            if(ticks==340){
                check(water(bellId)>0,"side breach lets air escape and floods the compartment; units="+water(bellId));assertFinite(bellId);assertFinite(lockId);
                if(ShipFlooding.volume(level,bellFloor)==0)trace();
                check(ShipFlooding.volume(level,bellFloor)>0,"the passenger test cell inside the cabin contains finite water: cell="+bellFloor+" water="+control(bellId).floodState.water+" reserve="+control(bellId).floodState.reserve+" excess="+control(bellId).floodState.excess);
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.01,bellFloor.getZ()+.5));passenger.setPos(p.x,p.y,p.z);
                level.setBlockAndUpdate(bottom,Blocks.OAK_PLANKS.defaultBlockState());level.setBlockAndUpdate(side,Blocks.OAK_PLANKS.defaultBlockState());door(outer,Direction.EAST,false);
            }
            if(ticks==379 || ticks==719){
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.01,bellFloor.getZ()+.5));passenger.setPos(p.x,p.y,p.z);passenger.setDeltaMovement(Vec3.ZERO);
            }
            if(ticks==380){trapped=water(bellId);lockWater=water(lockId);check(trapped>0 && lockWater>0,"closing the hull and airlock retains water");check(passenger.isInWater(),"real finite interior water remains visible to entity fluid physics after sealing the hull");System.out.println("FLOOD_VOLUME_TRACE bell="+trapped+" lock="+lockWater);}
            if(ticks==440){
                check(FloodZones.contains(level,ship(tallId),tallPour),"tall ship topology completes instead of exhausting its cell budget");
                check(water(tallId)==8 && control(tallId).floodState.water.size()>1 && ShipFlooding.volume(level,tallPour)<8,"deck bucket spreads while conserving its exact volume on a 70 x 51 x 21 ship");
                assertFinite(tallId);check(drain(tallId)==8,"the spread deck bucket can be collected without regenerated sources");
                check(water(bellId)==trapped && water(lockId)==lockWater,"isolated water volume stays constant instead of making sources");
                check(ShipFlooding.volume(level,mainFloor)==0,"main room stays dry with both doors geometrically closed");
                door(inner,Direction.NORTH,false); // OPEN=false, but collision plane leaves the passage clear.
            }
            if(ticks==680){
                check(water(lockId)==lockWater,"opening inner door redistributes the existing quantity only");check(ShipFlooding.volume(level,mainFloor)>0,"rotated false-closed door actually passes water into the main room");assertFinite(lockId);
                long upper=control(lockId).floodState.water.entrySet().stream().filter(v->BlockPos.of(v.getKey()).getY()>mainFloor.getY()).mapToLong(v->v.getValue()).sum();check(upper==0,"airlock water spreads into a floor puddle after entering larger room");
                buckets();orientations();anchors();waterloggedPacket();
                check(water(bellId)==0,"bucket and trapdoor tests leave no residual generated water");
                overboard();
                var p=ship(bellId).getShipToWorld().transformPosition(new Vector3d(bellFloor.getX()+.5,bellFloor.getY()+.01,bellFloor.getZ()+.5));passenger.setPos(p.x,p.y,p.z);
            }
            if(ticks==710){
                check(!FloodZones.contains(level,ship(bellId),overboard),"water outside the hull is not classified as a flooded compartment");
                check(ShipFlooding.volume(level,overboard)==0 && water(bellId)==3,"overboard full and partial buckets merge into the sea without deleting the submerged internal puddle: "+water(bellId));
                var load=SailsShipControl.class.getDeclaredField("floodLoad");load.setAccessible(true);
                check(((ShipFlooding.Load)load.get(control(bellId))).mass()==375,"discarded overboard water no longer contributes force to the ship");
                check(drain(bellId)==3,"the remaining submerged compartment still contains exactly its own water");
            }
            if(ticks==720){
                check(!passenger.isInWater(),"bailing the interior clears entity swimming despite external ocean");
                pose(82,30,15);
                var base=bellFloor.offset(-2,0,-2);
                for(int x=0;x<4;x++)for(int z=0;z<4;z++)check(ShipFlooding.pour(level,base.offset(x,0,z),8),"seed measured cabin water for the heel test");heelWater=128;
            }
            if(ticks==880){
                check(water(bellId)==heelWater,"combined roll and pitch conserve all cabin water");leftMean=meanX();immersion();pose(82,-30,-15);
            }
            if(ticks==1040){
                check(water(bellId)==heelWater,"reversing heel never creates or deletes water");
                check(meanX()>leftMean+.3,"native finite water shifts toward the newly lowered side: "+leftMean+" -> "+meanX());immersion();
                level.setBlockAndUpdate(overflowCell,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
                check(ShipFlooding.pour(level,overflowCell,8) && ShipFlooding.pour(level,overflowCell,8),"a fully sealed waterlogged cell retains a locally pending bucket");lockWater+=16;
                check(drain(bellId)==heelWater,"all heeled water remains recoverable by buckets");pose(72,-25,0);level.setBlockAndUpdate(side,Blocks.AIR.defaultBlockState());
            }
            if(ticks==1160){
                check(water(bellId)==0,"a real breach lifted above the ocean by heel admits no water: "+water(bellId));pose(72,25,0);
            }
            if(ticks==1320){check(water(bellId)>0,"the same breach resumes flooding when heel submerges it");pose(72,-25,0);}
            if(ticks==1380)raisedWater=water(bellId);
            if(ticks==1440){
                check(water(bellId)==raisedWater && raisedWater>0,"raising a flooded breach stops ingress while retaining trapped water");
                level.setBlockAndUpdate(side,Blocks.OAK_PLANKS.defaultBlockState());check(drain(bellId)==raisedWater,"repaired tilted compartment can be bailed dry");
            }
            if(ticks==1480){
                check(water(bellId)==0,"overboard and heel tests leave the saved hull dry");
                check(control(lockId).floodState.excess.getOrDefault(overflowCell.asLong(),0)==8 && water(lockId)==lockWater,"pending water stays in its sealed cell before the real save");
                Files.writeString(Path.of("flood-save.txt"),bellId+","+lockId+","+lockWater);
                System.out.println("FLOOD_SMOKE_PASS phase=airlock-bell-buckets-overboard-roll-pitch-breaches-immersion-anchor assertions="+checks+" airlockUnits="+lockWater);server.halt(false);server=null;
            }
        }catch(Throwable failure){System.out.println("FLOOD_SMOKE_FAIL tick="+ticks);failure.printStackTrace();server.halt(false);server=null;}
    }
}
