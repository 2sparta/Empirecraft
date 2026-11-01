import ua.empirecraft.shipphysics.ImpactSpread;
import ua.empirecraft.shipphysics.ImpactSpread.Point;
import java.util.*;

public final class ImpactSpreadTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Map<Point,Double> map(List<ImpactSpread.Share> shares){
        var result=new HashMap<Point,Double>();
        for(var share:shares)check(result.put(share.point(),share.fraction())==null,"unique target");
        return result;
    }
    public static void main(String[] args){
        Point origin=new Point(0,0,0);
        var sphere=map(ImpactSpread.cells(origin,4,p->true));
        check(sphere.size()==257,"bounded full radius-four sphere");
        check(Math.abs(sphere.values().stream().mapToDouble(Double::doubleValue).sum()-1)<1e-12,"energy conserved");
        for(var e:sphere.entrySet()){
            Point p=e.getKey();
            check(p.x()*p.x()+p.y()*p.y()+p.z()*p.z()<=16,"spherical boundary");
            check(Math.abs(e.getValue()-sphere.get(new Point(p.y(),p.z(),p.x())))<1e-12,"axis symmetry");
            check(Math.abs(e.getValue()-sphere.get(new Point(-p.x(),-p.y(),-p.z())))<1e-12,"opposite directions equal");
        }
        var decks=map(ImpactSpread.cells(origin,4,p->p.y()==0 || p.y()==3));
        check(decks.size()==49 && decks.keySet().stream().allMatch(p->p.y()==0),"air gap isolates upper deck");
        for(Point p:List.of(new Point(1,0,0),new Point(-1,0,0),new Point(0,0,1),new Point(0,0,-1)))
            check(decks.containsKey(p),"bottom impact spreads across neighbouring hull");
        check(decks.get(origin)>decks.get(new Point(1,0,0)) && decks.get(new Point(1,0,0))>decks.get(new Point(2,0,0)),"radial attenuation");
        var wall=ImpactSpread.cells(origin,4,p->p.x()!=1);
        check(wall.stream().noneMatch(s->s.point().x()>0),"unavailable or unbreakable plane blocks propagation");
        check(ImpactSpread.cells(origin,4,p->false).isEmpty(),"invalid origin produces no collateral damage");
        var single=ImpactSpread.cells(origin,0,p->true);
        check(single.size()==1 && single.get(0).fraction()==1,"one-cell configuration");
        int[] lookups={0};ImpactSpread.cells(origin,1000,p->{lookups[0]++;return true;});
        check(lookups[0]==257,"oversized request remains bounded");
        Point far=new Point(29999990,-60,-29999990);
        var shifted=map(ImpactSpread.cells(far,4,p->true));
        check(shifted.size()==sphere.size(),"shipyard and negative coordinate stability");
        for(var e:sphere.entrySet()){
            Point p=e.getKey();check(Math.abs(e.getValue()-shifted.get(new Point(far.x()+p.x(),far.y()+p.y(),far.z()+p.z())))<1e-12,"translation invariance");
        }
        check(ImpactSpread.radius(2e6,2500,8)==4,"strong impact radius bounded");
        check(ImpactSpread.radius(100,2500,8)==1,"weak impact remains local");
        check(ImpactSpread.radius(2e6,2500,1)==0,"legacy depth-one setting respected");
        check(ImpactSpread.radius(Double.NaN,2500,8)==0,"invalid energy safe");
        System.out.println("IMPACT_SPREAD_PASS assertions="+checks);
    }
}
