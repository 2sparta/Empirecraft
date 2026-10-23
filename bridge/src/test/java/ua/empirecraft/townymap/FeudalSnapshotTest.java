package ua.empirecraft.townymap;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeudalSnapshotTest {
    static Map<String,Object> row(String id,String world,int layer,String title,boolean marker){return Map.of("id",id,"name","Володіння","world",world,"color",0x123456,"cellSize",16,"cells",List.of(List.of(-2,3),List.of(-2,3)),"title",title,"layer",layer,"marker",marker);}
    @Test void separatesWorldsAndRetainsMetadata() throws Exception {
        var rows=List.of(row("duchy/world","world",1001,"Правитель А",false),row("duchy/europe","world_europe",1001,"Правитель А",false),row("market","world_europe",2000,"Торговий центр",true));
        var result=TownyAccess.parseFeudal(rows,w->w.equals("world")?"minecraft:overworld":"mohist:world_europe");
        assertEquals(3,result.size());assertEquals("minecraft:overworld",result.get("duchy/world").dimension());assertEquals("mohist:world_europe",result.get("market").dimension());assertTrue(result.get("market").marker());assertEquals(1,result.get("duchy/world").cells().size());assertEquals(1001,result.get("duchy/world").layer());
    }
    @Test void rulerAndDiplomacyChangesInvalidateClaimCache() throws Exception {var before=TownyAccess.parseFeudal(List.of(row("x","world",1000,"Правитель А; союз",false)),w->"minecraft:overworld");var after=TownyAccess.parseFeudal(List.of(row("x","world",1000,"Правитель Б; ненапад",false)),w->"minecraft:overworld");assertNotEquals(before,after);}
    @Test void unloadedWorldsAreSkipped() throws Exception {assertTrue(TownyAccess.parseFeudal(List.of(row("x","unloaded",1000,"",false)),w->null).isEmpty());}
    @Test void immutableCellsAndBoundedTitles() throws Exception {var r=TownyAccess.parseFeudal(List.of(row("x","world",1000,"A".repeat(5000),false)),w->"minecraft:overworld").get("x");assertEquals(2000,r.title().length());assertThrows(UnsupportedOperationException.class,()->r.cells().clear());}
}
