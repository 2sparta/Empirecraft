package guardtest;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import ua.empirecraft.claimguard.TownyGate;
import ua.empirecraft.claimguard.TownyGate.Target;

/** Exercises production reflection, world/position selection, current permissions and recovery. */
public final class TownyGateTest {
    public record FakeLevel(World world) { public World getWorld() { return world; } }
    private static int assertions;
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        TownyGate gate = new TownyGate();
        World world = new World("world"), europe = new World("world_europe");
        World.Claim claim = new World.Claim(); world.claims.put("-1:-1", claim);
        World.Claim pvp = new World.Claim(); pvp.pvp = true; europe.claims.put("-1:-1", pvp);
        FakeLevel level = new FakeLevel(world), europeLevel = new FakeLevel(europe);
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        Bukkit.ONLINE.put(owner, new Player(owner)); Bukkit.ONLINE.put(stranger, new Player(stranger));
        claim.permitted.add(owner);
        for (Target kind : Target.values()) {
            check(gate.allows(level, 0, 64, -1, kind, null), "wilderness " + kind);
            check(gate.allows(level, -17, 64, -1, kind, stranger), "negative boundary wilderness " + kind);
        }
        for (UUID actor : Arrays.asList(owner, stranger, null)) {
            check(!gate.allows(level, -1, 64, -1, Target.BLOCK, actor), "block protection even with permission");
            check(!gate.allows(level, -1, 64, -1, Target.OBJECT, actor), "objects always protected");
            check(!gate.allows(level, -1, 64, -1, Target.PLAYER, actor), "peaceful players always protected");
            check(gate.allows(europeLevel, -1, 64, -1, Target.PLAYER, actor), "PvP applies without granting build permission");
        }
        check(gate.allows(level, -1, 64, -1, Target.MOB, owner), "authorized mob damage");
        check(!gate.allows(level, -1, 64, -1, Target.MOB, stranger), "unauthorized mob damage");
        check(!gate.allows(level, -1, 64, -1, Target.MOB, null), "unknown shooter fails closed");
        check(!gate.allows(europeLevel, -1, 64, -1, Target.MOB, owner), "permissions are per world");
        check(!gate.allows(level, -16, 64, -16, Target.BLOCK, null), "negative inside boundary");
        check(!gate.allows(level, -1, 250, -1, Target.BLOCK, null), "claims protect full column");
        claim.permitted.remove(owner);
        check(!gate.allows(level, -1, 64, -1, Target.MOB, owner), "revoked permission applies at impact");
        claim.permitted.add(owner); Bukkit.ONLINE.remove(owner);
        check(!gate.allows(level, -1, 64, -1, Target.MOB, owner), "offline identity cannot gain permission");
        Bukkit.ONLINE.put(owner, new Player(owner));
        check(gate.allows(level, -1, 64, -1, Target.MOB, owner), "reconnected authorized owner");
        claim.pvp = true;
        check(gate.allows(level, -1, 64, -1, Target.PLAYER, null), "PvP flag changes apply immediately");
        claim.pvp = false;
        check(!gate.allows(level, -1, 64, -1, Target.PLAYER, owner), "permission never overrides peaceful PvP");
        World.Claim newClaim = new World.Claim(); world.claims.put("0:-1", newClaim);
        check(!gate.allows(level, 0, 64, -1, Target.BLOCK, null), "claim created after firing");
        world.claims.remove("0:-1");
        check(gate.allows(level, 0, 64, -1, Target.BLOCK, null), "removed claim is wilderness");
        Bukkit.primary = false;
        check(!gate.allows(level, 0, 64, -1, Target.BLOCK, null), "off-thread lookup fails closed");
        Bukkit.primary = true;
        Bukkit.MANAGER.plugin = null;
        check(!gate.allows(level, 0, 64, -1, Target.BLOCK, null), "absent Towny fails closed even in apparent wilderness");
        PluginManager.FakePlugin replacement = new PluginManager.FakePlugin();
        replacement.enabled = false; Bukkit.MANAGER.plugin = replacement;
        check(!gate.allows(level, -1, 64, -1, Target.MOB, owner), "disabled Towny fails closed");
        replacement.enabled = true;
        check(gate.allows(level, -1, 64, -1, Target.MOB, owner), "plugin replacement recovers");
        TownyAPI.instance = null;
        check(!gate.allows(level, 0, 64, -1, Target.BLOCK, null), "API not ready fails closed");
        TownyAPI.instance = new TownyAPI(); TownyAPI.broken = true;
        check(!gate.allows(level, -1, 64, -1, Target.PLAYER, owner), "API exception fails closed");
        TownyAPI.broken = false;
        check(gate.allows(level, 0, 64, -1, Target.BLOCK, null), "lookup recovers after API exception");
        world.managed = false;
        for (Target kind : Target.values()) check(gate.allows(level, -1, 64, -1, kind, null), "unmanaged world " + kind);
        check(PlayerCacheUtil.calls >= 5, "actual Towny permission bridge invoked");
        System.out.println("PASS Towny policy and reflection: " + assertions + " assertions");
    }
}
