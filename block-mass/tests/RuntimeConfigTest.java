import java.nio.file.Files;
import java.nio.file.Path;
import ua.empirecraft.shipphysics.PhysicsConfig;
import ua.empirecraft.shipphysics.BlockProperties;

/** Exercises the installed binary's parser and validation, without a Forge world. */
public class RuntimeConfigTest {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of(args[0]));
        PhysicsConfig.apply(json);
        assert BlockProperties.clears > 0;
        assert PhysicsConfig.get().blocks.size() == 48;
        assert PhysicsConfig.get().materials.get("timber_drive").density() == 650;
        assert PhysicsConfig.get().materials.get("iron").density() == 7870;
        assert PhysicsConfig.get().minimumImpactSpeed == 2;
        assert PhysicsConfig.get().massScanBudget == 8192;
        String synced = PhysicsConfig.json();
        assert synced.length() < 30000 : synced.length();
        assert synced.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 30000;
        PhysicsConfig.apply(synced);
        assert PhysicsConfig.get().blocks.size() == 48;
        rejects(" ".repeat(30001));
        rejects(json.replace("#empirecraft_mass:wood", "#INVALID:wood"));
        rejects(json.replace("\"density\": 650", "\"density\": -1"));
        PhysicsConfig.apply(synced);
        System.out.println("PASS actual Ship Physics 1.2.0 parser, validation and sync round-trip (" + synced.length() + " characters)");
    }

    static void rejects(String json) {
        try {
            PhysicsConfig.apply(json);
            throw new AssertionError("Invalid config accepted");
        } catch (IllegalArgumentException expected) {
            // Expected: the real parser checks its length/range/rule constraints.
        }
    }
}
