package ua.empirecraft.townymap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Per-session diff, bounded per tick. Failed sends are retried and old overlay pages are removed. */
final class OverlaySync<T> {
    private final Map<String, T> sent = new TreeMap<>();
    private final Set<String> dirty = new HashSet<>();

    void invalidate() { dirty.addAll(sent.keySet()); }

    void update(Map<String, T> desired, int budget, Consumer<String> remove, BiConsumer<String, T> show) {
        Iterator<String> old = sent.keySet().iterator();
        while (old.hasNext() && budget > 0) {
            String id = old.next();
            if (!desired.containsKey(id)) {
                remove.accept(id);
                old.remove();
                dirty.remove(id);
                budget--;
            }
        }
        for (var entry : desired.entrySet()) {
            if (budget <= 0) break;
            if (dirty.contains(entry.getKey()) || !Objects.equals(entry.getValue(), sent.get(entry.getKey()))) {
                show.accept(entry.getKey(), entry.getValue());
                sent.put(entry.getKey(), entry.getValue());
                dirty.remove(entry.getKey());
                budget--;
            }
        }
    }
}
