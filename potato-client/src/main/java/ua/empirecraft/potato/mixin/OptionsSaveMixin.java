package ua.empirecraft.potato.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ua.empirecraft.potato.OriginalOptions;

@Mixin(Options.class)
public abstract class OptionsSaveMixin {
    @WrapMethod(method={"save", "m_92169_"}, remap=false)
    private void potato$saveOriginalPreferences(Operation<Void> original) {
        try (var ignored = OriginalOptions.read()) { original.call(); }
    }
}
