package ua.empirecraft.townymap.contact.mixin;

import java.util.UUID;
import ua.empirecraft.townymap.contact.ContactMail;
import ua.empirecraft.townymap.contact.FairMailboxData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.flechazo.contact.common.storage.PlayerMailboxData",remap=false)
public abstract class ContactMailboxDataMixin implements FairMailboxData {
    @Unique private CompoundTag empirecraft$fairMail=new CompoundTag();
    @Override public CompoundTag empirecraft$fairMail(){return empirecraft$fairMail;}
    @Inject(method="writeToNBT",at=@At("RETURN"))
    private void empirecraft$save(CompoundTag tag,CallbackInfoReturnable<CompoundTag> callback){callback.getReturnValue().put(ContactMail.KEY,empirecraft$fairMail.copy());}
    @Inject(method="readFromNBT",at=@At("RETURN"))
    private void empirecraft$load(CompoundTag tag,CallbackInfo callback){empirecraft$fairMail=tag.getCompound(ContactMail.KEY).copy();}
    @Inject(method="setMailboxData",at=@At("RETURN"))
    private void empirecraft$binding(UUID owner,ResourceKey<Level> level,BlockPos pos,CallbackInfo callback){ContactMail.bindingChanged(this,owner);}
}
