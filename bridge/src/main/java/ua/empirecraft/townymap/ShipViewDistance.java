package ua.empirecraft.townymap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
/** Capture the vanilla client settings packet; no Mohist-only player fields. */
public final class ShipViewDistance {
 private static final Map<UUID,Integer> REQUESTED=new ConcurrentHashMap<>();
 public static int clamp(int chunks){return Math.max(2,Math.min(32,chunks));}
 public static void update(ServerPlayer player,int chunks){REQUESTED.put(player.getUUID(),clamp(chunks));}
 public static int blocks(ServerPlayer player){int server=clamp(player.getServer().getPlayerList().getViewDistance());return 16*Math.min(server,REQUESTED.getOrDefault(player.getUUID(),server));}
 public static void forget(UUID player){REQUESTED.remove(player);}
 public static void clear(){REQUESTED.clear();}
 private ShipViewDistance(){}
}
