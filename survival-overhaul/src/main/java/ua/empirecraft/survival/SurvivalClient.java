package ua.empirecraft.survival;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import sfiomn.legendarysurvivaloverhaul.client.render.*;
import sfiomn.legendarysurvivaloverhaul.client.integration.sereneseasons.RenderSeasonCards;
import sfiomn.legendarysurvivaloverhaul.registry.KeyMappingRegistry;

/** The vanilla heart overlay is neither replaced nor cancelled. */
public final class SurvivalClient {
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.FOOD_LEVEL.id(),"cold_hunger",RenderTemperatureGui.FOOD_BAR_COLD_EFFECT_GUI);
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(),"thirst",RenderThirstGui.THIRST_GUI);
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(),"temperature",RenderTemperatureGui.TEMPERATURE_GUI);
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(),"wetness",RenderWetnessGui.WETNESS_GUI);
        event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(),"temperature_overlay",RenderTemperatureOverlay.TEMPERATURE_OVERLAY);
        event.registerAbove(VanillaGuiOverlay.ITEM_NAME.id(),"item_frame_tooltip",RenderTooltipFrame.TOOLTIP_ITEM_FRAME);
        event.registerAbove(VanillaGuiOverlay.ITEM_NAME.id(),"season_card",RenderSeasonCards.SEASON_CARD_GUI);
    }
    public static void keys(RegisterKeyMappingsEvent event) {
        KeyMappingRegistry.showAddedDesc=new KeyMapping("key.legendarysurvivaloverhaul.added_desc",KeyConflictContext.GUI,InputConstants.Type.KEYSYM,340,"key.legendarysurvivaloverhaul.title");
        event.register(KeyMappingRegistry.showAddedDesc);
    }
}
