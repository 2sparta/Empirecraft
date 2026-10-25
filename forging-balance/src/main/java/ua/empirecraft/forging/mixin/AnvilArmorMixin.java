package ua.empirecraft.forging.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.stirdrem.overgeared.ForgingQuality;
import net.stirdrem.overgeared.config.ServerConfig;
import net.stirdrem.overgeared.block.custom.AbstractSmithingAnvil;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;
import net.stirdrem.overgeared.recipe.ForgingRecipe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.forging.*;

@Mixin(value = AbstractSmithingAnvilBlockEntity.class, remap = false)
public abstract class AnvilArmorMixin implements ArmorSession {
    @Shadow protected ItemStackHandler itemHandler;
    @Unique private ArmorRules.HitReport empirecraft$hits = new ArmorRules.HitReport(0, 0, 0);
    @Unique private String empirecraft$quality;

    @Unique private AbstractSmithingAnvilBlockEntity empirecraft$anvil() { return (AbstractSmithingAnvilBlockEntity)(Object)this; }
    @Unique private ArmorRules.Game empirecraft$game() {
        var anvil = empirecraft$anvil();
        Level level = anvil.m_58904_();
        var recipe = anvil.getCurrentRecipe();
        return level == null || recipe.isEmpty() ? null : ArmorGraph.game(level, recipe.get(), itemHandler.getStackInSlot(11));
    }

    @Override public boolean empirecraft$report(ArmorRules.HitReport report) {
        var anvil = empirecraft$anvil();
        if (empirecraft$game() == null || report == null || !report.follows(empirecraft$hits) || report.total() > anvil.getRequiredProgress()) return false;
        // Native use packets advance the anvil after the hit report, never accept a completed game early.
        if (report.total() > anvil.getProgress() + 1) return false;
        empirecraft$hits = report;
        return true;
    }

    @Inject(method = "minigameQuality", at = @At("HEAD"), cancellable = true, require = 1)
    private void empirecraft$sendDifficulty(CallbackInfoReturnable<String> cir) {
        ArmorRules.Game game = empirecraft$game();
        if (game != null) cir.setReturnValue(game.encode());
    }

    @Inject(method = "craftItem", at = @At("HEAD"), require = 1)
    private void empirecraft$captureQuality(CallbackInfo ci) {
        var game = empirecraft$game();
        empirecraft$quality = game == null ? null : empirecraft$hits.total() == empirecraft$anvil().getRequiredProgress()
            ? ArmorRules.result(game.tier(), game.blueprint(), empirecraft$hits.missed()) : "poor";
        if (empirecraft$quality != null) {
            // Research uses hit accuracy, independently of the current blueprint's output cap.
            // Otherwise a starting Well blueprint could never earn Expert-level research.
            String accuracy = ArmorRules.accuracy(empirecraft$hits, ServerConfig.PERFECT_QUALITY_SCORE.get(), ServerConfig.EXPERT_QUALITY_SCORE.get(), ServerConfig.WELL_QUALITY_SCORE.get());
            if (empirecraft$hits.missed() > 0) accuracy = ArmorRules.cap(accuracy, empirecraft$quality);
            AbstractSmithingAnvil.setQuality(accuracy);
        }
    }

    @Inject(method = "determineForgingQuality", at = @At("HEAD"), cancellable = true, require = 1)
    private void empirecraft$qualityFromHits(CallbackInfoReturnable<String> cir) {
        if (empirecraft$quality != null) cir.setReturnValue(empirecraft$quality);
    }

    @Inject(method = "rollFailure", at = @At("HEAD"), cancellable = true, require = 1)
    private void empirecraft$keepPoorArmor(ForgingQuality quality, CallbackInfoReturnable<Boolean> cir) {
        if (empirecraft$quality != null) cir.setReturnValue(false);
    }

    @Inject(method = "transferIngredientNBT", at = @At("RETURN"), require = 1)
    private void empirecraft$finalQuality(ItemStack result, ForgingRecipe recipe, CallbackInfo ci) {
        // Run after minimum-quality clamps, ingredient bonuses, the random Master roll and NBT transfer.
        if (empirecraft$quality != null) result.m_41784_().m_128359_("ForgingQuality", empirecraft$quality);
    }

    @Inject(method = "resetProgress", at = @At("HEAD"), require = 1)
    private void empirecraft$clearSession(CallbackInfo ci) {
        empirecraft$hits = new ArmorRules.HitReport(0, 0, 0);
        empirecraft$quality = null;
    }
}
