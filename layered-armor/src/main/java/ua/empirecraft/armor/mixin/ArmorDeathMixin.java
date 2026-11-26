package ua.empirecraft.armor.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.item.ItemEntity;
import ua.empirecraft.armor.LayerData;
@Mixin(value=Player.class,remap=false)
public abstract class ArmorDeathMixin {
    @Inject(remap=false,method="drop",at=@At("HEAD"),cancellable=true) private void individualDrops(CallbackInfoReturnable<ItemEntity> cir) {
        Player player=(Player)(Object)this;
        if(player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))return;
        for(var slot:LayerData.SLOTS) {
            var worn=player.getItemBySlot(slot);if(!LayerData.layered(worn))continue;
            var all=LayerData.pieces(worn);player.setItemSlot(slot,all.remove(all.size()-1));
            for(var inner:all)if(!EnchantmentHelper.hasVanishingCurse(inner))player.drop(inner,true,false);
        }
    }
}
