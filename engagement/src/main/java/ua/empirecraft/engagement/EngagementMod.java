package ua.empirecraft.engagement;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EngagementMod.ID)
public final class EngagementMod {
    public static final String ID="empirecraft_engagement";
    public EngagementMod(){NavalFlags.register(FMLJavaModLoadingContext.get().getModEventBus());}
}
