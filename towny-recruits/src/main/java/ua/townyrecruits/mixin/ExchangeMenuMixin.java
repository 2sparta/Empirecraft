package ua.townyrecruits.mixin;

import com.talhanation.recruits.inventory.RecruitInventoryMenu;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import ua.townyrecruits.ExchangeGuards;

@Mixin(value=RecruitInventoryMenu.class,remap=false)
public abstract class ExchangeMenuMixin {
    @Shadow public abstract AbstractRecruitEntity getRecruit();
    /** Override the inherited distance-only validity check, including menus open before a sale lock. */
    public boolean m_6875_(Player player){var r=getRecruit();return r!=null&&r.m_6084_()&&player.m_20280_(r)<=64&&ExchangeGuards.controls(r,player);}
}
