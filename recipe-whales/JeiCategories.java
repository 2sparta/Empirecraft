package ua.empirecraft.nativepatch;
import java.util.*;

public final class JeiCategories {
    public static final Set<String> ALLOWED = Set.of("milling", "pressing", "sawing", "block_cutting");
    public static boolean add(List<Object> categories, Object category, String id) {
        return ALLOWED.contains(id) && categories.add(category);
    }
}
