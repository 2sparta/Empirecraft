package ua.townyrecruits.mixin;

import com.talhanation.recruits.client.gui.CommandScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.townyrecruits.client.ClientTactics;
import ua.townyrecruits.tactics.TacticalOrder;
import ua.townyrecruits.tactics.TacticalOrder.Mode;

@Mixin(value = CommandScreen.class, remap = false)
public abstract class TacticalScreenMixin {
    @Inject(method = "sendMovementCommandToServer", at = @At("HEAD"), cancellable = true)
    private void previewMovement(int state, CallbackInfo ci) {
        Mode mode = switch (state) {
            case 2 -> Mode.HOLD; case 3 -> Mode.RETURN; case 4, 6 -> Mode.MOVE;
            case 7 -> Mode.ADVANCE; case 8 -> Mode.RETREAT; default -> null;
        };
        if (mode == null) return;
        ClientTactics.begin((CommandScreen)(Object)this, mode, TacticalOrder.nativeFormation(CommandScreen.formation.getIndex()), CommandScreen.tightFormation);
        ci.cancel();
    }
    @Inject(method = "sendFaceCommandToServer", at = @At("HEAD"), cancellable = true)
    private void previewFace(CallbackInfo ci) {
        ClientTactics.begin((CommandScreen)(Object)this, Mode.FACE, TacticalOrder.nativeFormation(CommandScreen.formation.getIndex()), CommandScreen.tightFormation);
        ci.cancel();
    }
    @Inject(method = "sendCommandInChat", at = @At("HEAD"), cancellable = true)
    private void waitForClick(int state, CallbackInfo ci) { if (ClientTactics.active()) ci.cancel(); }
}
