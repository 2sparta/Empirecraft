package ua.empirecraft.shipphysics;

import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.core.api.physics.ContactPoint;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.properties.ShipInertiaData;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import ua.empirecraft.shipphysics.BlockProperties;
import ua.empirecraft.shipphysics.BlockVersions;
import ua.empirecraft.shipphysics.ContactArea;
import ua.empirecraft.shipphysics.ContactMath;
import ua.empirecraft.shipphysics.DamageData;
import ua.empirecraft.shipphysics.EpisodeGate;
import ua.empirecraft.shipphysics.HullCollision;
import ua.empirecraft.shipphysics.ImpactMath;
import ua.empirecraft.shipphysics.MassSync;
import ua.empirecraft.shipphysics.NativeMaterials;
import ua.empirecraft.shipphysics.PenetrationPath;
import ua.empirecraft.shipphysics.PhysicsConfig;
import ua.empirecraft.shipphysics.ShipDynamics;
import ua.empirecraft.shipphysics.ShipPhysics;
import ua.empirecraft.shipphysics.SweepMotion;

public final class CollisionDamage {
    private static final ArrayBlockingQueue<Frame> QUEUE = new ArrayBlockingQueue(1024);
    private static final ArrayDeque<Work> PENDING = new ArrayDeque();
    private static final EpisodeGate GATE = new EpisodeGate();
    private static final GameProfile ACTOR = new GameProfile(UUID.fromString("0a4ff351-223c-4cfb-91aa-0f680943dd79"), "[ShipImpact]");
    private static volatile boolean running;
    private static long ticks;
    private static int destroyed;
    private static final AtomicLong captured;
    private static final AtomicLong recovered;
    private static final AtomicLong dropped;
    private static final AtomicLong nativeEvents;
    private static final AtomicLong probeFrames;
    private static final AtomicLong invalidContacts;
    private static long impacts;
    private static long broken;
    private static long protectedBlocks;
    private static long partial;
    private static long continued;
    private static long expired;
    private static long deferred;
    private static long merged;
    private static long groundBroken;
    private static long shipBroken;
    private static long groundPartial;
    private static long shipPartial;
    private static String last;

    private CollisionDamage() {
    }

    public static void start() {
        QUEUE.clear();
        PENDING.clear();
        GATE.clear();
        BlockVersions.clear();
        ticks = 0L;
        destroyed = 0;
        for (AtomicLong atomicLong : List.of(captured, recovered, dropped, nativeEvents, probeFrames, invalidContacts)) {
            atomicLong.set(0L);
        }
        shipPartial = 0L;
        groundPartial = 0L;
        shipBroken = 0L;
        groundBroken = 0L;
        merged = 0L;
        deferred = 0L;
        expired = 0L;
        continued = 0L;
        partial = 0L;
        protectedBlocks = 0L;
        broken = 0L;
        impacts = 0L;
        last = "no contacts";
        HullCollision.clear();
        running = true;
    }

    public static void stop() {
        running = false;
        QUEUE.clear();
        PENDING.clear();
        GATE.clear();
        BlockVersions.clear();
        HullCollision.clear();
    }

    public static void beginServerTick() {
        if (!running) {
            CollisionDamage.start();
        }
    }

