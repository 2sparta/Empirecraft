package ua.townyrecruits.tactics;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import com.talhanation.recruits.CommandEvents;
import ua.townyrecruits.Bridge;
import ua.townyrecruits.tactics.TacticalOrder.*;
import ua.townyrecruits.tactics.TacticalPlanner.*;

public final class TacticalNetwork {
    private static final String VERSION = "1.4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation("townyrecruits", "tactics")).networkProtocolVersion(() -> VERSION)
        .clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private static final Map<UUID, Long> PREVIEWS = new HashMap<>(), COMMITS = new HashMap<>();
    private TacticalNetwork() {}
    /** action: 0 preview, 1 commit, 2 inventory capability query. */
    public record Request(int sequence, int action, TacticalOrder order) {}
    public static void init() {
        CHANNEL.registerMessage(0, Request.class, Codec::writeRequest, Codec::readRequest,
            TacticalNetwork::request, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Preview.class, Codec::writePreview, Codec::readPreview,
            (message, context) -> {
                context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ua.townyrecruits.client.ClientTactics.receive(message)));
                context.get().setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clear() { PREVIEWS.clear(); COMMITS.clear(); TacticalRoutes.clear(); TacticalMovement.clear(); TacticalCombat.clear(); TacticalFire.clear(); TacticalActor.clear(); }
    public static void logout(UUID player) { PREVIEWS.remove(player); COMMITS.remove(player); }
    private static void request(Request message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer p = context.getSender();
            if (p == null || message.action < 0 || message.action > 2 || !message.order.sane()
                || Bridge.INSTANCE == null || !Bridge.INSTANCE.ready || p.m_5833_()) return;
            long now = p.m_20193_().m_46467_();
            Map<UUID, Long> limits = message.action == 1 ? COMMITS : PREVIEWS;
            if (message.order.mode() != Mode.CANCEL && now - limits.getOrDefault(p.m_20148_(), -100L) < (message.action == 1 ? 4 : 8)) return;
            limits.put(p.m_20148_(), now);
            if (message.action == 2) {
                boolean bombs = TacticalPlanner.select(p, message.order.groups()).stream()
                    .anyMatch(r -> RecruitBombs.count(r.getInventory(), -1) > 0);
                send(p, new Preview(message.sequence, message.order.mode(), message.order.formation(),
                    message.order.anchor(), message.order.yaw(), bombs, List.of())); return;
            }
            // Preview packets never grant authority. A click always recomputes paths, ammo and cooldowns.
            Plan plan = TacticalPlanner.plan(p, message.order);
            if (message.action == 0) { send(p, plan.preview(message.sequence)); return; }
            int performed = 0;
            if (message.order.mode().bomb()) {
                for (Assignment a : plan.units()) {
                    if (!a.marker().valid()) continue;
                    var random = a.recruit().m_217043_();
                    double[] offset = TacticalMath.disk(random.m_188500_(), random.m_188500_(), TacticalOrder.SCATTER);
                    Vec3 center = a.marker().target();
                    Vec3 landing = TacticalTerrain.surface(p.m_20193_(), center.f_82479_ + offset[0],
                        center.f_82481_ + offset[1], center.f_82480_, a.recruit());
                    if (landing != null && RecruitBombs.throwBomb(a.recruit(), RecruitBombs.shot(a.recruit(), landing), p.m_20148_())) performed++;
                }
            } else if (message.order.mode().action()) {
                var units = plan.units().stream().filter(a -> a.marker().valid()).map(Assignment::recruit).toList();
                performed = units.size();
                if (message.order.mode().fire()) {
                    for (var r : units) {
                        r.setShouldRanged(true);
                        if (!TacticalCombat.enemy(r, r.m_5448_())) r.m_6710_(TacticalCombat.nearest(r, TacticalActor.position(r), 48));
                    }
                    TacticalFire.order(units, message.order.mode());
                } else for (var r : units) {
                    if (message.order.mode() == Mode.CANCEL) TacticalOrders.cancel(r);
                    else {
                        if (message.order.mode() == Mode.HOLD_FRONT || message.order.mode() == Mode.KEEP_DISTANCE) TacticalOrders.interruptMovement(r);
                        TacticalCombat.action(r, message.order);
                    }
                }
            } else {
                performed = (int)plan.preview(message.sequence).validCount();
                if (performed > 0) {
                    CommandEvents.saveFormation(p, -1);
                    for (var a : plan.units()) if (a.marker().valid()) { TacticalOrders.replaceMovement(a.recruit()); TacticalCombat.speed(a.recruit(), message.order.speed()); }
                    if (message.order.mode() == Mode.ROUTE) TacticalRoutes.start(plan, p.m_20148_());
                    else if (message.order.mode().manoeuvre()) {
                        for (var a : plan.units()) if (a.marker().valid()) TacticalCombat.begin(a.recruit(), message.order);
                    } else TacticalMovement.execute(plan, p.m_20148_());
                }
            }
            p.m_5661_(Component.m_237110_(performed == 0 ? "tactics.townyrecruits.rejected" : "tactics.townyrecruits.accepted",
                performed, plan.units().size()), true);
            send(p, plan.preview(message.sequence));
        });
        context.setPacketHandled(true);
    }
    private static void send(ServerPlayer p, Preview preview) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), preview);
    }
    /** Codec can be tested without starting Forge's transformed networking event bus. */
    public static final class Codec {
        private Codec() {}
        public static void writeRequest(Request m, FriendlyByteBuf b) {
            b.writeInt(m.sequence); b.writeByte(m.action); TacticalOrder o = m.order;
            b.writeByte(o.mode().ordinal()); b.writeByte(o.formation().ordinal());
            b.writeByte(o.groups().size()); for (UUID group : o.groups()) b.m_130077_(group);
            vector(b, o.anchor()); b.writeFloat(o.yaw()); b.writeBoolean(o.tight());
            b.writeDouble(o.width()); b.writeByte(o.speed().ordinal()); b.writeDouble(o.leash()); b.writeDouble(o.distance());
            b.writeByte(o.waypoints().size());
            for (Waypoint point : o.waypoints()) { vector(b, point.anchor()); b.writeFloat(point.yaw()); b.writeByte(point.formation().ordinal()); b.writeDouble(point.width()); }
        }
        public static Request readRequest(FriendlyByteBuf b) {
            int seq = b.readInt(), action = b.readUnsignedByte();
            Mode mode = enumeration(b, Mode.values()); Formation formation = enumeration(b, Formation.values());
            int count = bounded(b.readUnsignedByte(), TacticalOrder.MAX_GROUPS);
            List<UUID> groups = new ArrayList<>(); for (int i = 0; i < count; i++) groups.add(b.m_130259_());
            Vec3 anchor = vector(b); float yaw = b.readFloat(); boolean tight = b.readBoolean();
            double width = b.readDouble(); Speed speed = enumeration(b, Speed.values()); double leash = b.readDouble(), distance = b.readDouble();
            int points = bounded(b.readUnsignedByte(), TacticalOrder.MAX_WAYPOINTS); List<Waypoint> waypoints = new ArrayList<>();
            for (int i = 0; i < points; i++) waypoints.add(new Waypoint(vector(b), b.readFloat(), enumeration(b, Formation.values()), b.readDouble()));
            return new Request(seq, action, new TacticalOrder(mode, formation, List.copyOf(groups), anchor, yaw, tight, width, speed, leash, distance, List.copyOf(waypoints)));
        }
        public static void writePreview(Preview p, FriendlyByteBuf b) {
            b.writeInt(p.sequence()); b.writeByte(p.mode().ordinal()); b.writeByte(p.formation().ordinal());
            vector(b, p.anchor()); b.writeFloat(p.yaw()); b.writeBoolean(p.hasBombs());
            b.writeDouble(p.width()); b.writeByte(p.speed().ordinal()); b.writeByte(p.points()); b.writeShort(p.markers().size());
            for (Marker m : p.markers()) {
                b.writeInt(m.entity()); vector(b, m.from()); vector(b, m.target()); b.writeFloat(m.yaw());
                b.writeDouble(m.footprint());
                b.writeByte(m.failure().ordinal()); b.writeByte(m.route().size()); for (Vec3 v : m.route()) vector(b, v);
            }
        }
        public static Preview readPreview(FriendlyByteBuf b) {
            int seq = b.readInt(); Mode mode = enumeration(b, Mode.values()); Formation shape = enumeration(b, Formation.values());
            Vec3 anchor = vector(b); float yaw = b.readFloat(); boolean bombs = b.readBoolean();
            double width = b.readDouble(); Speed speed = enumeration(b, Speed.values()); int points = bounded(b.readUnsignedByte(), TacticalOrder.MAX_WAYPOINTS);
            int count = bounded(b.readUnsignedShort(), TacticalOrder.MAX_UNITS);
            List<Marker> markers = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int entity = b.readInt(); Vec3 from = vector(b), target = vector(b); float facing = b.readFloat();
                double footprint = b.readDouble();
                Failure fail = enumeration(b, Failure.values()); int size = bounded(b.readUnsignedByte(), 64);
                List<Vec3> route = new ArrayList<>(); for (int j = 0; j < size; j++) route.add(vector(b));
                markers.add(new Marker(entity, from, target, facing, fail, List.copyOf(route), footprint));
            }
            return new Preview(seq, mode, shape, anchor, yaw, bombs, List.copyOf(markers), width, speed, points);
        }
        private static int bounded(int value, int max) {
            if (value < 0 || value > max) throw new IllegalArgumentException("Oversized tactical packet");
            return value;
        }
        private static <T> T enumeration(FriendlyByteBuf b, T[] values) { return values[bounded(b.readUnsignedByte(), values.length - 1)]; }
        private static void vector(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.f_82479_); b.writeDouble(v.f_82480_); b.writeDouble(v.f_82481_); }
        private static Vec3 vector(FriendlyByteBuf b) {
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            if (!Double.isFinite(x + y + z)) throw new IllegalArgumentException("Non-finite tactical coordinate");
            return new Vec3(x, y, z);
        }
    }
}
