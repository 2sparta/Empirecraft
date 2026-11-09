package ua.empirecraft.armor.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.armor.LayerData;

/** Draw each piece through vanilla/Forge's own armor model and texture hooks, inside first. */
@Mixin(value=HumanoidArmorLayer.class,remap=false)
public abstract class InnerArmorRenderMixin {
    @Unique private static final ThreadLocal<ItemStack> empirecraft$rendering=new ThreadLocal<>();
    @Invoker("m_117118_") protected abstract void empirecraft$piece(PoseStack pose,MultiBufferSource buffers,LivingEntity entity,EquipmentSlot slot,int light,HumanoidModel<?> model);
    @Inject(method="m_117118_",at=@At("HEAD"),cancellable=true)
    private void allPieces(PoseStack pose,MultiBufferSource buffers,LivingEntity entity,EquipmentSlot slot,int light,HumanoidModel<?> model,CallbackInfo ci) {
        if(empirecraft$rendering.get()!=null)return;
        var worn=entity.getItemBySlot(slot);if(!LayerData.layered(worn))return;
        try {for(var item:LayerData.pieces(worn)){empirecraft$rendering.set(item);empirecraft$piece(pose,buffers,entity,slot,light,model);}}
        finally{empirecraft$rendering.remove();}
        ci.cancel();
    }
    @Redirect(method="m_117118_",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack currentPiece(LivingEntity entity,EquipmentSlot slot){var active=empirecraft$rendering.get();return active==null?entity.getItemBySlot(slot):active;}
}
