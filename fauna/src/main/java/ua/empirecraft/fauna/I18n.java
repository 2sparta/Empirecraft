package ua.empirecraft.fauna;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Render on the server, so installing a client mod or language pack is not required. */
public final class I18n {
  private static final Map<String, Map<String, String>> LANG = new HashMap<>();

  static {
    for (String locale : List.of("uk_ua", "ru_ru"))
      try (var in =
          I18n.class.getResourceAsStream("/assets/empirecraft_fauna/lang/" + locale + ".json")) {
        if (in == null) throw new IOException("Missing fauna locale " + locale);
        var json =
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        var entries = new HashMap<String, String>();
        json.entrySet()
            .forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
        LANG.put(locale, Map.copyOf(entries));
      } catch (IOException error) {
        throw new ExceptionInInitializerError(error);
      }
    if (!LANG.get("uk_ua").keySet().equals(LANG.get("ru_ru").keySet()))
      throw new ExceptionInInitializerError("Fauna locales have different keys");
  }

  private I18n() {}

  public static String locale(ServerPlayer player) {
    return player.getLanguage().toLowerCase(Locale.ROOT).startsWith("ru")
        ? "ru_ru"
        : player.getLanguage().toLowerCase(Locale.ROOT).startsWith("uk")
            ? "uk_ua"
            : FaunaMod.session == null ? "uk_ua" : FaunaMod.session.settings.defaultLocale;
  }

  public static String text(String locale, String key, Object... args) {
    return String.format(
        Locale.ROOT, LANG.getOrDefault(locale, LANG.get("uk_ua")).getOrDefault(key, key), args);
  }

  public static String entity(String locale, String id) {
    return LANG.getOrDefault(locale, LANG.get("uk_ua"))
        .getOrDefault("entity." + id.replace(':', '.'), id);
  }

  public static String number(String locale, long value) {
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag(locale.replace('_', '-')))
        .format(value);
  }

  public static String percent(String locale, long count, long limit) {
    var format = NumberFormat.getPercentInstance(Locale.forLanguageTag(locale.replace('_', '-')));
    format.setMaximumFractionDigits(1);
    return format.format(limit <= 0 ? 0 : count / (double) limit);
  }
}