    public static void capture(CollisionEvent collisionEvent, boolean bl) {
        ShipDynamics.Motion motion;
        nativeEvents.incrementAndGet();
        if (!running) {
            return;
        }
        EpisodeGate.Pair pair = EpisodeGate.Pair.of(collisionEvent.getDimensionId(), collisionEvent.getShipIdA(), collisionEvent.getShipIdB());
        if (bl) {
            CollisionDamage.offer(new Frame(pair, List.of(), true));
            return;
        }
        PhysLevel physLevel = collisionEvent.getPhysLevel();
        PhysShip physShip = physLevel == null ? null : physLevel.getShipById(collisionEvent.getShipIdA());
        PhysShip physShip2 = physLevel == null ? null : physLevel.getShipById(collisionEvent.getShipIdB());
        ShipDynamics.Motion motion2 = ShipDynamics.recent(pair.dimension(), collisionEvent.getShipIdA());
        long l = Math.max(motion2 == null ? Long.MIN_VALUE : motion2.time(), (motion = ShipDynamics.recent(pair.dimension(), collisionEvent.getShipIdB())) == null ? Long.MIN_VALUE : motion.time());
        if (l == Long.MIN_VALUE) {
            l = System.nanoTime();
        }
        boolean bl2 = collisionEvent.getShipIdA() != pair.a();
        ArrayList<Contact> arrayList = new ArrayList<Contact>();
        double d = 0.0;
        double d2 = 0.0;
        for (ContactPoint contactPoint : collisionEvent.getContactPoints()) {
            Vector3d vector3d;
            Vector3dc vector3dc = contactPoint.getPosition();
            Vector3dc vector3dc2 = contactPoint.getNormal();
            Vector3dc vector3dc3 = contactPoint.getVelocity();
            if (!vector3dc.isFinite() || !vector3dc2.isFinite() || !vector3dc3.isFinite() || vector3dc2.lengthSquared() < 1.0E-10) {
                invalidContacts.incrementAndGet();
                continue;
            }
            Vector3d vector3d2 = CollisionDamage.orientedNormal(physShip, physShip2, vector3dc, vector3dc2);
            Vector3d vector3d3 = CollisionDamage.currentVelocity(physShip, vector3dc).sub((Vector3dc)CollisionDamage.currentVelocity(physShip2, vector3dc));
            Vector3d vector3d4 = (motion2 == null ? CollisionDamage.currentVelocity(physShip, vector3dc) : motion2.velocityAt(vector3dc)).sub((Vector3dc)(motion == null ? CollisionDamage.currentVelocity(physShip2, vector3dc) : motion.velocityAt(vector3dc)));
            if (physShip == null && physShip2 == null) {
                vector3d4.set(vector3dc3);
            }
            double d3 = ImpactMath.normalSpeed(vector3d4.x, vector3d4.y, vector3d4.z, vector3d2.x, vector3d2.y, vector3d2.z);
            double d4 = ImpactMath.normalSpeed(vector3d3.x, vector3d3.y, vector3d3.z, vector3d2.x, vector3d2.y, vector3d2.z);
            double d5 = Math.max(d3, d4);
            if (d3 > d4 + 1.0E-6) {
                recovered.incrementAndGet();
            }
            Vector3d vector3d5 = vector3d = d3 >= d4 ? vector3d4 : vector3d3;
            if (physShip == null && physShip2 == null) {
                vector3d = new Vector3d(vector3dc3);
            }
            Vector3d vector3d6 = new Vector3d();
            if (motion2 != null && physShip != null) {
                vector3d6.add((Vector3dc)new Vector3d(physShip.getTransform().getPositionInWorld()).sub(motion2.x(), motion2.y(), motion2.z()));
            }
            if (motion != null && physShip2 != null) {
                vector3d6.sub((Vector3dc)new Vector3d(physShip2.getTransform().getPositionInWorld()).sub(motion.x(), motion.y(), motion.z()));
            }
            d2 = Math.max(d2, Math.max(0.0, vector3d6.dot((Vector3dc)vector3d2)));
            d = Math.max(d, d4);
            if (bl2) {
                vector3d2.negate();
                vector3d = new Vector3d((Vector3dc)vector3d).negate();
            }
            LocalContact localContact = CollisionDamage.local(physShip, vector3dc, (Vector3dc)vector3d2, (Vector3dc)vector3d);
            LocalContact localContact2 = CollisionDamage.local(physShip2, vector3dc, (Vector3dc)vector3d2, (Vector3dc)vector3d);
            arrayList.add(new Contact(vector3dc.x(), vector3dc.y(), vector3dc.z(), vector3d2.x, vector3d2.y, vector3d2.z, d5, vector3d.x, vector3d.y, vector3d.z, bl2 ? localContact2 : localContact, bl2 ? localContact : localContact2));
            if (arrayList.size() < 128) continue;
            break;
        }
        if (!arrayList.isEmpty()) {
            captured.incrementAndGet();
            CollisionDamage.offer(new Frame(pair, List.copyOf(arrayList), false, l, d, d2));
        }
    }

    private static Vector3d orientedNormal(PhysShip physShip, PhysShip physShip2, Vector3dc vector3dc, Vector3dc vector3dc2) {
        boolean bl;
        Vector3d vector3d = new Vector3d(vector3dc2).normalize();
        Vector3d vector3d2 = null;
        // PhysShipImpl's AABB getters throw NotImplementedError in pinned VS 2.4.11.
        // The ground body has no PhysShip in this lookup; body presence is sufficient.
        boolean bl2 = physShip != null;
        bl = physShip2 != null;
        if (bl2 && bl) {
            vector3d2 = new Vector3d(physShip2.getTransform().getPositionInWorld()).sub(physShip.getTransform().getPositionInWorld());
        } else if (bl2) {
            vector3d2 = new Vector3d(vector3dc).sub(physShip.getTransform().getPositionInWorld());
        } else if (bl) {
            vector3d2 = new Vector3d(physShip2.getTransform().getPositionInWorld()).sub(vector3dc);
        }
        if (vector3d2 != null && vector3d.dot(vector3d2) < -1.0E-8) {
            vector3d.negate();
        }
        return vector3d;
    }

