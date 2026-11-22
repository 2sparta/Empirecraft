package ua.empirecraft.armory.client;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import ua.empirecraft.armory.*;

/** Compact enough for Minecraft's minimum GUI size; all recipe hints read synchronized slots. */
public final class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private static final int GOLD=0xffddc18a,TEXT=0xffe2ded4,MUTED=0xffa1adb0,GREEN=0xff9bc49a,RED=0xffe6a494;
    private EditBox search;
    private int scroll,mode,viewStage,lastStage=-1,lastSelected=-2,materialPage;
    private boolean help;
    private List<AssemblyRecipe> recipes=List.of(),filteredCache=List.of();
    private String filterKey;
    private AssemblyPlan cachedPlan;
    private final List<Choice> choices=new ArrayList<>();
    private final List<Action> tabs=new ArrayList<>(),steps=new ArrayList<>();
    private Action assemble,previous,next;
    public WorkshopScreen(WorkshopMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=320;imageHeight=238;inventoryLabelX=132;inventoryLabelY=136;}
    private static MutableComponent tr(String key,Object...args){return Component.translatable("armory."+key,args);}
    private Action button(String key,int x,int y,int w,int h,Button.OnPress press){return addRenderableWidget(new Action(leftPos+x,topPos+y,w,h,tr(key),press));}
    @Override protected void init(){
        super.init();filterKey=null;recipes=AssemblyRecipe.all(minecraft.level);choices.clear();tabs.clear();steps.clear();
        for(int n:new int[]{0,2}){final int tab=n;var b=button("tab."+n,8+n*51,24,100,14,a->{mode=tab;help=false;refresh();});b.setTooltip(Tooltip.create(tr("tab_hint."+n)));tabs.add(b);}
        button("help",296,6,16,14,b->{help=!help;refresh();}).setTooltip(Tooltip.create(tr("help_hint")));
        search=new EditBox(font,leftPos+9,topPos+42,97,14,tr("search"));search.setBordered(false);search.setMaxLength(64);search.setHint(tr("search"));search.setResponder(s->{scroll=0;refresh();});addRenderableWidget(search);
        for(int n=0;n<4;n++){final int row=n;var b=new Choice(leftPos+8,topPos+59+n*14,100,13,a->{var values=filtered();int i=scroll+row;if(i<values.size())ArmoryNetwork.select(menu.containerId,recipes.indexOf(values.get(i)));});choices.add(addRenderableWidget(b));}
        previous=button("previous",75,116,14,12,b->{scroll-=4;refresh();});next=button("next",91,116,14,12,b->{scroll+=4;refresh();});
        for(int n=0;n<4;n++){final int stage=n;var b=button("stage_short."+n,116+n*48,62,46,13,a->{viewStage=stage;materialPage=0;refresh();});steps.add(b);}
        assemble=button("assemble",116,116,192,16,b->{if(mode==0&&viewStage!=currentStage()){viewStage=currentStage();materialPage=0;refresh();}else send(mode==2?4:0);});
        refresh();
    }
    private void send(int operation){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,operation);}
    private int currentStage(){return Math.min(3,Math.max(0,menu.stage()));}
    private AssemblyRecipe recipe(){int i=menu.selected();return i>=0&&i<recipes.size()?recipes.get(i):null;}
    private List<AssemblyRecipe> filtered(){String q=search==null?"":search.getValue().toLowerCase(Locale.ROOT).strip();if(!q.equals(filterKey)){filterKey=q;filteredCache=recipes.stream().filter(r->r.result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)||r.profile.construction().contains(q)||r.profile.metal().contains(q)).toList();}return filteredCache;}
    public AssemblyPlan visiblePlan(){return cachedPlan;}
    private void refresh(){
        if(search==null)return;
        if(lastStage!=menu.stage()||lastSelected!=menu.selected()){viewStage=currentStage();materialPage=0;lastStage=menu.stage();lastSelected=menu.selected();}
        var selectedRecipe=recipe();cachedPlan=selectedRecipe==null?null:AssemblyPlan.inspect(selectedRecipe,viewStage,i->menu.slots.get(i).getItem());
        search.visible=mode==0&&!help;
        var values=filtered();scroll=Math.max(0,Math.min(scroll,Math.max(0,values.size()-4)));
        boolean locked=!menu.atAnvil()||menu.slots.get(WorkshopEntity.WORK).hasItem();
        for(int i=0;i<choices.size();i++){var b=choices.get(i);b.visible=mode==0&&!help&&scroll+i<values.size();if(b.visible){var r=values.get(scroll+i);b.item=r.result;b.setMessage(r.result.getHoverName());b.chosen=recipes.indexOf(r)==menu.selected();b.active=!locked&&menu.remaining()==0;b.setTooltip(Tooltip.create(locked?tr("selection_locked"):r.result.getHoverName()));}}
        for(int i=0;i<tabs.size();i++)tabs.get(i).chosen=mode==i*2;
        previous.visible=next.visible=mode==0&&!help;previous.active=scroll>0;next.active=scroll+4<values.size();
        for(int i=0;i<4;i++){var b=steps.get(i);b.visible=mode==0&&!help&&recipe()!=null;b.chosen=viewStage==i;b.setTooltip(Tooltip.create(tr("stage."+i).append("\n").append(tr(i<menu.stage()?"stage_installed":"stage_preview"))));}
        assemble.visible=!help;assemble.active=menu.atAnvil()&&menu.remaining()==0;
        if(mode==0){var plan=visiblePlan();boolean preview=viewStage!=currentStage();assemble.setMessage(preview?tr("return_current",currentStage()+1):tr("assemble_stage",currentStage()+1));
            assemble.active&=preview||plan!=null&&plan.complete&&(!locked||menu.slots.get(0).getItem().is(Armory.WORK.get()))&&(currentStage()!=3||!menu.slots.get(2).hasItem());
            assemble.setTooltip(Tooltip.create(tr(preview?"return_hint":plan==null?"select_hint":!plan.complete?"missing_hint":currentStage()==3&&menu.slots.get(2).hasItem()?"output_hint":"assemble_hint")));
        }else{assemble.setMessage(tr("repair"));assemble.setTooltip(Tooltip.create(tr("repair_hint")));}
        if(help){for(var b:steps)b.visible=false;for(var b:choices)b.visible=false;}
    }
    @Override protected void containerTick(){super.containerTick();search.tick();refresh();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256&&help){help=false;refresh();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(help)return true;if(mode==0&&inside(x,y,6,40,106,92)){scroll-=Integer.signum((int)delta);refresh();return true;}if(mode==0&&inside(x,y,114,77,196,36)){var plan=visiblePlan();if(plan!=null)materialPage=Math.max(0,Math.min((plan.needs.size()-1)/4,materialPage-Integer.signum((int)delta)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean mouseClicked(double x,double y,int button){if(help&&!inside(x,y,296,6,16,14)){help=false;refresh();return true;}if(mode==0&&button==0){var pages=visiblePlan();if(pages!=null&&pages.needs.size()>4&&inside(x,y,278,52,30,10)){materialPage=(materialPage+1)%((pages.needs.size()+3)/4);return true;}int i=materialAt(x,y);var plan=visiblePlan();if(i>=0&&plan!=null&&i<plan.needs.size()&&ModList.get().isLoaded("jei")){var samples=plan.needs.get(i).ingredient().getItems();if(samples.length>0&&ArmoryJei.showRecipe(samples[0]))return true;}}return super.mouseClicked(x,y,button);}
    private boolean inside(double x,double y,int dx,int dy,int w,int h){return x>=leftPos+dx&&x<leftPos+dx+w&&y>=topPos+dy&&y<topPos+dy+h;}
    private int materialAt(double x,double y){if(!inside(x,y,116,78,192,36))return -1;return materialPage*4+(int)(y-topPos-78)/18*2+(int)(x-leftPos-116)/96;}
    private static void panel(GuiGraphics g,int x,int y,int w,int h,int fill,int border){g.fill(x,y,x+w,y+h,border);g.fill(x+1,y+1,x+w-1,y+h-1,fill);}
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;panel(g,x,y,320,238,0xff20272a,0xff937c57);g.fill(x+3,y+3,x+317,y+21,0xff303a3d);
        panel(g,x+6,y+40,106,92,0xff161e22,0xff465052);panel(g,x+114,y+40,196,74,0xff293236,0xff465052);g.fill(x+7,y+130,x+313,y+131,0xff786344);
        for(var slot:menu.slots){int sx=x+slot.x,sy=y+slot.y;panel(g,sx-1,sy-1,18,18,0xff141b1e,slot.index<3?0xffa48a5e:0xff566064);g.fill(sx,sy,sx+16,sy+1,0xff080e11);}
        String[] icons={"W","T",">"};for(int i=0;i<3;i++)if(!menu.slots.get(i).hasItem())g.drawCenteredString(font,icons[i],x+102,y+153+i*24,0xff83918e);
        if(menu.remaining()>0)g.fill(x+8,y+225,x+8+(40-menu.remaining())*304/40,y+227,0xffd4ad69);
    }
    private void line(GuiGraphics g,Component c,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(c.getString(),width),x,y,color,false);}
    private void wrapped(GuiGraphics g,Component c,int x,int y,int width,int color){g.drawWordWrap(font,c,x,y,width,color);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        line(g,title,8,8,281,GOLD);line(g,tr("supplies"),8,136,80,MUTED);line(g,playerInventoryTitle,132,136,166,MUTED);
        if(help){panel(g,6,40,304,92,0xff202a2e,0xffb49461);wrapped(g,tr("help_body"),12,46,292,TEXT);return;}
        if(mode==0){line(g,tr("result_count",filtered().size()),8,119,64,MUTED);var r=recipe();if(r!=null){g.renderItem(r.result,117,43);line(g,r.result.getHoverName(),138,44,168,TEXT);line(g,tr("step",currentStage()+1),138,53,166,MUTED);var plan=visiblePlan();if(plan!=null){materialPage=Math.max(0,Math.min(materialPage,Math.max(0,(plan.needs.size()-1)/4)));for(int n=0;n<4;n++){int i=materialPage*4+n;if(i>=plan.needs.size())break;var need=plan.needs.get(i);var samples=need.ingredient().getItems();int cx=116+(n%2)*96,cy=78+(n/2)*18;var sample=samples.length==0?ItemStack.EMPTY:samples[(int)(System.currentTimeMillis()/1400%samples.length)];g.renderItem(sample,cx,cy);line(g,sample.getHoverName(),cx+18,cy,76,TEXT);int color=need.complete()?GREEN:need.missing()>0?RED:GOLD;line(g,Component.literal(need.ready()+" / "+need.required()+(need.complete()?" ✓":"")),cx+18,cy+9,76,color);}
                    if(plan.needs.size()>4)line(g,Component.literal("↕ "+(materialPage+1)+"/"+((plan.needs.size()+3)/4)),278,53,30,GOLD);}
            }else wrapped(g,tr("select_hint"),122,52,177,MUTED);
        }else{
            wrapped(g,tr("instructions."+mode),12,47,95,TEXT);var stack=menu.slots.get(mode==1?1:0).getItem();g.renderItem(stack,118,44);line(g,stack.isEmpty()?tr(mode==1?"process_title":"repair_title"):stack.getHoverName(),138,44,166,TEXT);
            if(mode==1){wrapped(g,tr("process_summary"),119,85,187,MUTED);if(stack.getItem() instanceof PartItem)line(g,Component.translatable("armory.treatment."+Parts.treatment(stack)),119,103,188,GOLD);}
            else wrapped(g,tr("repair_hint"),119,64,187,MUTED);
        }
        Component status=!menu.atAnvil()?tr("move_to_anvil"):menu.remaining()>0?tr("working"):mode==0&&recipe()!=null?tr(visiblePlan().complete?"ready_hint":"missing_hint"):tr("status."+menu.status());line(g,status,8,228,302,MUTED);
    }
    private void tooltip(GuiGraphics g,List<Component> text,int x,int y){List<Component> lines=new ArrayList<>();for(var c:text)for(var line:font.getSplitter().splitLines(c,Math.min(250,width-18),Style.EMPTY))lines.add(Component.literal(line.getString()).withStyle(c.getStyle()));g.renderComponentTooltip(font,lines,x,y);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){
        renderBackground(g);super.render(g,x,y,delta);if(help)return;
        if(hoveredSlot!=null&&hoveredSlot.index<WorkshopEntity.SLOTS){var lines=new ArrayList<Component>();if(hoveredSlot.hasItem())lines.addAll(hoveredSlot.getItem().getTooltipLines(minecraft.player,minecraft.options.advancedItemTooltips?TooltipFlag.ADVANCED:TooltipFlag.NORMAL));int i=hoveredSlot.index;lines.add(tr(i==0?"work_hint":i==1?"process_hint":i==2?"output_hint":"supply_hint").withStyle(ChatFormatting.GOLD));tooltip(g,lines,x,y);return;}
        int i=materialAt(x,y);var plan=visiblePlan();if(mode==0&&i>=0&&plan!=null&&i<plan.needs.size()){
            var need=plan.needs.get(i);var samples=need.ingredient().getItems();var lines=new ArrayList<Component>();if(samples.length>0)lines.add(samples[0].getHoverName().copy().withStyle(ChatFormatting.GOLD));lines.add(tr("required_count",need.required()));lines.add(tr("stock_count",need.available(),need.required()));lines.add(tr("ready_count",need.ready(),need.required()));
            if(need.missing()>0)lines.add(tr("missing_count",need.missing()).withStyle(ChatFormatting.RED));if(need.available()>need.ready())lines.add(tr("treatment_hint").withStyle(ChatFormatting.YELLOW));lines.add(tr("supply_hint").withStyle(ChatFormatting.GRAY));if(samples.length>1)lines.add(tr("alternatives").withStyle(ChatFormatting.GRAY));if(ModList.get().isLoaded("jei"))lines.add(tr("recipe_click").withStyle(ChatFormatting.AQUA));tooltip(g,lines,x,y);return;
        }
        if(mode==0&&recipe()!=null&&inside(x,y,116,40,192,20)){tooltip(g,List.of(recipe().result.getHoverName(),tr("stage."+viewStage),tr("materials_page",materialPage+1,visiblePlan()==null?1:(visiblePlan().needs.size()+3)/4)),x,y);return;}
        renderTooltip(g,x,y);
    }
    private static class Action extends Button {
        boolean chosen;
        Action(int x,int y,int w,int h,Component text,OnPress press){super(x,y,w,h,text,press,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){boolean hover=isHoveredOrFocused();int border=chosen?0xffdab879:hover?0xffbba16e:0xff596363;panel(g,getX(),getY(),width,height,chosen?0xff534832:hover&&active?0xff435054:0xff303a3d,border);g.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()+(height-8)/2,active?TEXT:MUTED);}
    }
    private static final class Choice extends Action {
        ItemStack item=ItemStack.EMPTY;
        Choice(int x,int y,int w,int h,OnPress press){super(x,y,w,h,Component.empty(),press);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){var font=Minecraft.getInstance().font;panel(g,getX(),getY(),width,height,chosen?0xff534832:isHoveredOrFocused()&&active?0xff435054:0xff252e32,chosen?0xffdab879:0xff465052);g.pose().pushPose();g.pose().translate(getX()+2,getY()+1,0);g.pose().scale(.6875f,.6875f,1);g.renderItem(item,0,0);g.pose().popPose();g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),width-18),getX()+16,getY()+3,chosen?GOLD:TEXT,false);}
    }
}
