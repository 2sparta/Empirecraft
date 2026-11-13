package ua.empirecraft.fauna;

/** Uses actual travel rather than camera direction; looking backwards does not move wildlife behind. */
public final class TravelHeading {
  public record Heading(double x, double z) {
    public boolean ahead(double dx, double dz) { return x * dx + z * dz > 0; }
    public double angle(double roll, double coneDegrees) {
      return Math.atan2(z, x) + (roll - .5) * Math.toRadians(coneDegrees);
    }
  }
  private double lastX, lastZ, vx, vz;
  private long lastTick = Long.MIN_VALUE, lastMoving = Long.MIN_VALUE;

  public void observe(double x, double z, long tick) {
    if (lastTick != Long.MIN_VALUE && tick > lastTick) {
      double dx = x - lastX, dz = z - lastZ;
      if (dx * dx + dz * dz > 64 * 64) {
        vx = vz = 0; lastMoving = Long.MIN_VALUE;
      } else if (dx * dx + dz * dz > .0025) {
        double elapsed = tick - lastTick;
        double nx = dx / elapsed, nz = dz / elapsed;
        // A deliberate turn should take effect immediately, without a backwards transition.
        if (vx * nx + vz * nz <= 0) { vx = nx; vz = nz; }
        else { vx = .4 * vx + .6 * nx; vz = .4 * vz + .6 * nz; }
        lastMoving = tick;
      }
    }
    lastX = x; lastZ = z; lastTick = tick;
  }

  public Heading heading(long tick, double lookX, double lookZ) {
    if (lastMoving != Long.MIN_VALUE && tick - lastMoving <= 30 && vx * vx + vz * vz > 1e-6)
      return normalized(vx, vz);
    return normalized(lookX, lookZ);
  }

  static Heading normalized(double x, double z) {
    double length = Math.hypot(x, z);
    return !Double.isFinite(length) || length < 1e-6 ? new Heading(0, 1) : new Heading(x / length, z / length);
  }
}
