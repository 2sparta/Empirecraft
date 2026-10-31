package ua.posters;

import java.io.*;
import java.util.*;

/** The same deterministic parchment compositor powers the real item and the preview artifact. */
public final class LegacyPaper {
    public static final int W=768,H=512;
    private static final int BLACK=0xff27241d, FAINT=0xff8a8068;
    private static final Map<Integer,byte[]> GLYPHS=loadFont();
    private static final String[] LABELS={"01 / ЗГОРИ · TOP","02 / СПРАВА · RIGHT","03 / СПЕРЕДУ · FRONT","04 / ЗЛІВА · LEFT","05 / ЗЗАДУ · BACK","06 / ЗНИЗУ · BOTTOM"};
    private final int[] pixels=new int[W*H];
    private static Map<Integer,byte[]> loadFont(){
        Map<Integer,byte[]> m=new HashMap<>();
        try(var stream=LegacyPaper.class.getResourceAsStream("/assets/create_posters/font.bin")){
            if(stream==null) throw new IOException("Missing poster font");
            var in=new DataInputStream(stream);int count=in.readInt();
            for(int i=0;i<count;i++){int c=in.readInt();byte[] bits=in.readNBytes(28);if(bits.length!=28)throw new IOException("font");m.put(c,bits);}
        }catch(IOException ex){throw new ExceptionInInitializerError(ex);}
        return m;
    }
    public static int[] compose(String title,int sx,int sy,int sz,List<Projection.View> views){
        if(views.isEmpty() || views.size()>6)throw new IllegalArgumentException("views");
        LegacyPaper p=new LegacyPaper();p.background();p.frame(16,16,W-17,H-17,BLACK);p.frame(20,20,W-21,H-21,FAINT);
        p.text("CREATE  /  КРЕСЛЕННЯ",32,30,FAINT,1,52);
        p.text(title.isBlank()?"БЕЗ НАЗВИ":title.toUpperCase(Locale.ROOT),32,51,BLACK,1,62);
        p.line(32,77,W-33,77,BLACK);
        int cols=views.size()==1?1:views.size()==2||views.size()==4?2:3;
        int rows=(views.size()+cols-1)/cols, cw=(W-64)/cols,ch=366/rows;
        for(int i=0;i<views.size();i++){
            Projection.View view=views.get(i);int x=32+(i%cols)*cw,y=88+(i/cols)*ch;
            if(i%cols>0)p.line(x-8,y+2,x-8,y+ch-12,FAINT);
            p.text(LABELS[view.direction()],x+4,y+2,BLACK,1,cols==3?27:45);
            int availW=cw-25,availH=ch-53,size=Math.min(availW,availH);
            int dx=x+(cw-size)/2,dy=y+24+(availH-size)/2;
            for(int py=0;py<size;py++)for(int px=0;px<size;px++){
                // Area sampling preserves subpixel lines when laying out six views.
                int ax=px*Projection.RES/size,ay=py*Projection.RES/size;
                int bx=Math.max(ax+1,(px+1)*Projection.RES/size),by=Math.max(ay+1,(py+1)*Projection.RES/size);
                boolean ink=false;
                for(int yy=ay;yy<by&&!ink;yy++)for(int xx=ax;xx<bx;xx++)if(Projection.ink(view.ink(),xx,yy)){ink=true;break;}
                if(ink)p.set(dx+px,dy+py,BLACK);
            }
            p.text(view.width()+" × "+view.height()+" БЛОКІВ",x+8,y+ch-23,FAINT,1,35);
            // Registration marks and dimension ticks.
            p.line(dx,dy+size+2,dx+size-1,dy+size+2,FAINT);
            p.line(dx,dy+size-1,dx,dy+size+5,BLACK);p.line(dx+size-1,dy+size-1,dx+size-1,dy+size+5,BLACK);
        }
        p.line(32,458,W-33,458,BLACK);
        p.text("РОЗМІРИ / X "+sx+"  Y "+sy+"  Z "+sz,32,472,BLACK,1,60);
        p.text("МАСШТАБ: ВПИСАНО",480,472,FAINT,1,28);
        return p.pixels;
    }
    private void background(){
        Random random=new Random(0x435245415445L);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double edge=Math.min(Math.min(x,W-1-x),Math.min(y,H-1-y));
            int shade=(int)(11*Math.exp(-edge/14.0))+random.nextInt(7)-3;
            int fiber=(x%137==0||y%83==0)?2:0;
            pixels[x+y*W]=0xff000000|((229-shade-fiber)<<16)|((218-shade-fiber)<<8)|(188-shade-fiber);
        }
    }
    private void set(int x,int y,int color){if(x>=0&&y>=0&&x<W&&y<H)pixels[x+y*W]=color;}
    private void line(int x,int y,int xx,int yy,int color){
        int dx=Math.abs(xx-x),sx=x<xx?1:-1,dy=-Math.abs(yy-y),sy=y<yy?1:-1,err=dx+dy;
        for(;;){set(x,y,color);if(x==xx&&y==yy)break;int e=2*err;if(e>=dy){err+=dy;x+=sx;}if(e<=dx){err+=dx;y+=sy;}}
    }
    private void frame(int x,int y,int xx,int yy,int c){line(x,y,xx,y,c);line(xx,y,xx,yy,c);line(xx,yy,x,yy,c);line(x,yy,x,y,c);}
    private void text(String s,int x,int y,int color,int scale,int max){
        int n=0;
        for(int c:s.codePoints().toArray()){
            if(n++>=max || x+8*scale>=W-24)break;
            byte[] bits=GLYPHS.getOrDefault(c,GLYPHS.get((int)'?'));
            if(bits!=null)for(int py=0;py<14;py++)for(int px=0;px<16;px++){
                int i=py*16+px;
                if((bits[i>>3]&(1<<(i&7)))!=0)for(int a=0;a<scale;a++)for(int b=0;b<scale;b++)set(x+px*scale+a,y+py*scale+b,color);
            }
            x+=9*scale;
        }
    }
}
