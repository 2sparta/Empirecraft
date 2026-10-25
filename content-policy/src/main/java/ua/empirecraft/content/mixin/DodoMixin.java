package ua.empirecraft.content.mixin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.entity.mob.Dodo",remap=false)
public abstract class DodoMixin {
    @Shadow public abstract ResourceLocation getDefaultVariant();
    @ModifyVariable(method="setVariant",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private ResourceLocation empirecraft$ordinary(ResourceLocation variant){return variant!=null && variant.toString().equals("spawn:magmatic")?getDefaultVariant():variant;}
}
