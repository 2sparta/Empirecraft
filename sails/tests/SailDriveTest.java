import com.quintonc.vs_sails.ship.*;
import org.joml.Vector3d;

/** Integrates the production controller against resistance, rather than just its unloaded target. */
public final class SailDriveTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static double simulate(int hz, double mass, double target, double drag, boolean oldGate) {
        double dt = 1.0 / hz;
        Vector3d bow = new Vector3d(-1, 0, 0), velocity = new Vector3d();
        SailDrive controller = new SailDrive();
        for (int tick = 0; tick < hz * 600; tick++) {
            Vector3d acceleration = controller.acceleration(bow, velocity, target, mass, true, false, false, false, dt);
            double speed = velocity.dot(bow);
            // Reproduce Sails 9's liquidOverlap == 0 gate by discarding only the
            // assistance. The rest is the same actual production controller.
            if (oldGate) acceleration.sub(new Vector3d(bow).mul(controller.assistance()));
            double force = acceleration.dot(bow) - drag * speed * Math.abs(speed);
            velocity.fma(force * dt, bow);
            check(velocity.isFinite() && velocity.y == 0, "finite horizontal propulsion");
            check(velocity.dot(bow) < target + .2, "no resistance-induced overspeed");
        }
        return velocity.dot(bow);
    }
    public static void main(String[] args) {
        double old = simulate(60, 50093.43, 8.42, .185, true);
        check(old > 1.6 && old < 1.7, "old overlap gate reproduces screenshot plateau: " + old);
        for (int hz : new int[]{20, 40, 60, 120}) {
            for (double mass : new double[]{20000, 50093.43, 1000000}) {
                for (double drag : new double[]{0, .1, .185, .5}) {
                    double speed = simulate(hz, mass, 8.42, drag, false);
                    check(speed > 8.38 && speed < 8.46, "loaded controller reaches target: " + hz + "/" + mass + "/" + drag + " -> " + speed);
                }
            }
            SailDrive controller = new SailDrive();
            Vector3d bow = new Vector3d(1, 0, 0), velocity = new Vector3d();
            double dt = 1.0 / hz;
            for (int tick = 0; tick < hz * 240; tick++) {
                double target = tick < hz * 70 ? 8.42 : tick < hz * 140 ? 2 : 0;
                Vector3d a = controller.acceleration(bow, velocity, target, 50093.43, true,
                        false, false, tick >= hz * 70, dt);
                velocity.x += (a.x - .185 * velocity.x * Math.abs(velocity.x)) * dt;
                if (tick == hz * 70 - 1) check(velocity.x > 8, "normal rig accelerates under resistance");
                if (tick == hz * 140 - 1) check(Math.abs(velocity.x - 2) < .03, "furling retains slow windage without runaway");
            }
            check(Math.abs(velocity.x) < .01, "no cloth/calm settles to rest; compensation is not a motor");
            for (int tick = 0; tick < hz * 25; tick++) {
                Vector3d a = controller.acceleration(bow, velocity, -1.5, 50093.43, true, false, true, false, dt);
                velocity.x += (a.x - .185 * velocity.x * Math.abs(velocity.x)) * dt;
            }
            check(Math.abs(velocity.x + 1.5) < .03, "reverse remains capped near 1.5");
            Vector3d brake = controller.acceleration(bow, velocity, 8.42, 50093.43, true, true, false, false, dt);
            check(brake.dot(velocity) < 0 && controller.assistance() == 0, "anchor brakes and clears learned assistance");
            controller.acceleration(bow, velocity, Double.NaN, 50093.43, true, false, false, false, dt);
            check(controller.assistance() == 0 && controller.drive() == 0, "invalid input resets bounded state");
        }
        System.out.println("SAIL_DRIVE_PASS assertions=" + checks + " oldPlateau=" + old);
    }
}
