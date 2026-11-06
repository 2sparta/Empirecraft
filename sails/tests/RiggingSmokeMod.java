package com.empirecraft.sails.test;

import com.quintonc.vs_sails.blocks.SailBlock;
import com.quintonc.vs_sails.compat.CopycatMaterials;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.util.*;

/** Disposable Forge/VS/Create/Copycats fixture. Never include in a production mod folder. */
@Mod("empirecraft_rigging_smoketest")
public final class RiggingSmokeMod {
    MinecraftServer server;ServerLevel level;LoadedServerShip ship;int ticks,checks;
    final Set<BlockPos> blocks=new HashSet<>();BlockPos origin;long shipId;
    public RiggingSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent e){
        server=e.getServer();level=server.overworld();
        // This disposable mechanics fixture has no Towny plugin. Its fail-closed access
        // policy is exercised separately by the native Towny/fleet integration fixtures.
        com.quintonc.vs_sails.protection.ProtectionConfig.get().enabled=false;
    }
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    BlockPos at(int x,int y,int z){return origin.offset(x,y,z);}
    BlockState copy(String name){var b=BuiltInRegistries.BLOCK.get(new ResourceLocation("copycats",name));check(b!=Blocks.AIR,name+" registered");return b.defaultBlockState();}
    void place(int x,int y,int z,BlockState state){BlockPos p=new BlockPos(x,100+y,z);level.setBlockAndUpdate(p,state);blocks.add(p);}
    void material(BlockPos pos,String key,BlockState material) throws Exception {
        var be=level.getBlockEntity(pos);check(be!=null,"copycat BE created");
        if(key==null)be.getClass().getMethod("setMaterial",BlockState.class).invoke(be,material);
        else be.getClass().getMethod("setMaterial",String.class,BlockState.class).invoke(be,key,material);
        var consumed=new net.minecraft.world.item.ItemStack(material.getBlock());
        if(key==null)be.getClass().getMethod("setConsumedItem",net.minecraft.world.item.ItemStack.class).invoke(be,consumed);
        else be.getClass().getMethod("setConsumedItem",String.class,net.minecraft.world.item.ItemStack.class).invoke(be,key,consumed);
        be.setChanged();SailsShipControl.markDirty(level,pos);
    }
    SailsShipControl control(){return SailsShipControl.getOrCreate(ship,level);}
    void build() throws Exception {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced(x,z,true);
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)place(x,0,z,Blocks.STONE.defaultBlockState());
        place(2,1,1,SailsBlocks.HELM_BLOCK.get().defaultBlockState());
        var sail=SailsBlocks.SAIL_BLOCK.get().defaultBlockState();
        place(0,4,0,copy("copycat_block"));material(new BlockPos(0,104,0),null,sail);
        place(1,4,0,sail);
        place(0,4,1,copy("copycat_block"));material(new BlockPos(0,104,1),null,sail);
        place(0,4,9,copy("copycat_block"));material(new BlockPos(0,104,9),null,sail);
        BlockState board=copy("copycat_board");
        board=board.setValue((BooleanProperty)board.getBlock().getStateDefinition().getProperty("north"),true)
                .setValue((BooleanProperty)board.getBlock().getStateDefinition().getProperty("south"),true);
        place(2,4,0,board);material(new BlockPos(2,104,0),"north",sail);material(new BlockPos(2,104,0),"south",sail);
        place(0,1,2,copy("copycat_board").setValue((BooleanProperty)board.getBlock().getStateDefinition().getProperty("north"),true));
        material(new BlockPos(0,101,2),"north",SailsBlocks.BUOY_BLOCK.get().defaultBlockState());
        // An absent side must not get counted, even if stale NBT still remembers a material.
        material(new BlockPos(0,101,2),"south",SailsBlocks.BUOY_BLOCK.get().defaultBlockState());
        place(1,1,2,copy("copycat_block"));material(new BlockPos(1,101,2),null,SailsBlocks.HELM_BLOCK.get().defaultBlockState());
        var body=ShipAssembler.assembleToShip(level,blocks,1);check(body!=null,"mixed copycat rig assembled");body.setStatic(true);
        shipId=body.getId();
    }
    double copiedBuoys() throws Exception {var f=SailsShipControl.class.getDeclaredField("copiedBuoyVolume");f.setAccessible(true);return f.getDouble(control());}
    void recount(){SailsShipControl.markDirty(level,at(0,4,0));control().onServerTick();}
    void click(BlockPos pos) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false);
        var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
        MinecraftForge.EVENT_BUS.post(event);check(event.isCanceled(),"registered empty-hand rigging interaction handled");
    }
    void exercise() throws Exception {
        recount();check(control().numSails==4,"projected native/copycat cloth counted once per layer group: "+control().numSails);
        check(control().numHelms==1,"copied helm does not become a second functional helm");
        check(control().numBuoys==0,"copied buoys do not receive the native buoy mass migration");
        check(copiedBuoys()>0&&copiedBuoys()<.1,"thin buoy board supplies only its real volume; absent side excluded: "+copiedBuoys());
        var before=level.getBlockEntity(at(0,4,0)).saveWithoutMetadata();
        System.out.println("RIGGING_ROOT hasChunk="+level.hasChunkAt(at(1,4,0))+" loaded="+(level.getChunkSource().getChunkNow(at(1,4,0).getX()>>4,at(1,4,0).getZ()>>4)!=null));
        click(at(1,4,0));recount();
        for(int[] p:new int[][]{{0,4,0},{1,4,0},{0,4,1},{2,4,0},{0,4,9}}) {
            var pos=at(p[0],p[1],p[2]);System.out.println("RIGGING_CELL "+Arrays.toString(p)+" state="+level.getBlockState(pos)+" parts="+CopycatMaterials.parts(level,pos,level.getBlockState(pos)));
        }
        check(control().numSails==1&&control().numFurledSails==3,"native click furls connected native, simple and multipart copycat sails; distant sail remains open: "+control().numSails+"/"+control().numFurledSails);
        check(CopycatMaterials.parts(level,at(2,4,0),level.getBlockState(at(2,4,0))).stream().allMatch(p->!p.material().getValue(SailBlock.SET)),"all present multipart sail materials furled");
        check(level.getBlockState(at(0,4,0)).getCollisionShape(level,at(0,4,0)).isEmpty(),"furled copied cloth has no invisible collision wall");
        var after=level.getBlockEntity(at(0,4,0)).saveWithoutMetadata();
        check(before.getCompound("Item").equals(after.getCompound("Item")),"furling preserves the consumed material item");
        click(at(0,4,0));recount();check(control().numSails==4&&control().numFurledSails==0,"copycat click reopens the complete connected rig");
        check(!level.getBlockState(at(0,4,0)).getCollisionShape(level,at(0,4,0)).isEmpty(),"unfurling restores the copycat collision shape");
        var be=level.getBlockEntity(at(0,4,0));var saved=be.saveWithoutMetadata();be.load(saved);recount();
        check(control().numSails==4,"material and deployed state survive NBT reload");
        level.setBlockAndUpdate(at(1,4,4),Blocks.OAK_PLANKS.defaultBlockState());recount();
        check(control().numSails==3,"wood behind cloth removes only its shadowed projected cell");
        level.setBlockAndUpdate(at(1,4,4),Blocks.AIR.defaultBlockState());recount();check(control().numSails==4,"removing wood restores wind exposure");
        level.setBlockAndUpdate(at(1,4,4),copy("copycat_block"));material(at(1,4,4),null,Blocks.OAK_PLANKS.defaultBlockState());recount();
        check(control().numSails==3,"copied wood shelters cloth like native wood");
        level.setBlockAndUpdate(at(1,4,4),Blocks.AIR.defaultBlockState());recount();
        control().shipDirection=Direction.EAST;recount();check(control().numSails==3,"front/back follows the ship's local axis, not a copied block's facing");
        material(at(0,1,2),"north",Blocks.STONE.defaultBlockState());recount();check(copiedBuoys()==0,"replacing buoy material removes copied buoyancy immediately");
        System.out.println("RIGGING_SMOKE_PASS assertions="+checks);server.halt(false);server=null;
    }
    void tick(TickEvent.ServerTickEvent e){if(server==null||e.phase!=TickEvent.Phase.END)return;try{
        if(++ticks==10)build();
        if(ticks==30) {
            ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);check(ship!=null,"ship loaded");
            var b=ship.getShipAABB();origin=new BlockPos(b.minX(),b.minY(),b.minZ());
            // No real player is present to hold the shipyard chunks in this disposable fixture.
            for(int x=b.minX()>>4;x<=(b.maxX()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
        }
        if(ticks==60)exercise();
    }catch(Throwable failure){failure.printStackTrace();System.out.println("RIGGING_SMOKE_FAIL "+failure);server.halt(false);server=null;}}
}
