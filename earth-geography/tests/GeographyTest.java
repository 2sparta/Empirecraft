package ua.empirecraft.earth;
import java.nio.file.*;
import java.io.*;
import com.google.gson.*;
public final class GeographyTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void near(double a,double b,double epsilon,String message){check(Math.abs(a-b)<epsilon,message+": "+a+" != "+b);}
    public static void main(String[] args)throws Exception{
        var source=Files.readString(Path.of(args[0]));var d=new Geography(new StringReader(source));
        double[][] points={{5954.56,-8417.41,29.075,41.1006},{6814.43,-7182.79,33.2736,35.0722},{-1119.73,-7367.81,-5.4674,35.9756},{7516.37,-9297.76,36.701,45.3992},{-522.24,-10127.21,-2.55,49.4493},{-16517.82,-5155.17,-80.6534,25.1717},{-16337,-1867,-79.7705,9.1162}};
        for(var row:points){var p=d.coordinates(row[0],row[1]);near(p.lon(),row[2],.0001,"longitude");near(p.lat(),row[3],.0001,"latitude");near(d.x(p.lon()),row[0],1e-7,"x round trip");near(d.z(p.lat()),row[1],1e-7,"z round trip");}
        var med=new Geography.Point(15,37);check(d.monthlyRainfall(med,0)>d.monthlyRainfall(med,6),"Mediterranean dry summer");
        var india=new Geography.Point(80,20);check(d.monthlyRainfall(india,6)>d.monthlyRainfall(india,0),"Indian summer monsoon");
        var frenchBoundary=d.coordinates(-188,-9012);
        var nearby=d.coordinates(-188,-9011);
        near(d.climate(frenchBoundary).mean(),d.climate(nearby).mean(),.5,"Continuous temperature at the French climate boundary");
        near(d.monthlyRainfall(frenchBoundary,6),d.monthlyRainfall(nearby,6),5,"Continuous summer rain at the French climate boundary");
        boolean variesAlongBoundary=false;
        for(int x=-512;x<=512;x+=16){
            variesAlongBoundary |= Math.abs(d.climate(d.coordinates(x,-9012)).mean()
                -d.climate(frenchBoundary).mean())>.25;
        }
        check(variesAlongBoundary,"Climate transition varies along longitude");
        check(d.soilClass(new Geography.Point(2.35,48.86)).equals("luvisol"),"Paris Basin soils");
        check(d.soilClass(new Geography.Point(-1,44.4)).equals("podzol"),"Landes sandy soils");
        check(d.soilClass(new Geography.Point(5.4,43.5)).equals("rendzina"),"Provence limestone soils");
        var k=new Geography.Point(32,48);check(d.soilClass(k).equals("chernozem"),"Central Ukraine black soil");
        check(d.soilClass(new Geography.Point(27,51.5)).equals("sod_podzolic"),"Polissia is not all chernozem");
        check(d.zone(new Geography.Point(24,48)).equals("montane_forest"),"Carpathian priority");
        var north=new Geography.Point(50,40);var south=new Geography.Point(50,-40);
        check(d.climate(north).temperature(6,north.lat())>d.climate(north).temperature(0,north.lat()),"Northern July summer");
        check(d.climate(south).temperature(0,south.lat())>d.climate(south).temperature(6,south.lat()),"Southern January summer");
        var equator=new Geography.Point(-60,0);check(d.climate(equator).temperature(0,0)>20,"Equatorial January warm");
        near(d.climate(equator).temperature(0,0),d.climate(equator).temperature(6,0),3.1,"Equatorial weak thermal seasons");
        check(Geography.localMonth(0,-40)==6,"Hemisphere shift");
        check(d.oreAllowed(new Geography.Point(33.4,48),"hematite"),"Kryvbas iron");
        check(!d.oreAllowed(new Geography.Point(28,51),"diamond"),"No blanket diamonds in Ukraine");
        check(d.oreAllowed(new Geography.Point(38,48),"bituminous_coal"),"Donbas coal");
        check(d.at("marine",new Geography.Point(33,43)).id().equals("black_sea"),"Black Sea");
        check(d.at("marine",new Geography.Point(36.5,46)).id().equals("azov"),"Azov overrides Black Sea");
        check(d.aquaticAllowed("minecraft:dolphin",new Geography.Point(33,43),"marine",10),"Black Sea dolphin");
        check(!d.aquaticAllowed("minecraft:dolphin",new Geography.Point(33,43),"marine",180),"Black Sea deep-water oxygen mask");
        check(d.aquaticAllowed("minecraft:zombie",new Geography.Point(33,43),"land",180),"Marine oxygen mask does not affect underground land mobs");
        check(d.aquaticAllowed("minecraft:salmon",new Geography.Point(-30,50),"marine",10),"Atlantic migratory salmon");
        check(d.aquaticAllowed("minecraft:salmon",new Geography.Point(30,50),"fresh",10),"Freshwater salmon");
        check(!d.aquaticAllowed("minecraft:cod",new Geography.Point(33,43),"marine",10),"No Atlantic cod in Black Sea");
        check(!d.aquaticAllowed("minecraft:dolphin",new Geography.Point(33,43),"fresh",10),"No dolphin in fresh water");
        check(!d.aquaticAllowed("minecraft:dolphin",new Geography.Point(33,43),"land",10),"Water mask");
        check(d.plantAllowed("minecraft:cocoa",equator,26,0),"Cocoa grows in equatorial January");
        check(!d.plantAllowed("minecraft:cocoa",k,-3,0),"Cocoa blocked in Ukraine winter");
        check(d.plantAllowed("minecraft:wheat",south,22,0),"Wheat southern summer");
        check(!d.plantAllowed("minecraft:wheat",south,22,6),"Wheat southern winter calendar gate");
        var root=JsonParser.parseString(source).getAsJsonObject();root.getAsJsonObject("projection").addProperty("blocks_per_degree",0);
        try{new Geography(new StringReader(root.toString()));throw new AssertionError("Accepted invalid scale");}catch(IllegalArgumentException expected){checks++;}
        String polygon="{\"id\":\"hole\",\"layer\":\"ecology\",\"label\":\"test\",\"priority\":999,\"rings\":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[2,2],[8,2],[8,8],[2,8],[2,2]]],\"values\":{}}";
        root=JsonParser.parseString(source).getAsJsonObject();root.getAsJsonArray("features").add(JsonParser.parseString(polygon));var holes=new Geography(new StringReader(root.toString()));
        check(holes.at("ecology",new Geography.Point(1,1)).id().equals("hole"),"Polygon shell");
        check(holes.at("ecology",new Geography.Point(5,5))==null || !holes.at("ecology",new Geography.Point(5,5)).id().equals("hole"),"Polygon hole");
        check(holes.at("ecology",new Geography.Point(0,5)).id().equals("hole"),"Polygon boundary");
        check(d.at("ecology",new Geography.Point(190,0))==null,"Map bounds");
        System.out.println("PASS "+checks+" geographic checks");
    }
}
