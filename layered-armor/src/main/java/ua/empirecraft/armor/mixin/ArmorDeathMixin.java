package ua.empirecraft.armor.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.armor.LayerData;
@Mixin(value=Player.class,remap=false)
public abstract class ArmorDeathMixin {
    @Inject(method="m_5907_",at=@At("HEAD")) private void individualDrops(CallbackInfo ci) {
        Player player=(Player)(Object)this;
        if(player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))return;
        for(var slot:LayerData.SLOTS) {
            var worn=player.getItemBySlot(slot);if(!LayerData.layered(worn))continue;
            var all=LayerData.pieces(worn);player.setItemSlot(slot,all.remove(all.size()-1));
            for(var inner:all)if(!EnchantmentHelper.hasVanishingCurse(inner))player.drop(inner,true,false);
        }
    }
}
