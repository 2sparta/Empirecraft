package com.quintonc.vs_sails.ship;

import java.util.*;

/** Project cloth along the local bow axis, independent of world yaw and wind angle. */
public final class SailExposure {
    private record Column(int across, int height) {}
    private final Map<Column, TreeMap<Integer, Boolean>> cloth = new HashMap<>();
    private final Set<Column> wood = new HashSet<>();
    public record Counts(int open, int furled) {}
    public void addSail(int along, int across, int height, boolean open) {
        cloth.computeIfAbsent(new Column(across,height), k -> new TreeMap<>()).merge(along,open,(a,b)->a||b);
    }
    public void addWood(int across, int height) { wood.add(new Column(across,height)); }
    public Counts count() {
        int open=0,furled=0;
        for (var entry:cloth.entrySet()) {
            if(wood.contains(entry.getKey()))continue;
            boolean started=false, deployed=false;int previous=0;
            for(var sail:entry.getValue().entrySet()) {
                if(started && (long)sail.getKey()-previous>=8) {
                    if(deployed)open++;else furled++;
                    deployed=false;
                }
                started=true;deployed|=sail.getValue();previous=sail.getKey();
            }
            if(started){if(deployed)open++;else furled++;}
        }
        return new Counts(open,furled);
    }
}
