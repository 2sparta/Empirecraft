package ua.empirecraft.equine.mixin;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.equine.LegacyMigration;
@Mixin(value=EntityType.class,remap=false)
public abstract class EntityLoadMixin {
    @Inject(method="m_20642_",at=@At("HEAD"))
    private static void migrate(CompoundTag tag,Level level,CallbackInfoReturnable<Optional<Entity>> ci){LegacyMigration.convert(tag);}
}
