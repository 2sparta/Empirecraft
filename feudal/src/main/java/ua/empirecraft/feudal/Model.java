package ua.empirecraft.feudal;

import java.math.BigDecimal;
import java.util.*;

public final class Model {
    private Model() {}
    public enum Kind {
        BARONY(1,"Баронство","Барон"), COUNTY(2,"Графство","Граф"), DUCHY(3,"Герцогство","Герцог"), KINGDOM(4,"Королівство","Король");
        public final int level; public final String label, title;
        Kind(int level,String label,String title) { this.level=level; this.label=label; this.title=title; }
        public static Kind parse(String value) {
            return switch(value.toLowerCase(Locale.ROOT)) {
                case "барон", "баронство", "baron", "barony" -> BARONY;
                case "граф", "графство", "count", "county" -> COUNTY;
                case "герцог", "герцогство", "duke", "duchy" -> DUCHY;
                case "король", "королівство", "king", "kingdom" -> KINGDOM;
                default -> throw new IllegalArgumentException("Невідомий титул: " + value);
            };
        }
    }
    public record Estate(String id, Kind kind, String name, String nation, String lord, boolean active) {
        public static Estate from(Map<String,Object> r) {
            return new Estate(Database.string(r,"id"),Kind.valueOf(Database.string(r,"kind")),Database.string(r,"name"),Database.string(r,"nation"),Database.string(r,"lord"),Database.bool(r,"active"));
        }
    }
    public record Snapshot(List<Estate> estates, Set<String> citizenships) {
        public Snapshot { estates=List.copyOf(estates); citizenships=Set.copyOf(citizenships); }
        public boolean citizen(String person,String nation) { return citizenships.contains(person+"@"+nation); }
    }
    public static long money(String text) {
        try {
            // Bound syntax before BigDecimal: exponent notation can allocate enormous integers.
            if (text==null || !text.matches("[0-9]{1,13}([.,][0-9]{1,2})?")) throw new ArithmeticException();
            long amount = new BigDecimal(text.replace(',','.')).movePointRight(2).longValueExact();
            if (amount<0 || amount>900_000_000_000_000L) throw new ArithmeticException();
            return amount;
        } catch (RuntimeException e) { throw new IllegalArgumentException("Сума має бути невід’ємним числом із максимум двома знаками після коми."); }
    }
    public static String format(long amount) { return BigDecimal.valueOf(amount,2).toPlainString(); }
    public static String validName(String name) {
        if (name==null || !name.matches("[\\p{L}\\p{N}_’' -]{2,48}")) throw new IllegalArgumentException("Назва: 2–48 літер, цифр, пробілів, дефісів або підкреслень.");
        return name;
    }
    public static void require(boolean condition,String message) { if(!condition) throw new IllegalArgumentException(message); }
}
