package ua.empirecraft.trmt;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="trmt",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TrailColors {
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block e) {
        for (RememberedPath path : RegionalTrails.PATHS.values()) {
            if (!path.kind.equals("grass")) continue;
            e.register((state, level, pos, index) -> e.getBlockColors().getColor(path.original.defaultBlockState(), level, pos, index), path);
        }
    }
}
