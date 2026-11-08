import com.quintonc.vs_sails.ship.WaterOcclusion;
import java.util.*;

public final class WaterOcclusionTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static WaterOcclusion.Vertex vertex(double x,double z){return new WaterOcclusion.Vertex(x,.5,z,new double[]{x,z,240,128});}
    static List<WaterOcclusion.Vertex> square(){return List.of(vertex(0,0),vertex(1,0),vertex(1,1),vertex(0,1));}
    static List<WaterOcclusion.Plane> box(double minX,double maxX,double minZ,double maxZ){
        return List.of(new WaterOcclusion.Plane(1,0,0,maxX),new WaterOcclusion.Plane(-1,0,0,-minX),
                new WaterOcclusion.Plane(0,1,0,1),new WaterOcclusion.Plane(0,-1,0,0),
                new WaterOcclusion.Plane(0,0,1,maxZ),new WaterOcclusion.Plane(0,0,-1,-minZ));
    }
    static double area(List<WaterOcclusion.Vertex> polygon){
        double sum=0;for(int i=0;i<polygon.size();i++){
            var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());sum+=a.x()*b.z()-a.z()*b.x();
        }return Math.abs(sum)*.5;
    }
    static double areaOf(List<List<WaterOcclusion.Vertex>> polygons){return polygons.stream().mapToDouble(WaterOcclusionTest::area).sum();}
    public static void main(String[] args){
        check(WaterOcclusion.subtract(square(),box(-1,2,-1,2)).isEmpty(),"ocean inside a compartment is removed");
        check(Math.abs(areaOf(WaterOcclusion.subtract(square(),box(2,3,2,3)))-1)<1e-10,"ocean outside the hull retains its whole face");
        check(Math.abs(areaOf(WaterOcclusion.subtract(square(),box(.25,.75,.25,.75)))-.75)<1e-10,"only the hole covered by the hull is removed");
        check(Math.abs(areaOf(WaterOcclusion.subtract(square(),box(1,2,0,1)))-1)<1e-10,"touching a hull boundary creates no crack in adjacent ocean");
        Random random=new Random(18531);
        for(int i=0;i<2000;i++){
            double x=random.nextDouble(),z=random.nextDouble(),width=random.nextDouble()*(1-x),length=random.nextDouble()*(1-z);
            var polygons=WaterOcclusion.subtract(square(),box(x,x+width,z,z+length));
            check(Math.abs(areaOf(polygons)-(1-width*length))<1e-9,"clipping conserves the exterior surface area");
            for(var polygon:polygons)for(var v:polygon){
                check(Double.isFinite(v.x()+v.y()+v.z()),"no non-finite vertices reach the renderer");
                check(Math.abs(v.data()[0]-v.x())<1e-9 && Math.abs(v.data()[1]-v.z())<1e-9,"UV gradients interpolate at every cut edge");
                check(v.data()[2]==240 && v.data()[3]==128,"constant light and alpha survive clipping");
            }
        }
        // An oblique ship-space face clips a diagonal half of a world ocean quad.
        var diagonal=WaterOcclusion.subtract(square(),List.of(new WaterOcclusion.Plane(1,0,1,1)));
        check(Math.abs(areaOf(diagonal)-.5)<1e-10,"rotated clipping planes retain the correct ocean triangle");
        List<List<WaterOcclusion.Vertex>> overlap=WaterOcclusion.subtract(square(),box(0,.6,0,1));
        List<List<WaterOcclusion.Vertex>> remainder=new ArrayList<>();
        for(var polygon:overlap)remainder.addAll(WaterOcclusion.subtract(polygon,box(.4,.8,0,1)));
        check(Math.abs(areaOf(remainder)-.2)<1e-10,"overlapping ship volumes remove ocean only once");
        System.out.println("WATER_OCCLUSION_PASS assertions="+checks);
    }
}
