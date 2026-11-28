package ua.empirecraft.provenance;

import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Provenance.ID)
public final class Provenance {
    public static final String ID = "empirecraft_provenance";
    private static final Logger LOGGER = LoggerFactory.getLogger("MakersMarks");

    public Provenance() {
        LOGGER.info("Makers' marks active: crafting signs tools, armour and clothing; "
                + "cloth and metal batches record their place of production");
    }

    @Mod.EventBusSubscriber(modid = ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        @SubscribeEvent
        public static void crafted(PlayerEvent.ItemCraftedEvent event) {
            Mark.stamp(event.getEntity(), event.getCrafting());
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            event.getToolTip().addAll(Mark.describe(event.getItemStack()));
        }
    }
}
