package ua.empirecraft.calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonalCropGrowthHandler;

public final class SeasonalPlants {
    private SeasonalPlants() {}
    /** Finding a wild plant is separate from growing a cultivated crop. */
    public static boolean canAppear(Level level) {
        return !ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())
            || sereneseasons.api.season.SeasonHelper.getSeasonState(level).getSeason()
                != sereneseasons.api.season.Season.WINTER;
    }
    public static boolean canGrow(Level level, BlockPos pos, BlockState state) {
        if (!ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) return true;
        // Reuse native fertility, tropical biomes, underground farms and greenhouse glass.
        CallbackInfo decision = new CallbackInfo("empirecraft:seasonal_growth", true);
        SeasonalCropGrowthHandler.onCropGrowth(level, pos, state, decision);
        return !decision.isCancelled();
    }
}
