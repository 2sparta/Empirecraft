package com.quintonc.vs_sails.util;

import java.util.*;

/** Face-connected components. Edge/corner contact never joins independent hulls. */
public final class HullConnectivity {
    public record Cell(int x,int y,int z) {}
    private static final int[][] STEPS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private HullConnectivity() {}
    public static List<Set<Cell>> components(Set<Cell> cells) {
        Set<Cell> remaining=new HashSet<>(cells);
        List<Set<Cell>> result=new ArrayList<>();
        while(!remaining.isEmpty()) {
            Cell seed=remaining.iterator().next();remaining.remove(seed);
            Set<Cell> component=new HashSet<>();ArrayDeque<Cell> queue=new ArrayDeque<>();queue.add(seed);
            while(!queue.isEmpty()) {
                Cell p=queue.removeFirst();component.add(p);
                for(int[] d:STEPS) {
                    Cell next=new Cell(p.x+d[0],p.y+d[1],p.z+d[2]);
                    if(remaining.remove(next))queue.addLast(next);
                }
            }
            result.add(component);
        }
        result.sort(Comparator.<Set<Cell>>comparingInt(Set::size).reversed());
        return result;
    }
}
