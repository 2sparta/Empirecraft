package ua.empirecraft.packfixes.mixin.client;

import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.packfixes.PonderSafety;

@Mixin(targets="com.simibubi.create.foundation.item.ItemDescription$Modifier",remap=false)
public abstract class CreateDescriptionSafetyMixin {
    @Shadow @Final protected Item item;
    @Inject(method="modify",at=@At("HEAD"),cancellable=true)
    private void empirecraft$availableDescriptions(ItemTooltipEvent event,CallbackInfo callback) {
        var id=ForgeRegistries.ITEMS.getKey(item);
        if(id!=null&&!PonderSafety.descriptionAllowed(id.toString()))callback.cancel();
    }
}
