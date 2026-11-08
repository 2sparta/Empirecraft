package ua.empirecraft.forging;

import java.util.UUID;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

/** Disable laceration from every source and repair effects saved before the update. */
public final class WoundPolicy {
    private static final UUID HEALTH_PENALTY = UUID.fromString("81aeaa56-376b-4498-935b-2f7f68070635");
    private WoundPolicy() {}

    private static boolean laceration(MobEffect effect) {
        var id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return id != null && id.toString().equals("magistuarmory:laceration");
    }

    public static void applicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance() != null && laceration(event.getEffectInstance().m_19544_())) event.setResult(Event.Result.DENY);
    }

    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().f_46443_ || !(event.getEntity() instanceof LivingEntity living)) return;
        for (var effect : java.util.List.copyOf(living.m_21220_()))
            if (laceration(effect.m_19544_())) living.m_21195_(effect.m_19544_());
        // Also repair the historical orphaned attribute if its effect is absent.
        var health = living.m_21051_(Attributes.f_22276_);
        if (health != null && health.m_22111_(HEALTH_PENALTY) != null) health.m_22120_(HEALTH_PENALTY);
    }
}
