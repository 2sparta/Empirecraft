package net.fretux.knockedback;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
public final class EmpireBleedout {
    public static void tick(Map<UUID, Integer> states) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID id : new ArrayList<>(states.keySet())) {
            Integer previous = states.get(id);
            if (previous == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            int left = previous - 1;
            if (player == null) { states.remove(id); continue; } // Logout already executes upstream death cleanup.
            if (left <= 0) {
                KnockedManager.markKillInProgress(player);
                try {
                    KnockedManager.removeKnockedState(player);
                    player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
                } finally { KnockedManager.unmarkKillInProgress(player); }
            } else {
                states.put(id, left);
                KnockedManager.applyKnockedEffects(player);
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new KnockedTimePacket(left));
            }
        }
    }
}
