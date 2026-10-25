package guardtest;

import java.lang.invoke.*;
import java.util.function.Supplier;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

/** Resolve the real transformed packet's methods with Mixin classes forbidden, as in ModLauncher. */
public final class PacketLinkage {
    public static void verify(ClassNode node) throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        byte[] bytes = writer.toByteArray();
        String name = node.name.replace('/', '.');
        ClassLoader loader = new ClassLoader(PacketLinkage.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(requested)) {
                    if (requested.startsWith("ua.empirecraft.forging.mixin.")) throw new ClassNotFoundException("Mixin implementation is invalid at runtime: "+requested);
                    if (!requested.equals(name)) return super.loadClass(requested, resolve);
                    Class<?> loaded = findLoadedClass(requested);
                    if (loaded == null) loaded = defineClass(requested, bytes, 0, bytes.length);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> packet = Class.forName(name, false, loader);
        packet.getDeclaredMethods(); // Resolves synthetic lambda signatures too: the reported common_setup crash.
        Class<?> buffer = Class.forName("net.minecraft.network.FriendlyByteBuf", false, loader);
        Class<?> pos = Class.forName("net.minecraft.core.BlockPos", false, loader);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        lookup.findStatic(packet, "encode", MethodType.methodType(void.class, packet, buffer));
        lookup.findStatic(packet, "decode", MethodType.methodType(packet, buffer));
        lookup.findStatic(packet, "handle", MethodType.methodType(void.class, packet, Supplier.class));
        Object instance = packet.getConstructor(pos, String.class).newInstance(null, "perfect");
        if (!"perfect".equals(packet.getMethod("getCounter").invoke(instance))) throw new AssertionError("Counter packet data changed");
        Class<?> bridge = Class.forName("ua.empirecraft.forging.CounterPacketPosition", false, loader);
        if (!bridge.isInstance(instance) || bridge.getMethod("empirecraft$position").invoke(instance) != null)
            throw new AssertionError("Packet position bridge was not applied");
        System.out.println("PASS native packet class loading, synthetic method linkage and network handler registration with Mixin classes forbidden");
    }
}
