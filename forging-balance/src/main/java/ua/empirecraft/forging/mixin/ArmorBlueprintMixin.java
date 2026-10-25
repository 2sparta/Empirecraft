package ua.empirecraft.forging.mixin;

import java.util.*;
import net.minecraft.world.item.*;
import net.stirdrem.overgeared.recipe.ForgingRecipe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgingRecipe.class, remap = false)
public abstract class ArmorBlueprintMixin {
    @Shadow @Final private ItemStack result;
    @Shadow @Final private boolean hasQuality;
    @Shadow @Final @Mutable private Set<String> blueprintTypes;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void empirecraft$armorBlueprints(CallbackInfo ci) {
        if (!hasQuality || !(result.m_41720_() instanceof ArmorItem armor)) return;
        Set<String> types = new HashSet<>(blueprintTypes);
        types.add("armor"); // Existing armor blueprints remain usable.
        types.add(switch (armor.m_40402_()) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> "armor";
        });
        blueprintTypes = types;
    }
}
