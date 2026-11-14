package ua.empirecraft.packfixes.mixin.client;

import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.packfixes.PonderSafety;

@Mixin(value=PonderSceneRegistry.class,remap=false)
public abstract class PonderSceneSafetyMixin {
    @Inject(method="addStoryBoard",at=@At("HEAD"),cancellable=true)
    private void empirecraft$availableScenes(StoryBoardEntry entry,CallbackInfo callback) {
        if(!PonderSafety.sceneAllowed(entry.getComponent().toString(),entry.getSchematicLocation().toString()))callback.cancel();
    }
}
