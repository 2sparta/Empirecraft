package ua.empirecraft.feudal;

import java.math.BigInteger;
import java.util.*;

/** Versioned JDK-only bridge to the Forge calendar. All costs use exact integer hunger units. */
public record ProvisionClock(long epoch,long phase,boolean paused,long observed) {
    public static final String PROPERTY="empirecraft.calendar.wallclock.v1";
    public static final long DAY=86_400_000L,MONTH=DAY,YEAR=12*DAY;
    public static final BigInteger UNIT=BigInteger.valueOf(DAY*10);
    private static final int[] RATES={80,40,64,120};
    public ProvisionClock {
        if(epoch<=0 || phase<0 || phase>=YEAR || observed<epoch)throw new IllegalArgumentException("Пошкоджена прив'язка календаря.");
    }
    public static ProvisionClock parse(String value,long now) {
        if(value==null)throw new IllegalStateException("Календар реального часу ще не готовий.");
        String[] p=value.split("\\|",-1);
        if(p.length!=5 || !p[0].equals("1") || !Set.of("true","false").contains(p[3]))throw new IllegalStateException("Несумісний календар реального часу.");
        var c=new ProvisionClock(Long.parseLong(p[1]),Long.parseLong(p[2]),Boolean.parseBoolean(p[3]),Long.parseLong(p[4]));
        if(Math.abs(now-c.observed)>300_000)throw new IllegalStateException("Календар реального часу не оновлюється.");
        return c;
    }
    public long phaseAt(long time){return paused?phase:Math.floorMod(phase+Math.floorMod(time-epoch,YEAR),YEAR);}
    public int season(long time){return (int)(phaseAt(time)/(3*MONTH));}
    public String seasonName(long time){return new String[]{"Весна ×1.0","Літо ×0.5","Осінь ×0.8","Зима ×1.5"}[season(time)];}
    public BigInteger cost(long from,long to,long chunks) {
        if(chunks<0)throw new IllegalArgumentException("Від'ємна територія");
        if(to<=from || chunks==0)return BigInteger.ZERO;
        long length=to-from;
        if(paused)return BigInteger.valueOf(length).multiply(BigInteger.valueOf(RATES[season(from)])).multiply(BigInteger.valueOf(chunks));
        long years=length/YEAR;
        BigInteger units=BigInteger.valueOf(years).multiply(BigInteger.valueOf(3*MONTH*(80+40+64+120)));
        long left=length%YEAR,at=from;
        while(left>0){long phase=phaseAt(at),step=Math.min(left,3*MONTH-phase%(3*MONTH));units=units.add(BigInteger.valueOf(step).multiply(BigInteger.valueOf(RATES[season(at)])));left-=step;at+=step;}
        return units.multiply(BigInteger.valueOf(chunks));
    }
    public long exhaustion(long from,long to,long chunks,BigInteger reserve){
        long low=from,high=to;
        while(low<high){long mid=low+(high-low)/2;if(cost(from,mid,chunks).compareTo(reserve)>=0)high=mid;else low=mid+1;}
        return low;
    }
    /** Towny plots can be configured larger/smaller than actual 16×16 Minecraft chunks. */
    public record Plot(String world,int x,int z) {}
    public static long chunks(Collection<Plot> plots,int cellSize){
        if(cellSize<1 || cellSize>1024)throw new IllegalArgumentException("Непідтримуваний розмір Towny plot");
        Set<Plot> chunks=new HashSet<>();
        for(var p:plots){long x=(long)p.x*cellSize,z=(long)p.z*cellSize;
            for(long cx=Math.floorDiv(x,16);cx<=Math.floorDiv(x+cellSize-1,16);cx++)for(long cz=Math.floorDiv(z,16);cz<=Math.floorDiv(z+cellSize-1,16);cz++)chunks.add(new Plot(p.world,Math.toIntExact(cx),Math.toIntExact(cz)));
        }return chunks.size();
    }
}
