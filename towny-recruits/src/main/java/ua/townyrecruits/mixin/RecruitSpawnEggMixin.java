package ua.townyrecruits.mixin;

import com.talhanation.recruits.items.RecruitsSpawnEgg;
import java.util.function.Supplier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A custom name/quality tag does not turn a normal egg into a serialized soldier. */
@Mixin(value=RecruitsSpawnEgg.class,remap=false)
public abstract class RecruitSpawnEggMixin extends ForgeSpawnEggItem {
    protected RecruitSpawnEggMixin(Supplier<? extends EntityType<? extends Mob>> type, int primary, int secondary, Item.Properties properties) {
        super(type,primary,secondary,properties);
    }

    @Inject(method="m_6225_",at=@At("HEAD"),cancellable=true)
    private void ordinaryEgg(UseOnContext context, CallbackInfoReturnable<InteractionResult> ci) {
        var tag=context.m_43722_().m_41783_();
        if(tag==null || tag.m_128456_())return;
        var saved=tag.m_128469_("EntityTag");
        // MessageWriteSpawnEgg writes all three lists. Vanilla EntityTag data
        // (e.g. CustomName) must keep vanilla initialization and positioning.
        if(!saved.m_128425_("Items",9) || !saved.m_128425_("ArmorItems",9) || !saved.m_128425_("HandItems",9))
            ci.setReturnValue(super.m_6225_(context));
    }
}
