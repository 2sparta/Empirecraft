package ua.empirecraft.forging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Set strength is measured before forging quality, independently for each worn item. */
public final class ArmorBalance {
    private ArmorBalance() {}

    public static double bonus(int chestDefense) {
        return chestDefense >= 9 ? 4 : chestDefense == 8 ? 2 : chestDefense == 7 ? 1.5 : 1;
    }

    public static int chestDefense(ArmorItem item) {
        int defense = item.m_40401_().m_7366_(ArmorItem.Type.CHESTPLATE);
        if (defense > 0 || item.m_40402_() != EquipmentSlot.HEAD) return defense;
        // Epic Knights registers historical helmets as separate materials whose
        // chest defense is zero. Associate these with their actual plate sets.
        var id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return 0;
        String[] parts = id.toString().split(":", 2);
        if (!java.util.Set.of("magistuarmory", "magistuarmoryaddon").contains(parts[0])) return 0;
        String name = parts[1];
        if (name.contains("maximilian") || name.contains("stechhelm") || name.contains("jousting")) return 9;
        if (name.contains("bascinet") && !name.contains("grand")) return 7;
        if (name.contains("armet") || name.contains("sallet") || name.contains("grand_bascinet")
                || name.contains("close_helmet") || name.contains("grilled_helmet")) return 8;
        return 0;
    }

    public static void apply(ItemAttributeModifierEvent event, ArmorItem item) {
        if (event.getSlotType() != item.m_40402_() || item.m_40404_() <= 0
                || !event.getModifiers().containsKey(Attributes.f_22284_)) return;
        UUID id = UUID.nameUUIDFromBytes(("empirecraft.base-armor." + event.getSlotType().name()).getBytes(StandardCharsets.UTF_8));
        event.addModifier(Attributes.f_22284_, new AttributeModifier(id, "Empirecraft base armor", bonus(chestDefense(item)), AttributeModifier.Operation.ADDITION));
    }
}
