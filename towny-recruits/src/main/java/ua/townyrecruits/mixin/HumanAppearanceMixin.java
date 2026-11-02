package ua.townyrecruits.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ua.townyrecruits.Appearance;
import java.util.ArrayDeque;
import java.util.Deque;

/** Client-only; preserves the native human renderer, armour, equipment and animation layers. */
@Mixin(targets="com.talhanation.recruits.client.render.RecruitHumanRenderer",remap=false)
public abstract class HumanAppearanceMixin {
    @Unique private static final ResourceLocation[] townyrecruits$skins=townyrecruits$makeSkins();
    @Unique private final Deque<PoseStack> townyrecruits$poses=new ArrayDeque<>();
    @Unique private static ResourceLocation[] townyrecruits$makeSkins(){
        ResourceLocation[] skins=new ResourceLocation[Appearance.SKIN_COUNT];
        for(int i=0;i<skins.length;i++)skins[i]=new ResourceLocation("townyrecruits","textures/entity/human/skin_"+i+".png");
        return skins;
    }
    @Inject(method="getTextureLocation(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void skin(AbstractRecruitEntity r,CallbackInfoReturnable<ResourceLocation> ci){ci.setReturnValue(townyrecruits$skins[Appearance.skin(r.m_20148_())]);}
    @ModifyArgs(method="render(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/MobRenderer;m_7392_(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void scale(Args args){
        AbstractRecruitEntity r=args.get(0);PoseStack pose=args.get(3);
        pose.m_85836_();townyrecruits$poses.push(pose);
        pose.m_85841_(1f,Appearance.renderScale(r.m_20148_()),1f);
    }
    @Inject(method="render(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void restore(CallbackInfo ci){if(!townyrecruits$poses.isEmpty())townyrecruits$poses.pop().m_85849_();}
}
