package ua.empirecraft.fauna;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Percentages belong to independent 30-second group attempts, before terrain/claim/cap checks. */
public final class SpawnCatalog {
  public record Entry(
      String name,
      List<String> ids,
      int[] chances,
      int min,
      int max,
      String time,
      boolean small,
      boolean warmSummer) {
    public int chance(int month, boolean hot) {
      return chances[hot && warmSummer ? 1 : season(month)];
    }
  }

  private record Data(List<Entry> entries) {}

  public static final List<Entry> ENTRIES = load();

  private SpawnCatalog() {}

  public static int season(int month) {
    return Math.floorMod(month - 2, 12) / 3;
  }

  public static boolean rolled(int percent, int roll) {
    return roll >= 0 && roll < 100 && roll < percent;
  }

  private static List<Entry> load() {
    try (var in = SpawnCatalog.class.getResourceAsStream("/nearby-fauna.json")) {
      if (in == null) throw new IllegalStateException("Missing nearby-fauna.json");
      var list =
          new Gson()
              .fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class)
              .entries();
      Set<String> ids = new HashSet<>();
      for (var row : list) {
        if (row.ids().isEmpty()
            || row.chances().length != 4
            || row.min() < 1
            || row.max() < row.min()
            || row.max() > 8
            || !Set.of("day", "night", "dusk", "any").contains(row.time()))
          throw new IllegalArgumentException(row.name());
        for (int p : row.chances())
          if (p < 0 || p > 100) throw new IllegalArgumentException(row.name());
        for (String id : row.ids())
          if (!id.matches("[a-z0-9_]+:[a-z0-9_/]+") || !ids.add(id))
            throw new IllegalArgumentException(id);
      }
      return List.copyOf(list);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid nearby fauna catalog", e);
    }
  }
}
