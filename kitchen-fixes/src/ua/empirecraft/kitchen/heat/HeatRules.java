package ua.empirecraft.kitchen.heat;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;

/** Household warmth stops at comfort; climate and exposed fire keep their native danger. */
public final class HeatRules {
    public static final float COMFORT = 24f;
    private static final class Scan {
        boolean warm, dangerous, baseline;
        float cold;
    }
    private static final ThreadLocal<Scan> SCAN = ThreadLocal.withInitial(Scan::new);
    private HeatRules() { }
    public static boolean baseline() { return SCAN.get().baseline; }
    public static float cold() { return SCAN.get().cold; }
    public static void begin() { var s=SCAN.get(); s.warm=false; s.dangerous=false; s.cold=0; }
    public static float source(float value, BlockState state) {
        if (value > 0) {
            var s=SCAN.get();
            if (state.getBlock() instanceof CampfireBlock || state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA)) s.dangerous=true;
            else s.warm=true;
        }
        return value;
    }
    public static float finish(float value, float coldest, float coldRemainder) {
        float alone=coldest*(float)Math.sqrt(Math.log10(Math.abs(coldRemainder)+10f));
        SCAN.get().cold=alone<coldest?Math.max(coldest-2.5f,alone):alone;
        return value;
    }
    public static float safe(float raw, float withoutHouseholdWarmth) {
        return Math.min(raw, Math.max(COMFORT, withoutHouseholdWarmth));
    }
    public static float playerTarget(float raw, Player player) {
        var s=SCAN.get();
        if (s.baseline || !s.warm || s.dangerous || raw<=COMFORT) return raw;
        s.baseline=true;
        try { return safe(raw,TemperatureUtil.getPlayerTargetTemperature(player)); }
        finally { s.baseline=false; }
    }
    public static float worldTarget(float raw, Level level, BlockPos pos) {
        var s=SCAN.get();
        if (s.baseline || !s.warm || s.dangerous || raw<=COMFORT) return raw;
        s.baseline=true;
        try { return safe(raw,TemperatureUtil.getWorldTemperature(level,pos)); }
        finally { s.baseline=false; }
    }
}
