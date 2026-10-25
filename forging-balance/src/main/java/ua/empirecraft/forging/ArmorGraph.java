package ua.empirecraft.forging;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.stirdrem.overgeared.recipe.ForgingRecipe;

public final class ArmorGraph {
    private static final Map<RecipeManager, Map<Item, Integer>> CACHE = new WeakHashMap<>();
    private ArmorGraph() {}

    public static synchronized void invalidate(RecipeManager manager) { CACHE.remove(manager); }

    private static int base(ArmorItem item) {
        return ArmorRules.difficulty(item.m_40404_(), item.m_40402_().name().toLowerCase(Locale.ROOT));
    }

    public static synchronized int difficulty(Level level, Item item) {
        if (!(item instanceof ArmorItem armor)) return 0;
        return CACHE.computeIfAbsent(level.m_7465_(), manager -> build(level, manager)).getOrDefault(item, base(armor));
    }

    private static Map<Item, Integer> build(Level level, RecipeManager manager) {
        UpgradeGraph<Item> graph = new UpgradeGraph<>();
        List<ArmorItem> armorItems = ForgeRegistries.ITEMS.getValues().stream().filter(ArmorItem.class::isInstance).map(ArmorItem.class::cast).toList();
        for (Recipe<?> recipe : manager.m_44051_()) {
            Item output = recipe.m_8043_(level.m_9598_()).m_41720_();
            if (!(output instanceof ArmorItem armor)) continue;
            graph.add(output, base(armor));
            // Existing cosmetic/assembly recipes on an anvil do not run a forging game either.
            if (recipe instanceof ForgingRecipe forging && (forging.hasQuality() || forging.needsMinigame())) continue;
            // Vanilla 1.20.1 smithing recipes return EMPTY from Recipe.getIngredients().
            // Ask their actual predicates so templates, tags and modded smithing recipes work.
            if (recipe instanceof SmithingRecipe smithing) {
                for (ArmorItem source : armorItems) {
                    ItemStack stack = new ItemStack(source);
                    if (!smithing.m_266343_(stack) && !smithing.m_266253_(stack)) continue;
                    graph.add(source, base(source));
                    graph.upgrade(source, output);
                }
            }
            for (Ingredient ingredient : recipe.m_7527_()) {
                for (ItemStack candidate : ingredient.m_43908_()) {
                    if (!(candidate.m_41720_() instanceof ArmorItem source)) continue;
                    graph.add(source, base(source));
                    graph.upgrade(source, output);
                }
            }
        }
        return graph.resolve();
    }

    public static ArmorRules.Game game(Level level, ForgingRecipe recipe, ItemStack blueprint) {
        if (!recipe.hasQuality()) return null;
        Item output = recipe.m_8043_(level.m_9598_()).m_41720_();
        if (!(output instanceof ArmorItem armor)) return null;
        int tier = difficulty(level, output);
        if (tier < 7) return null;
        String quality = blueprint.m_41619_() || !blueprint.m_41782_() ? "well" : blueprint.m_41783_().m_128461_("Quality");
        return new ArmorRules.Game(tier, armor.m_40402_() != net.minecraft.world.entity.EquipmentSlot.CHEST, ArmorRules.quality(quality));
    }
}
