package ua.empirecraft.ores;

import java.util.*;
import static ua.empirecraft.ores.OrePass.*;

public class OrePassTest {
    static int checks;
    static void check(boolean v,String name) { checks++; if (!v) throw new AssertionError(name); }
    static final class Grid implements Terrain {
        final Map<Pos,Kind> blocks = new HashMap<>();
        final Map<Pos,Kind> placed = new HashMap<>();
        final Kind fallback;
        final int min,max,top;
        boolean refuse;
        Grid(Kind fallback,int min,int max,int top) { this.fallback=fallback;this.min=min;this.max=max;this.top=top; }
        public int minY() { return min; }
        public int maxY() { return max; }
        public int ceiling(int x,int z) { return top; }
        public Kind kind(Pos p) { check(p.x()>=0 && p.x()<16 && p.z()>=0 && p.z()<16 && p.y()>=min && p.y()<=max,"read stays in chunk and height band");return blocks.getOrDefault(p,fallback); }
        public boolean place(Pos p,Kind ore,boolean deep) {
            check(p.y()<=top,"surface clearance");check(deep==(kind(p)==Kind.DEEP),"deepslate variant selection");
            if(refuse)return false;
            check(kind(p)==Kind.STONE || kind(p)==Kind.DEEP,"only replace host rock");
            blocks.put(p,ore);placed.put(p,ore);return true;
        }
    }
    public static void main(String[] args) {
        for(long seed=0;seed<40;seed++) {
            Grid a=new Grid(Kind.DEEP,-64,64,23),b=new Grid(Kind.DEEP,-64,64,23);
            var result=OrePass.run(a,seed,30,30);
            check(result.sulfurAdded()==30 && result.zincAdded()==30,"exact targets in suitable rock");
            check(a.placed.size()==60,"ores never overwrite each other");
            OrePass.run(b,seed,30,30);check(a.placed.equals(b.placed),"deterministic per seed");
            check(OrePass.run(a,seed,30,30).sulfurAdded()==0 && a.placed.size()==60,"existing ores counted");
        }
        Grid old=new Grid(Kind.STONE,-10,0,0);
        for(int x=0;x<16;x++)old.blocks.put(new Pos(x,-5,0),Kind.SULFUR);
        for(int x=0;x<12;x++)old.blocks.put(new Pos(x,-5,1),Kind.ZINC);
        var result=OrePass.run(old,44,30,30);check(result.sulfurAdded()==14 && result.zincAdded()==18,"only top up existing deposits");
        Grid scarce=new Grid(Kind.OTHER,0,1,1);scarce.blocks.put(new Pos(15,1,15),Kind.STONE);
        result=OrePass.run(scarce,123,30,30);check(result.sulfurAdded()==1 && result.zincAdded()==0,"scarce terrain is bounded");
        Grid empty=new Grid(Kind.OTHER,-64,64,64);result=OrePass.run(empty,7,30,30);
        check(result.sulfurAdded()==0 && result.zincAdded()==0,"void, water and structures preserved");
        Grid surface=new Grid(Kind.STONE,0,8,-1);check(OrePass.run(surface,7,30,30).sulfurAdded()==0,"no eligible underground layer");
        Grid blocked=new Grid(Kind.STONE,0,1,1);blocked.refuse=true;check(OrePass.run(blocked,7,30,30).sulfurAdded()==0,"rejected placements are not counted");
        Grid zero=new Grid(Kind.STONE,0,1,1);check(OrePass.run(zero,7,0,30).zincAdded()==30 && zero.placed.size()==30,"independent zero quota");
        for(long seed=0;seed<40;seed++) {
            Grid a=new Grid(Kind.DEEP,-64,64,23),b=new Grid(Kind.DEEP,-64,64,23);
            result=OrePass.run(a,seed,30,30,15);
            check(result.sulfurAdded()==30 && result.zincAdded()==30 && result.quartzAdded()==15,"three independent exact quotas");
            check(a.placed.size()==75,"quartz never overwrites existing ores");
            OrePass.run(b,seed,30,30,15);check(a.placed.equals(b.placed),"deterministic quartz deposits");
        }
        Grid migrated=new Grid(Kind.STONE,-10,0,0);
        // Version 1 completed chunks pass zero old quotas, even after every old ore was mined.
        result=OrePass.run(migrated,44,0,0,15);
        check(result.sulfurAdded()==0 && result.zincAdded()==0 && result.quartzAdded()==15,"upgrade adds quartz without restoring mined sulfur or zinc");
        Grid quartz=new Grid(Kind.STONE,-10,0,0);
        for(int x=0;x<7;x++)quartz.blocks.put(new Pos(x,-5,0),Kind.QUARTZ);
        result=OrePass.run(quartz,44,0,0,15);
        check(result.quartzExisting()==7 && result.quartzAdded()==8,"existing quartz counts toward 15");
        check(OrePass.run(quartz,44,0,0,15).quartzAdded()==0,"never exceed quartz quota");
        Grid excess=new Grid(Kind.STONE,-10,0,0);
        for(int x=0;x<16;x++)excess.blocks.put(new Pos(x,-5,0),Kind.QUARTZ);
        check(OrePass.run(excess,44,0,0,15).quartzAdded()==0,"existing surplus preserved");
        check(OrePass.run(empty,7,0,0,15).quartzAdded()==0,"quartz needs host rock");
        check(OrePass.run(blocked,7,0,0,15).quartzAdded()==0,"refused quartz placements not counted");
        System.out.println("ORE_PASS_TEST_PASS assertions="+checks);
    }
}
