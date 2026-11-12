package ua.empirecraft.tfc.mixin;
import com.dragn0007.dragnlivestock.entities.chicken.OChicken;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.tfc.AnimalIntegration;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

@Mixin(value=OChicken.class,remap=false)
public abstract class ChickenMixin {
    @Inject(method="dropFertilizedEgg",at=@At("HEAD"),cancellable=true)
    private void empirecraft$nest(ServerLevel level,OChicken mate,CallbackInfo ci) {
        AnimalIntegration.layEgg((OChicken)(Object)this,mate,level);ci.cancel();
    }
    @Redirect(method="m_8107_",at=@At(value="INVOKE",target="Lcom/dragn0007/dragnlivestock/entities/chicken/OChicken;m_19998_(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"),require=2)
    private ItemEntity empirecraft$regularEgg(OChicken hen,ItemLike item){return AnimalIntegration.layRegularEgg(hen,new ItemStack(item));}
    @Redirect(method="m_8107_",at=@At(value="INVOKE",target="Lcom/dragn0007/dragnlivestock/entities/chicken/OChicken;m_19983_(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"),require=2)
    private ItemEntity empirecraft$qualityEgg(OChicken hen,ItemStack stack){return AnimalIntegration.layRegularEgg(hen,stack);}
}
