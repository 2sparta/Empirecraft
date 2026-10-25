package com.empirecraft.sails.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quintonc.vs_sails.blocks.AnchorBlock;
import com.quintonc.vs_sails.blocks.BaseHelmBlock;
import com.quintonc.vs_sails.blocks.entity.BaseHelmBlockEntity;
import com.quintonc.vs_sails.entity.AnchorVisualEntity;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.AnchorMechanics;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.core.impl.game.ships.PhysShipImpl;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.nio.file.*;
import java.util.*;

/** Isolated, destructive fixture: builds a test ship and stops the server. Never ship this mod. */
@Mod("empirecraft_arcade_smoketest")
public final class ArcadeSmokeMod {
    private MinecraftServer server;
    private ServerLevel level;
    private LoadedServerShip ship;
    private BlockPos helm, capstan, anchor;
    private int ticks, assertions;
    private Probe probe;
    private boolean held;
    private int holdTick;
    private Vector3d heldPosition;
    private final boolean resume = Boolean.getBoolean("empirecraft.arcade.resume");
    public ArcadeSmokeMod() {
        ValkyrienSkies.api().registerAttachment(ValkyrienSkies.api().newAttachmentRegistrationBuilder(Probe.class).useLegacySerializer().build());
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void check(boolean value,String message) { assertions++; if(!value) throw new AssertionError(message); }
    private void started(ServerStartedEvent event) {
        server=event.getServer();
        level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation("minecraft:world_europe")));
        if(level==null) throw new AssertionError("world_europe test pack missing");
    }
    private void findBlocks(LoadedServerShip loaded) {
        var b=loaded.getShipAABB();
        for(int x=b.minX();x<=b.maxX();x++) for(int y=b.minY();y<=b.maxY();y++) for(int z=b.minZ();z<=b.maxZ();z++) {
            var p=new BlockPos(x,y,z);var block=level.getBlockState(p).getBlock();
            if(block instanceof com.quintonc.vs_sails.blocks.HelmBlock) helm=p;
            if(block instanceof com.quintonc.vs_sails.blocks.CapstanBlock) capstan=p;
            if(block instanceof AnchorBlock) anchor=p;
        }
    }
    private SailsShipControl control() { return SailsShipControl.getOrCreate(ship,level); }
    private int visuals() { return level.getEntitiesOfClass(AnchorVisualEntity.class,new AABB(-150,-64,-150,150,320,150)).size(); }
    private void tick(TickEvent.ServerTickEvent event) {
        if(server==null || event.phase!=TickEvent.Phase.END) return;
        try {
            ticks++;
            if(resume) { resumeTick(); return; }
            if(ticks==20) {
                BlockPos origin=new BlockPos(0,18,0);Set<BlockPos> blocks=new HashSet<>();
                for(int x=0;x<7;x++) for(int z=0;z<5;z++) {
                    if(x==5 && z==2) continue;
                    BlockPos at=origin.offset(x,0,z);level.setBlockAndUpdate(at,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(at);
                }
                helm=origin.offset(1,1,2);capstan=origin.offset(5,1,2);anchor=capstan.below(2);
                level.setBlockAndUpdate(helm,SailsBlocks.HELM_BLOCK.get().defaultBlockState().setValue(BaseHelmBlock.FACING,Direction.NORTH));blocks.add(helm);
                level.setBlockAndUpdate(capstan,SailsBlocks.CAPSTAN_BLOCK.get().defaultBlockState());blocks.add(capstan);
                level.setBlockAndUpdate(anchor,SailsBlocks.ANCHOR_BLOCK.get().defaultBlockState());blocks.add(anchor);
                for(int x=2;x<=4;x++) for(int y=1;y<=2;y++) {
                    BlockPos at=origin.offset(x,y,4);level.setBlockAndUpdate(at,SailsBlocks.SAIL_BLOCK.get().defaultBlockState());blocks.add(at);
                }
                for(int x:new int[]{0,6}) for(int z:new int[]{0,4}) {
                    BlockPos at=origin.offset(x,1,z);level.setBlockAndUpdate(at,SailsBlocks.BUOY_BLOCK.get().defaultBlockState());blocks.add(at);
                }
                ServerShip assembled=ShipAssembler.assembleToShip(level,blocks,1);
                check(assembled!=null,"real ship assembled");
                // Assembly/load callbacks fill the actual attachment on the next ticks.
                Files.writeString(Path.of("arcade-fixture-ship.txt"),Long.toString(assembled.getId()));
            }
            if(ticks==65) {
                long id=Long.parseLong(Files.readString(Path.of("arcade-fixture-ship.txt")).trim());
                ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);
                check(ship!=null,"ship loaded");findBlocks(ship);
                check(helm!=null && capstan!=null && anchor!=null,"all control blocks located");
                var bounds=ship.getShipAABB();
                for(int cx=bounds.minX()>>4;cx<=bounds.maxX()>>4;cx++) for(int cz=bounds.minZ()>>4;cz<=bounds.maxZ()>>4;cz++)
                    level.setChunkForced(cx,cz,true); // TEST ONLY: custom dimension has no players keeping its block entities ticking.
                ((BaseHelmBlockEntity)level.getBlockEntity(helm)).setItem(0,new ItemStack(SailsBlocks.SPRUCE_HELM_WHEEL.get()));
            }
            if(ticks==80) {
                var c=control();check(c.hasHelm && c.shipDirection==Direction.SOUTH,"north helm defines bow despite east-west hull bounds: has="+c.hasHelm+" dir="+c.shipDirection+" state="+level.getBlockState(helm));
                check(c.numSails==6 && c.numHelms==1,"real recount remains valid");
                for(Direction facing:new Direction[]{Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH}) {
                    c.submitHelm(helm,facing,0,true,true);
                    SailsShipControl.getOrCreate(ship,level);
                    check(c.shipDirection==facing.getOpposite(),"getOrCreate preserves helm-selected facing "+facing);
                }
                probe=new Probe();probe.controls=new ArrayList<>();
                for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
                    var sample=new SailsShipControl(); sample.world=level;
                    sample.submitHelm(helm,facing,0,true,true);sample.numSails=8;
                    probe.controls.add(sample);
                }
                ship.setAttachment(Probe.class,probe);
            }
            if(ticks==95) {
                check(probe!=null && probe.complete,"actual physics probe completed");
                check(probe.failure==null,"physics probe: "+probe.failure);
                check(probe.assertions==120,"expected physics assertions: "+probe.assertions);
                ship.removeAttachment(Probe.class);
                AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
                check(control().anchorDeployed && !control().anchorRaising,"anchor starts lowering");
                check(level.getBlockState(anchor).getValue(AnchorBlock.DEPLOYED),"stowed anchor model hidden during drop");
            }
            if(ticks==120) {
                check(control().anchorProgress>0 && control().anchorProgress<=1,"lowering is animated");
                check(visuals()==1,"one visual entity rather than many chain entities");
                var visual=level.getEntitiesOfClass(AnchorVisualEntity.class,new AABB(-150,-64,-150,150,320,150)).get(0);
                check(!visual.shouldBeSaved(),"visual cannot become a persistent ghost after reload");
            }
            if(ticks>120 && control().anchorLocked && !held) {
                held=true;holdTick=ticks;heldPosition=new Vector3d(ship.getTransform().getPositionInWorld());
                check(ship.isStatic(),"low-speed hold completely freezes real VS ship");
                check(control().anchorProgress==1,"hold follows completed lowering");
            }
            if(held && ticks>=holdTick+20) {
                check(heldPosition.distance(ship.getTransform().getPositionInWorld())<.03,"held ship position stays fixed over a second");
                Files.writeString(Path.of("arcade-fixture-hold-position.txt"),heldPosition.x+","+heldPosition.y+","+heldPosition.z);
                System.out.println("ARCADE_SMOKE_PASS phase=hold assertions="+assertions+" physicsAssertions="+probe.assertions);
                server.halt(false);server=null;
            }
            if(ticks>520) throw new AssertionError("anchor did not reach hold: progress="+control().anchorProgress+" velocity="+ship.getVelocity()+" omega="+ship.getAngularVelocity());
        } catch(Throwable error) { fail(error); }
    }
    private void resumeTick() throws Exception {
        if(ticks==60) {
            long id=Long.parseLong(Files.readString(Path.of("arcade-fixture-ship.txt")).trim());
            ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);
            check(ship!=null,"saved ship reloads"); findBlocks(ship);
            check(control().anchorDeployed && control().anchorLocked && ship.isStatic(),"anchor holds across restart");
            check(visuals()==1,"exactly one rebuilt visual after restart");
            String[] saved=Files.readString(Path.of("arcade-fixture-hold-position.txt")).split(",");
            check(new Vector3d(Double.parseDouble(saved[0]),Double.parseDouble(saved[1]),Double.parseDouble(saved[2])).distance(ship.getTransform().getPositionInWorld())<.03,"restart retains held position");
            control().raiseAnchor();
            check(control().anchorRaising && !ship.isStatic(),"raising releases the owned static hold immediately");
        }
        if(ticks==80) check(control().anchorProgress<1 && control().anchorProgress>0,"raising is animated");
        if(ticks==260) {
            check(!control().anchorDeployed && !control().anchorLocked,"raising completed");
            check(!level.getBlockState(anchor).getValue(AnchorBlock.DEPLOYED),"original anchor visible again");
            check(visuals()==0,"chain/anchor visual removed on raise");
            AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
            check(control().anchorDeployed,"can lower again");
            level.setBlockAndUpdate(anchor,Blocks.AIR.defaultBlockState());
            check(!control().anchorDeployed && !ship.isStatic(),"breaking anchor releases boat");
            check(level.getBlockState(anchor).isAir(),"cleanup never resurrects broken anchor");
            check(visuals()==0,"breaking anchor cleans visuals");
            level.setBlockAndUpdate(anchor,SailsBlocks.ANCHOR_BLOCK.get().defaultBlockState());
            ship.setStatic(true);
            AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
            check(control().anchorWasStatic,"records a ship that was already static");
        }
        if(ticks==450) {
            check(control().anchorLocked && ship.isStatic(),"pre-existing static ship reaches held state");
            control().releaseAnchor();
            check(ship.isStatic(),"release preserves a pre-existing external static state");
            ship.setStatic(false);
            AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
            check(control().anchorDeployed,"can deploy after releasing a prior static ship");
            control().anchorDimension="invalid-test-dimension";
            control().onServerTick();
            check(!control().anchorDeployed && !ship.isStatic(),"dimension mismatch releases safely");
            AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
            control().onServerTick();
            check(visuals()==1,"visual recreated on deployment");
            level.setBlockAndUpdate(capstan,Blocks.AIR.defaultBlockState());
            check(!control().anchorDeployed && visuals()==0,"breaking capstan removes the whole anchor visual");
            check(!level.getBlockState(anchor).getValue(AnchorBlock.DEPLOYED),"capstan cleanup restores the original anchor");
            System.out.println("ARCADE_SMOKE_PASS phase=resume assertions="+assertions);
            server.halt(false);server=null;
        }
    }
    private void fail(Throwable error) {
        System.out.println("ARCADE_SMOKE_FAIL "+error);error.printStackTrace();
        server.halt(false);server=null;
    }
    public static final class Probe implements ShipPhysicsListener {
        @JsonIgnore public List<SailsShipControl> controls;
        @JsonIgnore public volatile boolean complete;
        @JsonIgnore public String failure;
        @JsonIgnore public int assertions;
        private void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
        @SuppressWarnings("unchecked") private ArrayDeque<Vector3dc> queue(PhysShipImpl ship,String name)throws Exception {
            var f=PhysShipImpl.class.getDeclaredField(name);f.setAccessible(true);return(ArrayDeque<Vector3dc>)f.get(ship);
        }
        private void wind(SailsShipControl control,double direction,double strength)throws Exception {
            var f=SailsShipControl.class.getDeclaredField("sampledWind");f.setAccessible(true);
            var ctor=f.getType().getDeclaredConstructor(double.class,double.class);ctor.setAccessible(true);f.set(control,ctor.newInstance(direction,strength));
        }
        private Vector3d[] forces(SailsShipControl control,PhysShipImpl ship,PhysLevel level)throws Exception {
            var forces=queue(ship,"invPosForces");var positions=queue(ship,"invPosPositions");var torques=queue(ship,"invTorques");
            int nf=forces.size(),np=positions.size(),nt=torques.size();
            control.physTick(ship,level);Vector3d f=new Vector3d(),t=new Vector3d();
            while(forces.size()>nf)f.add(forces.removeLast());while(positions.size()>np)positions.removeLast();while(torques.size()>nt)t.add(torques.removeLast());
            return new Vector3d[]{f,t};
        }
        @Override public void physTick(PhysShip physShip,PhysLevel level) {
            if(complete || controls==null)return;
            PhysShipImpl ship=(PhysShipImpl)physShip;var saved=ship.getPoseVel();
            try {
                for(var control:controls) {
                    Direction bow=control.shipDirection.getOpposite();
                    Vector3d heading=com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),bow.getStepX(),bow.getStepZ());
                    for(int direction:new int[]{0,90,180,270})for(int sign:new int[]{1,-1}) {
                        ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,saved.c,new Vector3d(),new Vector3d()));
                        wind(control,direction,sign);var result=forces(control,ship,level);
                        check(result[0].dot(heading)>0,"all winds drive toward the helm bow");
                        check(Math.abs(result[0].y)<1e-7,"no vertical sail lift");
                        check(result[0].isFinite() && result[1].isFinite(),"finite production forces");
                    }
                    Vector3d side=new Vector3d(heading.z,0,-heading.x).mul(3);
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,saved.c,side,new Vector3d()));
                    var drift=forces(control,ship,level);check(drift[0].dot(side)<0,"production force kills sideways drift");
                    for(double turn:new double[]{-1,0,1}) {
                        ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,saved.c,heading.mul(3,new Vector3d()),new Vector3d(0,turn==0?.2:0,0)));
                        var f=SailsShipControl.class.getDeclaredField("helmSample");f.setAccessible(true);Object old=f.get(control);
                        var ctor=old.getClass().getDeclaredConstructors()[0];ctor.setAccessible(true);
                        f.set(control,ctor.newInstance(0L,bow,turn,2,0L));
                        var turning=forces(control,ship,level);
                        check(turn==0?turning[1].y<0:turning[1].y*turn>0,"correct arcade yaw direction");
                    }
                    control.anchorDeployed=true;
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,saved.c,new Vector3d(5,.2,-2),new Vector3d()));
                    var braking=forces(control,ship,level);
                    check(braking[0].dot(new Vector3d(5,.2,-2))<0,"anchor overrides propulsion with real braking");
                    check(braking[0].isFinite(),"finite anchor brake");
                }
            } catch(Throwable error) { failure=error.toString();error.printStackTrace(); }
            finally { ship.setPoseVel(saved);complete=true; }
        }
    }
}
