package ua.posters;
/** Front face: right-handed readable UVs, outward +Z normal, then rotate by -wall yaw. */
public final class PosterSurface {
    public record Corner(float x,float y,float u,float v){}
    public static final float FRONT_Z=.02f,NORMAL_Z=1;
    public static float rotation(float wallYaw){return -wallYaw;}
    public static Corner[] corners(float halfWidth,float halfHeight){return new Corner[]{new Corner(-halfWidth,halfHeight,0,0),new Corner(-halfWidth,-halfHeight,0,1),new Corner(halfWidth,-halfHeight,1,1),new Corner(halfWidth,halfHeight,1,0)};}
}
