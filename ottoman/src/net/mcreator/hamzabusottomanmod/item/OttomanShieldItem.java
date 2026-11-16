package net.mcreator.hamzabusottomanmod.item;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
/** Ordinary directional blocking; the integration removes shield-induced knockback. */
public class OttomanShieldItem extends ShieldItem {
    public OttomanShieldItem(){super(new Item.Properties().durability(95).rarity(Rarity.COMMON));}
    @Override public boolean isValidRepairItem(ItemStack shield,ItemStack ingredient){return ingredient.getItem()==ForgeRegistries.ITEMS.getValue(new ResourceLocation("overgeared:steel_ingot"));}
}
