package ua.empirecraft.forging;

import java.util.*;

/** Reverse fixed point; alternatives and cycles cannot hide a higher reachable result. */
public final class UpgradeGraph<T> {
    private final Map<T, Integer> ratings = new HashMap<>();
    private final Map<T, Set<T>> predecessors = new HashMap<>();
    public void add(T item, int rating) { ratings.merge(item, rating, Math::max); }
    public void upgrade(T ingredient, T output) { predecessors.computeIfAbsent(output, k -> new HashSet<>()).add(ingredient); }
    public Map<T, Integer> resolve() {
        ArrayDeque<T> pending = new ArrayDeque<>(ratings.keySet());
        while (!pending.isEmpty()) {
            T output = pending.removeFirst();
            int maximum = ratings.getOrDefault(output, 0);
            for (T input : predecessors.getOrDefault(output, Set.of())) {
                if (maximum <= ratings.getOrDefault(input, 0)) continue;
                ratings.put(input, maximum);
                pending.addLast(input);
            }
        }
        return Map.copyOf(ratings);
    }
}
