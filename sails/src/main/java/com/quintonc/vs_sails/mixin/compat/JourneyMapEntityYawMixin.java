package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.client.JourneyMapHeading;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {"journeymap.client.model.entity.EntityDTO", "journeymap.client.render.draw.DrawEntityStep"}, remap = false)
public abstract class JourneyMapEntityYawMixin {
    // External JourneyMap methods are not remapped; invocation selectors use pinned 1.20.1 SRG names.
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_146908_()F"), remap = false, require = 1)
    private float empirecraft$entityHeading(LivingEntity entity) { return JourneyMapHeading.entityYaw(entity); }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_146908_()F"), remap = false, require = 0)
    private float empirecraft$offscreenHeading(Player player) { return JourneyMapHeading.entityYaw(player); }
}
