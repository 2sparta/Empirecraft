package ua.posters;

/** Size is physical wall area. A fixed 1:N scale means N model blocks per wall block. */
public record PosterOptions(int size,int scale,boolean muted) {
    public static final int[] RATIOS={0,0,16,32,64,128,256};
    public static final PosterOptions DEFAULT=new PosterOptions(0,0,true);
    public PosterOptions {if(size<0||size>3||scale<0||scale>=RATIOS.length)throw new IllegalArgumentException("options");}
    public int multiplier(){return size+1;}
    public int width(){return 3*multiplier();}
    public int height(){return 2*multiplier();}
    public int paper(){return 4*multiplier()*multiplier();}
    public int ink(){return multiplier();}
    public int resolution(){return new int[]{384,512,768,1024}[size];}
    public int textureWidth(){return new int[]{1536,2304,3072,3072}[size];}
    public int textureHeight(){return textureWidth()*2/3;}
    public int ratio(){return RATIOS[scale];}
}
