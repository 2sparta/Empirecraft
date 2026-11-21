package ua.empirecraft.forgemigration;

import com.google.gson.JsonParser;
import guardtest.OfflineMixinService;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import org.objectweb.asm.ClassWriter;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

public final class MigrationTest {
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        login(Path.of(args[0]));
    }

    private static void login(Path world) throws Exception {
        Path directory = world.resolve("serverlogin");
        Files.createDirectories(directory);
        Path file = directory.resolve("users.json");
        Files.writeString(directory.resolve("reserved-names.json"), "[\"reserved\"]");
        LoginMigration.load(world);
        String salt = Base64.getEncoder().encodeToString(new byte[16]);
        String password = "migration-fixture-ї";
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((salt + password).getBytes(StandardCharsets.UTF_8));
        Files.writeString(file, """
                {"formatVersion":1,"users":{"oldplayer":{
                "lastKnownName":"OldPlayer","salt":"rlogin:%s","hash":"%s","iterations":1}}}
                """.formatted(salt, Base64.getEncoder().encodeToString(digest)));

        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("empirecraft_forge_migration.mixins.json");
        var service = (OfflineMixinService) MixinService.getService();
        var transformer = service.transformer();
        String prefix = "ru.iwoss.serverlogin.auth.";
        ClassLoader loader = new ClassLoader(MigrationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith(prefix)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try {
                        var node = service.getClassNode(name);
                        boolean changed = transformer.transformClass(environment, name, node);
                        if (name.equals(prefix + "PasswordHasher") || name.equals(prefix + "UserDatabase")
                                || name.equals(prefix + "UserDatabase$UserRecord")) check(changed, "mixin " + name);
                        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                        node.accept(writer);
                        byte[] bytecode = writer.toByteArray();
                        type = defineClass(name, bytecode, 0, bytecode.length);
                    } catch (Exception error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };

        var database = loader.loadClass(prefix + "UserDatabase");
        var open = database.getConstructor(Path.class);
        var load = database.getMethod("load");
        var authenticate = database.getMethod("authenticate", String.class, char[].class, char[].class, int.class);
        var persistRegistration = database.getMethod("register", String.class, char[].class, int.class);
        var shutdown = database.getMethod("shutdown");
        Object store = open.newInstance(world);
        load.invoke(store);
        check(!authenticate(store, authenticate, "oldplayer", password), "wrong letter case denied");
        check(!authenticate(store, authenticate, "OldPlayer", "wrong-fixture"), "wrong password denied");
        check(!result(persistRegistration.invoke(store, "Reserved", password.toCharArray(), 600000)), "reserved name denied");
        check(authenticate(store, authenticate, "OldPlayer", password), "legacy password accepted");
        try (var reader = Files.newBufferedReader(file)) {
            var saved = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("users").getAsJsonObject("oldplayer");
            check(saved.get("iterations").getAsInt() == 600000, "PBKDF2 upgrade");
            check(!saved.get("salt").getAsString().startsWith("rlogin:"), "legacy salt replaced");
        }
        shutdown.invoke(store);
        Object reopened = open.newInstance(world);
        load.invoke(reopened);
        check(authenticate(reopened, authenticate, "OldPlayer", password), "upgraded account works after restart");
        check(result(persistRegistration.invoke(reopened, "NewPlayer", password.toCharArray(), 600000)), "new account registration");
        check(authenticate(reopened, authenticate, "NewPlayer", password), "new account login");
        shutdown.invoke(reopened);
        LoginMigration.stop();
        System.out.println("PASS Server Login 1.0.3 with applied mixins, legacy password, PBKDF2, restart and registration");
    }

    private static boolean authenticate(Object store, Method login, String name, String password) throws Exception {
        return result(login.invoke(store, name, password.toCharArray(), null, 600000));
    }

    private static boolean result(Object future) throws Exception {
        return Boolean.TRUE.equals(((CompletableFuture<?>) future).get());
    }
}
