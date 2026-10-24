package com.palmergames.bukkit.towny;
public final class TownyAPI {
    public static TownyAPI instance=new TownyAPI();public static boolean broken;
    public static org.bukkit.Location last;
    public static TownyAPI getInstance(){return instance;}
    public boolean isTownyWorld(org.bukkit.World world){return world.managed;}
    public Object getTownBlock(org.bukkit.Location loc){
        if(broken)throw new IllegalStateException("database unavailable");
        last=loc;return loc.world().claimed?this:null;
    }
}
