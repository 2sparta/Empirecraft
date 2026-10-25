package com.quintonc.vs_sails.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.quintonc.vs_sails.entity.AnchorEntities;
import com.quintonc.vs_sails.entity.AnchorVisualEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = "vs_sails", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AnchorVisualRenderer extends EntityRenderer<AnchorVisualEntity> {
    private final BlockRenderDispatcher blocks;
    public AnchorVisualRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AnchorEntities.TYPE, AnchorVisualRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(AnchorVisualEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public boolean shouldRender(AnchorVisualEntity entity, Frustum frustum, double x, double y, double z) {
        Vec3 end = entity.position().add(entity.anchorOffset(1));
        AABB bounds = new AABB(entity.position(), end).inflate(2);
        return entity.shouldRender(x, y, z) && frustum.isVisible(bounds);
    }
    @Override public void render(AnchorVisualEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 bottom = entity.anchorOffset(partialTick);
        if (!Double.isFinite(bottom.lengthSqr()) || bottom.lengthSqr() > 96 * 96) return;
        Vec3 chain = bottom.add(0, 1.75, 0);
        double length = chain.length();
        if (length > .05 && chain.y < 0) {
            Vec3 direction = chain.scale(1 / length);
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
            int segments = Math.min(96, (int) Math.ceil(length));
            for (int i = 0; i < segments; i++) {
                double size = Math.min(1, length - i);
                Vec3 center = direction.scale(i + size * .5);
                pose.pushPose(); pose.translate(center.x, center.y, center.z); pose.mulPose(rotation);
                pose.scale(1, (float) size, 1); pose.translate(-.5, -.5, -.5);
                blocks.renderSingleBlock(Blocks.CHAIN.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.pushPose(); pose.translate(bottom.x - .5, bottom.y, bottom.z - .5);
        blocks.renderSingleBlock(entity.anchorState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
