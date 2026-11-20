package ua.empirecraft.launcherclient.mixin;
import com.quickskin.mod.client.rendering.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import ua.empirecraft.launcherclient.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerModelRenderer.class,remap=false)
public abstract class HomeArmorPreview {
 @Invoker("renderPlayerModelManual") public static void empirecraft$manual(GuiGraphics g,int x,int y,float scale,float rotation,PreviewPlayerData data,int mx,int my,boolean follow){throw new AssertionError();}
 @Inject(method="renderPlayerModel(Lnet/minecraft/client/gui/GuiGraphics;IIFFLcom/quickskin/mod/client/rendering/PreviewPlayerData;IIZ)V",at=@At("HEAD"),cancellable=true)
 private static void empirecraft$home(GuiGraphics g,int x,int y,float scale,float rotation,PreviewPlayerData data,int mx,int my,boolean follow,CallbackInfo ci){if(HomePortrait.active()){empirecraft$manual(g,x,y,scale,rotation,data,mx,my,follow);ci.cancel();}}
 @Redirect(method="renderPlayerModelManual",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/PlayerModel;m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
 private static void empirecraft$armor(PlayerModel<?> model,PoseStack pose,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a){model.renderToBuffer(pose,vertices,light,overlay,r,g,b,a);HomeArmor.render(model,pose);}
}
