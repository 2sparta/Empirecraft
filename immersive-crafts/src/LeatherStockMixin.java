package ua.empirecraft.immersivecrafts.mixin;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.registry.ModItems;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LeatherData.class, remap = false)
public abstract class LeatherStockMixin {
    private static final TagKey<Item> PACK_LEATHER = TagKey.create(
            Registries.ITEM, new ResourceLocation("empirecraft_immersive", "finished_leather"));

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void empirecraft$recognizeFinishedLeather(
            ItemStack stack, CallbackInfoReturnable<Optional<LeatherData>> callback) {
        if (callback.getReturnValue().isEmpty() && stack.is(PACK_LEATHER)
                && !stack.is(ModItems.BINDING_LEATHER.get())
                && !stack.is(ModItems.LEATHER_SCRAPS.get())) {
            callback.setReturnValue(Optional.of(new LeatherData(Finish.HARNESS, 50, null)));
        }
    }
}
