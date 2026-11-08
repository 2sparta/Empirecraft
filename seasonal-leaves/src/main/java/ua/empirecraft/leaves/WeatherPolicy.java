package ua.empirecraft.leaves;

public final class WeatherPolicy {
    private WeatherPolicy() {}
    public static int duration(double phase,boolean wet,int day,long random) {
        double min,max;
        if(phase>=9) {min=wet?2:0.25;max=wet?5:1;}
        else if(phase>=6) {min=wet?0.3:0.25;max=wet?1.5:1.2;}
        else if(phase>=3) {min=wet?0.08:1;max=wet?0.3:3;}
        else {min=0.25;max=wet?1:1.5;}
        double u=(random>>>11)*0x1.0p-53;
        return (int)Math.max(40,Math.min(Integer.MAX_VALUE-100L,Math.round(Math.max(1,day)*(min+(max-min)*u))));
    }
}
