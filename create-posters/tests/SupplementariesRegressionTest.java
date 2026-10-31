package ua.posters;
import java.util.*;import java.nio.file.*;import java.util.zip.*;import org.joml.Matrix4d;import net.minecraft.nbt.CompoundTag;
/** Actual installed model factory and atlas; no Supplementaries client class is loaded. */
public final class SupplementariesRegressionTest {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static int count(Projection.View v,int material){int n=0;for(byte b:v.pixels())if((b&15)==material)n++;return n;}
    static int pixel(Projection.View v,List<Mesh.Triangle> mesh,double x,double y){
        double[] b=CompatibilityTest.bounds(mesh);double scale=(v.resolution()-12.0)/Math.max(v.spanW(),v.spanH());
        int px=(int)Math.floor((v.resolution()-v.spanW()*scale)/2+(x-b[0])*scale),py=(int)Math.floor((v.resolution()-v.spanH()*scale)/2+(b[4]-y)*scale);
        return v.pixels()[px+py*v.resolution()]&255;
    }
    static Mesh.Triangle atZ(List<Mesh.Triangle> mesh,double z){return mesh.stream().filter(t->Math.abs(t.a().p().z()-z)<1e-8&&Math.abs(t.b().p().z()-z)<1e-8&&Math.abs(t.c().p().z()-z)<1e-8).findFirst().orElseThrow();}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),mods=Path.of(args[1]),path;
        try(var files=Files.list(mods)){path=files.filter(p->p.getFileName().toString().startsWith("supplementaries-")&&p.toString().endsWith(".jar")).findFirst().orElse(null);}
        if(path==null){System.out.println("SKIP Supplementaries regression: mod not installed");return;}
        try(var jar=new ZipFile(path.toFile())){
            ModelAssets assets=new ModelAssets((n,p)->{var entry=jar.getEntry(p);return entry==null?null:jar.getInputStream(entry).readAllBytes();});
            var factory=JavaModelReader.read(assets.resource("supplementaries","net/mehvahdjukaar/supplementaries/client/renderers/tiles/CannonBlockTileRenderer.class"),"createMesh");
            var texture=assets.texture("supplementaries:block/cannon");check(factory.cubeCount()==5,"all real model cubes retained");check(factory.textureWidth()==64&&factory.textureHeight()==64,"LayerDefinition texture dimensions retained");
            var head=factory.find("head").geometry(new Matrix4d(),Map.of(),texture.rgb(),texture);var rim=atZ(head,-6.5/16);var core=atZ(head,-6.1875/16);
            double u=(rim.a().u()+rim.c().u())/2,v=(rim.a().v()+rim.c().v())/2;
            check(Math.abs(u-12.0/64)<1e-9&&Math.abs(v-58.0/64)<1e-9,"outer muzzle face uses its own atlas coordinates");
            check(!rim.mask().visible(u,v),"transparent muzzle opening remains open");
            u=(core.a().u()+core.c().u())/2;v=(core.a().v()+core.c().v())/2;
            check(Math.abs(u-19.0/64)<1e-9&&Math.abs(v-37.0/64)<1e-9,"inner recessed face retains UVs despite CubeDeformation");
            check(core.mask().visible(u,v)&&(core.mask().appearance(u,v,Projection.material(core.rgb()),0,true)&15)==13,"recessed muzzle core samples real black bore");
            SupplementariesModels models=new SupplementariesModels(assets);var mesh=models.cannon("up",0,0);
            check(mesh.size()==60&&mesh.stream().allMatch(t->t.mask()!=null),"all model surfaces carry their texture sampler");
            Set<Integer> colors=new HashSet<>();for(var t:mesh)colors.add(Projection.material(t.rgb()));
            check(mesh.stream().flatMap(t->List.of(t.a(),t.b(),t.c()).stream()).allMatch(vert->vert.u()>=0&&vert.u()<=1&&vert.v()>=0&&vert.v()<=1),"UVs within declared atlas");
            check(colors.size()>=2,"wood and metal have separate face colours");
            List<Projection.View> views=new ArrayList<>();for(int d=0;d<6;d++){
                var view=Projection.project(List.of(),mesh,1,1,1,d,384);views.add(view);
                check(CompatibilityTest.count(view,Projection.OUTLINE)>300,"readable outer contour, direction "+d);
            }
            var front=views.get(2);check((pixel(front,mesh,.5,.5625)&15)==13,"front view shows dark muzzle through transparent rim");
            check((pixel(front,mesh,.18,.5625)&15)==7,"metal rim remains a wash distinct from the bore");
            check(count(views.get(0),7)>1000,"top of barrel remains metal instead of solid dark graphite");
            check(pixel(front,mesh,.5,.16)==0,"real opening above the base remains unfilled");
            check(count(front,2)+count(front,3)+count(front,15)>1000,"wooden base remains visible");
            check(count(front,7)+count(front,1)>1000,"metal remains distinct from wood");
            List<Mesh.Triangle> old=mesh.stream().map(t->new Mesh.Triangle(t.a(),t.b(),t.c(),t.part(),texture.rgb(),null)).toList();
            var oldFront=Projection.project(List.of(),old,1,1,1,2,384);check((pixel(oldFront,old,.5,.5625)&15)!=13,"old atlas-average rendering reproduces the lost bore");
            for(String facing:List.of("up","down","north","south","east","west")){
                var aimed=models.cannon(facing,60,25);check(aimed.size()==60&&aimed.stream().allMatch(t->t.mask()!=null),"saved angles preserve mapped surfaces on "+facing);
            }
            check(!Arrays.equals(CompatibilityTest.bounds(mesh),CompatibilityTest.bounds(models.cannon("up",45,35))),"saved yaw and pitch still change the model pose");
            CompoundTag drawing=DrawingData.make("SUPPLEMENTARIES · ГАРМАТА",1,1,1,views.subList(0,3));check(DrawingData.valid(drawing)&&DrawingData.fits(drawing),"detailed cannon survives drawing compression");
            ProjectionTest.write(out.resolve("regression-supplementaries.ppm"),DrawingData.pixels(drawing),1536,1024);
            var largeOptions=new PosterOptions(2,0,true);List<Projection.View> large=new ArrayList<>();for(int d=0;d<3;d++)large.add(Projection.project(List.of(),mesh,1,1,1,d,largeOptions.resolution()));
            var big=DrawingData.make("SUPPLEMENTARIES · ГАРМАТА",1,1,1,large,largeOptions);check(DrawingData.valid(big)&&DrawingData.fits(big),"9x6 poster remains valid at higher resolution");
            for(var view:large)check(CompatibilityTest.count(view,Projection.OUTLINE)>600,"large-poster contours remain visible, direction "+view.direction());
            ProjectionTest.write(out.resolve("regression-supplementaries-large.ppm"),DrawingData.pixels(big),largeOptions.textureWidth(),largeOptions.textureHeight());
        }
        System.out.println("PASS "+checks+" Supplementaries regression checks: atlas UVs, transparency, dark bore, separate wood/metal, contours, mounting and large poster");
    }
}
