package ua.townyrecruits.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import ua.townyrecruits.RegimentalBanners.*;
import ua.townyrecruits.map.ArmyMapData.*;

/** CPU rasterizer; pattern masks are read from the current resource pack, not re-created approximations. */
public final class ArmyMapRaster {
    private ArmyMapRaster() {}
    public static BufferedImage unit(Kind kind,Relation relation,boolean routing) {
        BufferedImage image=new BufferedImage(18,18,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(0xd020252a,true));
        Shape shape=switch(relation){case ALLY->new java.awt.geom.Ellipse2D.Double(1,1,15,15);case NEUTRAL->new Polygon(new int[]{9,17,9,1},new int[]{0,9,17,9},4);case ENEMY->new Polygon(new int[]{9,17,1},new int[]{0,16,16},3);};
        g.fill(shape);g.setColor(new Color(relation.color));g.setStroke(new BasicStroke(2));g.draw(shape);g.setColor(Color.WHITE);g.setStroke(new BasicStroke(1));
        switch(kind) {
            case INFANTRY -> {g.drawLine(6,12,11,5);g.drawLine(5,10,8,13);}
            case PIKE -> {g.drawLine(9,14,9,4);g.drawLine(9,3,7,6);g.drawLine(9,3,11,6);}
            case RANGED -> {g.drawArc(4,4,8,10,-90,180);g.drawLine(8,4,8,14);g.drawLine(4,9,13,9);}
            case CAVALRY, MOUNTED_RANGED -> {g.fillPolygon(new int[]{5,5,9,9,12,12,8,7},new int[]{13,9,5,3,6,10,9,13},8);if(kind==Kind.MOUNTED_RANGED){g.setColor(new Color(relation.color));g.drawLine(6,10,12,7);}}
        }
        if(routing){g.setColor(new Color(0xffa526));g.fillRect(13,1,3,6);g.fillRect(13,8,3,2);}g.dispose();return image;
    }
    public static BufferedImage flag(Design design,Relation relation,int[] colors,Function<String,BufferedImage> masks) {
        BufferedImage image=new BufferedImage(28,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(0xd020252a,true));g.fillRect(3,0,22,30);g.setColor(new Color(relation.color));g.drawRect(3,0,22,30);
        g.setColor(new Color(0xffd6c098,true));g.fillRect(5,2,2,28);g.fillRect(4,2,4,2);g.dispose();
        for(int y=0;y<24;y++)for(int x=0;x<12;x++) {
            int color=colors[design.base()];
            for(Layer layer:design.layers()) {
                BufferedImage mask=masks.apply(layer.pattern());if(mask==null||mask.getWidth()<22||mask.getHeight()<42)continue;
                int pixel=mask.getRGB(1+x*20/12,1+y*40/24),alpha=(pixel>>>24)&255;
                color=blend(color,colors[layer.color()],alpha);
            }
            image.setRGB(8+x,3+y,0xff000000|color);
        }
        return image;
    }
    static int blend(int base,int layer,int alpha){int out=0;for(int shift:new int[]{0,8,16})out|=((((base>>shift)&255)*(255-alpha)+((layer>>shift)&255)*alpha+127)/255)<<shift;return out;}
}
