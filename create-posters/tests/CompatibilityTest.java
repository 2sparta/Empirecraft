package ua.posters;
import java.util.*;import java.nio.file.*;import java.io.*;import java.util.zip.*;
import javax.imageio.ImageIO;import java.awt.image.BufferedImage;
import com.google.gson.*;import net.minecraft.nbt.*;import org.joml.Matrix4d;import org.joml.Quaterniond;import org.joml.Vector3d;
public final class CompatibilityTest {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static double[] bounds(List<Mesh.Triangle> mesh){double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(var t:mesh)for(var v:List.of(t.a(),t.b(),t.c())){var p=v.p();b[0]=Math.min(b[0],p.x());b[1]=Math.min(b[1],p.y());b[2]=Math.min(b[2],p.z());b[3]=Math.max(b[3],p.x());b[4]=Math.max(b[4],p.y());b[5]=Math.max(b[5],p.z());}return b;}
    static int count(Projection.View v,int weight){int n=0;for(byte b:v.pixels())if((b&15)!=0&&(weight<0||(b&0x30)==weight))n++;return n;}
    static CompoundTag material(String name){CompoundTag t=new CompoundTag();t.m_128359_("Name",name);return t;}
    static byte[] text(String s){return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]),mods=Path.of(args[1]);
        for(int direction=0;direction<4;direction++){
            double yaw=direction*90;Quaterniond q=new Quaterniond().rotationY(Math.toRadians(PosterSurface.rotation((float)yaw)));
            Vector3d outward=new Vector3d(0,0,1).rotate(q),right=new Vector3d(outward.z,0,-outward.x);
            Vector3d x=new Vector3d(1,0,0).rotate(q),up=new Vector3d(0,1,0).rotate(q);
            check(x.dot(right)>.999999,"readable left-to-right on wall "+direction);check(up.y>.999999,"upright on wall "+direction);
            var corners=PosterSurface.corners(6,4);Vector3d first=new Vector3d(corners[0].x(),corners[0].y(),PosterSurface.FRONT_Z).rotate(q),last=new Vector3d(corners[3].x(),corners[3].y(),PosterSurface.FRONT_Z).rotate(q);
            check(first.dot(right)<last.dot(right)&&corners[0].u()==0&&corners[3].u()==1,"UV orientation on wall "+direction);check(first.dot(outward)>0&&PosterSurface.NORMAL_Z>0,"front is outside wall "+direction);
        }
        Map<String,byte[]> files=new HashMap<>();
        files.put("assets/test/blockstates/pane.json",text("{\"variants\":{\"\":{\"model\":\"test:block/child\"}}}"));
        files.put("assets/test/models/block/child.json",text("{\"parent\":\"test:block/parent\",\"textures\":{\"all\":\"test:block/holes\"}}"));
        files.put("assets/test/models/block/parent.json",text("{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,0],\"faces\":{\"north\":{\"texture\":\"#all\",\"uv\":[0,0,16,16]}}}]}"));
        BufferedImage mask=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<16;y++)for(int x=0;x<16;x++)mask.setRGB(x,y,x>=4&&x<12&&y>=4&&y<12?0:0xffad8d56);
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(mask,"PNG",png);files.put("assets/test/textures/block/holes.png",png.toByteArray());
        ModelAssets assets=new ModelAssets((n,p)->files.get(p));JsonModels json=new JsonModels(assets);var pane=json.block("test:pane",Map.of(),0x898989);
        check(pane.size()==2,"inherited model with resolved texture");check(pane.get(0).mask()!=null,"texture transparency available");
        var cutout=Projection.project(List.of(),pane,1,1,1,2,384);check(cutout.pixels()[192+192*384]==0,"transparent texture hole preserved");check(count(cutout,-1)>1000,"opaque cutout model retained");
        check(JsonModels.variant("facing=north,open=false",Map.of("facing","north","open","false")),"blockstate variant selection");
        check(JsonModels.matches(JsonParser.parseString("{\"OR\":[{\"north\":\"true\"},{\"axis\":\"x|z\"}]}"),Map.of("north","false","axis","z")),"multipart OR and value alternatives");
        files.put("assets/test/models/block/slope.json",text("{\"elements\":[{\"from\":[0,8,0],\"to\":[16,8,16],\"rotation\":{\"axis\":\"x\",\"angle\":45,\"origin\":[8,8,8]},\"faces\":{\"up\":{\"texture\":\"#missing\"}}}]}"));
        var slope=json.model("test:block/slope",0xad8d56);check(slope.size()==2,"rotated flat model elements");var sloped=Projection.project(List.of(),slope,1,1,1,0,384);
        check(count(sloped,Projection.DETAIL)<count(sloped,-1)/20,"no false contour on a smooth sloped surface");
        var outer=Mesh.box(0,0,0,1,1,1,0,0xad8d56);var both=new ArrayList<>(outer);both.addAll(Mesh.box(.2,.2,.2,.8,.8,.8,1,0xa34040));
        for(int d=0;d<6;d++)check(Arrays.equals(Projection.project(List.of(),outer,1,1,1,d,384).pixels(),Projection.project(List.of(),both,1,1,1,d,384).pixels()),"hidden model faces "+d);
        CompoundTag single=new CompoundTag();single.m_128365_("Material",material("minecraft:oak_planks"));check(CopycatMaterials.read(single).get("default").m_128461_("Name").equals("minecraft:oak_planks"),"Create single copycat NBT");
        CompoundTag multi=new CompoundTag(),store=new CompoundTag(),bottom=new CompoundTag(),top=new CompoundTag();bottom.m_128365_("material",material("minecraft:oak_planks"));top.m_128365_("material",material("minecraft:stone"));store.m_128365_("bottom",bottom);store.m_128365_("top",top);multi.m_128365_("material_data",store);
        var materials=CopycatMaterials.read(multi);check(materials.size()==2&&materials.get("top").m_128461_("Name").equals("minecraft:stone"),"Copycats+ multistate NBT");
        List<Projection.Box> cube=List.of(new Projection.Box(0,0,0,1,1,1,0));var halves=new ArrayList<>(Mesh.clip(cube,0,0,0,1,.5,1,0xad8d56));halves.addAll(Mesh.clip(cube,0,.5,0,1,1,1,0x898989));
        check(halves.size()==2&&halves.get(0).y1()==halves.get(1).y0(),"copycat part clipping respects shared boundary");var twoColors=Projection.project(halves,1,1,1,2,384);Set<Integer> colors=new HashSet<>();for(byte b:twoColors.pixels())if((b&15)!=0)colors.add(b&15);check(colors.size()==2,"both copycat materials appear in projection");
        Map<String,ZipFile> jars=new HashMap<>();for(String name:List.of("supplementaries","createbigcannons","copycats"))try(var paths=Files.list(mods)){Path p=paths.filter(f->f.getFileName().toString().startsWith(name+"-")&&f.toString().endsWith(".jar")).findFirst().orElse(null);if(p==null){for(var open:jars.values())open.close();System.out.println("PASS "+checks+" basic compatibility checks; installed-mod fixtures skipped (missing "+name+")");return;}jars.put(name,new ZipFile(p.toFile()));}
        ModelAssets installed=new ModelAssets((n,p)->{ZipFile jar=jars.get(n);if(jar==null)return null;ZipEntry e=jar.getEntry(p);return e==null?null:jar.getInputStream(e).readAllBytes();});JsonModels models=new JsonModels(installed);
        var barrelUp=models.block("createbigcannons:cast_iron_cannon_barrel",Map.of("facing","up"),0x898989);check(!barrelUp.isEmpty(),"real CBC barrel model loaded");double[] bu=bounds(barrelUp);check(Math.abs(bu[3]-bu[0]-.75)<1e-8&&Math.abs(bu[4]-bu[1]-1)<1e-8,"actual CBC barrel dimensions");
        for(String facing:List.of("north","south","east","west","up","down")){
            var barrel=models.block("createbigcannons:cast_iron_cannon_barrel",Map.of("facing",facing),0x898989);check(!barrel.isEmpty(),"CBC barrel "+facing);var b=bounds(barrel);int axis=facing.equals("east")||facing.equals("west")?0:facing.equals("up")||facing.equals("down")?1:2;check(Math.abs(b[axis+3]-b[axis]-1)<1e-8,"CBC facing/axis "+facing);
        }
        var small=models.block("createbigcannons:bronze_autocannon_barrel",Map.of("facing","east","assembled","false","end","flanged"),0xb76d45);check(!small.isEmpty(),"real CBC autocannon flanged model");
        var mount=models.block("createbigcannons:cannon_mount",Map.of("facing","north","vertical_direction","down","assembly_powered","false","fire_powered","false"),0x898989);check(mount.size()>12,"CBC mount multipart geometry");
        var factory=JavaModelReader.read(installed.resource("supplementaries","net/mehvahdjukaar/supplementaries/client/renderers/tiles/CannonBlockTileRenderer.class"),"createMesh");check(factory.cubeCount()==5&&factory.find("head_pivot")!=null,"real Supplementaries renderer factory read without loading client classes");
        SupplementariesModels supplementaries=new SupplementariesModels(installed);var cannon=supplementaries.cannon("up",0,0);check(cannon.size()==60,"Supplementaries cannon complete model");
        var aimed=supplementaries.cannon("up",45,35);check(!Arrays.equals(bounds(cannon),bounds(aimed)),"saved yaw and pitch affect the cannon");
        for(String facing:List.of("up","down","north","south","east","west"))check(supplementaries.cannon(facing,60,25).size()==60,"Supplementaries support face "+facing);
        int discovered=0,parsed=0;var jar=jars.get("createbigcannons");for(var entries=jar.entries();entries.hasMoreElements();){var entry=entries.nextElement();String n=entry.getName();if(!n.startsWith("assets/createbigcannons/blockstates/")||!n.endsWith(".json"))continue;discovered++;JsonObject state=JsonParser.parseString(new String(jar.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();Map<String,String> props=new HashMap<>();
            if(state.has("variants")){var first=state.getAsJsonObject("variants").entrySet().iterator();if(first.hasNext()){String cond=first.next().getKey();if(!cond.isBlank())for(String pair:cond.split(",")){String[] p=pair.split("=",2);if(p.length==2)props.put(p[0],p[1].split("\\|")[0]);}}}
            String id="createbigcannons:"+n.substring(n.lastIndexOf('/')+1,n.length()-5);if(!models.block(id,props,0x898989).isEmpty())parsed++;
        }
        check(parsed>50,"generic model reader covers CBC model library");
        List<Mesh.Triangle> gallery=new ArrayList<>();gallery.addAll(Mesh.transform(cannon,new Matrix4d().translate(1,0,1),0,Integer.MIN_VALUE));gallery.addAll(Mesh.transform(aimed,new Matrix4d().translate(3,0,1),1,Integer.MIN_VALUE));gallery.addAll(Mesh.transform(barrelUp,new Matrix4d().translate(5,0,1),2,Integer.MIN_VALUE));gallery.addAll(Mesh.transform(small,new Matrix4d().translate(7,0,1),3,Integer.MIN_VALUE));gallery.addAll(Mesh.transform(mount,new Matrix4d().translate(9,0,1),4,Integer.MIN_VALUE));
        List<Projection.View> galleryViews=new ArrayList<>();for(int d:new int[]{0,1,2})galleryViews.add(Projection.project(List.of(),gallery,11,3,3,d,384));CompoundTag drawing=DrawingData.make("Моделі гармат",11,3,3,galleryViews);check(DrawingData.valid(drawing),"mod-model gallery NBT");ProjectionTest.write(output.resolve("preview-mods.ppm"),DrawingData.pixels(drawing),1536,1024);
        for(var z:jars.values())z.close();System.out.println("PASS "+checks+" compatibility checks; CBC JSON models: "+parsed+"/"+discovered+"; actual Supplementaries factory and Copycats NBT; readable UVs on four walls");
    }
}
