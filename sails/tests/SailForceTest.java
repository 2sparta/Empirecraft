import com.quintonc.vs_sails.ship.SailingMath;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Direction/coordinate regressions, independent of Minecraft and the VS engine. */
public final class SailForceTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static Vector3d force(Quaterniond rotation, double x, double z, double wind, double strength, int square, int fore) {
        return SailingMath.sailForce(rotation, x, z, wind, strength, square, fore, 25000, Math.PI / 4, false);
    }
    private static void near(Vector3d a, Vector3d b, String message) {
        check(a.distance(b) <= 1e-7 * Math.max(1, b.length()), message + ": " + a + " / " + b);
    }
    public static void main(String[] args) {
        Quaterniond identity = new Quaterniond();
        check(force(identity, 0, -1, 270, 1, 8, 0).z < 0, "north wind must push north, not the old fixed south thrust");
        check(force(identity, 1, 0, 0, 1, 8, 0).x > 0, "east wind must push east, not the old fixed west thrust");
        check(force(identity, 0, 1, 90, -1, 8, 0).z < 0, "negative strength reverses south wind");
        near(force(identity, 1, 0, 90, 1, 8, 3), new Vector3d(), "beam wind has no arbitrary axial motor");
        near(force(identity, 0, 1, 0, 1, 8, 3), new Vector3d(), "beam wind on the other hull axis");
        for (double[] axis : new double[][]{{1,0}, {-1,0}, {0,1}, {0,-1}}) {
            for (int yaw=0; yaw<360; yaw+=15) {
                Quaterniond rotation = new Quaterniond().rotateY(Math.toRadians(yaw));
                for (int wind=-360; wind<360; wind+=15) {
                    for (int[] sails : new int[][]{{8,0}, {0,8}, {4,4}}) {
                        Vector3d positive = force(rotation, axis[0], axis[1], wind, .8, sails[0], sails[1]);
                        double radians = Math.toRadians(wind);
                        Vector3d air = new Vector3d(Math.cos(radians), 0, Math.sin(radians));
                        check(positive.isFinite() && positive.y == 0, "finite horizontal wind force");
                        check(positive.dot(air) >= -1e-7, "sails cannot accelerate opposite the indicated wind");
                        check(positive.length() <= 8 * 25000 * .8 * .8 / 2 + 1e-7, "bounded sail force");
                        near(force(rotation, axis[0], axis[1], wind, -.8, sails[0], sails[1]), positive.negate(new Vector3d()), "signed strength reverses thrust");
                        near(force(rotation, -axis[0], -axis[1], wind, .8, sails[0], sails[1]), positive, "reversing the stored axis cannot reverse world thrust");
                        near(force(rotation, axis[0], axis[1], wind+360, .8, sails[0], sails[1]), positive, "wind wraps through zero");
                        near(force(rotation, axis[0], axis[1], wind, .4, sails[0], sails[1]), positive.mul(.25, new Vector3d()), "strength keeps square-law scaling");
                    }
                }
            }
        }
        for (int yaw=0; yaw<360; yaw+=15) {
            Quaterniond rotation = new Quaterniond().rotateY(Math.toRadians(yaw));
            Vector3d rotated = rotation.transform(force(identity, 1, 0, 30, 1, 4, 2), new Vector3d());
            near(force(rotation, 1, 0, 30-yaw, 1, 4, 2), rotated, "rotating ship and wind together preserves force");
        }
        for (int tilt=-80; tilt<=80; tilt+=10) {
            Vector3d force = force(new Quaterniond().rotateXYZ(Math.toRadians(tilt), .3, -.2), 0, 1, 90, 1, 8, 0);
            check(force.isFinite() && force.y == 0, "heel and pitch do not turn wind into vertical lift");
        }
        near(force(identity, 0, 1, 90, 0, 8, 4), new Vector3d(), "calm wind");
        near(force(identity, 0, 1, 90, 1, 0, 0), new Vector3d(), "no deployed sails");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            near(force(identity, 1, 0, bad, 1, 8, 0), new Vector3d(), "invalid wind direction");
            near(force(identity, 1, 0, 0, bad, 8, 0), new Vector3d(), "invalid wind strength");
        }
        near(force(new Quaterniond().set(Double.NaN, 0, 0, 1), 1, 0, 0, 1, 8, 0), new Vector3d(), "invalid transform");
        System.out.println("PASS: " + assertions + " wind direction assertions");
    }
}
