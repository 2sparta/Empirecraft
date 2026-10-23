package org.bukkit;
import java.util.*;
public final class World {
    public final String name;
    public boolean managed = true;
    public final Map<String, Claim> claims = new HashMap<>();
    public World(String name) { this.name = name; }
    public Claim at(int x, int z) { return claims.get(Math.floorDiv(x, 16) + ":" + Math.floorDiv(z, 16)); }
    public static final class Claim {
        public boolean pvp;
        public final Set<UUID> permitted = new HashSet<>();
    }
}
