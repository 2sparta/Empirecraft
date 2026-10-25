package ua.empirecraft.chat;

import java.util.Objects;
import java.util.UUID;

/** Server-side recipients; no operator or client-side visibility bypass. */
public final class ChatRules {
    private ChatRules() {}
    public record Member(UUID player, UUID town, UUID kingdom, UUID world, double x, double y, double z) {}
    public static boolean receives(Channel channel, Member sender, Member recipient) {
        return switch (channel) {
            case GLOBAL -> true;
            case SYSTEM -> false;
            case TOWN -> sender.town() != null && sender.town().equals(recipient.town());
            case KINGDOM -> sender.kingdom() != null && sender.kingdom().equals(recipient.kingdom());
            case LOCAL -> Objects.equals(sender.world(), recipient.world()) && distanceSquared(sender, recipient) <= 2500.0;
        };
    }
    private static double distanceSquared(Member a, Member b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
    public static String body(String value) {
        String text = value.strip();
        if (text.isEmpty() || text.length() > 256 || text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7 || (c >= 0xe000 && c <= 0xf8ff)))
            throw new IllegalArgumentException("Повідомлення має містити 1–256 символів без кодів форматування та значків титулів.");
        return text;
    }
}