    private static Vector3d currentVelocity(PhysShip physShip, Vector3dc vector3dc) {
        return physShip == null || physShip.isStatic() ? new Vector3d() : SweepMotion.velocityAt(physShip.getVelocity(), physShip.getAngularVelocity(), physShip.getTransform().getPositionInWorld(), vector3dc);
    }

    public static void captureProbe(EpisodeGate.Pair pair, List<Contact> list) {
        CollisionDamage.captureProbe(pair, list, System.nanoTime(), 0.0, 0.0);
    }

    public static void captureProbe(EpisodeGate.Pair pair, List<Contact> list, long l, double d, double d2) {
        if (!running || list.isEmpty()) {
            return;
        }
        captured.incrementAndGet();
        probeFrames.incrementAndGet();
        CollisionDamage.offer(new Frame(pair, List.copyOf(list.subList(0, Math.min(128, list.size()))), false, l, d, d2));
    }

    private static void offer(Frame frame) {
        if (!QUEUE.offer(frame)) {
            dropped.incrementAndGet();
        }
    }

    private static LocalContact local(PhysShip physShip, Vector3dc vector3dc, Vector3dc vector3dc2, Vector3dc vector3dc3) {
        if (physShip == null) {
            return null;
        }
        Vector3d vector3d = physShip.getWorldToShip().transformPosition(new Vector3d(vector3dc));
        Vector3d vector3d2 = physShip.getWorldToShip().transformDirection(new Vector3d(vector3dc2)).normalize();
        Vector3d vector3d3 = physShip.getWorldToShip().transformDirection(new Vector3d(vector3dc3));
        return vector3d.isFinite() && vector3d2.isFinite() && vector3d3.isFinite() ? new LocalContact(vector3d.x, vector3d.y, vector3d.z, vector3d2.x, vector3d2.y, vector3d2.z, vector3d3.x, vector3d3.y, vector3d3.z) : null;
    }

    public static String status() {
        return "version=1.2.2 contacts=" + captured.get() + " recovered=" + recovered.get() + " dropped=" + dropped.get() + " impacts=" + impacts + " continued=" + continued + " pendingWork=" + PENDING.size() + " expiredWork=" + expired + " deferredFrames=" + deferred + " mergedContacts=" + merged + " broken=" + broken + " partial=" + partial + " groundBroken=" + groundBroken + " shipBroken=" + shipBroken + " groundPartial=" + groundPartial + " shipPartial=" + shipPartial + " denied=" + protectedBlocks + " nativeEvents=" + nativeEvents.get() + " invalid=" + invalidContacts.get() + " probeFrames=" + probeFrames.get() + " running=" + running + " ticks=" + ticks + HullCollision.status() + NativeMaterials.status() + " last=" + last;
    }

    public static void tick(MinecraftServer minecraftServer) {
        Frame frame3;
        ++ticks;
        destroyed = 0;
        if (!PhysicsConfig.get().collisionDamage) {
            QUEUE.clear();
            PENDING.clear();
            GATE.clear();
            BlockVersions.clear();
            return;
        }
        CollisionDamage.drain(minecraftServer);
        LinkedHashMap<EpisodeGate.Pair, Frame> linkedHashMap = new LinkedHashMap<EpisodeGate.Pair, Frame>();
        for (int i = 0; i < 1024 && (frame3 = QUEUE.poll()) != null; ++i) {
            Frame frame2;
            if (frame3.ended) {
                frame2 = (Frame)linkedHashMap.remove(frame3.pair);
                if (frame2 != null) {
                    CollisionDamage.impact(minecraftServer, frame2, CollisionDamage.speed(frame2));
                }
                GATE.end(frame3.pair, ticks);
                continue;
            }
            frame2 = (Frame)linkedHashMap.get(frame3.pair);
            if (frame2 != null && !(CollisionDamage.speed(frame3) > CollisionDamage.speed(frame2)) && (CollisionDamage.speed(frame3) != CollisionDamage.speed(frame2) || frame3.stamp <= frame2.stamp)) continue;
            linkedHashMap.put(frame3.pair, frame3);
        }
        for (Frame selectedFrame : linkedHashMap.values()) {
            CollisionDamage.impact(minecraftServer, selectedFrame, CollisionDamage.speed(selectedFrame));
        }
        if (PENDING.isEmpty()) {
            BlockVersions.clear();
        }
        if (ticks % 100L == 0L) {
            GATE.prune(ticks);
        }
    }

