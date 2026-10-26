import com.quintonc.vs_sails.ship.SailsShipControl;
import com.quintonc.vs_sails.ship.AnchorMechanics;
import com.quintonc.vs_sails.util.HullConnectivity;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.PhysShip;
import java.lang.reflect.Proxy;
import java.util.*;

public final class ShipIsolationTest {
    static int checks;
    static void check(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
    static final class Body {
        final long id;SailsShipControl controller;int staticChanges;
        Body(long id) {this.id=id;}
        LoadedServerShip proxy() {
            return (LoadedServerShip)Proxy.newProxyInstance(LoadedServerShip.class.getClassLoader(),new Class[]{LoadedServerShip.class},(p,m,a) -> {
                return switch(m.getName()) {
                    case "getId" -> id;
                    case "getAttachment" -> controller;
                    case "setAttachment" -> {controller=(SailsShipControl)a[a.length-1];yield null;}
                    case "getShipAABB" -> null;
                    case "setStatic" -> {staticChanges++;yield null;}
                    case "toString" -> "Test ship "+id;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p==a[0];
                    default -> throw new AssertionError("Unexpected ship access "+m.getName());
                };
            });
        }
    }
    public static void main(String[] args) {
        Body a=new Body(11),b=new Body(22);var shipA=a.proxy();var shipB=b.proxy();
        var first=SailsShipControl.getOrCreate(shipA,null);
        first.anchorDeployed=true;first.anchorLocked=true;first.numBuoys=17;first.numSails=9;
        b.controller=first; // Reproduce an aliased attachment from a clone/paste.
        var second=SailsShipControl.getOrCreate(shipB,null);
        check(first!=second,"copied attachment split into independent controller instances");
        check(first.ship==shipA && first.owns(shipA) && !first.owns(shipB),"source ownership never stolen");
        check(second.ship==shipB && second.owns(shipB),"destination bound to its own ID");
        check(first.anchorDeployed && !second.anchorDeployed && second.numBuoys==0 && second.numSails==0,"anchors and counts are never inherited by another ship");
        check(SailsShipControl.getOrCreate(shipA,null)==first,"repeated lookup preserves original state");
        PhysShip foreign=(PhysShip)Proxy.newProxyInstance(PhysShip.class.getClassLoader(),new Class[]{PhysShip.class},(p,m,x) -> {
            if(m.getName().equals("getId"))return 22L;
            throw new AssertionError("Foreign physics body was touched: "+m.getName());
        });
        first.physTick(foreign,null);check(first.anchorDeployed,"foreign body receives no anchor or buoyancy mutation");
        second.anchorDeployed=true;second.anchorLocked=true;
        AnchorMechanics.forgetForeignAnchor(second);
        check(!second.anchorDeployed && !second.anchorLocked && b.staticChanges==0,"stale saved anchor cleared without unfreezing destination");
        Set<HullConnectivity.Cell> hulls=new HashSet<>();
        for(int x=0;x<4;x++)for(int y=0;y<3;y++)for(int z=0;z<4;z++) {
            hulls.add(new HullConnectivity.Cell(x,y,z));
            hulls.add(new HullConnectivity.Cell(x+4,y,z+4));
        }
        var parts=HullConnectivity.components(hulls);
        check(parts.size()==2 && parts.get(0).size()==48 && parts.get(1).size()==48,"edge-touching hulls remain separate");
        hulls.add(new HullConnectivity.Cell(4,0,3));
        check(HullConnectivity.components(hulls).size()==1,"a real face-connected bridge makes one structure");
        check(HullConnectivity.components(Set.of()).isEmpty(),"empty topology is valid");
        System.out.println("SHIP_ISOLATION_PASS assertions="+checks);
    }
}
