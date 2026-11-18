package ua.empirecraft.armory;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

final class ArmorProvenance {
    static final String KEY="provenance";

    private ArmorProvenance() {}

    static CompoundTag capture(Player player, BlockPos bench, String metal) {
        var data=new CompoundTag();
        data.putString("metal",metal);
        data.putString("maker",player.getGameProfile().getName());
        data.putUUID("makerId",player.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            String town=town(serverPlayer,bench);
            if (!town.isBlank()) data.putString("town",town);
        }
        return data;
    }

    private static String town(ServerPlayer player, BlockPos bench) {
        try {
            var bukkit=Class.forName("org.bukkit.Bukkit");
            var manager=bukkit.getMethod("getPluginManager").invoke(null);
            var plugin=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"Towny");
            if (plugin==null || !(boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin)) return "";
            var apiType=plugin.getClass().getClassLoader().loadClass("com.palmergames.bukkit.towny.TownyAPI");
            var locationType=Class.forName("org.bukkit.Location");
            var world=player.level().getClass().getMethod("getWorld").invoke(player.level());
            var location=locationType.getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class)
                .newInstance(world,bench.getX()+0.5,bench.getY(),bench.getZ()+0.5);
            var api=apiType.getMethod("getInstance").invoke(null);
            if (api==null) return "";
            var plot=apiType.getMethod("getTownBlock",locationType).invoke(api,location);
            if (plot==null) return "";
            var town=plot.getClass().getMethod("getTownOrNull").invoke(plot);
            return town==null?"":(String)town.getClass().getMethod("getName").invoke(town);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return "";
        }
    }

    static void tooltip(ItemStack stack, List<Component> lines) {
        if (!stack.hasTag() || !stack.getTag().contains(Parts.DATA,Tag.TAG_COMPOUND)) return;
        var armor=stack.getTag().getCompound(Parts.DATA);
        var origin=armor.getCompound(KEY);
        if (origin.contains("maker",Tag.TAG_STRING)) {
            lines.add(Component.translatable("armory.origin.maker",origin.getString("maker")).withStyle(ChatFormatting.GRAY));
            var town=origin.contains("town",Tag.TAG_STRING) ? Component.literal(origin.getString("town"))
                : Component.translatable("armory.origin.wilderness");
            lines.add(Component.translatable("armory.origin.town",town).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("armory.origin.metal",
                Component.translatable("armory.origin.material."+origin.getString("metal"))).withStyle(ChatFormatting.GRAY));
        }
        if (!stack.isDamaged()) return;
        String weakest=weakestPart(armor);
        var id=ResourceLocation.tryParse(weakest);
        if (id!=null && ForgeRegistries.ITEMS.getValue(id) instanceof PartItem part) {
            lines.add(Component.translatable("armory.origin.repair",part.getDescription()).withStyle(ChatFormatting.GOLD));
        }
    }

    private static String weakestPart(CompoundTag armor) {
        var parts=armor.getList("parts",Tag.TAG_COMPOUND);
        String id="";
        int lowest=Parts.QUALITY.size();
        for (int i=0;i<parts.size();i++) {
            var record=parts.getCompound(i);
            var key=ResourceLocation.tryParse(record.getString("id"));
            if (key==null || !(ForgeRegistries.ITEMS.getValue(key) instanceof PartItem)) continue;
            int quality=Parts.QUALITY.indexOf(record.getString("quality"));
            if (quality>=0 && quality<lowest) {
                id=key.toString();
                lowest=quality;
            }
        }
        return id;
    }
}
