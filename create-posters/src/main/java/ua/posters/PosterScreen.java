package ua.posters;
import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
public final class PosterScreen extends Screen {
    private static int rememberedMask=7,nextSerial;private static PosterOptions rememberedOptions=PosterOptions.DEFAULT;
    private final SchematicTableScreen parent;private final int container;
    private int mask=rememberedMask,serial,left,top,panelW,panelH;private PosterOptions options=rememberedOptions;
    private boolean pending,printFits;private CompoundTag drawing;private String status="gui.create_posters.help";
    private Button preview,print,size,scale,style;private final java.util.List<Button> choices=new java.util.ArrayList<>();
    public PosterScreen(SchematicTableScreen parent){super(Component.m_237115_("gui.create_posters.title"));this.parent=parent;container=parent.m_6262_().f_38840_;}
    @Override protected void m_7856_(){
        choices.clear();panelW=Math.min(740,f_96543_-16);panelH=Math.min(430,f_96544_-16);left=(f_96543_-panelW)/2;top=(f_96544_-panelH)/2;
        for(int d=0;d<6;d++){
            final int direction=d;Button b=Button.m_253074_(label(d),button->{mask^=1<<direction;rememberedMask=mask;button.m_93666_(label(direction));invalidate();})
                .m_252987_(left+10+d%2*83,top+34+d/2*20,80,18).m_253136_();m_142416_(b);choices.add(b);
        }
        size=m_142416_(Button.m_253074_(sizeLabel(),b->{options=new PosterOptions((options.size()+1)%4,options.scale(),options.muted());b.m_93666_(sizeLabel());invalidate();}).m_252987_(left+10,top+98,163,18).m_253136_());
        scale=m_142416_(Button.m_253074_(scaleLabel(),b->{options=new PosterOptions(options.size(),(options.scale()+1)%PosterOptions.RATIOS.length,options.muted());b.m_93666_(scaleLabel());invalidate();}).m_252987_(left+10,top+119,163,18).m_253136_());
        style=m_142416_(Button.m_253074_(styleLabel(),b->{options=new PosterOptions(options.size(),options.scale(),!options.muted());b.m_93666_(styleLabel());invalidate();}).m_252987_(left+10,top+140,163,18).m_253136_());
        preview=m_142416_(Button.m_253074_(Component.m_237115_("gui.create_posters.preview"),b->request(false)).m_252987_(left+10,top+176,79,18).m_253136_());
        print=m_142416_(Button.m_253074_(Component.m_237115_("gui.create_posters.print"),b->request(true)).m_252987_(left+93,top+176,80,18).m_253136_());
        m_142416_(Button.m_253074_(Component.m_237115_("gui.create_posters.back"),b->f_96541_.m_91152_(parent)).m_252987_(left+10,top+panelH-23,163,18).m_253136_());buttons();
    }
    private Component sizeLabel(){return Component.m_237110_("gui.create_posters.size",options.width(),options.height());}
    private Component scaleLabel(){return options.ratio()>0?Component.m_237110_("gui.create_posters.scale.fixed",options.ratio()):Component.m_237115_(options.scale()==0?"gui.create_posters.scale.shared":"gui.create_posters.scale.each");}
    private Component styleLabel(){return Component.m_237115_(options.muted()?"gui.create_posters.style.muted":"gui.create_posters.style.graphite");}
    private Component label(int d){return Component.m_237113_((mask&(1<<d))!=0?"✓ ":"  ").m_7220_(Component.m_237115_("view.create_posters."+d));}
    private void invalidate(){rememberedOptions=options;drawing=null;printFits=false;status=mask==0?"error.create_posters.views":"gui.create_posters.help";buttons();}
    private void buttons(){for(Button b:choices)b.f_93623_=!pending;if(size!=null)size.f_93623_=!pending;if(scale!=null)scale.f_93623_=!pending;if(style!=null)style.f_93623_=!pending;if(preview!=null)preview.f_93623_=!pending&&mask!=0;if(print!=null)print.f_93623_=!pending&&mask!=0&&drawing!=null&&printFits;}
    private void request(boolean printing){pending=true;status="gui.create_posters.working";buttons();PosterNetwork.CHANNEL.sendToServer(new PosterNetwork.Request(container,serial=++nextSerial,mask,"",printing,options.size(),options.scale(),options.muted()));}
    public void result(PosterNetwork.Result r){
        if(r.container()!=container||r.serial()!=serial)return;pending=false;
        if(r.drawing()!=null&&DrawingData.valid(r.drawing())){drawing=r.drawing();printFits=DrawingData.fits(drawing);}
        status=!r.error().isEmpty()?"error.create_posters."+r.error():r.printed()?"gui.create_posters.done":"gui.create_posters.ready";buttons();
    }
    @Override public void m_86600_(){var mc=Minecraft.m_91087_();if(mc.f_91074_==null||mc.f_91074_.f_36096_.f_38840_!=container)mc.m_91152_(null);}
    @Override public void m_7379_(){parent.m_7379_();}
    @Override public boolean m_7043_(){return false;}
    @Override public void m_88315_(GuiGraphics g,int mouseX,int mouseY,float partial){
        m_280273_(g);g.m_280509_(left,top,left+panelW,top+panelH,0xffe4d6b5);
        g.m_280509_(left+181,top+29,left+panelW-8,top+panelH-9,0xff413b31);
        g.m_280614_(f_96547_,f_96539_,left+12,top+10,0xff30291e,false);
        g.m_280614_(f_96547_,Component.m_237115_("gui.create_posters.views"),left+11,top+23,0xff655840,false);
        g.m_280614_(f_96547_,Component.m_237110_("gui.create_posters.cost",options.paper(),options.ink()),left+11,top+163,0xff5b4c35,false);
        if(panelH>280)g.m_280554_(f_96547_,Component.m_237115_(status),left+11,top+210,160,0xff5a4831);
        int areaW=panelW-205,areaH=panelH-65;
        if(drawing!=null){
            int w=Math.min(areaW,areaH*3/2),h=w*2/3,x=left+192+(areaW-w)/2,y=top+35+(areaH-h)/2;
            int tw=DrawingData.textureWidth(drawing),th=DrawingData.textureHeight(drawing);var pose=g.m_280168_();pose.m_85836_();pose.m_85837_(x,y,0);pose.m_85841_(w/(float)tw,h/(float)th,1);
            g.m_280163_(PosterTextures.texture(drawing),0,0,0f,0f,tw,th,tw,th);pose.m_85849_();
        }else g.m_280554_(f_96547_,Component.m_237115_(status),left+190,top+44,Math.max(40,areaW),0xffe6d8b9);
        super.m_88315_(g,mouseX,mouseY,partial);
    }
}
