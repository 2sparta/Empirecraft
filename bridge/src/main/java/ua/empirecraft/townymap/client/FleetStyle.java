package ua.empirecraft.townymap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Painted with native GUI primitives: no downloaded textures or resource-pack dependency. */
public final class FleetStyle {
    public static final int NAVY=0xff172a35,HEADER=0xff203746,BRASS=0xffc6a56a,PAPER=0xffe8dfc9,
        CARD=0xfff5eddb,LINE=0xffcabc9d,INK=0xff303b3d,MUTED=0xff6b7169,LIGHT=0xfff2e7cd;
    private FleetStyle(){}
    public static void border(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);}
    public static void card(GuiGraphics g,FleetLayout.Rect r,boolean hover){g.fill(r.x()+1,r.y()+2,r.right()+1,r.bottom()+2,0x220b1922);g.fill(r.x(),r.y(),r.right(),r.bottom(),hover?0xfffbf5e8:CARD);border(g,r.x(),r.y(),r.w(),r.h(),hover?0xffa88c59:LINE);}
    public static void icon(GuiGraphics g,int kind,int x,int y,int color){
        if(kind==0){ // mast, sail and hull
            g.fill(x+6,y,x+7,y+11,color);for(int n=0;n<7;n++)g.fill(x+7,y+1+n,x+8+n,y+2+n,color);
            g.fill(x,y+10,x+14,y+12,color);g.fill(x+2,y+12,x+12,y+14,color);
        }else if(kind==1){ // market stall
            g.fill(x,y+2,x+14,y+5,color);g.fill(x+2,y,x+12,y+2,color);g.fill(x+1,y+5,x+3,y+14,color);g.fill(x+11,y+5,x+13,y+14,color);g.fill(x+3,y+11,x+11,y+14,color);
        }else if(kind==2){ // gavel
            g.fill(x+1,y+1,x+9,y+5,color);g.fill(x+5,y+5,x+7,y+11,color);g.fill(x+2,y+12,x+12,y+14,color);
        }else if(kind==3){ // helm
            border(g,x+2,y+2,10,10,color);g.fill(x+6,y,x+8,y+14,color);g.fill(x,y+6,x+14,y+8,color);
        }else if(kind==4){ // telescope
            g.fill(x+1,y+5,x+9,y+9,color);g.fill(x+9,y+3,x+13,y+11,color);g.fill(x+5,y+9,x+7,y+14,color);
        }else{g.fill(x+6,y+1,x+8,y+13,color);g.fill(x+1,y+6,x+13,y+8,color);}
    }
    public static void ship(GuiGraphics g,int x,int y,int color){
        // Quiet engraving, deliberately small enough to leave the text readable.
        g.fill(x+30,y,x+32,y+42,color);g.fill(x+14,y+10,x+16,y+43,color);
        for(int n=0;n<25;n++){g.fill(x+33,y+4+n,x+34+n,y+5+n,color);if(n<17)g.fill(x+16,y+13+n,x+17+n,y+14+n,color);}
        g.fill(x+3,y+42,x+65,y+45,color);for(int n=0;n<7;n++)g.fill(x+5+n,y+45+n,x+63-n,y+46+n,color);
        g.fill(x,y+56,x+25,y+57,color);g.fill(x+35,y+56,x+70,y+57,color);
    }
    public static final class Action extends Button {
        private final boolean primary,selected,nav;private final int symbol;
        public Action(Component title,int x,int y,int w,int h,boolean primary,boolean selected,boolean nav,int symbol,Runnable run){
            super(x,y,w,h,title,b->run.run(),DEFAULT_NARRATION);this.primary=primary;this.selected=selected;this.nav=nav;this.symbol=symbol;setTooltip(Tooltip.create(title));
        }
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
            var font=Minecraft.getInstance().font;boolean hover=isHoveredOrFocused();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            int bg=nav?(selected?0xff344d57:hover?0xff2b424e:NAVY):primary?(hover?0xff3b6267:0xff294b50):(hover?0xffe3d5b7:0xffeee5d0);
            int fg=nav?(selected?BRASS:LIGHT):primary?LIGHT:INK;if(!active){bg=nav?NAVY:0xffd8d0bd;fg=0xff8b8c81;}
            g.fill(x,y,x+w,y+h,bg);
            if(nav){if(selected)g.fill(x,y,x+2,y+h,BRASS);}else border(g,x,y,w,h,primary?0xff244047:LINE);
            if(isFocused())border(g,x,y,w,h,BRASS);
            if(symbol>=0)icon(g,symbol,x+7,y+(h-14)/2,fg);
            int inset=symbol>=0?27:8,available=w-inset-6;
            String full=getMessage().getString(),label=font.width(full)>available?font.plainSubstrByWidth(full,Math.max(0,available-font.width("…")))+"…":full;
            int tx=nav?x+inset:x+(w-font.width(label))/2;g.drawString(font,label,tx,y+(h-8)/2,fg,false);
        }
    }
}