    private static double speed(Frame frame) {
        double d = 0.0;
        for (Contact contact : frame.points) {
            if (!Double.isFinite(contact.speed)) continue;
            d = Math.max(d, contact.speed);
        }
        return d;
    }

    private static void impact(MinecraftServer minecraftServer, Frame frame, double d) {
        double d2;
        double d3;
        PhysicsConfig.Settings settings = PhysicsConfig.get();
        if (d < settings.minimumCrushSpeed) {
            GATE.relax(frame.pair, ticks, frame.stamp);
            return;
        }
        ServerLevel serverLevel = VSGameUtilsKt.getLevelFromDimensionId((MinecraftServer)minecraftServer, (String)frame.pair.dimension());
        if (serverLevel == null) {
            last = "unknown dimension";
            return;
        }
        VsiServerShipWorld vsiServerShipWorld = VSGameUtilsKt.getShipObjectWorld((ServerLevel)serverLevel);
        LoadedServerShip loadedServerShip = (LoadedServerShip)vsiServerShipWorld.getLoadedShips().getById(frame.pair.a());
        LoadedServerShip loadedServerShip2 = (LoadedServerShip)vsiServerShipWorld.getLoadedShips().getById(frame.pair.b());
        Long l = (Long)vsiServerShipWorld.getDimensionToGroundBodyIdImmutable().get(frame.pair.dimension());
        if (loadedServerShip == null && (l == null || l.longValue() != frame.pair.a()) || loadedServerShip2 == null && (l == null || l.longValue() != frame.pair.b()) || loadedServerShip == null && loadedServerShip2 == null) {
            last = "unloaded or unknown body";
            return;
        }
        if (loadedServerShip != null && MassSync.busy(loadedServerShip.getId()) || loadedServerShip2 != null && MassSync.busy(loadedServerShip2.getId())) {
            last = "mass rebuild";
            return;
        }
        LinkedHashMap<Object, Patch> linkedHashMap = new LinkedHashMap<Object, Patch>();
        for (Contact contact : frame.points) {
            if (!CollisionDamage.finite(contact.x, contact.y, contact.z, contact.nx, contact.ny, contact.nz, contact.dx, contact.dy, contact.dz, contact.speed) || contact.speed < settings.minimumCrushSpeed) continue;
            Vector3d vector3d = new Vector3d(contact.x, contact.y, contact.z);
            Vector3d vector3d2 = new Vector3d(contact.nx, contact.ny, contact.nz).normalize();
            if (!vector3d2.isFinite()) continue;
            Vector3d vector3d3 = new Vector3d(contact.dx, contact.dy, contact.dz);
            Target target = CollisionDamage.find(serverLevel, loadedServerShip, vector3d, vector3d2, vector3d3, contact.a);
            Target target2 = CollisionDamage.find(serverLevel, loadedServerShip2, vector3d, vector3d2, vector3d3, contact.b);
            if (target == null && target2 == null) continue;
            PatchKey patchKey = new PatchKey(target == null ? null : target.pos, target2 == null ? null : target2.pos, (int)Math.round(vector3d2.x * 8.0), (int)Math.round(vector3d2.y * 8.0), (int)Math.round(vector3d2.z * 8.0));
            double d4 = Math.max(1.0E-4, target == null ? target2.area : (target2 == null ? target.area : Math.min(target.area, target2.area)));
            Patch patch = new Patch(contact, target, target2, d4);
            Patch patch2 = (Patch)linkedHashMap.get(patchKey);
            if (patch2 == null || contact.speed > patch2.contact.speed) {
                linkedHashMap.put(patchKey, patch);
                continue;
            }
            ++merged;
        }
        ArrayList<Patch> arrayList = new ArrayList<>(linkedHashMap.values());
        if (arrayList.isEmpty()) {
            last = "no solid block at contact";
            return;
        }
        if (PENDING.size() + arrayList.size() * 2 > settings.maximumPendingWork) {
            ++deferred;
            CollisionDamage.offer(frame);
            return;
        }
        double d5 = 0.0;
        double d6 = 0.0;
        double d7 = 0.0;
        for (Patch patch : arrayList) {
            d5 += patch.area;
            d6 += patch.area * patch.contact.speed;
            d3 = patch.a == null ? 0.0 : patch.a.surface.restitution();
            double d8 = patch.b == null ? 0.0 : patch.b.surface.restitution();
            d7 += patch.area * Math.max(d3, d8);
        }
        d6 /= d5;
        d7 /= d5;
        double d9 = ContactMath.effectiveMass(CollisionDamage.inverse(loadedServerShip, arrayList, true, d5), CollisionDamage.inverse(loadedServerShip2, arrayList, false, d5));
        d3 = ContactMath.dissipated(d9, d6, d7, settings.damageMultiplier, settings.maximumImpactEnergy);
        double d10 = GATE.grant(frame.pair, ticks, frame.stamp, d6, settings.minimumImpactSpeed, d3, d2 = ContactMath.dissipated(d9, frame.postSpeed, d7, settings.damageMultiplier, settings.maximumImpactEnergy), frame.travel, settings.compressionDistance);
        if (d10 <= 0.0) {
            last = "no fresh impact work";
            return;
        }
        if (d10 + 1.0E-8 < d3) {
            ++continued;
        }
        ++impacts;
        last = "speed=" + ShipPhysics.format(d6) + " effectiveMass=" + ShipPhysics.format(d9) + " area=" + ShipPhysics.format(d5) + " energy=" + ShipPhysics.format(d10);
        double d11 = 0.0;
        for (Object object : arrayList) {
            d11 += ((Patch)object).area * ((Patch)object).contact.speed * ((Patch)object).contact.speed;
        }
        ArrayList<Work> arrayList2 = new ArrayList<>();
        for (Patch patch : arrayList) {
            boolean bl;
            Target target = patch.a;
            Target target3 = patch.b;
            boolean bl2 = target == null || target.properties.unbreakable() || loadedServerShip == null && !settings.terrainDamage;
            boolean bl3 = bl = target3 == null || target3.properties.unbreakable() || loadedServerShip2 == null && !settings.terrainDamage;
            if (bl2 && bl) continue;
            double d12 = d10 * patch.area * patch.contact.speed * patch.contact.speed / d11;
            double d13 = ImpactMath.shareForA(target == null ? 1.0 : target.properties.toughness(), target3 == null ? 1.0 : target3.properties.toughness(), bl2, bl);
            if (!bl2) {
                arrayList2.add(CollisionDamage.plan(serverLevel, loadedServerShip, target, d12 * d13));
            }
            if (bl) continue;
            arrayList2.add(CollisionDamage.plan(serverLevel, loadedServerShip2, target3, d12 * (1.0 - d13)));
        }
        arrayList2.sort(Comparator.comparingInt(work -> work.ship == null ? 0 : 1));
        PENDING.addAll(arrayList2);
        CollisionDamage.drain(minecraftServer);
    }

