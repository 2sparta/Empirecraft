package com.empirecraft.sails.test;

import com.quintonc.vs_sails.blocks.SailBlock;
import com.quintonc.vs_sails.blocks.entity.BaseHelmBlockEntity;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.util.HashSet;

/** Isolated test-only mod. Never included in the deployment folders. */
@Mod("empirecraft_sails_smoketest")
public final class SailsSmokeMod {
    private MinecraftServer server;
    private ServerLevel level;
    private ServerShip ship;
    private BlockPos helmPos, sailPos, buoyPos;
    private int ticks;
    private int assertions;
    public SailsSmokeMod() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void check(boolean value,String message) { assertions++; if(!value) throw new AssertionError(message); }
    private BlockPos local(BlockPos world) {
        Vector3d p=ship.getTransform().getWorldToShip().transformPosition(new Vector3d(world.getX()+.5,world.getY()+.5,world.getZ()+.5));
        return BlockPos.containing(p.x,p.y,p.z);
    }
    private void started(ServerStartedEvent event) {
        server=event.getServer();level=server.overworld();
        try {
            var snapshot=new java.util.ArrayList<java.util.Map<String,Object>>();
            for(var mod:net.minecraftforge.fml.ModList.get().getMods()) {
                var entry=new java.util.LinkedHashMap<String,Object>();
                entry.put("id",mod.getModId());entry.put("version",mod.getVersion().toString());
                var path=mod.getOwningFile().getFile().getFilePath();
                String sha=null;
                if(path.toUri().getScheme().equals("file") && java.nio.file.Files.isRegularFile(path))
                    sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path)));
                entry.put("sha256",sha);snapshot.add(entry);
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of("server-mod-snapshot.json"),new com.google.gson.Gson().toJson(snapshot));
        } catch(Exception error) { throw new IllegalStateException(error); }
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(server==null || event.phase!=TickEvent.Phase.END) return;
        try {
            ticks++;
            if(ticks==20) {
                BlockPos start=level.getSharedSpawnPos().above(20);
                HashSet<BlockPos> blocks=new HashSet<>();
                for(int x=0;x<5;x++) for(int z=0;z<3;z++) {
                    BlockPos pos=start.offset(x,0,z);level.setBlockAndUpdate(pos,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(pos);
                }
                helmPos=start.offset(0,1,1);buoyPos=start.offset(4,1,1);sailPos=start.offset(2,1,0);
                level.setBlockAndUpdate(helmPos,SailsBlocks.HELM_BLOCK.get().defaultBlockState());blocks.add(helmPos);
                level.setBlockAndUpdate(buoyPos,SailsBlocks.BUOY_BLOCK.get().defaultBlockState());blocks.add(buoyPos);
                for(int z=0;z<3;z++) for(int y=1;y<=2;y++) {
                    BlockPos pos=start.offset(2,y,z);level.setBlockAndUpdate(pos,SailsBlocks.SAIL_BLOCK.get().defaultBlockState());blocks.add(pos);
                }
                ship=ShipAssembler.assembleToShip(level,blocks,1);
                check(ship!=null,"assembly returns a ship");
                helmPos=local(helmPos);sailPos=local(sailPos);buoyPos=local(buoyPos);
            }
            if(ticks==80) {
                var idPath=java.nio.file.Path.of("config/admanticheat/idfile/admanticheat_id_list.json");
                if(java.nio.file.Files.exists(idPath)) {
                    var managerType=Class.forName("org.admany.anticheat.core.utils.IdManager");
                    var manager=managerType.getConstructor(org.slf4j.Logger.class).newInstance(org.slf4j.LoggerFactory.getLogger("sails-test"));
                    var ids=managerType.getMethod("loadIdListFromFile",String.class).invoke(manager,idPath.toString());
                    var entries=(java.util.List<?>)ids.getClass().getField("modIds").get(ids);
                    for(String[] expected:new String[][]{{"vs_sails","vs_sails-1.20.1-0.3.2-empirecraft.1-forge.jar"},{"valkyrienskies","valkyrienskies-120-2.4.11-empirecraft.1.jar"}}) {
                        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("mods",expected[1]))));
                        Object found=null;
                        for(var entry:entries) if(expected[0].equals(entry.getClass().getField("id").get(entry))) found=entry;
                        check(found!=null && hash.equals(found.getClass().getField("sha256").get(found)),"ADM loaded exact hash for "+expected[0]);
                    }
                    check(((java.util.List<?>)ids.getClass().getField("resourcePackIds").get(ids)).size()==4,"ADM preserved four resource packs");
                }
                var loaded=VSGameUtilsKt.getShipObjectManagingPos(level,helmPos);
                check(loaded!=null,"assembled helm belongs to loaded ship");
                var control=SailsShipControl.getOrCreate(loaded,level);
                check(control.numSails==6,"initial sails: "+control.numSails);
                check(control.numHelms==1,"initial helms: "+control.numHelms);
                check(control.numBuoys==1,"initial buoys: "+control.numBuoys);
                check(!com.quintonc.vs_sails.util.AssemblySafety.allows(level,
                    net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level), java.util.Set.of(helmPos)),
                    "assembly fails closed while Towny is unavailable");
                // Assembly centres the build on a new block grid. Find the actual local origin.
                var bounds=loaded.getShipAABB();
                for(int x=bounds.minX();x<=bounds.maxX();x++) for(int y=bounds.minY();y<=bounds.maxY();y++) for(int z=bounds.minZ();z<=bounds.maxZ();z++) {
                    var at=new BlockPos(x,y,z);
                    if(level.getBlockState(at).getBlock() instanceof com.quintonc.vs_sails.blocks.HelmBlock) helmPos=at;
                }
                buoyPos=helmPos.offset(4,0,0);sailPos=helmPos.offset(2,0,-1);
                var helm=(BaseHelmBlockEntity)level.getBlockEntity(helmPos);
                check(helm!=null,"helm block entity moved with ship");
                helm.setItem(0,new ItemStack(SailsBlocks.SPRUCE_HELM_WHEEL.get(),64));
                check(helm.getItem(0).getCount()==1,"wheel stack limited to one");
                check(helm.removeItem(0,0).isEmpty() && helm.getItem(0).getCount()==1,"zero removal preserves wheel");
                check(helm.removeItemNoUpdate(0).getCount()==1 && helm.getItem(0).isEmpty(),"automation extraction clears wheel");
                CompoundTag tag=helm.saveWithoutMetadata();helm.load(tag);
                check(helm.getItem(0).isEmpty(),"empty wheel remains empty after NBT reload");
                helm.setItem(0,new ItemStack(SailsBlocks.SPRUCE_HELM_WHEEL.get(),1));
                tag=helm.saveWithoutMetadata();tag.putInt("wheel_angle",9999);helm.load(tag);
                check(helm.wheelAngle==720,"NBT angle is clamped");
                tag.putInt("wheel_angle",360);helm.load(tag);
                level.setBlockAndUpdate(sailPos,Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(buoyPos,Blocks.STONE.defaultBlockState());
            }
            if(ticks==130) {
                var control=SailsShipControl.getOrCreate(VSGameUtilsKt.getShipObjectManagingPos(level,helmPos),level);
                check(control.numSails==5,"replacement recount: "+control.numSails);
                check(control.numBuoys==0,"buoy replacement recount: "+control.numBuoys);
                BlockPos another=sailPos.above();
                var state=level.getBlockState(another);((SailBlock)state.getBlock()).toggleFirstSail(state,level,another);
            }
            if(ticks==190) {
                var control=SailsShipControl.getOrCreate(VSGameUtilsKt.getShipObjectManagingPos(level,helmPos),level);
                check(control.numSails==0,"connected sails doused: "+control.numSails);
                check(control.numSquareSails==0 && control.numFnASails==0,"doused sail thrust cleared");
                System.out.println("SAILS_SMOKE_PASS assertions="+assertions);
                server.halt(false);server=null;
            }
        } catch(Throwable failure) {
            System.out.println("SAILS_SMOKE_FAIL "+failure);failure.printStackTrace();
            server.halt(false);server=null;
        }
    }
}
