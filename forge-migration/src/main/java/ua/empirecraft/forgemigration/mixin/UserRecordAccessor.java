package ua.empirecraft.forgemigration.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "ru.iwoss.serverlogin.auth.UserDatabase$UserRecord", remap = false)
public interface UserRecordAccessor {
    @Accessor("lastKnownName")
    String empirecraft$lastKnownName();
}
