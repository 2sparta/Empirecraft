package ua.empirecraft.armor.mixin;
import java.util.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.armor.*;
@Mixin(value=Inventory.class,remap=false)
public abstract class ArmorWearMixin {
    @Unique private final Map<Integer,ItemStack> empirecraft$previous=new HashMap<>();
    @Inject(method="hurtArmor",at=@At("HEAD")) private void innerWear(DamageSource source,float damage,int[] slots,CallbackInfo ci) {
        empirecraft$previous.clear();if(damage<=0)return;
        Player player=((Inventory)(Object)this).player;
        Inventory inventory=(Inventory)(Object)this;
        for(int slot:slots) {
            var stack=inventory.armor.get(slot);
            if(LayerData.layered(stack)) {
                ArmorWear.damageInner(player,stack,source,damage);
                empirecraft$previous.put(slot,stack.copy());
            }
        }
    }
    @Inject(method="hurtArmor",at=@At("RETURN")) private void retainInner(DamageSource source,float damage,int[] slots,CallbackInfo ci) {
        Inventory inventory=(Inventory)(Object)this;
        for(var entry:empirecraft$previous.entrySet())if(inventory.armor.get(entry.getKey()).isEmpty())inventory.armor.set(entry.getKey(),ArmorWear.afterOuterBreak(entry.getValue()));
        empirecraft$previous.clear();
    }
}
