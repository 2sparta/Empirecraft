package ua.empirecraft.recruits;

/** Exact supply units are ledger nutrition; one nutrition restores five recruit hunger points. */
public final class RationMath {
    private RationMath(){}
    public static float drain(float hunger,long previous,long now,double perMinute){
        if(!Float.isFinite(hunger)||!Double.isFinite(perMinute)||perMinute<=0)throw new IllegalArgumentException("Некоректний облік голоду");
        double seconds=previous<=0?1:Math.max(0,Math.min(60,(now-previous)/1000.));return (float)Math.max(0,Math.min(100,hunger)-seconds*perMinute/60);
    }
    public static float restore(float hunger,int nutrition){if(nutrition<0||nutrition>20||!Float.isFinite(hunger))throw new IllegalArgumentException();return Math.min(100,hunger+nutrition*5);}
    public static boolean starvation(float hunger,long last,long now,int seconds){return hunger<=0&&seconds>0&&now>=last&&now-last>=seconds*1000L;}
}
