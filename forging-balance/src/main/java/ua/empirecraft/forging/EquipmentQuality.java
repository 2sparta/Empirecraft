package ua.empirecraft.forging;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.event.ItemAttributeModifierEvent;


public final class EquipmentQuality {
    private EquipmentQuality() {}
    public static boolean equipment(ItemStack stack) {
        Item item = stack.m_41720_();
        return item instanceof ArmorItem || item instanceof TieredItem || item instanceof ProjectileWeaponItem
            || item.m_7167_(EquipmentSlot.MAINHAND).containsKey(Attributes.f_22281_);
    }
    public static boolean broken(ItemStack stack) {
        return stack.m_41763_() && stack.m_41773_() >= stack.m_41776_();
    }
    public static String quality(ItemStack stack) {
        var tag = stack.m_41783_();
        return tag == null ? "" : tag.m_128461_("ForgingQuality");
    }
    /** Existing Empirecraft gear and assembled armor keep their quality modifiers. */
    public static boolean apply(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!equipment(stack)) return false;
        if (broken(stack)) return true;
        boolean armor = stack.m_41720_() instanceof ArmorItem;
        Attribute attribute = armor ? Attributes.f_22284_ : Attributes.f_22281_;
        double bonus = QualityStats.bonus(quality(stack), !armor);
        // Empty slots/offhand must not gain a new armor or attack modifier.
        if (bonus != 0.0 && event.getModifiers().containsKey(attribute))
            event.addModifier(attribute, new AttributeModifier(UUID.nameUUIDFromBytes(
                ("empirecraft.quality." + (armor ? "armor." : "weapon.") + event.getSlotType().name()).getBytes(StandardCharsets.UTF_8)),
                "Empirecraft forging quality", bonus, AttributeModifier.Operation.ADDITION));
        return true;
    }
}
