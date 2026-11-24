package ua.empirecraft.forgemigration.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.forgemigration.LegacyPassword;

@Mixin(targets = "ru.iwoss.serverlogin.auth.PasswordHasher", remap = false)
abstract class PasswordHasherMixin {
    @Inject(method = "verify", at = @At("HEAD"), cancellable = true)
    private static void empirecraft$verify(char[] password, String salt, String hash, int iterations,
                                          CallbackInfoReturnable<Boolean> callback) {
        if (salt != null && salt.startsWith(LegacyPassword.PREFIX)) {
            callback.setReturnValue(LegacyPassword.verify(password, salt, hash, iterations));
        }
    }
}
