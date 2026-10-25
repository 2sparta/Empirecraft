package ua.empirecraft.forging.mixin.client;

import net.stirdrem.overgeared.event.AnvilMinigameEvents;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.forging.ArmorRules;

@Mixin(value = AnvilMinigameEvents.class, remap = false)
public abstract class ArmorMinigameMixin {
    @Unique private static ArmorRules.Game empirecraft$game;

    @Inject(method = "setupForQuality", at = @At("HEAD"), cancellable = true, require = 1)
    private static void empirecraft$armorSpeed(String quality, CallbackInfo ci) {
        ArmorRules.Game game = ArmorRules.Game.decode(quality);
        if (game == null) { empirecraft$game = null; return; }
        AnvilMinigameEvents.setupForQuality(game.blueprint());
        empirecraft$game = game;
        AnvilMinigameEvents.arrowSpeed = ArmorRules.initialSpeed(game.tier(), game.smallerPiece());
        AnvilMinigameEvents.maxArrowSpeed = ArmorRules.maximumSpeed(game.tier(), game.smallerPiece());
        AnvilMinigameEvents.speedIncreasePerHit = ArmorRules.acceleration(game.tier(), game.smallerPiece()) / 2;
        // Native handleHit AND the anvil's client use handler each add this increment.
        ci.cancel();
    }

    @Inject(method = "finishForging", at = @At("RETURN"), cancellable = true, require = 1)
    private static void empirecraft$missPenalty(CallbackInfoReturnable<String> cir) {
        var game = empirecraft$game;
        if (game != null) cir.setReturnValue(ArmorRules.result(game.tier(), game.blueprint(), AnvilMinigameEvents.missedHits));
    }

    @Inject(method = "handleHit", at = @At("RETURN"), cancellable = true, require = 1)
    private static void empirecraft$reportHits(CallbackInfoReturnable<String> cir) {
        if (empirecraft$game != null) cir.setReturnValue(new ArmorRules.HitReport(AnvilMinigameEvents.perfectHits, AnvilMinigameEvents.goodHits, AnvilMinigameEvents.missedHits).encode());
    }
}
