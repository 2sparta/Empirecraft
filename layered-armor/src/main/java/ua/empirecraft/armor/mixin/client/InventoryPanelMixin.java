package ua.empirecraft.armor.mixin.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.armor.*;
import ua.empirecraft.armor.client.*;
@Mixin(value=InventoryScreen.class,remap=false)
public abstract class InventoryPanelMixin extends AbstractContainerScreen<InventoryMenu> {
    protected InventoryPanelMixin(InventoryMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Unique private LayerInventory empirecraft$state(){return ((LayerInventoryAccess)menu).empirecraft$layers();}
    @Inject(remap=false,method="init()V",at=@At("TAIL"))
    private void layout(CallbackInfo ci) {
        if(!empirecraft$state().expanded())return;
        int before=leftPos;
        leftPos=Math.max(LayerInventory.WIDTH+2,(width-imageWidth+LayerInventory.WIDTH)/2);
        for(var child:children())if(child instanceof AbstractWidget widget)widget.setX(widget.getX()+leftPos-before);
    }
    @Inject(remap=false,method="render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",at=@At("HEAD"))
    private void recipeBook(GuiGraphics gui,int x,int y,float partial,CallbackInfo ci) {
        var screen=(InventoryScreen)(Object)this;
        if(empirecraft$state().expanded()&&screen.getRecipeBookComponent().isVisible())LayerClient.toggle(screen,false);
    }
    @Inject(remap=false,method="renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",at=@At("TAIL"))
    private void panel(GuiGraphics gui,float partial,int x,int y,CallbackInfo ci) {
        if(!empirecraft$state().expanded())return;
        gui.fill(leftPos-LayerInventory.WIDTH,topPos-14,leftPos-3,topPos+82,0xff373737);
        gui.fill(leftPos-LayerInventory.WIDTH+1,topPos-13,leftPos-4,topPos+81,0xffc6c6c6);
        Item[] icons={Items.LEATHER_CHESTPLATE,Items.CHAINMAIL_CHESTPLATE,Items.IRON_CHESTPLATE};
        for(int rank=0;rank<3;rank++) {
            int sx=leftPos-58+rank*18;
            gui.renderItem(new ItemStack(icons[rank]),sx,topPos-12);
            for(int row=0;row<4;row++) {
                int sy=topPos+8+row*18;
                gui.fill(sx-1,sy-1,sx+17,sy+17,0xff373737);
                gui.fill(sx,sy,sx+17,sy+17,0xffffffff);
                gui.fill(sx,sy,sx+16,sy+16,0xff8b8b8b);
            }
        }
    }
    @Inject(remap=false,method="isHovering",at=@At("HEAD"),cancellable=true)
    private void inside(int left,int top,int mouseX,int mouseY,double x,double y,CallbackInfoReturnable<Boolean> cir) {
        if(empirecraft$state().expanded()&&x>=leftPos-LayerInventory.WIDTH&&x<leftPos&&y>=topPos-14&&y<topPos+82)cir.setReturnValue(false);
    }
    @Inject(remap=false,method="render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",at=@At("TAIL"))
    private void tooltip(GuiGraphics gui,int x,int y,float partial,CallbackInfo ci) {
        if(!empirecraft$state().expanded())return;
        for(int rank=0;rank<3;rank++)if(x>=leftPos-58+rank*18&&x<leftPos-42+rank*18&&y>=topPos-12&&y<topPos+4)
            gui.renderTooltip(font,ArmorLabels.rank(rank),x,y);
    }
}
