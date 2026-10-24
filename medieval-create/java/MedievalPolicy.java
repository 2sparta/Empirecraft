package ua.empirecraft.create;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Runtime guard embedded in the patched Create JAR. No client classes are loaded on servers. */
public final class MedievalPolicy {
    private static final Set<String> ALLOWED = loadAllowed();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static boolean registered;

    private static Set<String> loadAllowed() {
        try (InputStream stream = MedievalPolicy.class.getResourceAsStream("/empirecraft/medieval-create-allowed.txt")) {
            if (stream == null) throw new IOException("Missing medieval Create policy");
            return new HashSet<>(new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    public static boolean allowedId(String id) { return !id.startsWith("create:") || ALLOWED.contains(id); }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        String key = target.getClass().getName() + ":" + name + ":" + Arrays.toString(Arrays.stream(args).map(x -> x == null ? null : x.getClass()).toArray());
        Method method = METHODS.get(key);
        if (method == null) {
            for (Method candidate : target.getClass().getMethods()) {
                if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) continue;
                boolean matches = true;
                for (int i = 0; i < args.length; i++) {
                    Class<?> type = candidate.getParameterTypes()[i];
                    if (type == boolean.class) type = Boolean.class;
                    if (args[i] != null && !type.isInstance(args[i])) matches = false;
                }
                if (matches) { method = candidate; METHODS.put(key, method); break; }
            }
            if (method == null) throw new NoSuchMethodException(key);
        }
        return method.invoke(target, args);
    }

    private static Object mapped(Object value, String srg, String named, Object... args) throws ReflectiveOperationException {
        try { return call(value, srg, args); }
        catch (NoSuchMethodException e) { return call(value, named, args); }
    }

    private static boolean registryAllowed(String registry, Object value) {
        try {
            Object entries = Class.forName("net.minecraftforge.registries.ForgeRegistries").getField(registry).get(null);
            Object id = Class.forName("net.minecraftforge.registries.IForgeRegistry").getMethod("getKey", Object.class).invoke(entries, value);
            return id == null || allowedId(id.toString());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Medieval Create registry guard failed", e); }
    }

    public static boolean itemAllowed(Object item) { return registryAllowed("ITEMS", item); }
    public static List<?> filter(List<?> items) { return items.stream().filter(MedievalPolicy::itemAllowed).toList(); }

    private static boolean stackAllowed(Object stack) throws ReflectiveOperationException {
        return itemAllowed(mapped(stack, "m_41720_", "getItem"));
    }
    private static boolean stateAllowed(Object state) throws ReflectiveOperationException {
        return registryAllowed("BLOCKS", mapped(state, "m_60734_", "getBlock"));
    }

    public static synchronized void register() {
        if (registered) return;
        try {
            Object bus = Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            Class<?> priority = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            Object highest = priority.getField("HIGHEST").get(null);
            Method add = Class.forName("net.minecraftforge.eventbus.api.IEventBus").getMethod("addListener", priority, boolean.class, Class.class, Consumer.class);
            String[] events = {
                "net.minecraftforge.event.level.BlockEvent$EntityPlaceEvent",
                "net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickItem",
                "net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickBlock"
            };
            for (String event : events) add.invoke(bus, highest, false, Class.forName(event), (Consumer<Object>) MedievalPolicy::guardEvent);
            registered = true;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Medieval Create event registration failed", e); }
    }

    private static void guardEvent(Object event) {
        try {
            boolean allowed;
            if (event.getClass().getName().contains("PlaceEvent")) {
                allowed = stateAllowed(call(event, "getPlacedBlock"));
                if (event.getClass().getSimpleName().equals("EntityMultiPlaceEvent")) {
                    for (Object snapshot : (Iterable<?>) call(event, "getReplacedBlockSnapshots"))
                        allowed &= stateAllowed(call(snapshot, "getCurrentBlock"));
                }
            } else {
                allowed = stackAllowed(call(event, "getItemStack"));
                if (event.getClass().getSimpleName().equals("RightClickBlock")) {
                    Object state = mapped(call(event, "getLevel"), "m_8055_", "getBlockState", call(event, "getPos"));
                    allowed &= stateAllowed(state);
                }
            }
            if (!allowed) call(event, "setCanceled", true);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Medieval Create interaction guard failed", e); }
    }

    public static void hideJei(Object runtime) {
        try {
            Class<?> runtimeType = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            Object manager = runtimeType.getMethod("getIngredientManager").invoke(runtime);
            Class<?> managerType = Class.forName("mezz.jei.api.runtime.IIngredientManager");
            Class<?> ingredientType = Class.forName("mezz.jei.api.ingredients.IIngredientType");
            Object stacks = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
            Collection<?> all = (Collection<?>) managerType.getMethod("getAllIngredients", ingredientType).invoke(manager, stacks);
            List<Object> hidden = new ArrayList<>();
            for (Object stack : all) if (!stackAllowed(stack)) hidden.add(stack);
            managerType.getMethod("removeIngredientsAtRuntime", ingredientType, Collection.class).invoke(manager, stacks, hidden);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Medieval Create JEI filter failed", e); }
    }
}
