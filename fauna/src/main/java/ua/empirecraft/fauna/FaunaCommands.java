package ua.empirecraft.fauna;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class FaunaCommands {
  private FaunaCommands() {}

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    var admin = Commands.literal("admin").requires(FaunaCommands::allowed);
    var stats = Commands.literal("stats").executes(c -> stats(c.getSource(), "all"));
    for (String kind : List.of("domestic", "wild", "pets", "wilderness"))
      stats.then(Commands.literal(kind).executes(c -> stats(c.getSource(), kind)));
    admin
        .then(stats)
        .then(Commands.literal("cities").executes(c -> cities(c.getSource(), null)))
        .then(
            Commands.literal("city")
                .then(
                    Commands.argument("city", StringArgumentType.greedyString())
                        .suggests(
                            (c, b) -> {
                              var s = FaunaMod.session;
                              if (s != null)
                                s.population
                                    .land()
                                    .cities()
                                    .values()
                                    .forEach(city -> b.suggest(city.name()));
                              return b.buildFuture();
                            })
                        .executes(
                            c -> cities(c.getSource(), StringArgumentType.getString(c, "city")))))
        .then(Commands.literal("monitor").executes(c -> monitor(c.getSource())))
        .then(
            Commands.literal("reload")
                .executes(
                    c -> {
                      var s = FaunaMod.session;
                      String locale = locale(c.getSource());
                      try {
                        if (s == null) throw new IllegalStateException();
                        s.reload();
                        say(c.getSource(), I18n.text(locale, "reloaded"));
                        return 1;
                      } catch (Exception error) {
                        FaunaMod.LOG.warn("Fauna configuration reload rejected", error);
                        say(c.getSource(), I18n.text(locale, "reload_failed"));
                        return 0;
                      }
                    }));
    dispatcher.register(Commands.literal("fauna").then(admin));
  }

  static boolean allowed(CommandSourceStack source) {
    if (source.hasPermission(2)) return true;
    if (!(source.getEntity() instanceof ServerPlayer player)) return false;
    try {
      var bukkit = Class.forName("org.bukkit.Bukkit");
      var target = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUUID());
      return target != null
          && Boolean.TRUE.equals(
              target
                  .getClass()
                  .getMethod("hasPermission", String.class)
                  .invoke(target, "empirecraft.fauna.admin"));
    } catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  static String locale(CommandSourceStack source) {
    return source.getEntity() instanceof ServerPlayer player
        ? I18n.locale(player)
        : FaunaMod.session == null ? "uk_ua" : FaunaMod.session.settings.defaultLocale;
  }

  static void say(CommandSourceStack source, String line) {
    source.sendSuccess(() -> Component.literal(line), false);
  }

  static Session ready(CommandSourceStack source) {
    var s = FaunaMod.session;
    if (s == null) {
      say(source, I18n.text(locale(source), "unavailable"));
      return null;
    }
    s.refreshLand(true);
    s.refreshLoaded();
    if (!s.population.censusReady || !s.population.land().ready())
      say(
          source,
          I18n.text(
              locale(source), "census", s.population.chunksScanned, s.population.scanFailures));
    return s;
  }

  static void pair(CommandSourceStack source, String title, long count, long maximum) {
    String l = locale(source);
    say(source, I18n.text(l, title));
    say(
        source,
        I18n.text(
            l,
            "total",
            I18n.number(l, count),
            maximum < 0 ? I18n.text(l, "unlimited") : I18n.number(l, maximum)));
    if (maximum >= 0) {
      say(source, I18n.text(l, "free", I18n.number(l, Math.max(0, maximum - count))));
      say(source, I18n.text(l, "occupancy", I18n.percent(l, count, maximum)));
    }
  }

  static int stats(CommandSourceStack source, String mode) {
    var s = ready(source);
    if (s == null) return 0;
    String l = locale(source);
    say(source, I18n.text(l, "header"));
    long cityCount = s.population.count("DOMESTIC") - s.population.count("DOMESTIC/city/");
    long capacity =
        s.population.land().cities().values().stream().mapToLong(s.population::capacity).sum();
    if (Set.of("all", "domestic").contains(mode)) {
      pair(source, "domestic", cityCount, capacity);
      say(source, I18n.text(l, "domestic_all", I18n.number(l, s.population.count("DOMESTIC"))));
      pair(
          source,
          "wilderness",
          s.population.count("budget/DOMESTIC/city/"),
          s.settings.wildernessDomesticGlobalLimit);
    }
    if (mode.equals("wilderness"))
      pair(
          source,
          "wilderness",
          s.population.count("budget/DOMESTIC/city/"),
          s.settings.wildernessDomesticGlobalLimit);
    if (Set.of("all", "wild").contains(mode)) {
      pair(source, "wild", s.population.count("budget/WILD"), s.settings.wildGlobalLimit);
      say(source, I18n.text(l, "wild_all", I18n.number(l, s.population.count("WILD"))));
      say(source, I18n.text(l, "nearby", s.nearby.attempts, s.nearby.spawned, s.nearby.expired));
    }
    if (Set.of("all", "pets").contains(mode))
      say(source, I18n.text(l, "pets", I18n.number(l, s.population.count("PET"))));
    say(
        source,
        I18n.text(
            l,
            "loaded",
            I18n.number(l, s.population.count("loaded")),
            I18n.number(l, s.population.count("all") - s.population.count("loaded"))));
    return 1;
  }

  static int cities(CommandSourceStack source, String match) {
    var s = ready(source);
    if (s == null) return 0;
    String l = locale(source);
    var cities =
        s.population.land().cities().values().stream()
            .filter(
                c ->
                    match == null
                        || c.id().equalsIgnoreCase(match)
                        || c.name().equalsIgnoreCase(match.replace('_', ' ')))
            .sorted(Comparator.comparing(Population.City::name))
            .toList();
    if (match != null && cities.isEmpty()) {
      say(source, I18n.text(l, "city_missing", match));
      return 0;
    }
    say(source, I18n.text(l, "cities"));
    long total = 0, capacity = 0;
    for (var city : cities) {
      int count = s.population.count("DOMESTIC/city/" + city.id()),
          limit = s.population.capacity(city);
      total += count;
      capacity += limit;
      say(
          source,
          city.name()
              + "  "
              + I18n.number(l, count)
              + "/"
              + I18n.number(l, limit)
              + " ("
              + I18n.percent(l, count, limit)
              + ")");
      if (match != null) {
        say(
            source,
            I18n.text(
                l,
                "city_level",
                city.level(),
                city.capital() ? I18n.text(l, "capital") : I18n.text(l, "not_capital")));
        var species = new TreeMap<String, Integer>();
        for (var animal : s.population.all())
          if (s.population.kind(animal) == Population.Kind.DOMESTIC
              && s.population.land().at(animal).equals(city.id()))
            species.merge(animal.type(), 1, Integer::sum);
        species.forEach((id, n) -> say(source, I18n.entity(l, id) + ": " + I18n.number(l, n)));
        say(source, I18n.text(l, "pets", s.population.count("PET/city/" + city.id())));
      }
    }
    say(source, I18n.text(l, "total", I18n.number(l, total), I18n.number(l, capacity)));
    return 1;
  }

  static int monitor(CommandSourceStack source) {
    var s = ready(source);
    if (s == null) return 0;
    String l = locale(source);
    say(
        source,
        I18n.text(
            l,
            "monitor",
            s.population.count("all"),
            s.population.count("loaded"),
            String.format(Locale.ROOT, "%.2f", source.getServer().getAverageTickTime()),
            String.format(Locale.ROOT, "%.3f", s.trackingNanos / 1000000d),
            s.blocked,
            s.population.chunksScanned));
    return 1;
  }
}
