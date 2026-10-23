package com.palmergames.bukkit.towny;
import org.bukkit.*;
public final class TownyAPI {
    public static TownyAPI instance = new TownyAPI();
    public static boolean broken;
    public static TownyAPI getInstance() { return instance; }
    public boolean isTownyWorld(World world) { return world.managed; }
    public Object getTownBlock(Location loc) {
        if (broken) throw new IllegalStateException("database unavailable");
        return loc.world.at(loc.x, loc.z);
    }
    public boolean isPVP(Location loc) { return loc.world.at(loc.x, loc.z).pvp; }
}
