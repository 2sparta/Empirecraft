package com.empirecraft.sails.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quintonc.vs_sails.blocks.AnchorBlock;
import com.quintonc.vs_sails.blocks.BaseHelmBlock;
import com.quintonc.vs_sails.blocks.entity.BaseHelmBlockEntity;
import com.quintonc.vs_sails.blocks.entity.HelmBlockEntity;
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
import org.joml.Quaterniond;
import org.joml.primitives.AABBi;
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
    private net.minecraft.server.level.ServerPlayer observer;
    private final boolean resume = Boolean.getBoolean("empirecraft.arcade.resume");
    private final boolean speedRun = Boolean.getBoolean("empirecraft.arcade.speed");
    private final boolean coastRun = Boolean.getBoolean("empirecraft.arcade.coast");
    private double furlSpeed;
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
    private void edgeSailRecount() throws Exception {
        // TEST ONLY: load one remote chunk, leave the outside neighbour absent. Old recount stalls here.
        var chunk=level.getChunk(500,500);
        var type=Class.forName("com.quintonc.vs_sails.ship.ShipBlockScan");
        var ctor=type.getDeclaredConstructor(org.joml.primitives.AABBic.class,Direction.class);ctor.setAccessible(true);
        var step=type.getDeclaredMethod("step",net.minecraft.world.level.Level.class,SailsShipControl.class);step.setAccessible(true);
        for(Direction edge:new Direction[]{Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH}) {
            int cx=500+edge.getStepX(),cz=500+edge.getStepZ();
            check(level.getChunkSource().getChunkNow(cx,cz)==null,"outside neighbour absent at "+edge);
            boolean alongZ=edge.getAxis()==Direction.Axis.X;
            var at=new BlockPos(alongZ?(edge==Direction.WEST?8000:8015):8004,35,alongZ?8004:(edge==Direction.NORTH?8000:8015));
            for(int y=0;y<4;y++) for(int n=0;n<6;n++)
                chunk.setBlockState(at.offset(alongZ?0:n,y,alongZ?n:0),SailsBlocks.SAIL_BLOCK.get().defaultBlockState(),false);
            Object scan=ctor.newInstance(new AABBi(at.getX(),35,at.getZ(),at.getX()+(alongZ?1:6),39,at.getZ()+(alongZ?6:1)),Direction.NORTH);
            var counts=new SailsShipControl();
            check((Boolean)step.invoke(scan,level,counts),"recount completes at "+edge);
            check(counts.numSails==24 && (alongZ?counts.numFnASails:counts.numSquareSails)==24,"all 24 edge sails counted at "+edge);
            check(level.getChunkSource().getChunkNow(cx,cz)==null,"recount never loads outside neighbour at "+edge);
            for(int y=0;y<4;y++) for(int n=0;n<6;n++)
                chunk.setBlockState(at.offset(alongZ?0:n,y,alongZ?n:0),Blocks.AIR.defaultBlockState(),false);
        }
    }
    private double helmTurn() throws Exception {
        var f=SailsShipControl.class.getDeclaredField("helmSample");f.setAccessible(true);
        Object sample=f.get(control());var turn=sample.getClass().getDeclaredMethod("turn");turn.setAccessible(true);
        return (Double)turn.invoke(sample);
    }
    private void speedTick() throws Exception {
        if(ticks==95) {
            check(probe.complete && probe.failure==null && probe.assertions==480,"rotated production physics probe passed");
            ship.removeAttachment(Probe.class);
        }
        if(ticks==100) {
            var position=ship.getTransform().getPositionInWorld();
            double strength=com.quintonc.vs_sails.wind.ServerWindManager.getWindStrength(level,BlockPos.containing(position.x(),position.y(),position.z()));
            double direction=com.quintonc.vs_sails.wind.ServerWindManager.getWindDirection(level,new net.minecraft.world.phys.Vec3(position.x(),position.y(),position.z()));
            check(Math.abs(strength-1)<1e-6 && Math.abs(direction-270)<1e-6,"TEST ONLY fixed tailwind reaches real server wind manager");
        }
        if(ticks>=100 && ticks%20==0) {
            var heading=com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),0,-1);
            double forward=ship.getVelocity().dot(heading);
            System.out.println("ARCADE_SPEED tick="+ticks+" forward="+forward+" velocity="+ship.getVelocity()+" sails="+control().numSails+" mass="+ship.getInertiaData().getMass()+" static="+ship.isStatic()+" position="+ship.getTransform().getPositionInWorld());
        }
        if(ticks==280) {
            var heading=com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),0,-1);
            double forward=ship.getVelocity().dot(heading);
            check(forward>8 && forward<=18.5,"real VS ship reaches useful speed under tailwind: "+forward);
            check(Math.abs(ship.getVelocity().x()*heading.z-ship.getVelocity().z()*heading.x)<.5,"real VS ship retains lateral grip at speed");
            System.out.println("ARCADE_SMOKE_PASS phase=speed assertions="+assertions+" physicsAssertions="+probe.assertions+" physicsCases="+probe.cases+" forward="+forward);
            if (!coastRun) { server.halt(false);server=null; }
            else {
                furlSpeed=forward;
                var b=ship.getShipAABB(); boolean toggled=false;
                for(int x=b.minX();x<b.maxX()&&!toggled;x++)for(int y=b.minY();y<b.maxY()&&!toggled;y++)for(int z=b.minZ();z<b.maxZ();z++) {
                    var at=new BlockPos(x,y,z);var state=level.getBlockState(at);
                    if(state.getBlock() instanceof com.quintonc.vs_sails.blocks.SailBlock sail && state.getValue(com.quintonc.vs_sails.blocks.SailBlock.SET)) {
                        sail.toggleFirstSail(state,level,at);toggled=true;break;
                    }
                }
                check(toggled,"actual connected sail furling requested");
            }
        }
        if(coastRun && ticks==300) {
            check(control().numSails==0 && control().numFurledSails==6,"recount preserves all six furled cloth blocks, including hidden ones");
            double forward=ship.getVelocity().dot(com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),0,-1));
            check(forward>furlSpeed-2.5 && forward>6,"one second after furling still carries momentum: "+forward);
        }
        if(coastRun && ticks==1000) {
            double forward=ship.getVelocity().dot(com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),0,-1));
            check(forward>1.4 && forward<2.4,"actual furled ship settles near two blocks/s: "+forward);
            check(ship.getTransform().getPositionInWorld().y()>10 && ship.getTransform().getPositionInWorld().y()<23,"coasting retains real buoyancy");
            AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
            check(control().anchorDeployed,"anchor still deploys from residual movement");
        }
        if(coastRun && ticks==1100) {
            check(control().anchorLocked && ship.isStatic(),"anchor still fully stops furled ship");
            control().releaseAnchor();
            var pilot=FakePlayerFactory.getMinecraft(level);var wheel=(HelmBlockEntity)level.getBlockEntity(helm);
            check(wheel.startRiding(pilot,true,helm,level.getBlockState(helm),level),"pilot mounts again for actual reverse manoeuvre");
            var input=new org.valkyrienskies.mod.api.SeatedControllingPlayer(Direction.NORTH);input.setForwardImpulse(-1);
            ship.setAttachment(org.valkyrienskies.mod.api.SeatedControllingPlayer.class,input);
            HelmBlockEntity.tick(level,helm,level.getBlockState(helm));
        }
        if(coastRun && ticks==1200) {
            double forward=ship.getVelocity().dot(com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),0,-1));
            check(forward<-1.3 && forward>=-1.6,"back key produces actual reverse capped near 1.5 blocks/s: "+forward);
            ship.getAttachment(org.valkyrienskies.mod.api.SeatedControllingPlayer.class).setForwardImpulse(0);
            HelmBlockEntity.tick(level,helm,level.getBlockState(helm));
        }
        if(coastRun && ticks==1240) {
            double forward=ship.getVelocity().dot(com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),0,-1));
            check(forward>-.8,"releasing back returns to ordinary windage: "+forward);
            FakePlayerFactory.getMinecraft(level).stopRiding();ship.removeAttachment(org.valkyrienskies.mod.api.SeatedControllingPlayer.class);
            System.out.println("ARCADE_SMOKE_PASS phase=coast assertions="+assertions+" physicsAssertions="+probe.assertions+" physicsCases="+probe.cases);
            server.halt(false);server=null;
        }
    }
    private void wheelControls() throws Exception {
        HelmBlockEntity wheel=(HelmBlockEntity)level.getBlockEntity(helm);var state=level.getBlockState(helm);
        wheel.wheelAngle=360;
        var pilot=FakePlayerFactory.getMinecraft(level);
        check(wheel.startRiding(pilot,true,helm,state,level),"test pilot mounts the actual helm seat");
        check(wheel.hasPilot(),"actual helm detects the test pilot");
        var input=new org.valkyrienskies.mod.api.SeatedControllingPlayer(Direction.NORTH);
        ship.setAttachment(org.valkyrienskies.mod.api.SeatedControllingPlayer.class,input);
        input.setLeftImpulse(1);HelmBlockEntity.tick(level,helm,state);
        check(wheel.wheelAngle==366,"left input uses original six degree step");
        input.setLeftImpulse(0);HelmBlockEntity.tick(level,helm,state);
        check(wheel.wheelAngle==366 && Math.abs(helmTurn()-1.0/60)<1e-9,"release preserves wheel and actual rudder command");
        input.setLeftImpulse(-1);HelmBlockEntity.tick(level,helm,state);
        check(wheel.wheelAngle==360 && helmTurn()==0,"opposite input manually centres the helm");
        input.setLeftImpulse(0);
        input.setForwardImpulse(-1);HelmBlockEntity.tick(level,helm,state);
        var sampleField=SailsShipControl.class.getDeclaredField("helmSample");sampleField.setAccessible(true);
        var sample=sampleField.get(control());var back=sample.getClass().getDeclaredMethod("back");back.setAccessible(true);
        check((Boolean)back.invoke(sample),"seated back input reaches the actual controller");
        input.setForwardImpulse(0);HelmBlockEntity.tick(level,helm,state);
        check(!(Boolean)back.invoke(sampleField.get(control())),"back release clears the command");
        input.setLeftImpulse(1);HelmBlockEntity.tick(level,helm,state);
        pilot.stopRiding();ship.removeAttachment(org.valkyrienskies.mod.api.SeatedControllingPlayer.class);
        HelmBlockEntity.tick(level,helm,state);
        check(wheel.wheelAngle==366 && helmTurn()>0,"dismount also preserves set rudder");
        var tag=wheel.saveWithoutMetadata();wheel.wheelAngle=360;wheel.load(tag);HelmBlockEntity.tick(level,helm,state);
        check(wheel.wheelAngle==366 && helmTurn()>0,"saved wheel angle still steers after NBT reload");
        wheel.wheelAngle=360;HelmBlockEntity.tick(level,helm,state);
        check(control().boundx==7 && control().boundz==5,"local hull dimensions are used for turning: "+control().boundx+"x"+control().boundz+" bounds="+ship.getShipAABB());
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(server==null || event.phase!=TickEvent.Phase.END) return;
        try {
            ticks++;
            if(resume) { resumeTick(); return; }
            if(ticks==20) {
                level.setChunkForced(0,0,true); // TEST ONLY: there is no player keeping the ocean ticking.
                if(speedRun) {
                    // VS pauses unattended bodies. Add a server-side test observer for this traversal run.
                    observer=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("0c301236-822e-40b9-8c5a-196766856035"),"SailsSpeedProbe"));
                    observer.setPos(3,24,2);observer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    level.addNewPlayer(observer);
                    // FakePlayer has no login handshake. Register its backing player list only in this test.
                    for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
                        if(field.getType()!=java.util.List.class) continue;
                        field.setAccessible(true);
                        try { ((java.util.List)field.get(server.getPlayerList())).add(observer);break; }
                        catch(UnsupportedOperationException ignored) { }
                    }
                }
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
                edgeSailRecount();wheelControls();
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
            if(speedRun && ship!=null) {
                var position=ship.getTransform().getPositionInWorld();
                if(observer!=null) { observer.setPos(position.x(),position.y()+8,position.z());level.getChunkSource().move(observer); }
                int cx=((int)Math.floor(position.x()))>>4,cz=((int)Math.floor(position.z()))>>4;
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) level.setChunkForced(cx+dx,cz+dz,true);
            }
            if(speedRun && ticks>80) { speedTick();return; }
            if(ticks==95) {
                check(probe!=null && probe.complete,"actual physics probe completed");
                check(probe.failure==null,"physics probe: "+probe.failure);
                check(probe.assertions==480,"expected physics assertions: "+probe.assertions);
                ship.removeAttachment(Probe.class);
                AnchorMechanics.use(level,capstan,FakePlayerFactory.getMinecraft(level));
                check(control().anchorDeployed && !control().anchorRaising,"anchor starts lowering: position="+ship.getTransform().getPositionInWorld()+" static="+ship.isStatic()+" capstan="+capstan+" anchor="+anchor);
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
        @JsonIgnore public int cases;
        private void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
        @SuppressWarnings("unchecked") private ArrayDeque<Vector3dc> queue(PhysShipImpl ship,String name)throws Exception {
            var f=PhysShipImpl.class.getDeclaredField(name);f.setAccessible(true);return(ArrayDeque<Vector3dc>)f.get(ship);
        }
        private void wind(SailsShipControl control,double direction,double strength)throws Exception {
            var f=SailsShipControl.class.getDeclaredField("sampledWind");f.setAccessible(true);
            var ctor=f.getType().getDeclaredConstructor(double.class,double.class);ctor.setAccessible(true);f.set(control,ctor.newInstance(direction,strength));
        }
        private void command(SailsShipControl control,Direction bow,double turn,boolean back)throws Exception {
            var f=SailsShipControl.class.getDeclaredField("helmSample");f.setAccessible(true);Object old=f.get(control);
            var ctor=old.getClass().getDeclaredConstructors()[0];ctor.setAccessible(true);
            f.set(control,ctor.newInstance(0L,bow,turn,back,2,0L));
        }
        private Vector3d[] forces(SailsShipControl control,PhysShipImpl ship,PhysLevel level)throws Exception {
            var forces=queue(ship,"invPosForces");var positions=queue(ship,"invPosPositions");var torques=queue(ship,"invTorques");
            int nf=forces.size(),np=positions.size(),nt=torques.size();
            var predictor=SailsShipControl.class.getDeclaredField("dragCompensator");predictor.setAccessible(true);((com.quintonc.vs_sails.ship.DragCompensator)predictor.get(control)).reset(); // each force probe is an independent pose, not a time step
            control.physTick(ship,level);cases++;Vector3d f=new Vector3d(),t=new Vector3d();
            while(forces.size()>nf)f.add(forces.removeLast());while(positions.size()>np)positions.removeLast();while(torques.size()>nt)t.add(torques.removeLast());
            return new Vector3d[]{f,t};
        }
        @Override public void physTick(PhysShip physShip,PhysLevel level) {
            if(complete || controls==null)return;
            PhysShipImpl ship=(PhysShipImpl)physShip;var saved=ship.getPoseVel();
            try {
                for(var control:controls) for(int yaw:new int[]{0,45,90}) {
                    control.anchorDeployed=false;
                    control.numSails=8;control.numFurledSails=0;
                    var rotation=new Quaterniond(saved.c).rotateY(Math.toRadians(yaw));
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(),new Vector3d()));
                    Direction bow=control.shipDirection.getOpposite();
                    command(control,bow,0,false);
                    Vector3d heading=com.quintonc.vs_sails.ship.ArcadeSailing.heading(ship.getTransform().getShipToWorldRotation(),bow.getStepX(),bow.getStepZ());
                    for(int direction:new int[]{0,90,180,270})for(int sign:new int[]{1,-1}) {
                        ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(),new Vector3d()));
                        wind(control,direction,sign);var result=forces(control,ship,level);
                        check(result[0].dot(heading)>0,"all winds drive toward the helm bow");
                        check(Math.abs(result[0].y)<1e-7,"no vertical sail lift");
                        check(result[0].isFinite() && result[1].isFinite(),"finite production forces");
                    }
                    double along=Math.toDegrees(Math.atan2(heading.z,heading.x));
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,heading.mul(3,new Vector3d()),new Vector3d()));
                    wind(control,along,.5);var tail=forces(control,ship,level);
                    wind(control,along+180,.5);var head=forces(control,ship,level);
                    check(tail[0].dot(heading)>0,"actual rotated hull accelerates at 3 blocks/s with tailwind");
                    check(head[0].dot(heading)<0,"same hull brakes toward the slow headwind target");
                    Vector3d side=new Vector3d(heading.z,0,-heading.x).mul(3);
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,side,new Vector3d()));
                    var drift=forces(control,ship,level);check(drift[0].dot(side)<0,"production force kills sideways drift");
                    for(double turn:new double[]{-1,0,1}) {
                        ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,heading.mul(3,new Vector3d()),new Vector3d(0,turn==0?.2:0,0)));
                        command(control,bow,turn,false);
                        var turning=forces(control,ship,level);
                        check(turn==0?turning[1].y<0:turning[1].y*turn>0,"correct arcade yaw direction");
                    }
                    control.boundx=7;control.boundz=5;
                    var small=forces(control,ship,level);
                    control.boundx=500;control.boundz=150;
                    var large=forces(control,ship,level);
                    check(large[1].y>0 && large[1].y<small[1].y,"production turn torque accounts for hull size with a useful floor");
                    control.numSails=0;control.numFurledSails=8;
                    wind(control,along,1);
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,heading.mul(14,new Vector3d()),new Vector3d()));
                    var coast=forces(control,ship,level);
                    double deceleration=coast[0].dot(heading)/ship.getMass();
                    check(deceleration<0 && deceleration>-1.2,"furled production force decelerates gently");
                    check(ship.getDoFluidDrag(),"furled mode preserves native buoyancy and fluid physics");
                    command(control,bow,0,true);
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,heading.mul(3,new Vector3d()),new Vector3d()));
                    var fastBack=forces(control,ship,level);
                    check(fastBack[0].dot(heading)/ship.getMass()>-1,"back key cannot engage reverse above threshold");
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(),new Vector3d()));
                    var reverse=forces(control,ship,level);
                    check(reverse[0].dot(heading)<0,"reverse starts from rest with actual back command");
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,heading.mul(-1.7,new Vector3d()),new Vector3d()));
                    var limited=forces(control,ship,level);
                    check(limited[0].dot(heading)>0,"reverse speed limit brakes excessive sternward motion");
                    control.anchorDeployed=true;
                    ship.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(5,.2,-2),new Vector3d()));
                    var braking=forces(control,ship,level);
                    check(braking[0].dot(new Vector3d(5,.2,-2))<0,"anchor overrides propulsion with real braking");
                    check(braking[0].isFinite(),"finite anchor brake");
                    check(ship.getDoFluidDrag(),"anchor restores native fluid drag");
                    check(braking[0].length()/ship.getMass()>Math.abs(deceleration),"anchor brakes much harder than furled cloth");
                }
            } catch(Throwable error) { failure=error.toString();error.printStackTrace(); }
            finally { ship.setPoseVel(saved);complete=true; }
        }
    }
}
