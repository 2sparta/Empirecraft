package ua.empirecraft.armory.client;

import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ua.empirecraft.armory.*;

public final class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private EditBox search;
    private int scroll;
    private final List<Button> choices=new ArrayList<>();
    private List<AssemblyRecipe> recipes=List.of();
    public WorkshopScreen(WorkshopMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=310;imageHeight=238;inventoryLabelY=139;inventoryLabelX=78;}
    @Override protected void init(){
        super.init();recipes=AssemblyRecipe.all(minecraft.level);choices.clear();
        search=new EditBox(font,leftPos+10,topPos+26,91,16,Component.translatable("armory.search"));search.setMaxLength(48);search.setHint(Component.translatable("armory.search"));search.setResponder(s->{scroll=0;refresh();});addRenderableWidget(search);
        for(int n=0;n<5;n++){final int row=n;choices.add(addRenderableWidget(Button.builder(Component.empty(),b->{var filtered=filtered();int i=scroll+row;if(i<filtered.size())send(100+recipes.indexOf(filtered.get(i)));}).bounds(leftPos+9,topPos+47+n*17,94,16).build()));}
        addRenderableWidget(Button.builder(Component.translatable("armory.assemble"),b->send(0)).bounds(leftPos+114,topPos+119,76,18).build());
        addRenderableWidget(Button.builder(Component.translatable("armory.repair"),b->send(4)).bounds(leftPos+194,topPos+119,56,18).build());
        addRenderableWidget(Button.builder(Component.translatable("armory.quench"),b->send(1)).bounds(leftPos+198,topPos+45,68,16).build());
        addRenderableWidget(Button.builder(Component.translatable("armory.temper"),b->send(2)).bounds(leftPos+198,topPos+64,68,16).build());
        addRenderableWidget(Button.builder(Component.translatable("armory.rework"),b->send(3)).bounds(leftPos+198,topPos+83,68,16).build());refresh();
    }
    private void send(int button){if(button>=100)ArmoryNetwork.select(menu.containerId,button-100);else minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    private List<AssemblyRecipe> filtered(){String text=search==null?"":search.getValue().toLowerCase(Locale.ROOT);return recipes.stream().filter(r->r.result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(text)||r.profile.construction().contains(text)||r.profile.metal().contains(text)).toList();}
    private void refresh(){var values=filtered();scroll=Math.max(0,Math.min(scroll,Math.max(0,values.size()-5)));for(int i=0;i<choices.size();i++){var b=choices.get(i);b.visible=scroll+i<values.size();if(b.visible){var r=values.get(scroll+i);b.setMessage(Component.literal(font.plainSubstrByWidth(r.result.getHoverName().getString(),83)));b.active=recipes.indexOf(r)!=menu.selected();b.setTooltip(Tooltip.create(r.result.getHoverName()));}}}
    @Override protected void containerTick(){super.containerTick();search.tick();refresh();}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(x>=leftPos+7&&x<leftPos+106&&y>=topPos+45&&y<topPos+135){scroll-=Integer.signum((int)delta);refresh();return true;}return super.mouseScrolled(x,y,delta);}
    @Override protected void renderBg(GuiGraphics g,float delta,int mouseX,int mouseY){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff24292c);g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xffc9bea7);g.fill(leftPos+5,topPos+21,leftPos+107,topPos+137,0xff423d36);g.fill(leftPos+110,topPos+21,leftPos+303,topPos+115,0xffafa38e);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,0xff514c43);g.fill(x,y,x+16,y+16,0xff867b68);}
        if(menu.remaining()>0)g.fill(leftPos+115,topPos+112,leftPos+115+(40-menu.remaining())*181/40,topPos+114,0xffbb7c36);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,9,8,0xff30291f,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xff30291f,false);
        int selected=menu.selected();if(selected>=0&&selected<recipes.size()){
            var recipe=recipes.get(selected);g.drawString(font,font.plainSubstrByWidth(recipe.result.getHoverName().getString(),180),115,23,0xff30291f,false);
            g.drawString(font,Component.translatable("armory.step",Math.min(4,menu.stage()+1)),138,44,0xff30291f,false);
        }
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("armory.status."+menu.status()).getString(),290),9,228,0xff493c2d,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g);super.render(g,x,y,delta);renderTooltip(g,x,y);int selected=menu.selected();if(selected>=0&&selected<recipes.size()&&x>=leftPos+135&&x<leftPos+193&&y>=topPos+37&&y<topPos+60){var r=recipes.get(selected);List<Component> lines=new ArrayList<>();lines.add(Component.translatable("armory.stage."+menu.stage()));for(var in:r.stage(menu.stage()))if(in.getItems().length>0)lines.add(in.getItems()[0].getHoverName());g.renderComponentTooltip(font,lines,x,y);}}
}
