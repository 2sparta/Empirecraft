package com.epicdeathscreen.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact registry identities, never guesses from an attacker's translated name. */
public final class DeathProfiles {
    public record Match(String cue, String damage, String entity) {}
    private static final Map<String, String> RULES = load();
    private DeathProfiles() {}

    private static Map<String, String> load() {
        try (var stream = DeathProfiles.class.getResourceAsStream("/assets/epicdeathscreen/empirecraft/profiles.tsv")) {
            if (stream == null) throw new IOException("Missing death profiles");
            Map<String, String> result = new LinkedHashMap<>();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] parts = line.split("\t", -1);
                    if (parts.length != 3 || !Set.of("damage", "entity", "block").contains(parts[0])
                            || !parts[1].matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !parts[2].matches("[a-z_]+"))
                        throw new IOException("Invalid death profile");
                    if (result.put(parts[0] + ":" + parts[1], parts[2]) != null) throw new IOException("Duplicate death profile");
                }
            }
            return Map.copyOf(result);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    private static Match find(String kind, String id) {
        String cue = RULES.get(kind + ":" + Objects.toString(id, ""));
        if (cue == null) return null;
        return new Match("empirecraft/" + cue,
                kind.equals("damage") ? id : kind.equals("block") ? "empirecraft:falling_" + id.replace(':', '_') : "",
                kind.equals("entity") ? id : "");
    }

    public static Match match(String damage, String directEntity, String attacker, String fallingBlock) {
        Match found = find("damage", damage);
        if (found == null) found = find("block", fallingBlock);
        if (found == null) found = find("entity", directEntity);
        if (found == null) found = find("entity", attacker);
        return found;
    }
}
