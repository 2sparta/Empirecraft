package com.quintonc.vs_sails.ship;

import java.lang.reflect.Proxy;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

/** Server-side hold ownership and reload semantics, independent of native timing. */
public final class SailHoldTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static final class Body {
        boolean frozen;SailsShipControl control;
        LoadedServerShip proxy() {
            return (LoadedServerShip)Proxy.newProxyInstance(LoadedServerShip.class.getClassLoader(),new Class[]{LoadedServerShip.class},(p,m,a)->switch(m.getName()) {
                case "getId" -> 17L;
                case "getShipAABB" -> null;
                case "getAttachment" -> control;
                case "setAttachment" -> {control=(SailsShipControl)a[a.length-1];yield null;}
                case "isStatic" -> frozen;
                case "setStatic" -> {frozen=(Boolean)a[0];yield null;}
                default -> throw new AssertionError(m.getName());
            });
        }
    }
    public static void main(String[] args)throws Exception {
        Body body=new Body();var control=SailsShipControl.getOrCreate(body.proxy(),null);
        control.publishHullSafety(16,0);check(control.sailSafetyHeld && body.frozen,"cloth hold acquired");
        var ready=SailsShipControl.class.getDeclaredField("hullReady");ready.setAccessible(true);ready.setBoolean(control,false);
        var update=SailsShipControl.class.getDeclaredMethod("updateSafetyHold");update.setAccessible(true);update.invoke(control);
        check(control.sailSafetyHeld && body.frozen,"dirty recount never releases an owned hold");
        control.publishHullSafety(16,1);check(!control.sailSafetyHeld && !body.frozen,"hull releases only the owned hold");
        body.frozen=true;control.publishHullSafety(16,0);control.publishHullSafety(16,1);
        check(body.frozen && !control.sailSafetyHeld,"external freeze preserved");
        Body saved=new Body();saved.frozen=true;var restored=SailsShipControl.getOrCreate(saved.proxy(),null);
        restored.sailSafetyHeld=true;restored.sailSafetyWasStatic=false;update.invoke(restored);
        check(restored.sailSafetyHeld && saved.frozen,"saved hold remains frozen until first complete recount");
        restored.publishHullSafety(16,0);check(restored.sailSafetyHeld && saved.frozen,"saved cloth hold confirmed");
        restored.publishHullSafety(16,1);check(!saved.frozen,"saved owned hold releases after real hull is confirmed");
        System.out.println("SAIL_HOLD_PASS assertions="+checks);
    }
}
