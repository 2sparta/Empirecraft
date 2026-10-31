package ua.posters;

import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class PosterScreen extends Screen {
    private static int rememberedMask=7;
    private final SchematicTableScreen parent;
    private final int container;
    private int mask=rememberedMask,serial=0,left,top,panelW,panelH;
    private boolean pending;
    private CompoundTag drawing;
    private String status="gui.create_posters.help";
    private Button preview,print;
    private final java.util.List<Button> choices=new java.util.ArrayList<>();
    public PosterScreen(SchematicTableScreen parent){super(Component.m_237115_("gui.create_posters.title"));this.parent=parent;this.container=parent.m_6262_().f_38840_;}
    @Override protected void m_7856_(){
        choices.clear();
        panelW=Math.min(680,f_96543_-16);panelH=Math.min(400,f_96544_-16);left=(f_96543_-panelW)/2;top=(f_96544_-panelH)/2;
        for(int d=0;d<6;d++){
            final int direction=d;Button b=Button.m_253074_(label(d),button->{
                mask^=1<<direction;rememberedMask=mask;button.m_93666_(label(direction));drawing=null;status=mask==0?"error.create_posters.views":"gui.create_posters.help";buttons();
            }).m_252987_(left+10+(d%2)*73,top+45+(d/2)*23,70,20).m_253136_();m_142416_(b);choices.add(b);
        }
        preview=m_142416_(Button.m_253074_(Component.m_237115_("gui.create_posters.preview"),b->request(false)).m_252987_(left+10,top+119,143,20).m_253136_());
        print=m_142416_(Button.m_253074_(Component.m_237115_("gui.create_posters.print"),b->request(true)).m_252987_(left+10,top+143,143,20).m_253136_());
        m_142416_(Button.m_253074_(Component.m_237115_("gui.create_posters.back"),b->f_96541_.m_91152_(parent)).m_252987_(left+10,top+panelH-29,143,20).m_253136_());
        buttons();
    }
    private Component label(int d){return Component.m_237113_((mask&(1<<d))!=0?"✓ ":"  ").m_7220_(Component.m_237115_("view.create_posters."+d));}
    private void buttons(){for(Button b:choices)b.f_93623_=!pending;if(preview!=null)preview.f_93623_=!pending&&mask!=0;if(print!=null)print.f_93623_=!pending&&mask!=0&&drawing!=null;}
    private void request(boolean printing){
        pending=true;status="gui.create_posters.working";buttons();
        PosterNetwork.CHANNEL.sendToServer(new PosterNetwork.Request(container,++serial,mask,"",printing));
    }
    public void result(PosterNetwork.Result r){
        if(r.container()!=container||r.serial()!=serial)return;pending=false;
        if(r.drawing()!=null&&DrawingData.valid(r.drawing()))drawing=r.drawing();
        status=!r.error().isEmpty()?"error.create_posters."+r.error():r.printed()?"gui.create_posters.done":"gui.create_posters.ready";buttons();
    }
    @Override public void m_86600_(){
        var mc=Minecraft.m_91087_();if(mc.f_91074_==null||mc.f_91074_.f_36096_.f_38840_!=container){mc.m_91152_(null);return;}
    }
    @Override public void m_7379_(){parent.m_7379_();}
    @Override public boolean m_7043_(){return false;}
    @Override public void m_88315_(GuiGraphics g,int mouseX,int mouseY,float partial){
        m_280273_(g);g.m_280509_(left,top,left+panelW,top+panelH,0xffe4d6b5);
        g.m_280509_(left+160,top+34,left+panelW-8,top+panelH-9,0xff413b31);
        g.m_280614_(f_96547_,f_96539_,left+12,top+12,0xff30291e,false);
        g.m_280614_(f_96547_,Component.m_237115_("gui.create_posters.views"),left+11,top+31,0xff655840,false);
        int statusY=top+171;
        if(panelH>255){
            g.m_280554_(f_96547_,Component.m_237115_("gui.create_posters.cost"),left+11,top+172,142,0xff403729);statusY=top+207;
        }
        if(panelH>235)g.m_280554_(f_96547_,Component.m_237115_(status),left+11,statusY,140,0xff5a4831);
        int areaW=panelW-180,areaH=panelH-60;
        if(drawing!=null){
            int w=Math.min(areaW,areaH*3/2),h=w*2/3;int x=left+170+(areaW-w)/2,y=top+40+(areaH-h)/2;
            var pose=g.m_280168_();pose.m_85836_();pose.m_85837_(x,y,0);pose.m_85841_(w/(float)Paper.W,h/(float)Paper.H,1);
            g.m_280163_(PosterTextures.texture(drawing),0,0,0f,0f,Paper.W,Paper.H,Paper.W,Paper.H);pose.m_85849_();
        }else{
            g.m_280554_(f_96547_,Component.m_237115_(status),left+172,top+52,Math.max(40,areaW-12),0xffe6d8b9);
        }
        super.m_88315_(g,mouseX,mouseY,partial);
    }
}
