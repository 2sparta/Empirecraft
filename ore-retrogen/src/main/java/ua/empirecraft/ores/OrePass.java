package ua.empirecraft.ores;

import java.util.*;

/** Bounded to one 16x16 chunk. No world access or chunk loading in this planner. */
public final class OrePass {
    public enum Kind { OTHER, STONE, DEEP, SULFUR, ZINC, QUARTZ }
    public record Pos(int x, int y, int z) {}
    public record Result(int sulfurAdded, int zincAdded, int sulfurExisting, int zincExisting, int quartzAdded, int quartzExisting) {}
    public interface Terrain {
        int minY();
        int maxY(); // inclusive
        int ceiling(int x, int z); // inclusive, below the local surface/seafloor
        Kind kind(Pos pos);
        boolean place(Pos pos, Kind ore, boolean deep);
    }
    private static final int[][] DIRECTIONS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    public static Result run(Terrain terrain, long seed, int sulfurTarget, int zincTarget) {
        return run(terrain,seed,sulfurTarget,zincTarget,0);
    }

    public static Result run(Terrain terrain, long seed, int sulfurTarget, int zincTarget, int quartzTarget) {
        if (terrain.minY() > terrain.maxY() || sulfurTarget < 0 || zincTarget < 0 || quartzTarget < 0)
            throw new IllegalArgumentException("Invalid ore bounds or targets");
        List<Pos> candidates = new ArrayList<>();
        int sulfur = 0, zinc = 0, quartz = 0;
        for (int y = terrain.minY(); y <= terrain.maxY(); y++) {
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                Pos pos = new Pos(x,y,z);
                Kind kind = terrain.kind(pos);
                // Count existing ores even if surface clearance excludes them from replacement.
                if (kind == Kind.SULFUR) sulfur++;
                if (kind == Kind.ZINC) zinc++;
                if (kind == Kind.QUARTZ) quartz++;
                if (y <= terrain.ceiling(x,z) && host(kind)) candidates.add(pos);
            }
        }
        Random random = new Random(seed);
        Collections.shuffle(candidates, random);
        int sulfurAdded = fill(terrain,candidates,Kind.SULFUR,Math.max(0,sulfurTarget-sulfur),random);
        int zincAdded = fill(terrain,candidates,Kind.ZINC,Math.max(0,zincTarget-zinc),random);
        int quartzAdded = fill(terrain,candidates,Kind.QUARTZ,Math.max(0,quartzTarget-quartz),random);
        return new Result(sulfurAdded,zincAdded,sulfur,zinc,quartzAdded,quartz);
    }

    private static boolean host(Kind kind) { return kind == Kind.STONE || kind == Kind.DEEP; }
    private static boolean inside(Terrain terrain, Pos p) {
        return p.x >= 0 && p.x < 16 && p.z >= 0 && p.z < 16
            && p.y >= terrain.minY() && p.y <= terrain.maxY() && p.y <= terrain.ceiling(p.x,p.z);
    }
    private static int fill(Terrain terrain,List<Pos> seeds,Kind ore,int needed,Random random) {
        int added = 0;
        for (Pos seed : seeds) {
            if (added >= needed) break;
            if (!host(terrain.kind(seed))) continue;
            int veinLimit = Math.min(4+random.nextInt(5),needed-added), vein = 0;
            ArrayDeque<Pos> frontier = new ArrayDeque<>();
            Set<Pos> visited = new HashSet<>();
            frontier.add(seed); visited.add(seed);
            while (!frontier.isEmpty() && vein < veinLimit) {
                Pos pos = frontier.removeFirst();
                if (!inside(terrain,pos)) continue;
                Kind before = terrain.kind(pos);
                if (!host(before) || !terrain.place(pos,ore,before == Kind.DEEP)) continue;
                added++; vein++;
                // Randomize the six directions without unbounded random-walk retries.
                int start = random.nextInt(6);
                for (int i = 0; i < 6; i++) {
                    int[] d = DIRECTIONS[(start+i)%6];
                    Pos next = new Pos(pos.x+d[0],pos.y+d[1],pos.z+d[2]);
                    if (inside(terrain,next) && visited.add(next)) frontier.addLast(next);
                }
            }
        }
        return added;
    }
}
