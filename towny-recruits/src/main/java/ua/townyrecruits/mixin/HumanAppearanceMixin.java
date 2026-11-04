package ua.townyrecruits.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.townyrecruits.Appearance;
import java.util.ArrayDeque;
import java.util.Deque;

/** Client-only. Ordinary typed variable hooks require no generated Args classes. */
@Mixin(targets="com.talhanation.recruits.client.render.RecruitHumanRenderer",remap=false)
public abstract class HumanAppearanceMixin {
    @Unique private static final ResourceLocation[] townyrecruits$skins=townyrecruits$makeSkins();
    @Unique private final Deque<PoseStack> townyrecruits$poses=new ArrayDeque<>();
    @Unique private float townyrecruits$scale=1f;
    @Unique private static ResourceLocation[] townyrecruits$makeSkins(){
        ResourceLocation[] skins=new ResourceLocation[Appearance.SKIN_COUNT];
        for(int i=0;i<skins.length;i++)skins[i]=new ResourceLocation("townyrecruits","textures/entity/human/skin_"+i+".png");
        return skins;
    }
    @Inject(method="getTextureLocation(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void skin(AbstractRecruitEntity r,CallbackInfoReturnable<ResourceLocation> ci){ci.setReturnValue(townyrecruits$skins[Appearance.skin(r.m_20148_())]);}
    @ModifyVariable(method="render(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"),argsOnly=true,index=1)
    private AbstractRecruitEntity rememberEntity(AbstractRecruitEntity r){townyrecruits$scale=Appearance.renderScale(r.m_20148_());return r;}
    @ModifyVariable(method="render(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="LOAD",ordinal=0),argsOnly=true,index=4)
    private PoseStack scalePose(PoseStack pose){
        // Applied at the first pose argument load, after entity capture and immediately
        // before the native renderer call. This also avoids per-frame argument arrays.
        pose.m_85836_();townyrecruits$poses.push(pose);pose.m_85841_(1f,townyrecruits$scale,1f);return pose;
    }
    @Inject(method="render(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void restore(CallbackInfo ci){if(!townyrecruits$poses.isEmpty())townyrecruits$poses.pop().m_85849_();}
}
