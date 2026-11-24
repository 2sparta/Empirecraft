package ua.empirecraft.forgemigration;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class LoginMigration {
    private static final Pattern PROFILE = Pattern.compile("[a-z0-9_]{1,16}");
    private static volatile Set<String> reserved;

    private LoginMigration() {}

    static void load(Path world) throws IOException {
        try (Reader reader = Files.newBufferedReader(world.resolve("serverlogin/reserved-names.json"))) {
            Set<String> names = Set.copyOf(Arrays.asList(new Gson().fromJson(reader, String[].class)));
            if (names.stream().anyMatch(name -> !PROFILE.matcher(name).matches())) {
                throw new IOException("Invalid name in the rlogin reservation file");
            }
            reserved = names;
        }
    }

    static void stop() {
        reserved = null;
    }

    public static boolean registrationBlocked(String name) {
        Set<String> names = reserved;
        return names == null || names.contains(name.toLowerCase(Locale.ROOT));
    }
}
