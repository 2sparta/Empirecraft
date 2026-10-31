package ua.posters;
import java.io.*;import java.util.*;
/** The preview and placed poster share one compositor, including physical print scale. */
public final class Paper {
    public static final int BASE_W=768,BASE_H=512;
    private static final int INK=0xff40382b,FAINT=0xff958a72,PARCHMENT=0xffe5dabc;
    private static final int[] WASH={0,0xb6b2a6,0xbba57d,0x9b8060,0xa4b096,0xe7dfcc,0x9eaeb5,0x9c9c98,0xc0987c,0xbe9b86,0xd7c598,0xb3a7b5,0xc49b91,0x9c978c,0xf1ebdb,0xbba88e};
    private static final String[] LABELS={"01 / ЗГОРИ","02 / СПРАВА","03 / СПЕРЕДУ","04 / ЗЛІВА","05 / ЗЗАДУ","06 / ЗНИЗУ"};
    private static final Map<Integer,byte[]> GLYPHS=loadFont();
    public record Panel(Projection.View view,double x,double y,double width,double height,double pixelsPerBlock){}
    public record Layout(List<Panel> panels,boolean fits,double sharedPixelsPerBlock){}
    private final int width,height;private final double factor;private final int[] pixels;
    private Paper(PosterOptions options){width=options.textureWidth();height=options.textureHeight();factor=width/(double)BASE_W;pixels=new int[width*height];}
    private static Map<Integer,byte[]> loadFont(){
        Map<Integer,byte[]> m=new HashMap<>();
        try(var stream=Paper.class.getResourceAsStream("/assets/create_posters/font-hd.bin")){
            if(stream==null)throw new IOException("Missing poster font");var in=new DataInputStream(stream);int count=in.readInt();
            for(int i=0;i<count;i++){int c=in.readInt();byte[] data=in.readNBytes(18*28);if(data.length!=504)throw new IOException("font");m.put(c,data);}
        }catch(IOException ex){throw new ExceptionInInitializerError(ex);}return m;
    }
    public static Layout layout(List<Projection.View> views,PosterOptions options){
        if(views.isEmpty()||views.size()>6)return new Layout(List.of(),false,0);
        List<Projection.View> ordered=new ArrayList<>(views);List<double[]> cells=new ArrayList<>();
        if(views.size()==3){
            Projection.View main=ordered.stream().max(Comparator.comparingDouble(v->v.spanW()*(v.direction()==1?1.00001:v.direction()==2?1.000005:1))).orElseThrow();
            ordered.remove(main);ordered.add(1,main);
            cells.add(new double[]{32,88,176,366});cells.add(new double[]{208,88,352,366});cells.add(new double[]{560,88,176,366});
        }else{
            int cols=views.size()==1?1:views.size()==2||views.size()==4?2:3,rows=(views.size()+cols-1)/cols;
            double cw=704.0/cols,ch=366.0/rows;
            for(int i=0;i<views.size();i++)cells.add(new double[]{32+i%cols*cw,88+i/cols*ch,cw,ch});
        }
        double common=Double.POSITIVE_INFINITY;
        for(int i=0;i<ordered.size();i++)common=Math.min(common,maxScale(ordered.get(i),cells.get(i)));
        double requested=options.ratio()>0?BASE_W/(double)options.width()/options.ratio():common;
        boolean fits=options.ratio()==0||requested<=common+1e-8;
        List<Panel> panels=new ArrayList<>();
        for(int i=0;i<ordered.size();i++){
            var v=ordered.get(i);var c=cells.get(i);double ppu=options.scale()==1?maxScale(v,c):Math.min(requested,common);
            panels.add(new Panel(v,c[0],c[1],c[2],c[3],ppu));
        }
        return new Layout(List.copyOf(panels),fits,options.scale()==1?0:Math.min(requested,common));
    }
    private static double maxScale(Projection.View v,double[] c){return Math.min((c[2]-30)/v.spanW(),(c[3]-69)/v.spanH());}
    public static int[] compose(String title,int sx,int sy,int sz,List<Projection.View> views,PosterOptions options){
        Paper p=new Paper(options);p.background();p.frame(16,16,751,495,INK);p.frame(20,20,747,491,FAINT);
        p.text("CREATE  /  КРЕСЛЕННЯ",32,28,FAINT,.78,60);p.text(title.isBlank()?"БЕЗ НАЗВИ":title.toUpperCase(Locale.ROOT),32,48,INK,1.05,66);
        p.line(32,77,735,77,INK,.45);Layout layout=layout(views,options);
        for(Panel panel:layout.panels())p.panel(panel,options);
        p.line(32,458,735,458,INK,.45);
        p.text("ГАБАРИТИ СХЕМИ / X "+sx+"  Y "+sy+"  Z "+sz,32,467,INK,.69,68);
        p.text("АРКУШ "+options.width()+" × "+options.height()+"  /  "+views.size()+" "+(views.size()==1?"ВИД":views.size()<5?"ВИДИ":"ВИДІВ"),480,467,INK,.69,44);
        String scale=options.scale()==1?"МАСШТАБ: ВПИСАНО ОКРЕМО":options.ratio()>0?"МАСШТАБ: 1:"+options.ratio():"СПІЛЬНИЙ МАСШТАБ: "+ratio(options,layout.sharedPixelsPerBlock());
        if(!layout.fits())scale="ОБРАНИЙ МАСШТАБ НЕ ВМІЩУЄТЬСЯ";
        p.text(scale,32,481,layout.fits()?FAINT:0xff9c6148,.62,75);
        p.text(options.muted()?"МАТЕРІАЛИ / ВИЦВІЛІ ТОНИ":"МАТЕРІАЛИ / ГРАФІТ",480,481,FAINT,.62,45);
        return p.pixels;
    }
    private void panel(Panel panel,PosterOptions options){
        var v=panel.view();double x=panel.x(),y=panel.y(),w=panel.width(),h=panel.height();
        if(x>32)line(x,y+2,x,y+h-9,0xffb8ad92,.3);
        if(y>88)line(x+7,y-4,x+w-7,y-4,0xffb8ad92,.3);
        text(LABELS[v.direction()],x+9,y+2,INK,.7,35);
        double dw=v.spanW()*panel.pixelsPerBlock(),dh=v.spanH()*panel.pixelsPerBlock();
        double dx=x+(w-dw)/2,dy=y+29+(h-69-dh)/2;
        dashed(dx-5,dy+dh/2,dx+dw+5,dy+dh/2);dashed(dx+dw/2,dy-5,dx+dw/2,dy+dh+5);
        paint(v,dx,dy,dw,dh,options.muted());
        int dim=0xffaa9b7e;
        line(dx,dy+dh+7,dx+dw,dy+dh+7,dim,.35);line(dx-2,dy+dh+9,dx+2,dy+dh+5,INK,.4);line(dx+dw-2,dy+dh+9,dx+dw+2,dy+dh+5,INK,.4);
        line(dx-7,dy,dx-7,dy+dh,dim,.3);line(dx-9,dy+2,dx-5,dy-2,INK,.35);line(dx-9,dy+dh+2,dx-5,dy+dh-2,INK,.35);
        text(number(v.spanW())+" × "+number(v.spanH())+" БЛ.",x+10,y+h-27,INK,.66,40);
        text(ratio(options,panel.pixelsPerBlock()),x+10,y+h-13,FAINT,.58,35);
    }
    private static String number(double value){return Math.abs(value-Math.rint(value))<.01?Long.toString(Math.round(value)):String.format(Locale.ROOT,"%.1f",value);}
    public static String ratio(PosterOptions o,double ppu){return ppu<=0?"—":"1:"+number(BASE_W/(double)o.width()/ppu);}
    private void paint(Projection.View v,double dx,double dy,double dw,double dh,boolean muted){
        int n=v.resolution();double rasterScale=(n-12.0)/Math.max(v.spanW(),v.spanH());
        double ox=(n-v.spanW()*rasterScale)/2,oy=(n-v.spanH()*rasterScale)/2;
        int[] tones=tones(muted);int x0=(int)Math.floor(dx*factor)-1,x1=(int)Math.ceil((dx+dw)*factor)+1;
        int y0=(int)Math.floor(dy*factor)-1,y1=(int)Math.ceil((dy+dh)*factor)+1;
        for(int y=Math.max(0,y0);y<Math.min(height,y1);y++)for(int x=Math.max(0,x0);x<Math.min(width,x1);x++){
            double u=ox+((x+.5)/factor-dx)/dw*v.spanW()*rasterScale-.5;
            double vv=oy+((y+.5)/factor-dy)/dh*v.spanH()*rasterScale-.5;
            int ax=(int)Math.floor(u),ay=(int)Math.floor(vv);double fx=u-ax,fy=vv-ay;
            int bg=pixels[x+y*width],a=sample(v,ax,ay,tones,bg),b=sample(v,ax+1,ay,tones,bg),c=sample(v,ax,ay+1,tones,bg),d=sample(v,ax+1,ay+1,tones,bg);
            pixels[x+y*width]=mix(mix(a,b,fx),mix(c,d,fx),fy);
        }
    }
    private static int sample(Projection.View v,int x,int y,int[] tones,int bg){
        if(x<0||y<0||x>=v.resolution()||y>=v.resolution())return bg;
        int code=v.pixels()[x+y*v.resolution()]&255;if(code==0)return bg;
        int tone=tones[code],grain=((bg>>16)&255)-229;
        return 0xff000000|clamp(((tone>>16)&255)+grain)<<16|clamp(((tone>>8)&255)+grain)<<8|clamp((tone&255)+grain);
    }
    private static int[] tones(boolean muted){
        int[] out=new int[256];for(int c=1;c<256;c++){
            int material=c&15;if(material==0)continue;
            int fill=mix(PARCHMENT,0xff000000|(muted?WASH[material]:0xbcb7a8),muted?.66:.29);
            // Deep bores and dark insets need stronger graphite than an ordinary material wash.
            if(material==13)fill=mix(PARCHMENT,0xff5b5446,muted?.82:.72);
            fill=mix(fill,0xff776c58,((c>>6)&3)*.035);
            out[c]=switch(c&0x30){case Projection.JOINT->mix(fill,0xffa39780,.32);case Projection.DETAIL->mix(fill,0xff76624b,.67);case Projection.OUTLINE->mix(fill,INK,.96);default->fill;};
        }return out;
    }
    private static int clamp(int n){return Math.max(0,Math.min(255,n));}
    private static int mix(int a,int b,double t){int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bb=(int)Math.round((a&255)*(1-t)+(b&255)*t);return 0xff000000|r<<16|g<<8|bb;}
    private void background(){
        Random random=new Random(0x435245415445L);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            double edge=Math.min(Math.min(x,width-1-x),Math.min(y,height-1-y))/factor;
            int shade=(int)(10*Math.exp(-edge/13.0))+random.nextInt(5)-2;
            pixels[x+y*width]=0xff000000|(229-shade)<<16|(218-shade)<<8|(188-shade);
        }
    }
    private void dashed(double x,double y,double xx,double yy){
        double distance=Math.hypot(xx-x,yy-y);if(distance==0)return;
        for(double a=0;a<distance;a+=7){double b=Math.min(distance,a+3);line(x+(xx-x)*a/distance,y+(yy-y)*a/distance,x+(xx-x)*b/distance,y+(yy-y)*b/distance,0xffc1b79f,.26);}
    }
    private void line(double x,double y,double xx,double yy,int color,double stroke){
        int ax=(int)Math.round(x*factor),ay=(int)Math.round(y*factor),bx=(int)Math.round(xx*factor),by=(int)Math.round(yy*factor);
        int dx=Math.abs(bx-ax),sx=ax<bx?1:-1,dy=-Math.abs(by-ay),sy=ay<by?1:-1,error=dx+dy;int radius=Math.max(0,(int)Math.ceil(stroke*factor/2)-1);
        for(;;){for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++)set(ax+a,ay+b,color);if(ax==bx&&ay==by)break;int e=2*error;if(e>=dy){error+=dy;ax+=sx;}if(e<=dx){error+=dx;ay+=sy;}}
    }
    private void frame(double x,double y,double xx,double yy,int c){line(x,y,xx,y,c,.5);line(xx,y,xx,yy,c,.5);line(xx,yy,x,yy,c,.5);line(x,yy,x,y,c,.5);}
    private void set(int x,int y,int color){if(x>=0&&y>=0&&x<width&&y<height)pixels[x+y*width]=color;}
    private void text(String s,double x,double y,int color,double scale,int max){
        int count=0;double unit=factor*scale/2;
        for(int code:s.codePoints().toArray()){
            if(count++>=max||x+8*scale>740)break;byte[] glyph=GLYPHS.getOrDefault(code,GLYPHS.get((int)'?'));
            if(glyph!=null)for(int py=0;py<28;py++)for(int px=0;px<18;px++){
                int alpha=glyph[px+py*18]&255;if(alpha==0)continue;
                int x0=(int)Math.round(x*factor+px*unit),x1=(int)Math.round(x*factor+(px+1)*unit),y0=(int)Math.round(y*factor+py*unit),y1=(int)Math.round(y*factor+(py+1)*unit);
                for(int yy=y0;yy<y1;yy++)for(int xx=x0;xx<x1;xx++)if(xx>=0&&yy>=0&&xx<width&&yy<height)pixels[xx+yy*width]=mix(pixels[xx+yy*width],color,alpha/255.0);
            }
            x+=8.3*scale;
        }
    }
}
