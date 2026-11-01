package com.quintonc.vs_sails.protection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;

public final class ProtectionConfig {
    public boolean enabled=true, protectAlliedTowns=true, protectSameNation=true, protectExplosions=true;
    public String controlPermission="vs_sails.ship.control", managePermission="vs_sails.ship.manage", bypassPermission="vs_sails.ship.bypass";
    private static ProtectionConfig current;
    public static ProtectionConfig get() {
        if(current==null)reload();return current;
    }
    public static void reload() {
        Path path=FMLPaths.CONFIGDIR.get().resolve("vs_sails/ship-protection.json");
        try {
            Gson gson=new GsonBuilder().setPrettyPrinting().create();
            ProtectionConfig next;
            if(Files.exists(path))next=gson.fromJson(Files.readString(path),ProtectionConfig.class);
            else {next=new ProtectionConfig();Files.createDirectories(path.getParent());Files.writeString(path,gson.toJson(next)+"\n");}
            if(next==null || invalid(next.controlPermission) || invalid(next.managePermission) || invalid(next.bypassPermission))
                throw new IllegalArgumentException("Permission nodes must be nonempty");
            current=next;
        } catch(Exception ex) { throw new IllegalStateException("Cannot read "+path,ex); }
    }
    private static boolean invalid(String node) { return node==null || !node.matches("[a-z0-9_.-]+" ); }
}
