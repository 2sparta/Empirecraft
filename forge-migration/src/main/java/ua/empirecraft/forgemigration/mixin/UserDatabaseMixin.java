package ua.empirecraft.forgemigration.mixin;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.forgemigration.LoginMigration;

@Mixin(targets = "ru.iwoss.serverlogin.auth.UserDatabase", remap = false)
abstract class UserDatabaseMixin {
    @Shadow @Final private Map<String, Object> users;

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private void empirecraft$register(String name, char[] password, int iterations,
                                     CallbackInfoReturnable<CompletableFuture<Boolean>> callback) {
        if (LoginMigration.registrationBlocked(name)) {
            empirecraft$reject(callback, password);
        }
    }

    @Inject(method = "authenticate", at = @At("HEAD"), cancellable = true)
    private void empirecraft$authenticate(String name, char[] primary, char[] fallback, int iterations,
                                         CallbackInfoReturnable<CompletableFuture<Boolean>> callback) {
        if (empirecraft$wrongCase(name)) {
            empirecraft$reject(callback, primary, fallback);
        }
    }

    @Inject(method = "verify", at = @At("HEAD"), cancellable = true)
    private void empirecraft$verify(String name, char[] primary, char[] fallback,
                                   CallbackInfoReturnable<CompletableFuture<Boolean>> callback) {
        if (empirecraft$wrongCase(name)) {
            empirecraft$reject(callback, primary, fallback);
        }
    }

    @Unique
    private boolean empirecraft$wrongCase(String name) {
        Object record = users.get(name.toLowerCase(Locale.ROOT));
        return record != null && !name.equals(((UserRecordAccessor) record).empirecraft$lastKnownName());
    }

    @Unique
    private static void empirecraft$reject(CallbackInfoReturnable<CompletableFuture<Boolean>> callback,
                                         char[]... secrets) {
        for (char[] secret : secrets) {
            if (secret != null) Arrays.fill(secret, '\0');
        }
        callback.setReturnValue(CompletableFuture.completedFuture(false));
    }
}
