package ua.empirecraft.interactions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.empirecraft.interactions.ThrownImpacts;

@Mixin(value = ServerPlayer.class, remap = false)
public abstract class ServerPlayerImpactMixin {
    @Redirect(method = "m_6469_", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;m_7099_(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean empirecraft$serverSelfHit(ServerPlayer defender, Player attacker, DamageSource source, float amount) {
        return ThrownImpacts.selfImpact(source.getDirectEntity(), defender, attacker) || defender.canHarmPlayer(attacker);
    }
}
