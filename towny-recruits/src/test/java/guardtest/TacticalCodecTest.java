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
        for (Mode mode : Mode.values()) for (Formation shape : Formation.values()) {
            var order = new TacticalOrder(mode, shape, List.of(UUID.randomUUID()), new Vec3(-14.75, 64.625, 7.125), -135, true);
            assert order.sane();
            var request = new TacticalNetwork.Request(531, 0, order);
            FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
            TacticalNetwork.Codec.writeRequest(request, b); var copy = TacticalNetwork.Codec.readRequest(b);
            assert copy.equals(request); assert !b.isReadable(); b.release();
        }
        var markers = new ArrayList<Marker>();
        for (int i = 0; i < 128; i++) markers.add(new Marker(i, Vec3.f_82478_, new Vec3(i, 1.5, 2), 90,
            Failure.values()[i % Failure.values().length], List.of(new Vec3(i, 1, 0), new Vec3(i, 1.5, 2))));
        var p = new Preview(42, Mode.MARCH, Formation.SQUARE, new Vec3(0.125, 1.5, -19), 90, true, markers);
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
        System.out.println("PASS all tactical request codecs, mixed-validity 128-unit preview, group/enum bounds and non-finite intent rejection");
    }
}
