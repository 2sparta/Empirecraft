package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import ua.townyrecruits.GunGoal;

public final class TacticalEquipment {
    private static final TagKey<Item> PIKES = TagKey.m_203882_(Registries.f_256913_, new ResourceLocation("townyrecruits", "pikes"));
    private TacticalEquipment() {}
    public static boolean pike(AbstractRecruitEntity r) {
        ItemStack s = r.m_21205_();
        if (s.m_204117_(PIKES)) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.m_41720_());
        if (id == null) return false;
        return java.util.Set.of("magistuarmory", "magistuarmoryaddon", "darkagesarmory", "antiquelegacy", "slavicarmory")
            .contains(id.m_135827_()) && (id.m_135815_().endsWith("_pike") || id.m_135815_().endsWith("_spear"));
    }
    public static boolean shield(AbstractRecruitEntity r) {
        return r.m_21206_().canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK);
    }
    public static boolean ranged(AbstractRecruitEntity r) {
        ItemStack s = r.m_21205_();
        return s.m_41720_() instanceof BowItem || s.m_41720_() instanceof CrossbowItem || GunGoal.gun(s);
    }
}
