package com.empirecraft.sails.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.core.impl.game.ships.PhysShipImpl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashSet;

/** Test-only: probes actual production physTick force queues on a real VS physics body. */
@Mod("empirecraft_wind_smoketest")
public final class WindDirectionSmokeMod {
    private MinecraftServer server;
    private ServerLevel level;
    private BlockPos localBlock;
    private Probe probe;
    private int ticks, assertions;
    private final boolean baseline = Boolean.getBoolean("empirecraft.wind.baseline");

    public WindDirectionSmokeMod() {
        ValkyrienSkies.api().registerAttachment(ValkyrienSkies.api()
                .newAttachmentRegistrationBuilder(Probe.class).useLegacySerializer().build());
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private void started(ServerStartedEvent event) {
        server = event.getServer();
        level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft:world_europe")));
        if (level == null) throw new AssertionError("test world_europe is required");
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END) return;
        try {
            ticks++;
            if (ticks == 20) {
                BlockPos origin = new BlockPos(0, 150, 0);
                HashSet<BlockPos> blocks = new HashSet<>();
                for (int x=0; x<5; x++) for (int z=0; z<3; z++) {
                    BlockPos at = origin.offset(x, 0, z);
                    level.setBlockAndUpdate(at, Blocks.OAK_PLANKS.defaultBlockState());
                    blocks.add(at);
                }
                ServerShip assembled = ShipAssembler.assembleToShip(level, blocks, 1);
                check(assembled != null, "real test ship assembled in world_europe");
                Vector3d local = assembled.getTransform().getWorldToShip().transformPosition(new Vector3d(.5, 150.5, .5));
                localBlock = BlockPos.containing(local.x, local.y, local.z);
            }
            if (ticks == 60) {
                LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, localBlock);
                check(ship != null, "test ship loaded");
                probe = new Probe();
                ship.setAttachment(Probe.class, probe);
            }
            if (probe != null && probe.complete) {
                check(probe.error == null, "physics probe: " + probe.error);
                check(probe.cases == 96, "all axis, sign and sail-type cases exercised: " + probe.cases);
                if (baseline) {
                    check(probe.opposite > 0, "old JAR reproduces force opposite the indicators");
                    check(probe.beamMotors > 0, "old JAR reproduces arbitrary thrust at beam wind");
                } else {
                    check(probe.opposite == 0, "no force opposite wind: " + probe.opposite);
                    check(probe.beamMotors == 0, "beam wind fades to zero: " + probe.beamMotors);
                    check(probe.missingThrust == 0, "nonzero axial wind gives thrust: " + probe.missingThrust);
                    check(probe.vertical == 0, "sail force stays horizontal");
                    check(probe.asymmetries == 0, "opposite wind strengths give opposite forces");
                }
                System.out.println("WIND_SMOKE_PASS baseline=" + baseline + " assertions=" + assertions
                        + " physicsCases=" + probe.cases + " opposite=" + probe.opposite
                        + " beamMotors=" + probe.beamMotors + " missing=" + probe.missingThrust);
                server.halt(false); server = null;
            } else if (ticks > 200) throw new AssertionError("physics probe timed out");
        } catch (Throwable failure) {
            System.out.println("WIND_SMOKE_FAIL " + failure);
            failure.printStackTrace();
            server.halt(false); server = null;
        }
    }

    public static final class Probe implements ShipPhysicsListener {
        @JsonIgnore public volatile boolean complete;
        @JsonIgnore public volatile String error;
        @JsonIgnore public int cases, opposite, beamMotors, missingThrust, vertical, asymmetries;
        @Override public void physTick(PhysShip physShip, PhysLevel physLevel) {
            if (complete) return;
            try {
                PhysShipImpl body = (PhysShipImpl) physShip;
                // VS 2.4.11 converts both legacy force APIs to these world-space queues.
                ArrayDeque<Vector3dc> worldForces = queue(body, "invPosForces");
                ArrayDeque<Vector3dc> positions = queue(body, "invPosPositions");
                for (int[] sails : new int[][]{{4,0}, {0,4}, {2,2}}) {
                    for (Direction axis : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                        for (int wind : new int[]{0,90,180,270}) {
                            Vector3d first = null;
                            for (int sign : new int[]{1,-1}) {
                                SailsShipControl control = new SailsShipControl();
                                control.shipDirection = axis;
                                control.numSails = 4;
                                control.numSquareSails = sails[0]; control.numFnASails = sails[1];
                                setWind(control, wind, sign);
                                int beforeForces = worldForces.size(), beforePositions = positions.size();
                                control.physTick(body, physLevel);
                                Vector3d force = new Vector3d();
                                while (worldForces.size() > beforeForces) force.add(worldForces.removeLast());
                                while (positions.size() > beforePositions) positions.removeLast();
                                Vector3d air = new Vector3d(Math.cos(Math.toRadians(wind)) * sign, 0, Math.sin(Math.toRadians(wind)) * sign);
                                Vector3d heading = body.getTransform().getShipToWorldRotation().transform(new Vector3d(axis.getStepX(), 0, axis.getStepZ()));
                                heading.y = 0; heading.normalize();
                                double component = heading.dot(air);
                                cases++;
                                if (!force.isFinite()) throw new AssertionError("non-finite production force");
                                if (force.dot(air) < -1e-6) opposite++;
                                if (Math.abs(component) < 1e-6 && force.length() > 1e-4) beamMotors++;
                                if (Math.abs(component) > .01 && force.length() < 1e-4) missingThrust++;
                                if (Math.abs(force.y) > 1e-6) vertical++;
                                if (sign == 1) first = force;
                                else if (force.add(first, new Vector3d()).length() > 1e-4) asymmetries++;
                            }
                        }
                    }
                }
                System.out.println("WIND_PHYSICS_PROBE cases=" + cases + " opposite=" + opposite + " beamMotors=" + beamMotors);
            } catch (Throwable failure) { error = failure.toString(); failure.printStackTrace(); }
            finally { complete = true; }
        }
        @SuppressWarnings("unchecked") private static ArrayDeque<Vector3dc> queue(PhysShipImpl body, String name) throws Exception {
            Field field = PhysShipImpl.class.getDeclaredField(name); field.setAccessible(true);
            return (ArrayDeque<Vector3dc>) field.get(body);
        }
        private static void setWind(SailsShipControl control, double direction, double strength) throws Exception {
            try {
                Field sample = SailsShipControl.class.getDeclaredField("sampledWind"); sample.setAccessible(true);
                var constructor = sample.getType().getDeclaredConstructor(double.class, double.class); constructor.setAccessible(true);
                sample.set(control, constructor.newInstance(direction, strength));
            } catch (NoSuchFieldException oldJar) {
                Field d = SailsShipControl.class.getDeclaredField("sampledWindDirection"); d.setAccessible(true); d.setDouble(control, direction);
                Field s = SailsShipControl.class.getDeclaredField("sampledWindStrength"); s.setAccessible(true); s.setDouble(control, strength);
            }
        }
    }
}
