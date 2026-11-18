package ua.empirecraft.townymap;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(EmpireTownyMap.MOD_ID)
public final class EmpireTownyMap {
    public static final String MOD_ID = "empirecraft_townymap";
    static final Logger LOG = LogUtils.getLogger();
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue UPDATE_SECONDS;
    static final ForgeConfigSpec.BooleanValue INDEPENDENT_TOWNS;
    static final ForgeConfigSpec.DoubleValue FILL_OPACITY;
    static final ForgeConfigSpec.DoubleValue BORDER_WIDTH;
    static {
        var b = new ForgeConfigSpec.Builder();
        UPDATE_SECONDS = b.comment("Seconds between Towny snapshots. Only the occupied chunk updates personal history.")
                .defineInRange("updateIntervalSeconds", 5, 1, 300);
        INDEPENDENT_TOWNS = b.comment("Also discover visited towns which do not belong to a nation.")
                .define("showIndependentTowns", false);
        FILL_OPACITY = b.defineInRange("fillOpacity", 0.18, 0.0, 1.0);
        BORDER_WIDTH = b.defineInRange("borderWidth", 2.0, 0.0, 10.0);
        SPEC = b.build();
    }
    public EmpireTownyMap() {
        CurrencyNetwork.init();
        MarketNetwork.init();
        FleetNetwork.init();
        MapHistoryNetwork.init();
        StrategyNetwork.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}

