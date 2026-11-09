import com.quintonc.vs_sails.protection.ShipAccess;
import java.util.*;

public final class ShipAccessTest {
    static int checks;
    static void check(boolean b,String message) {checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args) {
        UUID home=UUID.randomUUID(),ally=UUID.randomUUID(),other=UUID.randomUUID(),captain=UUID.randomUUID(),stranger=UUID.randomUUID();
        var ship=new ShipAccess.Vessel(home,captain);var fleet=new ShipAccess.Grants();
        check(ShipAccess.allows(ship,fleet,captain,home,true),"own town with permission");
        check(!ShipAccess.allows(ship,fleet,captain,home,false),"own residents still need control permission");
        check(!ShipAccess.allows(ship,fleet,stranger,ally,true),"alliance grants no ship access");
        ship.grants.towns.add(ally);
        check(ShipAccess.allows(ship,fleet,stranger,ally,true),"trusted town's authorised captain");
        check(!ShipAccess.allows(ship,fleet,stranger,ally,false),"trusted town's unauthorised resident");
        check(!ShipAccess.allows(ship,fleet,stranger,other,true),"town departure removes inherited permission");
        ship.grants.players.add(stranger);
        check(ShipAccess.allows(ship,fleet,stranger,other,true),"explicit player grant");
        check(ShipAccess.allows(ship,fleet,stranger,other,false),"explicit crew grant is sufficient without a separate permission");
        ship.captain=stranger;ship.grants.players.clear();check(ShipAccess.allows(ship,fleet,stranger,other,false),"captain does not need a global control permission");ship.captain=null;
        ship.offeredTo=other;
        try{ship.accept(ally);throw new AssertionError("wrong destination accepted");}catch(IllegalStateException expected){checks++;}
        check(ship.town.equals(home),"offer leaves original ownership active");
        ship.accept(other);
        check(ship.town.equals(other) && ship.offeredTo==null,"destination accepts transfer");
        check(ship.grants.players.isEmpty() && ship.grants.towns.isEmpty(),"transfer clears seller's grants");
        check(!ShipAccess.allows(ship,fleet,captain,home,true),"seller immediately loses access");
        check(ShipAccess.allows(ship,fleet,captain,other,true),"buyer's permitted resident gains access");
        fleet.towns.add(ally);
        check(ShipAccess.allows(ship,fleet,stranger,ally,true),"fleet town grant");
        check(!ShipAccess.allows(ship,fleet,stranger,ally,false),"fleet does not bypass rank");
        fleet.towns.clear();check(!ShipAccess.allows(ship,fleet,stranger,ally,true),"fleet revocation is immediate");
        fleet.players.add(stranger);check(ShipAccess.allows(ship,fleet,stranger,null,true),"authorised individual can be townless");
        check(ShipAccess.allows(ship,fleet,stranger,null,false),"explicit fleet crew grant needs no extra permission");
        check(!ShipAccess.allows(null,fleet,stranger,home,true),"legacy ownership is never guessed");
        check(!ShipAccess.allows(new ShipAccess.Vessel(null,captain),fleet,captain,home,true),"personal founder must register a town");
        System.out.println("SHIP_ACCESS_PASS assertions="+checks);
    }
}
