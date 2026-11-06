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
    public static boolean ammunition(AbstractRecruitEntity r) {
        boolean gun = GunGoal.gun(r.m_21205_()); String id = gun ? GunGoal.ammo(r.m_21205_()) : "";
        for (int i = 6; i < r.getInventory().m_6643_(); i++) {
            ItemStack s = r.getInventory().m_8020_(i);
            if (s.m_41619_()) continue;
            if (!gun && s.m_204117_(net.minecraft.tags.ItemTags.f_13161_)) return true;
            if (gun && id.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(s.m_41720_())))) return true;
        }
        return false;
    }
}
