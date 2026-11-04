package ua.empirecraft.shipphysics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

/** Material-specific impact/cracking feedback at the real contact, after protection checks. */
public final class CollisionSounds {
    private static final Map<ServerLevel, LinkedHashMap<Long, Long>> LAST = new WeakHashMap<>();
    private CollisionSounds() {}

    public static void play(ServerLevel level, LoadedServerShip ship, BlockPos pos, BlockState state, boolean broken) {
        long id = ship == null ? Long.MIN_VALUE : ship.getId();
        var recent = LAST.computeIfAbsent(level, key -> new LinkedHashMap<>());
        long now = level.getGameTime();
        Long previous = recent.get(id);
        if (previous != null && now >= previous && now - previous < 4) return;
        recent.remove(id);
        recent.put(id, now);
        if (recent.size() > 1024) recent.remove(recent.keySet().iterator().next());
        Vector3d world = new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (ship != null) ship.getShipToWorld().transformPosition(world);
        if (!world.isFinite()) return;
        var material = state.getSoundType();
        level.playSound(null, world.x, world.y, world.z, broken ? material.getBreakSound() : material.getHitSound(),
                SoundSource.BLOCKS, broken ? 1.4f : .7f, broken ? .7f : .85f);
    }
}
