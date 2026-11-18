package ua.empirecraft.interactions;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("empirecraft_interactions")
public final class GameplayInteractions {
    public GameplayInteractions() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::disabledInteraction);
        MinecraftForge.EVENT_BUS.register(new AnimalThreats());
        MinecraftForge.EVENT_BUS.addListener(TableAccess::commands);
        MinecraftForge.EVENT_BUS.addListener(AnimalThreats::commands);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) { event.enqueueWork(() -> { MaterialBalance.load(); DisabledAlexBlocks.load(); }); }

    private void disabledInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (DisabledAlexBlocks.contains(event.getLevel().getBlockState(event.getPos()))) event.setCanceled(true);
    }
}
