package ua.empirecraft.interactions.mixin.client;

import com.magistuarmory.block.PaviseBlock;
import com.magistuarmory.block.PaviseBlockEntity;
import com.magistuarmory.block.PaviseUpperCollisionBlock;
import com.magistuarmory.client.render.tileentity.PaviseBlockRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.math.Axis;
import java.util.SortedSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PaviseBlockRenderer.class, remap = false)
public abstract class PaviseCrackRenderMixin {
    @Shadow ModelPart[] plate;

    @Inject(method = "render(Lcom/magistuarmory/block/PaviseBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("TAIL"))
    private void empirecraft$renderCracks(PaviseBlockEntity pavise, float partialTick, PoseStack poses,
                                         MultiBufferSource buffers, int light, int overlay, CallbackInfo callback) {
        var minecraft = Minecraft.getInstance();
        var stages = ((PaviseDamageStages) minecraft.levelRenderer).empirecraft$damageStages();
        var pos = pavise.getBlockPos();
        int stage = empirecraft$stage(stages.get(pos.asLong()));
        if (pavise.getLevel() != null && pavise.getLevel().getBlockState(pos.above()).getBlock() instanceof PaviseUpperCollisionBlock) {
            stage = Math.max(stage, empirecraft$stage(stages.get(pos.above().asLong())));
        }
        if (stage < 0 || stage >= ModelBakery.DESTROY_TYPES.size()) return;
        var base = poses.last();
        var buffer = minecraft.renderBuffers().crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(stage));
        var decal = new SheetedDecalTextureGenerator(buffer, base.pose(), base.normal(), 1.0f);
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(-22.5f * pavise.getBlockState().getValue(PaviseBlock.ROTATION)));
        poses.scale(1, -1, -1);
        for (ModelPart part : plate) part.render(poses, decal, light, OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }

    @Unique
    private static int empirecraft$stage(SortedSet<BlockDestructionProgress> stages) {
        return stages == null || stages.isEmpty() ? -1 : stages.last().getProgress();
    }
}
