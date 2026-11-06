package guardtest;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.tactics.*;
import ua.townyrecruits.tactics.TacticalOrder.*;
import ua.townyrecruits.tactics.TacticalPlanner.*;

public final class TacticalCodecTest {
    public static void main(String[] args) {
        for (Mode mode : Mode.values()) for (Formation shape : Formation.values()) for (Speed speed : Speed.values()) {
            List<Waypoint> points = mode == Mode.ROUTE ? java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> new Waypoint(new Vec3(i * 4, 64.5, -i), i * 15, Formation.values()[i], 0.6 + i * 0.1)).toList() : List.of();
            var order = new TacticalOrder(mode, shape, List.of(UUID.randomUUID()), new Vec3(-14.75, 64.625, 7.125), -135, true,
                1.7, speed, 4, 24, points);
            assert order.sane();
            var request = new TacticalNetwork.Request(531, 0, order);
            FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
            TacticalNetwork.Codec.writeRequest(request, b); var copy = TacticalNetwork.Codec.readRequest(b);
            assert copy.equals(request); assert !b.isReadable(); b.release();
        }
        var markers = new ArrayList<Marker>();
        for (int i = 0; i < 128; i++) markers.add(new Marker(i, Vec3.f_82478_, new Vec3(i, 1.5, 2), 90,
            Failure.values()[i % Failure.values().length], java.util.stream.IntStream.range(0, 64).mapToObj(j -> new Vec3(j, 1, 0)).toList(), 1.4));
        var p = new Preview(42, Mode.ROUTE, Formation.SQUARE, new Vec3(0.125, 1.5, -19), 90, true, markers, 2.2, Speed.FAST, 8);
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        TacticalNetwork.Codec.writePreview(p, b); assert TacticalNetwork.Codec.readPreview(b).equals(p); assert !b.isReadable(); b.release();
        b = new FriendlyByteBuf(Unpooled.buffer());
        b.writeInt(1); b.writeByte(0); b.writeByte(0); b.writeByte(0); b.writeByte(17);
        try { TacticalNetwork.Codec.readRequest(b); throw new AssertionError("Oversized group list accepted"); }
        catch (IllegalArgumentException expected) {} finally { b.release(); }
        b = new FriendlyByteBuf(Unpooled.buffer());
        b.writeInt(1); b.writeByte(0); b.writeByte(255); b.writeByte(0); b.writeByte(0);
        try { TacticalNetwork.Codec.readRequest(b); throw new AssertionError("Unknown command accepted"); }
        catch (IllegalArgumentException expected) {} finally { b.release(); }
        UUID group = UUID.randomUUID();
        assert !new TacticalOrder(Mode.MOVE, Formation.LINE, List.of(group, group), Vec3.f_82478_, 0, false).sane();
        assert !new TacticalOrder(Mode.MOVE, Formation.LINE, List.of(group), new Vec3(Double.NaN, 0, 0), 0, false).sane();
        assert !new TacticalOrder(Mode.MOVE, Formation.LINE, List.of(group), Vec3.f_82478_, Float.POSITIVE_INFINITY, false).sane();
        for (double width : new double[]{Double.NaN, 0.59, 3.01})
            assert !new TacticalOrder(Mode.MOVE, Formation.LINE, List.of(group), Vec3.f_82478_, 0, false, width, Speed.NORMAL, 2, 16, List.of()).sane();
        assert !new TacticalOrder(Mode.ROUTE, Formation.KEEP, List.of(group), Vec3.f_82478_, 0, false).sane();
        List<Waypoint> tooMany = Collections.nCopies(9, new Waypoint(Vec3.f_82478_, 0, Formation.LINE, 1));
        var oversized = new TacticalNetwork.Request(1, 0, new TacticalOrder(Mode.ROUTE, Formation.LINE, List.of(group), Vec3.f_82478_, 0, false, 1, Speed.NORMAL, 2, 16, tooMany));
        assert !oversized.order().sane();
        b = new FriendlyByteBuf(Unpooled.buffer()); TacticalNetwork.Codec.writeRequest(oversized, b);
        try { TacticalNetwork.Codec.readRequest(b); throw new AssertionError("Ninth waypoint accepted"); }
        catch (IllegalArgumentException expected) {} finally { b.release(); }
        assert !new Waypoint(new Vec3(Double.POSITIVE_INFINITY, 0, 0), 0, Formation.COLUMN, 1).sane();
        assert !new TacticalOrder(Mode.HOLD_FRONT, Formation.KEEP, List.of(group), Vec3.f_82478_, 0, false, 1, Speed.NORMAL, 17, 16, List.of()).sane();
        assert !new TacticalOrder(Mode.KEEP_DISTANCE, Formation.KEEP, List.of(group), Vec3.f_82478_, 0, false, 1, Speed.NORMAL, 2, 33, List.of()).sane();
        System.out.println("PASS all tactical request codecs, mixed-validity 128-unit preview, group/enum bounds and non-finite intent rejection");
    }
}
