package ua.empirecraft.worldrules.mixin;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=UseItemGoal.class,remap=false)
public interface UseItemGoalAccess {
    @Accessor("f_25968_") ItemStack empirecraft$useItem();
}
