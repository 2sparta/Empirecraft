package ua.empirecraft.worldrules;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.Predicate;

/** World-independent deposit rules, also used by the regression tests. */
public final class RenewalPolicy {
    public record Pos(int x, int y, int z) {}
    public static boolean eligible(long seed, int x, int z, int percent) {
        long value = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return Math.floorMod(value ^ (value >>> 31), 100) < percent;
    }
    public static boolean leavesReserve(Pos start, Predicate<Pos> loaded, Predicate<Pos> lapis) {
        var queue = new ArrayDeque<Pos>(); var seen = new HashSet<Pos>();
        queue.add(start); int found = 0;
        int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty() && seen.size() < 64) {
            Pos pos = queue.removeFirst();
            if (!seen.add(pos) || !loaded.test(pos) || !lapis.test(pos)) continue;
            if (++found >= 3) return true;
            for (int[] d : directions) queue.addLast(new Pos(pos.x+d[0], pos.y+d[1], pos.z+d[2]));
        }
        return false;
    }
    private RenewalPolicy() {}
}
