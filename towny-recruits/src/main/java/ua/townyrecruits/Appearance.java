package ua.townyrecruits;

import java.util.UUID;

/** Stateless identity-based appearance: identical on server, clients and after NBT reload. */
public final class Appearance {
    public static final int SKIN_COUNT=128;
    private Appearance() {}
    private static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    private static long seed(UUID id,long salt){return mix(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),23)^salt);}
    public static int skin(UUID id){return (int)Long.remainderUnsigned(seed(id,0x8d8cbabe9e3779b9L),SKIN_COUNT);}
    public static float height(UUID id){
        long value=seed(id,0x68bc21eb5af27ac1L);
        int roll=(int)Long.remainderUnsigned(value,10000);
        // Separate random bits for interpolation; don't correlate stature with the skin index.
        float u=(mix(value^0x2d358dccaa6c78a5L)>>>40)/16777215.0f;
        if(roll<8500)return 1.75f+0.15f*u;
        if(roll<9500)return 1.65f+0.40f*u;
        if(roll<9900)return 1.50f+0.20f*u;
        return 2.10f+0.40f*u;
    }
    // Standard HumanoidModel geometry spans 32 texture pixels, i.e. two blocks.
    public static float renderScale(UUID id){return height(id)/2f;}
    public static float scale(UUID id,float baseHeight){return baseHeight>0?height(id)/baseHeight:1f;}
    public static float eyeHeight(float original,float scale,float totalHeight){return Math.max(0.05f,Math.min(totalHeight-0.1f,original*scale));}
}
