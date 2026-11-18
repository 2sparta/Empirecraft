package ua.empirecraft.interactions.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Mob.class, remap = false)
public interface MobGoalAccess {
    @Accessor("f_21345_")
    GoalSelector empirecraft$goals();
}
