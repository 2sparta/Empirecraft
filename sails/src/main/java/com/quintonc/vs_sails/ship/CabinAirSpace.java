package com.quintonc.vs_sails.ship;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Bounded, loaded-chunk-only confirmation of native air pockets after hull edits.
 * VS 2.4.11 can retain DISCONNECTED for an opened compartment. Never use that
 * stale result as proof of sealing. No world blocks or ocean fluids are changed.
 */
public final class CabinAirSpace {
    private static final int LIMIT = 65536;
    private static final Map<Level, LinkedHashMap<Long, Snapshot>> CACHE = new WeakHashMap<>();
    private record Cell(BlockState state, VoxelShape shape) {}
    private static final class Snapshot {
        final AABBi bounds;
        final long tick;
        final Map<BlockPos, Boolean> results = new HashMap<>();
        Snapshot(AABBic bounds, long tick) { this.bounds = new AABBi(bounds); this.tick = tick; }
    }
    private CabinAirSpace() {}

    public static synchronized void invalidate(Level level, BlockPos pos) {
        var ships = CACHE.get(level);
        if (ships == null || ships.isEmpty()) return;
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship != null) ships.remove(ship.getId());
    }

    /** Null delegates non-ship positions to VS. Called only on game/render threads. */
    public static Boolean atStoragePosition(Level level, BlockPos pos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        return ship == null ? null : sealed(level, ship, pos);
    }

    public static synchronized boolean sealed(Level level, Ship ship, BlockPos start) {
        AABBic bounds = ship.getShipAABB();
        if (bounds == null || !inside(bounds, start)) return false;
        long now = level.getGameTime();
        var ships = CACHE.computeIfAbsent(level, key -> new LinkedHashMap<>());
        Snapshot snapshot = ships.get(ship.getId());
        int lifetime = level.isClientSide ? 5 : 20;
        if (snapshot == null || now < snapshot.tick || now - snapshot.tick >= lifetime || !snapshot.bounds.equals(bounds)) {
            snapshot = new Snapshot(bounds, now); ships.put(ship.getId(), snapshot);
            if (ships.size() > 128) ships.remove(ships.keySet().iterator().next());
        }
        Boolean cached = snapshot.results.get(start);
        if (cached != null) return cached;
        Cell initial = cell(level, start);
        if (initial == null || !initial.state.getFluidState().isEmpty() || Shapes.block().equals(initial.shape)) return false;
        Map<BlockPos, Cell> visited = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.put(start.immutable(), initial); queue.add(start.immutable());
        boolean closed = true;
        search: while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst(); Cell from = visited.get(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (visited.containsKey(next)) continue;
                if (!inside(bounds, next)) {
                    if (!Shapes.mergedFaceOccludes(from.shape, Shapes.empty(), direction)) { closed = false; break search; }
                    continue;
                }
                Cell to = cell(level, next);
                if (to == null) { closed = false; break search; }
                if (Shapes.mergedFaceOccludes(from.shape, to.shape, direction)) continue;
                if (visited.size() >= LIMIT || !to.state.getFluidState().isEmpty()) { closed = false; break search; }
                visited.put(next, to); queue.addLast(next);
            }
        }
        // All visited cells belong to this same air component. Reuse the result for
        // other passengers rather than walking the compartment for every entity.
        if (snapshot.results.size() + visited.size() > LIMIT) snapshot.results.clear();
        for (BlockPos pos : visited.keySet()) snapshot.results.put(pos, closed);
        return closed;
    }

    private static boolean inside(AABBic b, BlockPos p) {
        return p.getX() >= b.minX() && p.getX() < b.maxX() && p.getY() >= b.minY()
                && p.getY() < b.maxY() && p.getZ() >= b.minZ() && p.getZ() < b.maxZ();
    }

    private static Cell cell(Level level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() || !level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        return new Cell(state, state.getCollisionShape(level, pos));
    }
}
