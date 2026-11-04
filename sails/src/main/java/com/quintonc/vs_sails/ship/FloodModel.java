package com.quintonc.vs_sails.ship;

import java.util.*;

/** Conserved eighth-bucket volumes on an immutable compartment graph. No game/world reads. */
public final class FloodModel {
    public static final int FULL=8, MAX_CELLS=65536;
    public record Port(int node,double surface,double opening,double up,boolean water,boolean source,boolean air) {}
    public static final class Graph {
        public final int[][] edges;
        public final int[] component;
        public final double[] floor,height;
        public final int components;
        private double[] localX,localZ;
        private double upX,upY=1,upZ,span=1,signalX,signalY=1,signalZ;
        public Graph(int[][] edges,int[] component,double[] floor,double[] height,int components) {
            if(edges.length>MAX_CELLS || component.length!=edges.length || floor.length!=edges.length || height.length!=edges.length)
                throw new IllegalArgumentException("Invalid flood graph");
            this.edges=edges;this.component=component;this.floor=floor;this.height=height;this.components=components;
        }
        public int size(){return edges.length;}
        public Graph coordinates(double[] x,double[] z){localX=x;localZ=z;return this;}
        public double floor(int node){return localX==null?floor[node]
                :localX[node]*upX+(floor[node]+.5)*upY+localZ[node]*upZ-span*.5;}
        public double height(int node){return localX==null?height[node]:span;}
        private boolean projection(double x,double y,double z){
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)+Math.abs(y)+Math.abs(z)<1e-6)return false;
            boolean changed=Math.abs(x-signalX)+Math.abs(y-signalY)+Math.abs(z-signalZ)>.005;
            if(changed){signalX=x;signalY=y;signalZ=z;}
            upX=x;upY=y;upZ=z;span=Math.abs(x)+Math.abs(y)+Math.abs(z);return changed;
        }
    }
    public final Graph graph;
    private final byte[] amount;
    private final double[] pressure,sourceHead;
    private final int[] pressureEpoch;
    private final boolean[] vented;
    private final ArrayDeque<Integer> active=new ArrayDeque<>();
    private final BitSet queued=new BitSet(),changed=new BitSet(),surface=new BitSet();
    private List<Port> ports=List.of();
    private int portCursor,epoch=1,reserve;
    private long total,inflow,outflow,bailed,poured;
    private long stateHash;
    private int sweepLeft;
    private boolean sweepPressure;
    private boolean externalHead;
    private final Set<Long> recent=new LinkedHashSet<>();

    public FloodModel(Graph graph,int[] initial,int reserve) {
        if(initial.length!=graph.size() || reserve<0)throw new IllegalArgumentException("Invalid stored flood volume");
        this.graph=graph;this.reserve=reserve;total=reserve;
        amount=new byte[initial.length];pressure=new double[initial.length];sourceHead=new double[initial.length];
        pressureEpoch=new int[initial.length];vented=new boolean[graph.components];Arrays.fill(sourceHead,Double.NaN);
        for(int i=0;i<initial.length;i++){
            if(initial[i]<0 || initial[i]>FULL || !Double.isFinite(graph.floor[i]) || !Double.isFinite(graph.height[i]) || graph.height[i]<=0)
                throw new IllegalArgumentException("Invalid flood cell");
            amount[i]=(byte)initial[i];total+=initial[i];stateHash^=token(i,initial[i]);
        }
        for(int i=0;i<initial.length;i++){updateSurface(i);if(surface.get(i))activate(i);}
        if(reserve>0 && initial.length>0){
            int lowest=0;for(int i=1;i<initial.length;i++)if(graph.floor(i)<graph.floor(lowest))lowest=i;
            activate(lowest);
        }
    }
    public int amount(int node){return amount[node];}
    public int reserve(){return reserve;}
    public long total(){return total;}
    public long inflow(){return inflow;}
    public long outflow(){return outflow;}
    public long bailed(){return bailed;}
    public long poured(){return poured;}
    public int activeCells(){return active.size();}
    public void project(double x,double y,double z){
        if(graph.projection(x,y,z)){
            wake();
            epoch++;for(int i=surface.nextSetBit(0);i>=0;i=surface.nextSetBit(i+1))activate(i);
        }
    }
    public BitSet takeChanges(){BitSet result=(BitSet)changed.clone();changed.clear();return result;}

    public void setPorts(List<Port> replacement) {
        if(ports.equals(replacement))return;
        ports=List.copyOf(replacement);portCursor=0;Arrays.fill(sourceHead,Double.NaN);Arrays.fill(vented,false);
        wake();
        if(++epoch==Integer.MAX_VALUE){Arrays.fill(pressureEpoch,0);epoch=1;}
        for(Port p:ports){
            if(p.node<0 || p.node>=amount.length || !Double.isFinite(p.opening) || !Double.isFinite(p.up)
                    || p.water && !Double.isFinite(p.surface))throw new IllegalArgumentException("Invalid flood port");
            // Bottom-only immersion traps air. A dry vent, or a side/top opening
            // through which bubbles can escape, lets external water displace it.
            if(p.air || p.water && p.up>-.5)vented[graph.component[p.node]]=true;
            activate(p.node);
        }
        externalHead=false;
        for(Port p:ports)if(p.water && p.source && vented[graph.component[p.node]]){
            externalHead=true;sourceHead[p.node]=Double.isNaN(sourceHead[p.node])?p.surface:Math.max(sourceHead[p.node],p.surface);
        }
    }

    /** Returns work used; newly activated cells wait for the next call. */
    public int step(int budget) {
        if(budget<=0)return 0;
        int used=0,portWork=Math.min(ports.size(),Math.max(1,budget/4));
        for(int i=0;i<portWork && used<budget;i++,used++){
            Port p=ports.get(portCursor++%ports.size());int n=p.node;
            if(p.water && p.source && vented[graph.component[n]]){
                int target=(int)Math.max(0,Math.min(FULL,Math.ceil((p.surface-graph.floor(n))/graph.height(n)*FULL-1e-6)));
                int add=Math.min(2,Math.max(0,target-amount[n]));
                if(add>0){wake();set(n,amount[n]+add);total+=add;inflow+=add;}
            }
            double outside=p.water?p.surface:p.opening;
            if(amount[n]>0 && level(n)>outside+graph.height(n)/FULL*.5 && level(n)>p.opening){
                int drain=Math.min(2,amount[n]);wake();set(n,amount[n]-drain);total-=drain;outflow+=drain;
            }
        }
        if(sweepLeft<=0){sweepLeft=active.size();sweepPressure=false;if(recent.isEmpty())recent.add(cycleKey());}
        int count=Math.min(active.size(),budget-used);
        for(int i=0;i<count;i++,used++){
            sweepLeft--;
            int n=active.removeFirst();queued.clear(n);
            if(reserve>0 && amount[n]<FULL){int add=Math.min(reserve,FULL-amount[n]);reserve-=add;set(n,amount[n]+add);}
            if(amount[n]==0)continue;
            double head=level(n);
            if(!Double.isNaN(sourceHead[n]))head=Math.max(head,sourceHead[n]);
            else if(amount[n]==FULL)for(int other:graph.edges[n])if(amount[other]==FULL && pressureEpoch[other]==epoch)
                head=Math.max(head,pressure[other]);
            double old=pressure[n];pressure[n]=head;pressureEpoch[n]=epoch;
            if(Math.abs(head-old)>.01){sweepPressure=true;for(int other:graph.edges[n])if(amount[other]>0)activate(other);}
            int best=-1;double difference=0;
            for(int other:graph.edges[n])if(amount[other]<FULL){
                double theirs=level(other);
                if(!Double.isNaN(sourceHead[other]))theirs=Math.max(theirs,sourceHead[other]);
                double drop=head-theirs;
                if(drop>Math.max(.035,Math.min(graph.height(n),graph.height(other))/FULL*.55) && drop>difference){best=other;difference=drop;}
            }
            if(best>=0){
                int move=graph.floor(n)-graph.floor(best)>.5*Math.min(graph.height(n),graph.height(best))
                        ?Math.min(amount[n],FULL-amount[best]):1;
                set(n,amount[n]-move);set(best,amount[best]+move);
            }
        }
        if(!externalHead && count>0 && sweepLeft<=0){
            // Quantisation can swap an indivisible eighth back and forth. Only
            // detect repeats after a complete queue sweep, with stable pressure;
            // pending cells and pressure propagation must still get their turn.
            if(sweepPressure){recent.clear();recent.add(cycleKey());}
            else if(!recent.add(cycleKey())){active.clear();queued.clear();}
            if(recent.size()>32)recent.remove(recent.iterator().next());
        }
        return used;
    }

    public int pour(int node,int units) {
        if(units<0 || units>FULL)throw new IllegalArgumentException("A bucket holds at most eight units");
        wake();int add=Math.min(units,FULL-amount[node]);set(node,amount[node]+add);reserve+=units-add;
        total+=units;poured+=units;activate(node);return units;
    }

    /** Collect only water connected through currently passable, wet cells. */
    public int bail(int node,int maximum) {
        if(maximum<=0)return 0;
        wake();
        ArrayDeque<Integer> queue=new ArrayDeque<>();BitSet seen=new BitSet();queue.add(node);seen.set(node);
        int removed=0,visits=0;
        while(!queue.isEmpty() && removed<maximum && visits++<128){
            int n=queue.removeFirst();int take=Math.min(amount[n],maximum-removed);
            if(take==0)continue;
            removed+=take;set(n,amount[n]-take);
            for(int other:graph.edges[n])if(!seen.get(other) && amount[other]>0){seen.set(other);queue.addLast(other);}
        }
        total-=removed;bailed+=removed;return removed;
    }
    private double level(int node){return graph.floor(node)+amount[node]*graph.height(node)/FULL;}
    private void activate(int node){if(!queued.get(node)){queued.set(node);active.addLast(node);}}
    private void set(int node,int value){
        if(amount[node]==value)return;stateHash^=token(node,amount[node])^token(node,value);amount[node]=(byte)value;changed.set(node);activate(node);
        updateSurface(node);for(int other:graph.edges[node]){updateSurface(other);activate(other);}
    }
    private void updateSurface(int node){
        boolean exposed=amount[node]>0 && amount[node]<FULL;
        if(amount[node]==FULL)for(int other:graph.edges[node])if(amount[other]<FULL){exposed=true;break;}
        surface.set(node,exposed);
    }
    private void wake(){recent.clear();sweepLeft=0;sweepPressure=false;}
    private long cycleKey(){return stateHash^Long.rotateLeft(reserve*0x9e3779b97f4a7c15L,17);}
    private static long token(int node,int q){
        if(q==0)return 0;long n=(node*9L+q)*0x9e3779b97f4a7c15L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);
    }
}
