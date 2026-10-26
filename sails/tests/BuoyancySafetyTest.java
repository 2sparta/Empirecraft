import com.quintonc.vs_sails.ship.BuoyancySafety;
public final class BuoyancySafetyTest {
    public static void main(String[] args) {
        if(BuoyancySafety.safeForFluidDrag(1.6,16))throw new AssertionError("legacy inertia not ready");
        if(!BuoyancySafety.safeForFluidDrag(1600,16))throw new AssertionError("migrated inertia ready");
        if(BuoyancySafety.safeForFluidDrag(Double.NaN,16))throw new AssertionError("invalid inertia rejected");
        System.out.println("BUOYANCY_INERTIA_PASS");
    }
}