    private static double inverse(LoadedServerShip loadedServerShip, List<Patch> list, boolean bl, double d) {
        if (loadedServerShip == null || loadedServerShip.isStatic()) {
            return 0.0;
        }
        ShipInertiaData shipInertiaData = loadedServerShip.getInertiaData();
        Vector3dc vector3dc = shipInertiaData.getCenterOfMass();
        Vector3d vector3d = new Vector3d();
        Vector3d vector3d2 = new Vector3d();
        for (Patch patch : list) {
            Contact contact = patch.contact;
            LocalContact localContact = bl ? contact.a : contact.b;
            Vector3d vector3d3 = localContact == null ? loadedServerShip.getWorldToShip().transformPosition(new Vector3d(contact.x, contact.y, contact.z)) : new Vector3d(localContact.x, localContact.y, localContact.z);
            Vector3d vector3d4 = localContact == null ? loadedServerShip.getWorldToShip().transformDirection(new Vector3d(contact.nx, contact.ny, contact.nz)).normalize() : new Vector3d(localContact.nx, localContact.ny, localContact.nz).normalize();
            double d2 = patch.area / d;
            vector3d.fma(d2, (Vector3dc)vector3d4);
            if (vector3dc == null) continue;
            vector3d2.fma(d2, (Vector3dc)vector3d3.sub(vector3dc).cross((Vector3dc)vector3d4));
        }
        return ContactMath.inverseDistributed(shipInertiaData.getMass(), shipInertiaData.getInertiaTensor(), (Vector3dc)vector3d, (Vector3dc)vector3d2, false);
    }

