package guardtest;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import ua.townyrecruits.RegimentalBanners.*;
import ua.townyrecruits.client.ArmyMapRaster;
import ua.townyrecruits.map.ArmyMapData.*;

public final class ArmyMapRasterTest {
    static BufferedImage mask(String name)throws Exception {try(var in=ArmyMapRasterTest.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/entity/banner/"+name+".png")){assert in!=null;return ImageIO.read(in);}}
    static int hash(BufferedImage image){return Arrays.hashCode(image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));}
    public static void main(String[] args)throws Exception {
        System.setProperty("java.awt.headless","true");Set<Integer> images=new HashSet<>();
        BufferedImage sheet=new BufferedImage(240,120,BufferedImage.TYPE_INT_ARGB);var graphics=sheet.createGraphics();
        for(Relation relation:Relation.values())for(Kind kind:Kind.values())for(boolean routed:new boolean[]{false,true}) {
            var image=ArmyMapRaster.unit(kind,relation,routed);images.add(hash(image));assert image.getWidth()==18&&image.getHeight()==18;
            if(!routed)graphics.drawImage(image,kind.ordinal()*40+8,relation.ordinal()*40+10,null);
        }
        assert images.size()==30 : "Troop types, relation colors and routing warnings must remain distinct";
        int[] colors=new int[16];Arrays.fill(colors,0xffffff);colors[1]=0xff0000;colors[2]=0x0011ff;
        BufferedImage bottom=mask("stripe_bottom"),top=mask("stripe_top");Design design=new Design(0,List.of(new Layer("bs",1)));
        var flag=ArmyMapRaster.flag(design,Relation.ALLY,colors,id->bottom);
        assert (flag.getRGB(14,6)&0xffffff)==0xffffff;
        assert (flag.getRGB(14,25)&0xffffff)==0xff0000 : "The real vanilla lower-stripe mask must affect the lower cloth";
        assert hash(flag)==hash(ArmyMapRaster.flag(design,Relation.ALLY,colors,id->bottom));
        assert hash(flag)!=hash(ArmyMapRaster.flag(design,Relation.ALLY,colors,id->top));
        assert hash(flag)!=hash(ArmyMapRaster.flag(new Design(0,List.of(new Layer("bs",2))),Relation.ALLY,colors,id->bottom));
        for(Relation relation:Relation.values())graphics.drawImage(ArmyMapRaster.flag(design,relation,colors,id->bottom),205,relation.ordinal()*40+3,null);
        graphics.dispose();Path output=Path.of("towny-recruits/assets/army-map-preview.png");if(!Files.isDirectory(output.getParent()))output=Path.of("assets/army-map-preview.png");Files.createDirectories(output.getParent());ImageIO.write(sheet,"PNG",output.toFile());
        System.out.println("PASS 30 distinct troop/status icons, real vanilla banner masks/UV crop, dye colors, deterministic heraldry and generated map legend preview");
    }
}
