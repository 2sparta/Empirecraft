package ua.empirecraft.claimguard;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod("empirecraft_claim_guard")
public final class ClaimGuard {
    public ClaimGuard() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::detonate);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::attack);
    }

    private void detonate(ExplosionEvent.Detonate event) {
        Entity weapon = WeaponGuard.weapon(event.getExplosion());
        if (weapon == null || event.getLevel().m_5776_()) return;
        WeaponGuard.filterBlocks(event.getExplosion(), event.getLevel());
        WeaponGuard.filterEntities(event.getAffectedEntities(), weapon);
    }

    private void attack(LivingAttackEvent event) {
        Entity weapon = WeaponGuard.weapon(event.getSource());
        if (weapon != null && !WeaponGuard.damage(weapon, event.getEntity())) event.setCanceled(true);
    }
}
