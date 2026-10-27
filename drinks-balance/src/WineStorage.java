package net.satisfy.vinery.core.util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.satisfy.vinery.core.item.DrinkBlockItem;
public final class WineStorage {
    public static void touch(ItemStack stack, Level level) {
        if (stack.m_41720_() instanceof DrinkBlockItem) WineYears.setWineYear(stack,level);
    }
}
