package com.quintonc.vs_sails.ship;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

/** Server-owned positional sounds: one source per ship, never one per hull block. */
@Mod.EventBusSubscriber(modid = "vs_sails", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShipSounds {
    public static final SoundEvent CREAK = sound("hull_creak");
    public static final SoundEvent WATER = sound("hull_water");
    private long nextCreak, nextWater;

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation("vs_sails", name));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, CREAK.getLocation(), () -> CREAK);
        event.register(Registries.SOUND_EVENT, WATER.getLocation(), () -> WATER);
    }

    public void tick(ServerLevel level, LoadedServerShip ship, double immersion, double weather, boolean enabled) {
        if (!enabled || !Double.isFinite(immersion) || immersion < .002) return;
        var p = ship.getTransform().getPositionInWorld();
        if (!p.isFinite()) return;
        long now = level.getGameTime();
        double rough = Math.max(0, Math.min(1.35, weather));
        if (nextWater == 0) {
            nextWater = now + 10 + Math.floorMod(ship.getId() * 17, 50);
            nextCreak = now + 30 + Math.floorMod(ship.getId() * 31, 90);
        }
        if (now >= nextWater) {
            level.playSound(null, p.x(), p.y(), p.z(), WATER, SoundSource.BLOCKS,
                    (float)(.25 + rough * .22), .85f + level.random.nextFloat() * .25f);
            nextWater = now + 45 + level.random.nextInt(40);
        }
        if (now >= nextCreak) {
            if (!ship.isStatic() && ship.getAngularVelocity().lengthSquared() > 1e-6)
                level.playSound(null, p.x(), p.y(), p.z(), CREAK, SoundSource.BLOCKS,
                        (float)(.20 + rough * .3), .8f + level.random.nextFloat() * .3f);
            nextCreak = now + 80 + level.random.nextInt(100) - (int)(rough * 30);
        }
    }
}
