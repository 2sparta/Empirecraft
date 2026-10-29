package ua.empirecraft.shipcannons;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mod("empirecraft_ship_cannons")
public final class ShipCannons {
    public static final String VERSION="1.2.1";
    public ShipCannons() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,CannonRecoil.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }
    private void setup(FMLCommonSetupEvent event) {event.enqueueWork(()->ValkyrienSkiesMod.getApi().registerAttachment(RecoilController.class));}
}
