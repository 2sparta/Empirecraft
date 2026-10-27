package ua.empirecraft.feudal;

import com.palmergames.bukkit.config.CommentedConfiguration;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Database.*;

/** Real pinned Towny events and H2, with only the server/config environment staged. */
public final class ProvisionClaimTests {
    static void field(Object object,Class<?> type,String name,Object value)throws Exception {
        var f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    static void environment()throws Exception {
        Bukkit.setServer((Server)Proxy.newProxyInstance(ProvisionClaimTests.class.getClassLoader(),new Class<?>[]{Server.class},(proxy,method,args)->switch(method.getName()){
            case "isPrimaryThread" -> true;
            case "getLogger" -> Logger.getLogger("provision-claim-test");
            case "getName","getVersion","getBukkitVersion" -> "Pinned Towny provision regression";
            default -> throw new UnsupportedOperationException(method.getName());
        }));
        Path config=Files.createTempFile("towny-provision-claims-", ".yml");config.toFile().deleteOnExit();
        field(null,TownySettings.class,"config",new CommentedConfiguration(config,Logger.getLogger("provision-claim-test")));
        Translation.addTranslations(Map.of("en_US",Map.of("msg_claim_error","Native Towny claim rejected")));
    }
    record Candidate(Town town,TownBlock home,TownyWorld world) {}
    static Candidate candidate()throws Exception {
        var town=new Town("Town_"+UUID.randomUUID(),UUID.randomUUID());var world=new TownyWorld("world");
        var block=new TownBlock(0,0,world);attach(town,block);field(town,Town.class,"homeBlock",block);
        return new Candidate(town,block,world);
    }
    @SuppressWarnings("unchecked") static void attach(Town town,TownBlock block)throws Exception {
        // Native TownCommand.newTown calls block.setTown before firing the event.
        // Stage that exact unfinished state without starting Towny's scheduler/data source.
        field(block,TownBlock.class,"town",town);
        var f=Town.class.getDeclaredField("townBlocks");f.setAccessible(true);
        ((Map<WorldCoord,TownBlock>)f.get(town)).put(block.getWorldCoord(),block);
    }
    static Resident mayor(Town town)throws Exception {
        var resident=new Resident("Founder");resident.setUUID(UUID.randomUUID());
        field(town,Town.class,"mayor",resident);field(resident,Resident.class,"town",town);return resident;
    }
    static TownPreClaimEvent event(Town town,TownBlock block,boolean home,boolean outpost,boolean overclaim){return new TownPreClaimEvent(town,block,null,outpost,home,overclaim);}
    static void clock(Fixture f){long now=f.s.now();System.setProperty(ProvisionClock.PROPERTY,"1|"+now+"|0|false|"+now);}
    static ProvisionController controller(ProvisionService service){return new ProvisionController(null,service,null,null);}
    public static void main(String[] args)throws Exception {
        count=0;environment();Class.forName("org.h2.Driver");
        test("Native Towny founding already owns its first home plot before the mayor exists",()->{try(var f=new Fixture()){
            clock(f);var c=candidate();eq(c.town.getNumTownBlocks(),1);eq(c.town.hasMayor(),false);eq(c.home.getTownOrNull(),c.town);eq(c.town.getHomeBlockOrNull(),c.home);
            var service=new ProvisionService(f.s,new CommerceService(f.s));var e=event(c.town,c.home,true,false,false);controller(service).claim(e);
            eq(e.isCancelled(),false);eq(service.town(TownyBridge.townId(c.town)),null);
        }});
        test("Successful founding initializes the actual plot count and enables resident stock before expansion",()->{try(var f=new Fixture()){
            clock(f);var c=candidate();var commerce=new CommerceService(f.s);var service=new ProvisionService(f.s,commerce);var controller=controller(service);
            var first=event(c.town,c.home,true,false,false);controller.claim(first);eq(first.isCancelled(),false);
            var founder=mayor(c.town);eq(founder.hasTown(),true);eq(founder.getTownOrNull(),c.town);
            controller.founded(new NewTownEvent(c.town));String id=TownyBridge.townId(c.town);
            eq(number(service.town(id),"chunks"),1L);eq(service.reserve(id),java.math.BigInteger.ZERO);
            var second=new TownBlock(1,0,c.world);var denied=event(c.town,second,false,false,false);controller.claim(denied);
            eq(denied.isCancelled(),true);eq(denied.getCancelMessage().contains("24"),true);
            String op=service.transfer(a,id,0,0,"bread-full-NBT",4,5,64,new CommerceService.InventoryChange(a.id(),"before","after"));commerce.finish(op,true);
            var allowed=event(c.town,second,false,false,false);controller.claim(allowed);eq(allowed.isCancelled(),false);
            // Four bread = 20 hunger: enough for two spring chunks (16), not three (24).
            var third=event(c.town,new TownBlock(2,0,c.world),false,false,false);controller.claim(third);eq(third.isCancelled(),true);
        }});
        test("A home flag on an existing town does not bypass the 24-hour reserve",()->{try(var f=new Fixture()){
            clock(f);var c=candidate();mayor(c.town);var service=new ProvisionService(f.s,new CommerceService(f.s));
            var e=event(c.town,new TownBlock(1,0,c.world),true,false,false);controller(service).claim(e);eq(e.isCancelled(),true);
        }});
        test("A town with a mayor and zero plots cannot reuse the founding exemption",()->{try(var f=new Fixture()){
            clock(f);var town=new Town("Existing",UUID.randomUUID());mayor(town);var world=new TownyWorld("world");
            var e=event(town,new TownBlock(0,0,world),true,false,false);controller(new ProvisionService(f.s,new CommerceService(f.s))).claim(e);eq(e.isCancelled(),true);
        }});
        test("An unfinished multi-plot town cannot receive several free home plots",()->{try(var f=new Fixture()){
            clock(f);var c=candidate();attach(c.town,new TownBlock(1,0,c.world));
            var e=event(c.town,c.home,true,false,false);controller(new ProvisionService(f.s,new CommerceService(f.s))).claim(e);eq(e.isCancelled(),true);
        }});
        test("Outposts and takeovers are never founding home claims",()->{try(var f=new Fixture()){
            clock(f);var service=new ProvisionService(f.s,new CommerceService(f.s));
            for(boolean outpost:List.of(true,false)){var c=candidate();var e=event(c.town,c.home,true,outpost,!outpost);controller(service).claim(e);eq(e.isCancelled(),true);}
        }});
        test("Founding still fails closed when the calendar signal is unavailable",()->{try(var f=new Fixture()){
            System.clearProperty(ProvisionClock.PROPERTY);var c=candidate();var service=new ProvisionService(f.s,new CommerceService(f.s));
            var e=event(c.town,c.home,true,false,false);controller(service).claim(e);eq(e.isCancelled(),true);eq(service.town(TownyBridge.townId(c.town)),null);
        }});
        test("Another plugin's cancellation is preserved and no founding stock is invented",()->{try(var f=new Fixture()){
            clock(f);var c=candidate();var service=new ProvisionService(f.s,new CommerceService(f.s));var e=event(c.town,c.home,true,false,false);
            e.setCancelled(true);e.setCancelMessage("Other protection");controller(service).claim(e);eq(e.isCancelled(),true);eq(e.getCancelMessage(),"Other protection");eq(service.town(TownyBridge.townId(c.town)),null);
        }});
        System.clearProperty(ProvisionClock.PROPERTY);System.out.println("Native Towny provision claim regressions: "+count);
    }
}
