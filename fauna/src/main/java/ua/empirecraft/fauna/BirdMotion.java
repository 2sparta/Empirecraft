package ua.empirecraft.fauna;

/** Geometry shared by real bird steering and deterministic policy checks. */
public final class BirdMotion {
  public record Point(double x, double y, double z) {}
  private static final java.util.Set<String> LOUD = java.util.Set.of("minecraft:entity.generic.explode",
      "musketmod:musket_fire", "musketmod:blunderbuss_fire", "musketmod:blunderbuss_fire_flame",
      "musketmod:pistol_fire", "musketmod:dispenser_fire", "medieval_boomsticks:arquebus_shoot",
      "medieval_boomsticks:handgonne_shoot");

  public static long scatterSlot(long individual, int index, int members) {
    return members < 2 || index < 0 ? individual : Math.round(1008.0 * index / (members - 1));
  }

  public static Point escape(double x, double y, double z, double sx, double sz, long individual, boolean flying) {
    double dx = x - sx, dz = z - sz;
    double angle = Math.atan2(dz, dx);
    double variation = Math.floorMod(individual, 1009) / 1008.0;
    if (dx * dx + dz * dz < 1e-6) angle = variation * Math.PI * 2;
    else angle += (variation - .5) * 1.9;
    return new Point(x + Math.cos(angle) * 14, y + (flying ? 3 + 4 * variation : 0), z + Math.sin(angle) * 14);
  }

  public static Point formation(double x, double y, double z, int index) {
    double angle = index * 2.399963229728653;
    double radius = index == 0 ? 0 : 2 + Math.sqrt(index) * 1.3;
    return new Point(x + Math.cos(angle) * radius, y + (index % 3) * .7, z + Math.sin(angle) * radius);
  }

  public static double segmentDistanceSquared(Point p, Point a, Point b) {
    double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
    double length = dx * dx + dy * dy + dz * dz;
    double t = length < 1e-12 ? 0 : Math.max(0, Math.min(1,
        ((p.x - a.x) * dx + (p.y - a.y) * dy + (p.z - a.z) * dz) / length));
    double px = p.x - a.x - t * dx, py = p.y - a.y - t * dy, pz = p.z - a.z - t * dz;
    return px * px + py * py + pz * pz;
  }

  public static boolean shotSound(String id) {
    return LOUD.contains(id) || id.startsWith("createbigcannons:") && id.contains("fire");
  }

  public static boolean bullet(String id) {
    return id.startsWith("musketmod:") && id.contains("bullet")
        || id.startsWith("medieval_boomsticks:") && id.contains("round_ball")
        || id.startsWith("createbigcannons:")
            && (id.contains("shot") || id.contains("shell") || id.contains("bullet"));
  }
  private BirdMotion() {}
}
