package ua.empirecraft.casting;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CastingScreen extends AbstractContainerScreen<CastingMenu> {
    private static final int INK=0xff303b3d,MUTED=0xff687063;
    public CastingScreen(CastingMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=193;inventoryLabelY=99;}
    private static void border(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);}
    private void slot(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xffb4aa91);g.fill(x,y,x+16,y+16,0xfffff7e3);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x+3,y+4,x+imageWidth+3,y+imageHeight+4,0x88071016);g.fill(x,y,x+imageWidth,y+imageHeight,0xffe8dfc9);border(g,x,y,imageWidth,imageHeight,0xffc6a56a);g.fill(x+1,y+1,x+imageWidth-1,y+22,0xff203746);
        slot(g,x+43,y+43);slot(g,x+117,y+43);g.fill(x+70,y+48,x+104,y+53,0xffc1b69d);g.fill(x+98,y+44,x+101,y+57,0xffc1b69d);g.fill(x+101,y+46,x+104,y+55,0xffc1b69d);g.fill(x+104,y+48,x+107,y+53,0xffc1b69d);
        int progress=Math.max(0,Math.min(34,menu.progress()*34/CastingBlockEntity.CYCLE_TICKS));g.fill(x+70,y+48,x+70+progress,y+53,0xffb47b46);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+8+col*18,y+111+row*18);
        for(int col=0;col<9;col++)slot(g,x+8+col*18,y+169);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,8,8,0xfff2e7cd,false);g.drawCenteredString(font,Component.translatable("gui.empirecraft_casting.mold"),51,29,MUTED);g.drawCenteredString(font,Component.translatable("gui.empirecraft_casting.output"),125,29,MUTED);
        String[] keys={"no_mold","no_crucible","cold","not_enough","full","cooling","no_recipe","ready"};int status=Math.max(0,Math.min(keys.length-1,menu.status()));
        var lines=font.split(Component.translatable("gui.empirecraft_casting."+keys[status]),158);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),8,70+i*10,status==CastingBlockEntity.FULL?0xffa44838:INK,false);
        g.drawString(font,playerInventoryTitle,8,inventoryLabelY,MUTED,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
