package com.quintonc.vs_sails.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/** The Forge build checks permissions before relocating any block or inventory. */
public final class AssemblySafety {
    private AssemblySafety() {}

    public static boolean allows(ServerLevel level, Player player, Set<BlockPos> blocks) {
        if (player == null || player.isSpectator() || !player.getAbilities().mayBuild) return false;
        Object townyGate = null, destroy = null;
        Method townyAllows = null;
        try {
            // Empirecraft's existing bridge handles Towny plugin loading/reloading and plot permissions.
            Class.forName("org.bukkit.Bukkit");
            Class<?> gate = Class.forName("ua.empirecraft.claimguard.TownyGate");
            Class<?> target = Class.forName("ua.empirecraft.claimguard.TownyGate$Target");
            townyGate = gate.getConstructor().newInstance();
            destroy = target.getField("MOB").get(null); // This bridge action checks the actor's DESTROY permission.
            townyAllows = gate.getMethod("allows", Object.class, int.class, int.class, int.class, target, UUID.class);
        } catch (ClassNotFoundException unavailable) {
            // Plain Forge has no Bukkit/Towny. A Mohist server must have its permission bridge.
            try { Class.forName("org.bukkit.Bukkit"); return false; }
            catch (ClassNotFoundException plainForge) { /* vanilla and Forge event checks below */ }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) { return false; }
        for (BlockPos pos : blocks) {
            if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) return false;
            if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player))) return false;
            if (townyAllows != null) {
                try {
                    if (!Boolean.TRUE.equals(townyAllows.invoke(townyGate, level, pos.getX(), pos.getY(), pos.getZ(), destroy, player.getUUID()))) return false;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) { return false; }
            }
        }
        return true;
    }
}
