package ua.empirecraft.armor.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import ua.empirecraft.armor.LayerMenu;
public final class LayerScreen extends AbstractContainerScreen<LayerMenu> {
    private static final int LABEL_X=7,LABEL_WIDTH=54;
    public LayerScreen(LayerMenu menu,Inventory inventory,Component title){super(menu,inventory,ArmorLabels.title());imageHeight=190;inventoryLabelY=94;}
    private String label(int rank) {
        String text=ArmorLabels.rank(rank).getString();
        return font.width(text)<=LABEL_WIDTH?text:font.plainSubstrByWidth(text,LABEL_WIDTH-font.width("…"))+"…";
    }
    @Override protected void renderBg(GuiGraphics gui,float partial,int mouseX,int mouseY) {
        gui.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffc6c6c6);
        var headers=new Item[]{Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS};
        for(int col=0;col<4;col++)gui.renderItem(new ItemStack(headers[col]),leftPos+66+23*col,topPos+17);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;gui.fill(x-1,y-1,x+17,y+17,0xff373737);gui.fill(x,y,x+16,y+16,0xff8b8b8b);}
        for(int rank=0;rank<3;rank++)gui.drawString(font,label(rank),leftPos+LABEL_X,topPos+40+rank*22,0xff404040,false);
    }
    @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
        renderBackground(gui);super.render(gui,mouseX,mouseY,partial);renderTooltip(gui,mouseX,mouseY);
        for(int rank=0;rank<3;rank++)if(mouseX>=leftPos+LABEL_X&&mouseX<leftPos+LABEL_X+LABEL_WIDTH&&mouseY>=topPos+36+rank*22&&mouseY<topPos+54+rank*22)
            gui.renderTooltip(font,ArmorLabels.rank(rank),mouseX,mouseY);
    }
}
