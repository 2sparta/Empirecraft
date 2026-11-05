package ua.empirecraft.potato.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ua.empirecraft.potato.*;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin<T> implements OptionKey {
    @Unique private String potato$key;
    @Unique private boolean potato$managed;
    // All constructors take the translation key as their first String. No field-name guessing.
    @ModifyVariable(method="<init>*", at=@At("TAIL"), ordinal=0, argsOnly=true, remap=false)
    private String potato$rememberKey(String key) {
        potato$key = key; potato$managed = key != null && RenderPolicy.KEYS.contains(key); return key;
    }
    @Override public String potato$key() { return potato$key; }
    // The two names are the actual official-development and production SRG names for 1.20.1.
    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method={"get", "m_231551_"}, at=@At("RETURN"), remap=false)
    private T potato$renderValue(T original) {
        if (!potato$managed || OriginalOptions.active()) return original;
        return (T)RenderPolicy.value(potato$key, original, PotatoState.enabled());
    }
}
