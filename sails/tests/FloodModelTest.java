import com.quintonc.vs_sails.ship.FloodModel;
import com.quintonc.vs_sails.ship.FloodGeometry;
import java.util.*;

public final class FloodModelTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static FloodModel.Graph room(int xSize,int height){
        int n=xSize*height;int[][] edges=new int[n][];int[] groups=new int[n];double[] floor=new double[n],span=new double[n];
        for(int x=0;x<xSize;x++)for(int y=0;y<height;y++){
            int i=x*height+y;List<Integer> neighbors=new ArrayList<>();
            if(x>0)neighbors.add(i-height);if(x+1<xSize)neighbors.add(i+height);if(y>0)neighbors.add(i-1);if(y+1<height)neighbors.add(i+1);
            edges[i]=neighbors.stream().mapToInt(Integer::intValue).toArray();floor[i]=y;span[i]=1;
        }
        return new FloodModel.Graph(edges,groups,floor,span,1);
    }
    static void conserve(FloodModel model){
        long sum=model.reserve();for(int i=0;i<model.graph.size();i++)sum+=model.amount(i);
        check(sum==model.total(),"sparse water and reserve conserve exact units");
        for(int i=0;i<model.graph.size();i++)check(model.amount(i)>=0&&model.amount(i)<=8,"bounded cell volume");
    }
    public static void main(String[] args){
        var graph=room(4,3);var bell=new FloodModel(graph,new int[graph.size()],0);
        bell.setPorts(List.of(new FloodModel.Port(0,5,0,-1,true,true,false)));
        for(int i=0;i<300;i++)check(bell.step(32)<=32,"bounded bell work");
        check(bell.total()==0,"sealed bottom breach retains its air bell");
        bell.setPorts(List.of(new FloodModel.Port(0,5,0,-1,true,true,false),new FloodModel.Port(11,5,3,1,false,false,true)));
        for(int i=0;i<800;i++)bell.step(64);
        check(bell.total()==graph.size()*8L,"a real dry vent allows bottom ingress and full hydrostatic filling: total="+bell.total());conserve(bell);
        var side=new FloodModel(graph,new int[graph.size()],0);
        side.setPorts(List.of(new FloodModel.Port(0,5,0,0,true,true,false)));
        for(int i=0;i<800;i++)side.step(64);
        check(side.total()==graph.size()*8L,"side opening lets water displace air to the waterline");conserve(side);
        int total=(int)side.total();side.setPorts(List.of());
        for(int i=0;i<500;i++)side.step(32);
        check(side.total()==total,"closing the outer door creates no new water");
        var larger=room(24,3);int[] saved=new int[larger.size()];for(int i=0;i<graph.size();i++)saved[i]=side.amount(i);
        var airlock=new FloodModel(larger,saved,0);
        for(int i=0;i<3000;i++)airlock.step(128);
        check(airlock.total()==total,"opening inner door preserves the trapped water volume");
        check(airlock.amount(18)>0,"water spreads into the newly accessible room");
        check(airlock.amount(1)==0,"isolated water settles to a floor puddle");conserve(airlock);
        check(airlock.activeCells()==0,"settled airlock water stops consuming simulation work");
        int[] reverseWater=new int[larger.size()];Arrays.fill(reverseWater,reverseWater.length-graph.size(),reverseWater.length,8);
        var reverseLock=new FloodModel(larger,reverseWater,0);for(int i=0;i<3000;i++)reverseLock.step(128);
        check(reverseLock.activeCells()==0 && reverseLock.total()==total,"reverse-facing airlock also settles and conserves volume");
        for(int x=0;x<24;x++)check(reverseLock.amount(x*3)==4 && reverseLock.amount(x*3+1)==0 && reverseLock.amount(x*3+2)==0,"puddle equilibrium does not depend on cell numbering");
        int removed=0;for(int i=0;i<larger.size();i++)removed+=airlock.bail(i,8);
        check(removed==total && airlock.total()==0,"buckets recover all isolated water without replenishment");
        var partial=new FloodModel(room(2,1),new int[2],0);partial.pour(0,3);
        for(int i=0;i<30;i++)partial.step(8);
        check(partial.activeCells()==0,"odd eighth-bucket quantity settles without swapping levels forever");
        check(partial.total()==3 && partial.bail(1,8)==3,"less than one bucket is collected without rounding up");conserve(partial);
        partial.pour(0,8);partial.pour(0,8);partial.pour(0,8);conserve(partial);
        check(partial.total()==24,"water displaced by a full cell remains in a finite reserve");
        for(int i=0;i<100;i++)partial.step(8);conserve(partial);
        var restored=new FloodModel(room(4,1),new int[4],13);
        for(int i=0;i<100;i++)restored.step(8);
        check(restored.reserve()==0 && restored.total()==13,"restored displaced water re-enters an initially dry graph");conserve(restored);
        var tilted=new FloodModel(room(4,1).coordinates(new double[]{0,1,2,3},new double[4]),new int[4],0);
        tilted.pour(0,8);tilted.project(-.25,1,0);for(int i=0;i<200;i++)tilted.step(8);
        check(tilted.amount(3)>0 && tilted.total()==8,"heel moves conserved water toward the lower world-space side");conserve(tilted);
        for(int degrees=-180;degrees<=180;degrees+=15)for(double z:new double[]{0,.35}){
            double angle=Math.toRadians(degrees),x=Math.sin(angle)*Math.sqrt(1-z*z),y=Math.cos(angle)*Math.sqrt(1-z*z);
            double[] depths=FloodGeometry.depths(x,y,z);
            for(int q=1;q<=8;q++){
                check(Math.abs(FloodGeometry.fraction(depths[q],x,y,z)-q/8.0)<1e-7,"rotated voxel retains its exact water volume at "+degrees+" degrees, q="+q);
                var mesh=FloodGeometry.mesh(x,y,z,depths[q]);
                double volume=0;
                for(var face:mesh){var points=face.points();var a=points.get(0);
                    for(int v=1;v+1<points.size();v++){var b=points.get(v);var c=points.get(v+1);
                        volume+=(a.x()*(b.y()*c.z()-b.z()*c.y())+a.y()*(b.z()*c.x()-b.x()*c.z())+a.z()*(b.x()*c.y()-b.y()*c.x()))/6;
                    }
                }
                check(Math.abs(volume-q/8.0)<1e-7,"rendered polyhedron matches conserved volume, including capsizing");
                var center=FloodGeometry.centroid(mesh);
                check(center.x()>=0 && center.x()<=1 && center.y()>=0 && center.y()<=1 && center.z()>=0 && center.z()<=1,"water load stays inside the cell");
                for(var face:mesh)if(face.direction()<0){double plane=face.points().get(0).dot(x,y,z);
                    for(var vertex:face.points())check(Math.abs(vertex.dot(x,y,z)-plane)<1e-7,"free surface remains horizontal in world gravity");}
            }
        }
        var raised=new FloodModel(room(1,1),new int[1],0);
        raised.setPorts(List.of(new FloodModel.Port(0,.5,.75,0,true,true,false)));
        for(int i=0;i<100;i++)raised.step(8);
        check(raised.total()==0,"a breach above the waterline never admits water, even with a stale wet sample");
        raised.setPorts(List.of(new FloodModel.Port(0,.8,0,0,true,true,false)));
        for(int i=0;i<100;i++)raised.step(8);
        check(raised.total()>0,"the same breach fills again when submerged");
        raised.setPorts(List.of());long retained=raised.total();
        for(int i=0;i<100;i++)raised.step(8);
        check(raised.total()==retained,"lifting or repairing a breach retains already trapped water");
        var spillGraph=new FloodModel.Graph(new int[][]{{},{}},new int[]{0,1},new double[]{0,0},new double[]{1,1},2).interior(new boolean[]{true,false});
        var overboard=new FloodModel(spillGraph,new int[]{8,8},0);
        overboard.setPorts(List.of(new FloodModel.Port(1,2,0,0,true,false,false)));
        for(int i=0;i<50;i++)overboard.step(8);
        check(overboard.amount(0)==8 && overboard.amount(1)==0 && overboard.total()==8,"only exterior water merges with the ocean; submerged compartments retain their load");
        check(overboard.outflow()==8,"discarded exterior water removes exactly its own mass");
        check(overboard.bail(0,8)==8,"bailing removes the retained compartment load");overboard.pour(1,8);
        for(int i=0;i<50;i++)overboard.step(8);
        check(overboard.total()==0,"bailing and pouring overboard does not reattach the bucket to the ship");conserve(overboard);
        var sealedGraph=new FloodModel.Graph(new int[][]{{},{}},new int[]{0,1},new double[]{0,0},new double[]{1,1},2);
        var localOverflow=new FloodModel(sealedGraph,new int[]{8,0},0);localOverflow.pour(0,8);
        for(int i=0;i<50;i++)localOverflow.step(8);
        check(localOverflow.amount(1)==0 && localOverflow.pending().get(0)==8,"overflow cannot teleport through a closed bulkhead");
        var savedOverflow=new FloodModel(sealedGraph,new int[]{8,0},localOverflow.legacyReserve(),localOverflow.pending());
        check(savedOverflow.total()==16 && savedOverflow.pending().get(0)==8,"restored pending water keeps its exact position and mass");conserve(savedOverflow);
        var runoff=new FloodModel(room(1,1),new int[]{8},0);
        runoff.setPorts(List.of(new FloodModel.Port(0,0,0,0,false,false,true)));
        for(int i=0;i<20;i++)runoff.step(8);
        check(runoff.total()==0 && runoff.takeDischarges().stream().mapToInt(FloodModel.Discharge::units).sum()==8,"spills into air are handed to the world, with no missing or duplicated water");
        var finiteContact=new FloodModel(room(1,1),new int[1],0);
        finiteContact.setPorts(List.of(new FloodModel.Port(0,1,0,0,true,false,false)));
        for(int i=0;i<100;i++)finiteContact.step(8);
        check(finiteContact.total()==0,"a non-source exterior puddle cannot become an infinite reservoir");
        var stress=new FloodModel(room(8,3),new int[24],0);Random rng=new Random(15437);long expected=0;
        for(int i=0;i<1000;i++){
            int node=rng.nextInt(24);
            if(rng.nextBoolean()){int q=1+rng.nextInt(8);stress.pour(node,q);expected+=q;}else expected-=stress.bail(node,8);
            int budget=1+rng.nextInt(16);check(stress.step(budget)<=budget,"random edit work stays within budget");
            check(stress.total()==expected,"random pours and bails conserve exact total");
        }
        conserve(stress);
        var maximum=room(256,256);int[] full=new int[maximum.size()];Arrays.fill(full,8);
        var large=new FloodModel(maximum,full,0);
        for(int i=0;i<8;i++)check(large.step(8192)<=8192,"maximum-size graph obeys the shared work budget");
        check(large.activeCells()==0 && large.step(8192)==0,"settled maximum-size cabin performs no recurring cell walk");
        check(large.total()==65536L*8,"maximum-size compartment conserves water");
        var puddle=new FloodModel(room(4,1),new int[]{1,2,1,0},0);
        check(puddle.dryPuddle(0,1200,200,true)==0,"puddles are not removed immediately");
        check(puddle.dryPuddle(1199,1200,200,true)==0,"a full dry delay is required");
        check(puddle.dryPuddle(1200,1200,200,true)==1 && puddle.total()==3,"one measured eighth evaporates after a minute");
        check(puddle.dryPuddle(1201,1200,200,true)==0,"repeated calls do not accelerate drying");
        for(int tick=1400;tick<=1800;tick+=200)puddle.dryPuddle(tick,1200,200,true);
        check(puddle.total()==0 && puddle.evaporated()==4,"shallow residual water disappears completely with exact mass accounting");conserve(puddle);
        var deep=new FloodModel(room(1,1),new int[]{8},0);
        for(int tick=0;tick<10000;tick+=20)deep.dryPuddle(tick,1,1,true);
        check(deep.total()==8,"a deep bucket or tank is not a puddle");
        var leak=new FloodModel(room(4,1),new int[]{1,1,0,0},0);
        leak.setPorts(List.of(new FloodModel.Port(0,0,1,0,true,false,false)));
        for(int tick=0;tick<10000;tick+=20)leak.dryPuddle(tick,1,1,true);
        check(leak.total()==2,"water contacting the sea does not evaporate");
        leak.setPorts(List.of());leak.dryPuddle(10000,20,20,true);leak.dryPuddle(10020,20,20,false);
        check(leak.total()==2,"rain resets the quiet dry period");
        leak.dryPuddle(10040,20,20,true);leak.pour(2,1);leak.dryPuddle(10060,20,20,true);
        check(leak.total()==3,"newly poured water resets the dry period");
        var flooded=new FloodModel(room(20,1),new int[20],0);for(int i=0;i<20;i++)flooded.pour(i,1);
        for(int tick=0;tick<10000;tick+=20)flooded.dryPuddle(tick,1,1,true);
        check(flooded.total()==20,"larger floods retain their water even when shallow");
        var excess=new FloodModel(room(1,1),new int[]{1},1);
        for(int tick=0;tick<10000;tick+=20)excess.dryPuddle(tick,1,1,true);
        check(excess.total()==2,"unplaced reserve water prevents drying");
        System.out.println("FLOOD_MODEL_PASS assertions="+checks+" sideIngress="+total);
    }
}
