package ua.empirecraft.frontier;
public final class SpawnMath {
    private SpawnMath(){}
    public static double[] point(double x,double z,double min,double max,double radial,double angular){
        if(min<0||max<min||!Double.isFinite(max)||radial<0||radial>=1||angular<0||angular>=1)throw new IllegalArgumentException("Invalid spawn ring");
        double radius=Math.sqrt(min*min+radial*(max*max-min*min)),angle=angular*2*Math.PI;
        return new double[]{x+radius*Math.cos(angle),z+radius*Math.sin(angle)};
    }
}
