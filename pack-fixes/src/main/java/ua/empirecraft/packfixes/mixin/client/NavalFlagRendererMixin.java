package ua.empirecraft.packfixes.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.engagement.NavalFlags;
import ua.empirecraft.packfixes.client.NavalFlagWind;

@Mixin(targets="ua.empirecraft.engagement.client.FlagRenderer",remap=false)
public abstract class NavalFlagRendererMixin {
    @Redirect(method="render(Lua/empirecraft/engagement/NavalFlags$FlagEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;m_252781_(Lorg/joml/Quaternionf;)V"),require=1)
    private void empirecraft$wind(PoseStack pose,Quaternionf placement,NavalFlags.FlagEntity flag,float partial,PoseStack renderPose,MultiBufferSource buffers,int light,int overlay) {
        NavalFlagWind.rotate(pose,flag,partial);
    }
}
