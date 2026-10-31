package ua.posters;
import java.util.*;
/** A synthetic two-masted ship, not the user's schematic. Used only for regression checks/previews. */
public final class ShipFixture {
    public static List<Projection.Box> boxes(){
        List<Projection.Box> out=new ArrayList<>();int id=0;
        for(int z=0;z<91;z++){
            double t=(z-45)/46.0;int beam=Math.max(1,(int)(18*Math.sqrt(Math.max(0,1-t*t))));
            for(int x=18-beam;x<=18+beam;x++){
                int bottom=2+(int)(Math.pow(Math.abs(x-18)/(double)beam,2)*6+Math.abs(t)*4);
                out.add(new Projection.Box(x,bottom,z,x+1,bottom+1,z+1,id++,0x654a30));
                boolean edge=x==18-beam||x==18+beam;
                if(edge)for(int y=bottom+1;y<19;y++)out.add(new Projection.Box(x,y,z,x+1,y+1,z+1,id++,y==15?0x98574b:0x654a30));
                if(!edge&&z>4&&z<88)out.add(new Projection.Box(x,17,z,x+1,17.5,z+1,id++,0xad8d56));
                if(edge&&z>7&&z<86){out.add(new Projection.Box(x,20,z,x+1,20.25,z+1,id++,0xad8d56));if(z%4==0)out.add(new Projection.Box(x+.35,18,z+.35,x+.65,20,z+.65,id++,0x44484e));}
            }
        }
        for(int z=71;z<86;z++)for(int x=10;x<27;x++){
            out.add(new Projection.Box(x,23,z,x+1,23.5,z+1,id++,0xad8d56));
            if((x==10||x==26)&&z%3==0)out.add(new Projection.Box(x+.3,23,z+.3,x+.7,25,z+.7,id++,0x654a30));
        }
        // Two masts, each with yards, an ivory sail and fine standing rigging.
        int[] zs={26,59},tops={66,77};
        for(int m=0;m<2;m++){
            int z=zs[m],top=tops[m];out.add(new Projection.Box(17.6,18,z-.4,18.4,top,z+.4,id++,0x654a30));
            for(int yy:new int[]{top-5,top-25}){
                out.add(new Projection.Box(4,yy,z-.2,33,yy+.35,z+.2,id++,0x654a30));
                for(int y=yy-15;y<yy;y++){
                    double inset=(yy-y)/12.0;
                    out.add(new Projection.Box(4+inset,y,z+.55,33-inset,y+1,z+.65,id++,0xe5dfce));
                }
            }
            for(int step=0;step<60;step++){
                double a=step/60.0,b=(step+1)/60.0;
                for(int sign:new int[]{-1,1})out.add(new Projection.Box(18-.05,18+(top-22)*a,Math.min(z+sign*17*(1-a),z+sign*17*(1-b))-.06,18+.05,18+(top-22)*b+.1,Math.max(z+sign*17*(1-a),z+sign*17*(1-b))+.06,id++,0x44484e));
            }
            out.add(new Projection.Box(18.5,top-2,z,23.5,top-.6,z+.12,id++,m==0?0xa34040:0x526e91));
        }
        return out;
    }
}