    private static Target find(ServerLevel serverLevel, LoadedServerShip loadedServerShip, Vector3d vector3d, Vector3d vector3d2, Vector3d vector3d3, LocalContact localContact) {
        BlockPos blockPos;
        Vector3d vector3d4 = new Vector3d((Vector3dc)vector3d);
        Vector3d vector3d5 = new Vector3d((Vector3dc)vector3d2);
        Vector3d vector3d6 = new Vector3d((Vector3dc)vector3d3);
        if (loadedServerShip != null) {
            if (localContact != null) {
                vector3d4.set(localContact.x, localContact.y, localContact.z);
                vector3d5.set(localContact.nx, localContact.ny, localContact.nz);
                vector3d6.set(localContact.dx, localContact.dy, localContact.dz);
            } else {
                loadedServerShip.getWorldToShip().transformPosition(vector3d4);
                loadedServerShip.getWorldToShip().transformDirection(vector3d5).normalize();
                loadedServerShip.getWorldToShip().transformDirection(vector3d6);
            }
        }
        BlockPos blockPos2 = BlockPos.containing((double)vector3d4.x, (double)vector3d4.y, (double)vector3d4.z);
        BlockPos blockPos3 = null;
        double d = 0.6400000000000001;
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                for (int k = -1; k <= 1; ++k) {
                    BlockState blockState;
                    blockPos = blockPos2.offset(i, j, k);
                    if (!CollisionDamage.belongs(serverLevel, loadedServerShip, blockPos) || (blockState = serverLevel.getBlockState(blockPos)).isAir() || blockState.getBlock() instanceof LiquidBlock) continue;
                    for (AABB aABB : blockState.getCollisionShape((BlockGetter)serverLevel, blockPos).toAabbs()) {
                        double d2;
                        double d3;
                        double d4 = Math.max(aABB.minX + (double)blockPos.getX(), Math.min(vector3d4.x, aABB.maxX + (double)blockPos.getX()));
                        double d5 = vector3d4.distanceSquared(d4, d3 = Math.max(aABB.minY + (double)blockPos.getY(), Math.min(vector3d4.y, aABB.maxY + (double)blockPos.getY())), d2 = Math.max(aABB.minZ + (double)blockPos.getZ(), Math.min(vector3d4.z, aABB.maxZ + (double)blockPos.getZ())));
                        if (!(d5 < d - 1.0E-12) && (!(Math.abs(d5 - d) <= 1.0E-12) || blockPos3 != null && blockPos.asLong() >= blockPos3.asLong())) continue;
                        d = d5;
                        blockPos3 = blockPos;
                    }
                }
            }
        }
        if (blockPos3 == null) {
            return null;
        }
        BlockState blockState = serverLevel.getBlockState(blockPos3);
        double d6 = ContactArea.projected(blockState.getCollisionShape((BlockGetter)serverLevel, blockPos3), (Vector3dc)vector3d5);
        Vector3d towardBlock = new Vector3d((double)blockPos3.getX() + 0.5, (double)blockPos3.getY() + 0.5, (double)blockPos3.getZ() + 0.5).sub((Vector3dc)vector3d4);
        if (towardBlock.dot((Vector3dc)vector3d5) < 0.0) {
            vector3d5.negate();
        }
        return new Target(blockPos3, BlockProperties.at((BlockGetter)serverLevel, blockPos3, blockState), vector3d4, PenetrationPath.direction((Vector3dc)vector3d5, (Vector3dc)vector3d6), d6, BlockProperties.responseAt((BlockGetter)serverLevel, blockPos3, blockState));
    }

    private static boolean belongs(ServerLevel serverLevel, LoadedServerShip loadedServerShip, BlockPos blockPos) {
        if (blockPos.getY() < serverLevel.getMinBuildHeight() || blockPos.getY() >= serverLevel.getMaxBuildHeight() || serverLevel.getChunkSource().getChunkNow(blockPos.getX() >> 4, blockPos.getZ() >> 4) == null) {
            return false;
        }
        LoadedServerShip loadedServerShip2 = VSGameUtilsKt.getLoadedShipManagingPos((ServerLevel)serverLevel, (Vec3i)blockPos);
        return loadedServerShip == null ? loadedServerShip2 == null && !VSGameUtilsKt.isBlockInShipyard((Level)serverLevel, (BlockPos)blockPos) : loadedServerShip2 != null && loadedServerShip2.getId() == loadedServerShip.getId();
    }

    private static Work plan(ServerLevel serverLevel, LoadedServerShip loadedServerShip, Target target, double d) {
        int radius = ImpactSpread.radius(d, target.properties.toughness(), PhysicsConfig.get().maximumPenetrationBlocks);
        var origin = new ImpactSpread.Point(target.pos.getX(), target.pos.getY(), target.pos.getZ());
        var shares = ImpactSpread.cells(origin, radius, point -> {
            BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
            if (!belongs(serverLevel, loadedServerShip, pos)) return false;
            BlockState state = serverLevel.getBlockState(pos);
            return !state.isAir() && !(state.getBlock() instanceof LiquidBlock)
                    && !state.getCollisionShape(serverLevel, pos).isEmpty()
                    && !BlockProperties.at(serverLevel, pos, state).unbreakable();
        });
        ArrayList<Cell> cells = new ArrayList<>();
        for (var share : shares) {
            var point = share.point();
            BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
            BlockState state = serverLevel.getBlockState(pos);
            cells.add(new Cell(pos, BlockProperties.at(serverLevel, pos, state).fingerprint(),
                    BlockVersions.watch(serverLevel, pos), share.fraction()));
        }
        return new Work(VSGameUtilsKt.getDimensionId(serverLevel), serverLevel,
                loadedServerShip == null ? null : loadedServerShip.getId(), List.copyOf(cells),
                new Vector3d(target.localContact), new Vector3d(target.inward), d,
                ticks + PhysicsConfig.get().pendingWorkLifetime);
    }

    private static void finish(Work work) {
        for (Cell cell : work.cells) {
            BlockVersions.release(work.leaseLevel, cell.pos);
        }
    }

    private static void drain(MinecraftServer minecraftServer) {
        int n = Math.min(16384, PENDING.stream().mapToInt(w -> w.cells.size() - w.index).sum());
        while (!PENDING.isEmpty() && n-- > 0 && destroyed < PhysicsConfig.get().maximumDestroyedPerTick) {
            LoadedServerShip loadedServerShip;
            Work work = PENDING.removeFirst();
            if (work.expires < ticks) {
                ++expired;
                CollisionDamage.finish(work);
                continue;
            }
            ServerLevel serverLevel = VSGameUtilsKt.getLevelFromDimensionId((MinecraftServer)minecraftServer, (String)work.dimension);
            if (serverLevel == null) {
                ++expired;
                CollisionDamage.finish(work);
                continue;
            }
            LoadedServerShip loadedServerShip2 = loadedServerShip = work.ship == null ? null : (LoadedServerShip)VSGameUtilsKt.getShipObjectWorld((ServerLevel)serverLevel).getLoadedShips().getById(work.ship.longValue());
            if (work.ship != null && loadedServerShip == null || loadedServerShip != null && MassSync.busy(loadedServerShip.getId())) {
                PENDING.addLast(work);
                continue;
            }
            if (loadedServerShip == null && !PhysicsConfig.get().terrainDamage) {
                CollisionDamage.finish(work);
                continue;
            }
            if (work.index >= work.cells.size() || work.energy <= 0.0) {
                CollisionDamage.finish(work);
                continue;
            }
            Cell cell = work.cells.get(work.index);
            BlockPos blockPos = cell.pos;
            if (!CollisionDamage.belongs(serverLevel, loadedServerShip, blockPos)) {
                ++expired;
                CollisionDamage.finish(work);
                continue;
            }
            BlockState blockState = serverLevel.getBlockState(blockPos);
            if (blockState.isAir()) {
                ++work.index;
                PENDING.addLast(work);
                continue;
            }
            BlockProperties.Properties properties = BlockProperties.at((BlockGetter)serverLevel, blockPos, blockState);
            if (!properties.fingerprint().equals(cell.fingerprint) || BlockVersions.get(serverLevel, blockPos) != cell.version) {
                ++expired;
                CollisionDamage.finish(work);
                continue;
            }
            if (properties.unbreakable()) {
                CollisionDamage.finish(work);
                continue;
            }
            if (!CollisionDamage.canDamage(serverLevel, loadedServerShip, blockPos)) {
                ++protectedBlocks;
                last = "protection denied";
                CollisionDamage.finish(work);
                continue;
            }
            DamageData damageData = DamageData.get(serverLevel);
            double d = damageData.remaining(blockPos, properties);
            double cellEnergy = work.energy * cell.share;
            if (cellEnergy + 1.0E-8 < d) {
                damageData.add(blockPos, properties, cellEnergy);
                CollisionSounds.play(serverLevel, loadedServerShip, blockPos, blockState, false);
                ++partial;
                if (loadedServerShip == null) {
                    ++groundPartial;
                } else {
                    ++shipPartial;
                }
                ++work.index;
                if (work.index < work.cells.size()) PENDING.addLast(work);
                else CollisionDamage.finish(work);
                continue;
            }
            if (!serverLevel.destroyBlock(blockPos, PhysicsConfig.get().drops)) {
                CollisionDamage.finish(work);
                continue;
            }
            damageData.forget(blockPos);
            CollisionSounds.play(serverLevel, loadedServerShip, blockPos, blockState, true);
            // Each cell owns a fixed share; unused energy never becomes a drilling ray.
            ++work.index;
            ++destroyed;
            ++broken;
            if (loadedServerShip == null) {
                ++groundBroken;
            } else {
                ++shipBroken;
            }
            if (work.energy > 0.0 && work.index < work.cells.size()) {
                PENDING.addLast(work);
                continue;
            }
            CollisionDamage.finish(work);
        }
    }

    private static boolean canDamage(ServerLevel serverLevel, LoadedServerShip loadedServerShip, BlockPos blockPos) {
        Vector3d vector3d = new Vector3d((double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5);
        if (loadedServerShip != null) {
            loadedServerShip.getShipToWorld().transformPosition(vector3d);
        }
        BlockPos blockPos2 = BlockPos.containing((double)vector3d.x, (double)vector3d.y, (double)vector3d.z);
        if (!serverLevel.getWorldBorder().isWithinBounds(blockPos2)) {
            return false;
        }
        FakePlayer fakePlayer = FakePlayerFactory.get((ServerLevel)serverLevel, (GameProfile)ACTOR);
        fakePlayer.setPos(vector3d.x, vector3d.y, vector3d.z);
        if (serverLevel.getServer().isUnderSpawnProtection(serverLevel, blockPos2, (Player)fakePlayer)) {
            return false;
        }
        return ForgeHooks.onBlockBreakEvent((Level)serverLevel, (GameType)GameType.SURVIVAL, (ServerPlayer)fakePlayer, (BlockPos)blockPos) >= 0;
    }

    private static boolean finite(double ... dArray) {
        for (double d : dArray) {
            if (Double.isFinite(d)) continue;
            return false;
        }
        return true;
    }

    private static double clamp(double d, double d2, double d3) {
        return Math.max(d2, Math.min(d3, d));
    }

    static {
        captured = new AtomicLong();
        recovered = new AtomicLong();
        dropped = new AtomicLong();
        nativeEvents = new AtomicLong();
        probeFrames = new AtomicLong();
        invalidContacts = new AtomicLong();
        last = "no contacts";
    }

    public record Frame(EpisodeGate.Pair pair, List<Contact> points, boolean ended, long stamp, double postSpeed, double travel) {
        public Frame(EpisodeGate.Pair pair, List<Contact> list, boolean bl) {
            this(pair, list, bl, System.nanoTime(), 0.0, 0.0);
        }
    }

    public record LocalContact(double x, double y, double z, double nx, double ny, double nz, double dx, double dy, double dz) {
    }

    public record Contact(double x, double y, double z, double nx, double ny, double nz, double speed, double dx, double dy, double dz, LocalContact a, LocalContact b) {
    }

    private record Target(BlockPos pos, BlockProperties.Properties properties, Vector3d localContact, Vector3d inward, double area, PhysicsConfig.Surface surface) {
    }

    private record PatchKey(BlockPos a, BlockPos b, int nx, int ny, int nz) {
    }

    private record Patch(Contact contact, Target a, Target b, double area) {
    }

    private static final class Work {
        final String dimension;
        final ServerLevel leaseLevel;
        final Long ship;
        final List<Cell> cells;
        final Vector3d start;
        final Vector3d direction;
        final long expires;
        double energy;
        int index;

        Work(String string, ServerLevel serverLevel, Long l, List<Cell> list, Vector3d vector3d, Vector3d vector3d2, double d, long l2) {
            this.dimension = string;
            this.leaseLevel = serverLevel;
            this.ship = l;
            this.cells = list;
            this.start = vector3d;
            this.direction = vector3d2;
            this.energy = d;
            this.expires = l2;
        }
    }

    private record Cell(BlockPos pos, String fingerprint, long version, double share) {
    }
}
