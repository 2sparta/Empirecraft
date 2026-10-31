package ua.posters;
import java.util.*;import java.nio.file.*;import java.util.zip.*;
import org.joml.Matrix4d;import net.minecraft.nbt.CompoundTag;
/** Reproduction of a one-block-wide, three-block-high, three-block-long gun on a CBC mount. */
public final class CBCRegressionTest {
    static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static int pixel(Projection.View view,double x,double y,double z,double[] bounds,int direction){
        double u=direction==0?x:direction==1?3-z:x,v=direction==0?z:3-y;
        double minU=direction==0?bounds[0]:direction==1?3-bounds[5]:bounds[0],minV=direction==0?bounds[2]:3-bounds[4];
        double s=(view.resolution()-12.0)/Math.max(view.spanW(),view.spanH()),ox=(view.resolution()-view.spanW()*s)/2,oy=(view.resolution()-view.spanH()*s)/2;
        int px=(int)Math.floor(ox+(u-minU)*s),py=(int)Math.floor(oy+(v-minV)*s);return view.pixels()[px+py*view.resolution()]&255;
    }
    static double[] bounds(List<Mesh.Triangle> mesh){return CompatibilityTest.bounds(mesh);}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),mods=Path.of(args[1]);Map<String,ZipFile> jars=new HashMap<>();
        for(String name:List.of("create","createbigcannons"))try(var paths=Files.list(mods)){
            Path p=paths.filter(f->f.getFileName().toString().startsWith(name+"-")&&f.toString().endsWith(".jar")).findFirst().orElse(null);
            if(p==null){for(var jar:jars.values())jar.close();System.out.println("SKIP CBC fixture: missing "+name);return;}jars.put(name,new ZipFile(p.toFile()));
        }
        ModelAssets assets=new ModelAssets((name,path)->{var jar=jars.get(name);if(jar==null)return null;var e=jar.getEntry(path);return e==null?null:jar.getInputStream(e).readAllBytes();});JsonModels models=new JsonModels(assets);CBCModels cbc=new CBCModels(models);
        Map<String,String> state=Map.of("facing","north","vertical_direction","down","assembly_powered","false","fire_powered","false");
        var base=models.block("createbigcannons:cannon_mount",state,0x898989);var mount=cbc.mount(state,180,0,0x898989);
        check(mount.size()>base.size()+60,"mount includes dynamic models, axle and shafts");double[] floor=bounds(mount);check(floor[4]>2.75,"rotating frame reaches the gun above the base");check(floor[0]<0&&floor[3]>1,"axle and support protrusions retained");
        var chamber=models.block("createbigcannons:cast_iron_cannon_chamber",Map.of("facing","north"),0x898989);check(chamber.size()==12,"cube-column parent available on dedicated server");check(chamber.stream().allMatch(t->t.mask()!=null),"opaque chamber textures retained");
        var barrel=models.block("createbigcannons:cast_iron_cannon_barrel",Map.of("facing","north"),0x898989);var end=models.block("createbigcannons:cast_iron_cannon_end",Map.of("facing","south"),0x898989);
        List<Mesh.Triangle> gun=new ArrayList<>();gun.addAll(Mesh.transform(barrel,new Matrix4d().translate(0,2,0),1,Integer.MIN_VALUE));gun.addAll(Mesh.transform(chamber,new Matrix4d().translate(0,2,1),2,Integer.MIN_VALUE));gun.addAll(Mesh.transform(end,new Matrix4d().translate(0,2,2),3,Integer.MIN_VALUE));
        List<Mesh.Triangle> scene=new ArrayList<>(Mesh.transform(mount,new Matrix4d().translate(0,0,1),0,Integer.MIN_VALUE));scene.addAll(gun);double[] box=bounds(scene);
        List<Projection.View> views=new ArrayList<>();for(int d=0;d<6;d++)views.add(Projection.project(List.of(),scene,1,3,3,d,384));
        check(pixel(views.get(2),1.05,1.25,1.5,box,2)!=0,"front support rail is present across former gap");
        check(pixel(views.get(1),1.05,1.25,1.5,box,1)!=0,"side support is present across former gap");
        check((pixel(views.get(2),.5,2.5,0,box,2)&15)==13,"actual muzzle texture draws a dark bore");
        List<Mesh.Triangle> old=new ArrayList<>(Mesh.transform(base,new Matrix4d().translate(0,0,1),0,Integer.MIN_VALUE));old.addAll(gun);var broken=Projection.project(List.of(),old,1,3,3,2,384);
        check(pixel(broken,1.05,1.25,1.5,bounds(old),2)==0,"fixture reproduces missing support without dynamic parts");
        var chamberFront=Projection.project(List.of(),chamber,1,1,1,2,384);check((chamberFront.pixels()[192+192*384]&15)==13,"chamber end preserves its bore feature");
        var bodyTexture=assets.texture("createbigcannons:block/cannon_barrel/cast_iron_cannon_barrel_side");check(bodyTexture.mask()!=null&&bodyTexture.mask().details(),"side texture features enabled");
        Set<Integer> shades=new HashSet<>();for(int y=0;y<16;y++)for(int x=0;x<16;x++)shades.add(bodyTexture.mask().appearance((x+.5)/16,(y+.5)/16,Projection.material(bodyTexture.rgb()),0,true));check(shades.size()>1,"barrel grooves no longer averaged into one colour");
        int[] swatch=new int[256];Arrays.fill(swatch,0xff888888);for(int y=5;y<11;y++)for(int x=5;x<11;x++)swatch[x+y*16]=0xff050505;
        Mesh.Mask dark=new Mesh.Mask(16,16,swatch,true,110);check((dark.appearance(.5,.5,1,0,true)&15)==13,"opaque dark details are retained");check((dark.appearance(.5,.5,1,0,false)&15)==1,"fine details suppressed when too small to read");
        for(boolean muted:List.of(true,false)){
            var ink=Projection.project(List.of(new Projection.Box(0,0,0,1,1,1,0,Projection.MATERIALS[13])),1,1,1,2);
            var metal=Projection.project(List.of(new Projection.Box(0,0,0,1,1,1,0,Projection.MATERIALS[7])),1,1,1,2);
            int at=768+512*1536;var style=new PosterOptions(0,0,muted);
            int a=Paper.compose("",1,1,1,List.of(ink),style)[at],b=Paper.compose("",1,1,1,List.of(metal),style)[at];
            check(Mesh.Mask.luma(a)+35<Mesh.Mask.luma(b),"bore remains visibly darker than metal on paper, muted="+muted);
        }
        var upside=cbc.mount(Map.of("facing","north","vertical_direction","up","assembly_powered","false","fire_powered","false"),180,0,0x898989);double[] ceiling=bounds(upside);check(Math.abs(ceiling[1]-(1-floor[4]))<.00001,"ceiling mount mirrors the support height correctly");
        var quarter=cbc.mount(state,90,0,0x898989);double[] rotated=bounds(quarter);check(rotated[5]-rotated[2]>floor[5]-floor[2]+.1,"saved yaw rotates the frame and axle");
        var pitched=cbc.mount(state,180,35,0x898989);check(!pitched.equals(mount),"saved pitch moves the trunnion axle");
        CompoundTag tag=DrawingData.make("CBC · ПОВНИЙ ЛАФЕТ",1,3,3,views.subList(0,3));check(DrawingData.valid(tag)&&DrawingData.fits(tag),"full cannon drawing survives compression and scale validation");
        ProjectionTest.write(out.resolve("regression-cannon.ppm"),DrawingData.pixels(tag),1536,1024);
        for(var jar:jars.values())jar.close();System.out.println("PASS "+checks+" CBC screenshot-regression checks: complete 1x3x3 gun, mount continuity, muzzle bore, grooves, yaw/pitch and ceiling pose");
    }
}
