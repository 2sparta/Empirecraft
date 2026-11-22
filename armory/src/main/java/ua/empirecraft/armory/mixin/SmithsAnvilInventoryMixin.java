package ua.empirecraft.armory.mixin;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.armory.WorkshopEntity;
import ua.empirecraft.armory.SmithsAnvilWorkshop;

@Mixin(value = SmithsAnvilBlockEntity.class, remap = false)
public abstract class SmithsAnvilInventoryMixin implements SmithsAnvilWorkshop {
    @Unique private WorkshopEntity empirecraft$assembly;

    @Override
    public WorkshopEntity empirecraft$workshop() {
        var anvil = (SmithsAnvilBlockEntity) (Object) this;
        if (empirecraft$assembly == null) {
            empirecraft$assembly = new WorkshopEntity(anvil);
        }
        if (anvil.getLevel() != null && empirecraft$assembly.getLevel() != anvil.getLevel()) {
            empirecraft$assembly.setLevel(anvil.getLevel());
        }
        return empirecraft$assembly;
    }

    @Override
    public WorkshopEntity empirecraft$existingWorkshop() {
        return empirecraft$assembly;
    }

    @Inject(method = "m_142466_", at = @At("TAIL"))
    private void empirecraft$load(CompoundTag tag, CallbackInfo info) {
        if (tag.contains("EmpirecraftAssembly", Tag.TAG_COMPOUND)) {
            empirecraft$workshop().load(tag.getCompound("EmpirecraftAssembly"));
        }
    }

    @Inject(method = "m_183515_", at = @At("TAIL"))
    private void empirecraft$save(CompoundTag tag, CallbackInfo info) {
        if (empirecraft$assembly != null) {
            tag.put("EmpirecraftAssembly", empirecraft$assembly.saveAssembly());
        }
    }

    @Inject(method = "m_5995_", at = @At("RETURN"))
    private void empirecraft$omitInventory(CallbackInfoReturnable<CompoundTag> info) {
        info.getReturnValue().remove("EmpirecraftAssembly");
    }

    @Inject(method = "dropWorkpiece", at = @At("TAIL"))
    private void empirecraft$dropAssembly(CallbackInfo info) {
        if (empirecraft$assembly != null) {
            empirecraft$workshop().dropContents();
        }
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void empirecraft$tick(Level level, BlockPos pos, BlockState state, SmithsAnvilBlockEntity anvil, CallbackInfo info) {
        var workshop = ((SmithsAnvilWorkshop) anvil).empirecraft$existingWorkshop();
        if (workshop != null) {
            workshop.setLevel(level);
            WorkshopEntity.tick(level, pos, state, workshop);
        }
    }
}
