package ua.empirecraft.wildlife;
import com.google.gson.*;
import java.io.*;
import java.util.*;
public final class PolicyTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static boolean allowed(Policy p,String species,double lon,double lat,String habitat){return p.allowed("alexsmobs:"+species,"minecraft:overworld",p.earth.x(lon),150,p.earth.z(lat),habitat,35);}
    public static void main(String[] args)throws Exception{
        var p=new Policy(Policy.resource("wildlife-policy.json"),Policy.resource("earth-defaults.json"));
        check(allowed(p,"kangaroo",135,-25,"land"),"Kangaroo in Australia");
        check(!allowed(p,"kangaroo",32,48,"land"),"No kangaroos in Ukraine");
        check(!allowed(p,"kangaroo",25,-25,"land"),"No kangaroos in Africa's matching latitude");
        check(!allowed(p,"kangaroo",135,-25,"marine"),"Kangaroo terrain gate");
        check(allowed(p,"tasmanian_devil",147,-42,"land"),"Tasmanian devil in Tasmania");
        check(!allowed(p,"tasmanian_devil",135,-25,"land"),"Tasmanian devil excluded from mainland");
        check(allowed(p,"leafcutter_ant",-60,-5,"land"),"Leafcutters in Amazon");
        check(!allowed(p,"leafcutter_ant",25,0,"land"),"No leafcutters in Congo's matching rainforest");
        check(!allowed(p,"leafcutter_ant",32,48,"land"),"No leafcutters in Ukraine");
        check(allowed(p,"komodo_dragon",119.5,-8.5,"land"),"Komodo range");
        check(!allowed(p,"komodo_dragon",100,0,"land"),"Komodo excluded from mainland Asia");
        check(allowed(p,"crow",32,48,"land"),"Crow in Ukraine");
        check(allowed(p,"cachalot_whale",-30,35,"marine"),"Atlantic cachalot");
        check(allowed(p,"orca",-30,35,"marine"),"Keep Alex orca alongside TFC");
        check(!allowed(p,"cachalot_whale",33,43,"marine"),"No cachalots in Black Sea");
        check(!allowed(p,"orca",-30,35,"fresh"),"No whales in freshwater");
        check(!p.allowed("alexsmobs:orca","minecraft:overworld",p.earth.x(-30),62,p.earth.z(35),"marine",1),"Whale shallow water gate");
        check(p.removed("spawn:ant")&&!p.removed("naturalist:bear"),"Replace Spawn ant, retain Naturalist bear");
        for(String name:List.of("grizzly_bear","centipede_head","centipede_body","centipede_tail","fly","cockroach","tarantula_hawk","warped_mosco","void_worm","endergrade","sunbird","gazelle","moose","tiger","lobster","blue_jay","catfish"))check(p.removed("alexsmobs:"+name),"Removed "+name);
        for(var entry:p.animals().entrySet()) {
            String id=entry.getKey();var row=entry.getValue().getAsJsonObject();String habitat=row.get("habitat").getAsString();
            boolean found=false;
            // Use region centroids too: the pupfish's real range is much smaller than one degree.
            var points=new ArrayList<double[]>();
            for(var r:row.getAsJsonArray("regions")) {
                var ring=p.json.getAsJsonObject("regions").getAsJsonArray(r.getAsString());double x=0,z=0;
                for(int i=0;i<ring.size()-1;i++){x+=ring.get(i).getAsJsonArray().get(0).getAsDouble();z+=ring.get(i).getAsJsonArray().get(1).getAsDouble();}
                points.add(new double[]{x/(ring.size()-1),z/(ring.size()-1)});
            }
            for(int lon=-175;lon<=175;lon+=5)for(int lat=-75;lat<=75;lat+=5)points.add(new double[]{lon,lat});
            double depth=Math.max(row.get("min_depth").getAsDouble(),Math.min(35,row.get("max_depth").getAsDouble()));
            for(var point:points)if(p.allowed(id,"minecraft:overworld",p.earth.x(point[0]),150,p.earth.z(point[1]),habitat,depth)){found=true;break;}
            check(found,"At least one eligible geographic point for "+id);
            check(!p.allowed(id,"minecraft:the_nether",p.earth.x(135),150,p.earth.z(-25),habitat,35),"No Nether fallback "+id);
            check(!p.allowed(id,"minecraft:the_end",p.earth.x(135),150,p.earth.z(-25),habitat,35),"No End fallback "+id);
        }
        for(var whale:p.json.getAsJsonObject("whale_loot").entrySet()) {
            check(!whale.getValue().getAsJsonObject().has("wonderoussea:blaeen_bristles"),"Toothed whales have no baleen");
            check(whale.getValue().getAsJsonObject().getAsJsonArray("wonderoussea:raw_whale_venison").get(0).getAsInt()>=128,"Large whale yields");
        }
        try(var in=Policy.resource("earth-defaults.json")){
            var geography=JsonParser.parseReader(in).getAsJsonObject();geography.getAsJsonObject("projection").addProperty("offset_x",1000);geography.getAsJsonObject("projection").addProperty("offset_z",-2000);
            var shifted=new Policy(new StringReader(p.json.toString()),new StringReader(geography.toString()));
            check(allowed(shifted,"kangaroo",135,-25,"land"),"Honour administrator's Earth projection offsets");
        }
        for(String species:List.of("naturalist:elephant","alexsmobs:elephant","alexsmobs:cachalot_whale","alexsmobs:orca")) {
            double[] bonus=p.stats(species);
            check(bonus!=null&&bonus[0]>0&&bonus[1]>0,"Beast stats for "+species);
        }
        check(p.stats("alexsmobs:kangaroo")==null,"Kangaroos keep vanilla stats");
        System.out.println("WILDLIFE_POLICY_PASS checks="+checks+" species="+p.animals().size());
    }
}
