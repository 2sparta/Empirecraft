package ua.empirecraft.townymap;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.empirecraft.townymap.client.StrategyLayout;
import static org.junit.jupiter.api.Assertions.*;

class StrategyMapTest {
    static final String WORLD="minecraft:overworld";
    static final ClaimKnowledge.Chunk HOME=new ClaimKnowledge.Chunk(WORLD,0,0);
    @TempDir Path directory;
    private static ShipObservation ship(long id,long time,String country) {
        return new ShipObservation(id,WORLD,10,64,20,90,5,time,"Чорний крук",country,"Січ","STATE","{}");
    }
    private static Claims country(String profile) {
        return new Claims("KINGDOMS/nation/test/"+WORLD,"Січ",WORLD,0x123456,16,
                Set.of(new ClaimGeometry.Cell(0,0)),"Старі відомості",0,"",profile);
    }
    private static ClaimKnowledge knowledge(Claims claims) {
        var index=new ClaimIndex(Map.of(MapMode.KINGDOMS,Map.of(claims.id(),claims)));
        var memory=new ClaimKnowledge();memory.observe(HOME,index.observe(HOME),1);return memory;
    }
    @Test void governmentAndMultipleLeadersRemainExactlyAsObserved() {
        String profile="{\"government\":\"REPUBLIC\",\"leaders\":[{\"name\":\"Олена\"},{\"name\":\"Богдан\"}],\"flag\":{\"hash\":\"old\"}}";
        var memory=knowledge(country(profile));var before=memory.snapshot();
        var future=country("{\"government\":\"MONARCHY\",\"leaders\":[{\"name\":\"Інший\"}]}");
        var elsewhere=new ClaimKnowledge.Chunk(WORLD,100,100);
        memory.observe(elsewhere,new ClaimIndex(Map.of(MapMode.KINGDOMS,Map.of(future.id(),future))).observe(elsewhere),2);
        assertEquals(profile,ClaimKnowledge.claims(memory.snapshot(),MapMode.KINGDOMS).get(0).profile());
        assertEquals(before.observations().get(0),memory.at(HOME));
    }
    @Test void politicalProfileSurvivesDiskRestart()throws Exception {
        var original=knowledge(country("{\"country\":\"Січ\",\"leaders\":[{},{}]}"));var id=UUID.randomUUID();
        try(var store=new KnowledgeStore(directory,error->fail(error))){store.save(id,original.snapshot());}
        try(var store=new KnowledgeStore(directory,error->fail(error))) {
            assertEquals(original.snapshot(),store.load(id).snapshot());
        }
    }
    @Test void oldFilesWithoutProfileAreAcceptedWithoutResettingLand() {
        var json=new Gson().toJsonTree(knowledge(country("")).snapshot()).getAsJsonObject();
        for(var o:json.getAsJsonArray("observations"))for(var f:o.getAsJsonObject().getAsJsonArray("fragments"))
            f.getAsJsonObject().getAsJsonObject("label").remove("profile");
        var restored=ClaimKnowledge.restore(new Gson().fromJson(json,ClaimKnowledge.Snapshot.class));
        var rows=ClaimKnowledge.claims(restored.snapshot(),MapMode.KINGDOMS);
        assertEquals(1,rows.size());assertEquals("Січ",rows.get(0).name());assertEquals("",rows.get(0).profile());
    }
    @Test void unicodeProfileDoesNotExceedJavaModifiedUtfLimit() {
        String profile="Ї".repeat(32768);
        var claims=country(profile);var memory=knowledge(claims);
        assertDoesNotThrow(()->ClaimKnowledge.claims(memory.snapshot(),MapMode.KINGDOMS));
        assertThrows(IllegalArgumentException.class,()->new ClaimKnowledge.Label(MapMode.KINGDOMS,"id","Name",WORLD,1,16,"",0,profile+"a"));
    }
    @Test void changingProfileChangesOverlayIdentityWithoutMixingRulers() {
        var a=ClaimKnowledge.claims(knowledge(country("{\"ruler\":\"a\"}")).snapshot(),MapMode.KINGDOMS).get(0);
        var b=ClaimKnowledge.claims(knowledge(country("{\"ruler\":\"b\"}")).snapshot(),MapMode.KINGDOMS).get(0);
        assertNotEquals(a.id(),b.id());
    }
    @Test void banditVisibilityRetainsProfileOnlyInTemporaryView() {
        var base=country("{\"leaders\":[{},{}]}");
        var bandit=new Claims(base.id(),"Розбійники_Січ",WORLD,1,16,base.cells(),"Січ",0,"bandit",base.profile());
        var source=Map.of(MapMode.KINGDOMS,Map.of(bandit.id(),bandit));var live=new BanditTerritories(source);
        assertEquals(base.profile(),live.visible(MapMode.KINGDOMS,WORLD,live.nearby(WORLD,8,8)).get(0).profile());
        assertTrue(live.visible(MapMode.KINGDOMS,WORLD,live.nearby(WORLD,1000,1000)).isEmpty());
        assertTrue(new ClaimIndex(source).observe(HOME).isEmpty());
    }
    @Test void knownClimateRemainsVisibleWhenCountryIsHidden() {
        var cells=Set.of(new ClaimGeometry.Cell(0,0));
        var label=new ClaimKnowledge.Label(MapMode.CLIMATE,"climate/TEMPERATE","Помірний",WORLD,1,16,"TFC",0);
        var memory=new ClaimKnowledge();memory.observe(HOME,List.of(new ClaimKnowledge.Fragment(label,cells)),1);
        var bandit=new Claims("KINGDOMS/nation/a","РозбійникиA",WORLD,1,16,cells,"",0,"a");
        var live=new BanditTerritories(Map.of(MapMode.KINGDOMS,Map.of(bandit.id(),bandit)));
        assertEquals(1,ClaimKnowledge.claims(live.history(memory.snapshot()),MapMode.CLIMATE).size());
        assertTrue(ClaimKnowledge.claims(live.history(memory.snapshot()),MapMode.KINGDOMS).isEmpty());
    }
    @Test void climateAndRainfallHaveSeparateOverlayIdentity() {
        var climate=new ClaimKnowledge.Label(MapMode.CLIMATE,"climate/TEMPERATE","Помірний",WORLD,1,16,"TFC",0);
        var rain=new ClaimKnowledge.Label(MapMode.RAINFALL,"rainfall/2","Помірні опади",WORLD,2,16,"TFC",0);
        var memory=new ClaimKnowledge();var cell=Set.of(new ClaimGeometry.Cell(0,0));
        memory.observe(HOME,List.of(new ClaimKnowledge.Fragment(climate,cell),new ClaimKnowledge.Fragment(rain,cell)),1);
        assertEquals(1,ClaimKnowledge.claims(memory.snapshot(),MapMode.CLIMATE).size());
        assertEquals(1,ClaimKnowledge.claims(memory.snapshot(),MapMode.RAINFALL).size());
        assertNotEquals(climate.overlayId(),rain.overlayId());
    }
    @Test void addingModesDoesNotRenumberPreviouslySavedModes() {
        assertEquals(0,MapMode.KINGDOMS.ordinal());assertEquals(1,MapMode.TOWNS.ordinal());
        assertEquals(2,MapMode.HIERARCHY.ordinal());assertEquals(3,MapMode.TRADE.ordinal());
        assertTrue(MapMode.PROVINCES.ordinal()>3);assertTrue(MapMode.CLIMATE.ordinal()>3);
    }
    @Test void unseenShipRetainsPositionVelocityFlagAndOwnershipAsOneSnapshot() {
        var log=new ShipObservation.Log();var seen=ship(1,100,"Країна A");log.observe(seen);
        for(int i=0;i<100;i++)assertEquals(List.of(seen),log.snapshot());
        assertEquals(90,log.get(1).heading());assertEquals(5,log.get(1).speed());assertEquals("Країна A",log.get(1).country());
        assertThrows(UnsupportedOperationException.class,()->log.snapshot().clear());
    }
    @Test void outOfOrderSightingsCannotMoveShipBackwardsInTime() {
        var log=new ShipObservation.Log();assertTrue(log.observe(ship(1,200,"B")));
        assertFalse(log.observe(ship(1,100,"A")));assertEquals("B",log.get(1).country());
        assertFalse(log.observe(ship(1,200,"B")));assertEquals(1,log.snapshot().size());
    }
    @Test void differentPlayersNeverShareDiscoveries() {
        var a=new ShipObservation.Log();var b=new ShipObservation.Log();a.observe(ship(1,100,"A"));
        assertTrue(b.snapshot().isEmpty());assertNull(b.get(1));
    }
    @Test void opticalLogIsBoundedAndEvictsOldestObservation() {
        var log=new ShipObservation.Log();for(int i=0;i<129;i++)log.observe(ship(i,i,"A"));
        assertEquals(128,log.snapshot().size());assertNull(log.get(0));assertNotNull(log.get(128));
        log.observe(ship(1,10000,"B"));assertEquals(128,log.snapshot().size());assertEquals("B",log.get(1).country());
    }
    @Test void forgedOrDamagedShipCoordinatesAreRejected() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,30_000_001})
            assertThrows(IllegalArgumentException.class,()->new ShipObservation(1,WORLD,value,0,0,0,0,1,"a","b","c","STATE","{}"));
        assertThrows(IllegalArgumentException.class,()->new ShipObservation(-1,WORLD,0,0,0,0,0,1,"a","b","c","STATE","{}"));
        assertThrows(IllegalArgumentException.class,()->new ShipObservation(1,WORLD,0,0,0,0,-1,1,"a","b","c","STATE","{}"));
    }
    @Test void headingWrapsAndSpeedConversionIsPhysical() {
        var row=new ShipObservation(1,WORLD,0,0,0,-90,1,1,"a","b","c","STATE","{}");
        assertEquals(270,row.heading());assertEquals(1.943844492,row.knots(),1e-9);
    }
    @Test void diskHistorySurvivesRestartAndImmediateReconnect()throws Exception {
        var id=UUID.randomUUID();var old=ship(1,1,"A");var fresh=ship(1,2,"B");
        try(var store=new ShipObservationStore(directory,error->fail(error))){
            store.save(id,List.of(old));store.save(id,List.of(fresh));assertEquals(fresh,store.load(id).get(1));
        }
        try(var store=new ShipObservationStore(directory,error->fail(error))){assertEquals(fresh,store.load(id).get(1));}
    }
    @Test void corruptedShipFileIsNeverReplacedByEmptyData()throws Exception {
        var id=UUID.randomUUID();Path file=directory.resolve(id+".json.gz");byte[] raw={1,2,3,4};Files.write(file,raw);
        try(var store=new ShipObservationStore(directory,error->fail(error))){assertThrows(java.io.IOException.class,()->store.load(id));}
        assertArrayEquals(raw,Files.readAllBytes(file));
    }
    @Test void unsupportedFormatIsPreservedForRecovery()throws Exception {
        var id=UUID.randomUUID();var file=directory.resolve(id+".json.gz");
        try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write("{\"format\":99,\"ships\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        var before=Files.readAllBytes(file);
        try(var store=new ShipObservationStore(directory,error->fail(error))){assertThrows(java.io.IOException.class,()->store.load(id));}
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void sameUuidInDifferentWorldsHasSeparateShipHistory()throws Exception {
        var id=UUID.randomUUID();
        try(var first=new ShipObservationStore(directory.resolve("first"),error->fail(error));var second=new ShipObservationStore(directory.resolve("second"),error->fail(error))){
            first.save(id,List.of(ship(1,1,"A")));assertTrue(second.load(id).snapshot().isEmpty());
        }
    }
    @Test void duplicateOrOversizedRestoredShipLogsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->ShipObservation.Log.restore(List.of(ship(1,1,"A"),ship(1,2,"B"))));
        assertThrows(IllegalArgumentException.class,()->ShipObservation.Log.restore(Collections.nCopies(129,ship(1,1,"A"))));
    }
    @Test void sidebarInputAndDrawingUseTheSameBoundsAtDifferentGuiScales() {
        for(int width:List.of(320,420,640,854,1920))for(int height:List.of(240,360,480,1080)) {
            var layout=StrategyLayout.of(width,height);assertTrue(layout.panelWidth()+6<width);
            assertTrue(layout.panel(6,layout.panelTop()));assertFalse(layout.panel(5,layout.panelTop()));
            assertFalse(layout.panel(layout.panelWidth()+6,layout.panelTop()));assertFalse(layout.panel(10,layout.panelBottom()));
            assertTrue(layout.rows(155,40)>=1);assertTrue(layout.toolbar(10,10));assertFalse(layout.toolbar(width-1,100));
        }
    }
    @Test void oversizedOrMisnumberedNetworkPagesAreRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException.class,()->new StrategyNetwork.Observations("[]",-1,1,8,8));
        assertThrows(IllegalArgumentException.class,()->new StrategyNetwork.Observations("[]",-1,1,0,9));
        assertThrows(IllegalArgumentException.class,()->new StrategyNetwork.Observations("x".repeat(300001),-1,1,0,1));
    }
    @Test void shipViewChangesOnlyWhenAllPagesArrive() {
        var pages=new ShipObservation.Pages();
        assertTrue(pages.accept(1,0,2,List.of(ship(1,1,"A"))).isEmpty());
        var complete=pages.accept(1,1,2,List.of(ship(2,2,"B"))).orElseThrow();
        assertEquals(2,complete.size());assertTrue(complete.stream().anyMatch(s->s.ship()==1));
    }
    @Test void oldOrDuplicateShipPagesDoNotResurrectPreviousContacts() {
        var pages=new ShipObservation.Pages();assertTrue(pages.accept(2,0,1,List.of(ship(1,2,"B"))).isPresent());
        assertTrue(pages.accept(1,0,1,List.of(ship(1,1,"A"))).isEmpty());
        assertTrue(pages.accept(2,0,1,List.of(ship(1,2,"B"))).isEmpty());
    }
    @Test void repeatedFirstPageDoesNotDiscardPendingSecondPage() {
        var pages=new ShipObservation.Pages();pages.accept(1,0,2,List.of(ship(1,1,"A")));
        assertTrue(pages.accept(1,0,2,List.of(ship(1,1,"A"))).isEmpty());
        assertEquals(2,pages.accept(1,1,2,List.of(ship(2,2,"B"))).orElseThrow().size());
    }
    @Test void reconnectAcceptsANewServerSequenceAndAnEmptyLogClearsOldContacts() {
        var pages=new ShipObservation.Pages();pages.accept(100,0,1,List.of(ship(1,1,"A")));pages.clear();
        assertEquals(List.of(),pages.accept(1,0,1,List.of()).orElseThrow());
    }
}
