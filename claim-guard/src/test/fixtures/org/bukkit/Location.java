package org.bukkit;
public final class Location {
    public final World world;
    public final int x, y, z;
    public Location(World world, double x, double y, double z) {
        this.world = world; this.x = (int)Math.floor(x); this.y = (int)Math.floor(y); this.z = (int)Math.floor(z);
    }
    public org.bukkit.block.Block getBlock() { return new org.bukkit.block.Block(); }
}
