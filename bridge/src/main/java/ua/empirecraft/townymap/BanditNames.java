package ua.empirecraft.townymap;

import java.util.List;
import java.util.Locale;

/** Nation names only: the arbitrary name may use any alphabet or separators. */
final class BanditNames {
    private static final List<String> MARKERS = List.of("розбійники", "rozbiynyky", "разбойники");

    static boolean matches(String name) {
        if (name == null) return false;
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(word -> normalized.startsWith(word) || normalized.endsWith(word));
    }
}
