package guardtest;

import java.util.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

public final class MixinIntegrationTest {
    private static void requireMethod(OfflineMixinService service, String type, String name, String descriptor) throws Exception {
        if (service.getClassNode(type).methods.stream().noneMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))) {
            throw new AssertionError("Runtime API mismatch: " + type + "." + name + descriptor);
        }
    }

    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
        env.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("empirecraft_claim_guard.mixins.json");
        OfflineMixinService service = (OfflineMixinService)MixinService.getService();
        requireMethod(service, "com.palmergames.bukkit.towny.TownyAPI", "getInstance", "()Lcom/palmergames/bukkit/towny/TownyAPI;");
        requireMethod(service, "com.palmergames.bukkit.towny.TownyAPI", "isPVP", "(Lorg/bukkit/Location;)Z");
        requireMethod(service, "com.palmergames.bukkit.towny.TownyAPI", "isTownyWorld", "(Lorg/bukkit/World;)Z");
        requireMethod(service, "com.palmergames.bukkit.towny.TownyAPI", "getTownBlock", "(Lorg/bukkit/Location;)Lcom/palmergames/bukkit/towny/object/TownBlock;");
        requireMethod(service, "com.palmergames.bukkit.towny.utils.PlayerCacheUtil", "getCachePermission", "(Lorg/bukkit/entity/Player;Lorg/bukkit/Location;Lorg/bukkit/Material;Lcom/palmergames/bukkit/towny/object/TownyPermission$ActionType;)Z");
        if (!Boolean.getBoolean("guard.forgeOnly")) requireMethod(service, "net.minecraft.world.level.Level", "getWorld", "()Lorg/bukkit/craftbukkit/v1_20_R1/CraftWorld;");
        requireMethod(service, "net.minecraft.world.entity.player.Player", "m_36335_", "()Lnet/minecraft/world/item/ItemCooldowns;");
        requireMethod(service, "net.minecraft.world.item.ItemCooldowns", "m_41519_", "(Lnet/minecraft/world/item/Item;)Z");
        requireMethod(service, "net.minecraft.world.item.ItemCooldowns", "m_41524_", "(Lnet/minecraft/world/item/Item;I)V");
        IMixinTransformer transformer = service.transformer();
        String[] targets = {
            "net.mehvahdjukaar.supplementaries.common.items.BombItem",
            "net.minecraft.world.level.Explosion",
            "net.minecraft.world.entity.projectile.Projectile",
            "net.mehvahdjukaar.supplementaries.common.misc.explosion.BombExplosion",
            "net.mehvahdjukaar.supplementaries.common.misc.explosion.CannonBallExplosion",
            "net.mehvahdjukaar.supplementaries.common.entities.CannonBallEntity",
            "net.mehvahdjukaar.supplementaries.common.entities.BombEntity",
            "net.mehvahdjukaar.supplementaries.common.entities.BombEntity$BombType"
        };
        int calls = 0;
        for (String name : targets) {
            ClassNode node = service.getClassNode(name);
            if (!transformer.transformClass(env, name, node)) throw new AssertionError("No mixins applied: " + name);
            int guards = 0;
            for (MethodNode method : node.methods) {
                // Check data-flow and stack shapes after applying real Mixin 0.8.5 to pinned game/mod bytecode.
                if ((method.access & 0x0500) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && (call.owner.equals("ua/empirecraft/claimguard/WeaponGuard") || call.owner.equals("ua/empirecraft/claimguard/BombCooldown"))) guards++;
                }
            }
            if (guards == 0) throw new AssertionError("No guard calls in " + name);
            calls += guards;
            System.out.println("PASS transformed and verified " + name + " (" + guards + " guard calls)");
        }
        if (Mixins.getUnvisitedCount() != 0) throw new AssertionError("Unvisited mixin configuration");
        System.out.println("PASS exact-JAR integration: " + targets.length + " classes, " + calls + " guard calls");
        System.out.println(Boolean.getBoolean("guard.forgeOnly") ? "PASS exact Towny 0.101.2.0 API; standard Forge runtime (Mohist Level.getWorld contract skipped)" : "PASS exact Towny 0.101.2.0 and Mohist reflection API contracts");
    }
}
