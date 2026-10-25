package ua.empirecraft.feudal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Pure rectangular courtyard scan. The caller verifies loaded chunks and claims first. */
public final class CastleGeometry {
    private CastleGeometry() {}
    public record Cell(String data,boolean solid,boolean passable,boolean stone,boolean gate) {}
    @FunctionalInterface public interface Blocks { Cell at(int x,int y,int z); }
    public record Result(int area,boolean walls,boolean perimeter,boolean gates,String fingerprint) {}

    public static Result scan(Blocks blocks,int x0,int x1,int y,int z0,int z1,int height) {
        Model.require(x1>=x0 && z1>=z0 && (long)x1-x0<128 && (long)z1-z0<128 && height>=1 && height<=32,"Некоректні межі замку.");
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        int area=0,gateColumns=0;boolean walls=true,perimeter=true;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
            Cell floor=blocks.at(x,y-1,z),low=blocks.at(x,y,z),high=blocks.at(x,y+1,z);
            hash(digest,floor);hash(digest,low);hash(digest,high);
            if(floor.solid() && low.passable() && high.passable())area++;
        }
        for(int x=x0-1;x<=x1+1;x++)for(int z=z0-1;z<=z1+1;z++)
            if(x==x0-1 || x==x1+1 || z==z0-1 || z==z1+1) {
                boolean entrance=blocks.at(x,y,z).gate();
                if(entrance)gateColumns++;
                for(int dy=0;dy<height;dy++) {
                    Cell block=blocks.at(x,y+dy,z);hash(digest,block);
                    boolean allowed=block.stone() || entrance && dy<2 && (block.gate() || block.passable());
                    if(!allowed)walls=false;
                    if(dy==0 && !block.stone() && !entrance)perimeter=false;
                }
            }
        return new Result(area,walls,perimeter,gateColumns>0,HexFormat.of().formatHex(digest.digest()));
    }
    private static void hash(MessageDigest digest,Cell cell) {
        digest.update(cell.data().getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
    }
}
