package guardtest;

import java.io.*;
import java.net.*;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.*;
import org.spongepowered.asm.service.*;

/** Test-only bytecode service: transform exact installed JARs without booting a world. */
public final class OfflineMixinService extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
    public String getName() { return "EmpireCraft offline integration test"; }
    public boolean isValid() { return true; }
    public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
    public IClassProvider getClassProvider() { return this; }
    public IClassBytecodeProvider getBytecodeProvider() { return this; }
    public ITransformerProvider getTransformerProvider() { return null; }
    public IClassTracker getClassTracker() { return null; }
    public IMixinAuditTrail getAuditTrail() { return null; }
    public Collection<String> getPlatformAgents() { return List.of(); }
    public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("offline-test"); }
    public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
    public URL[] getClassPath() { return new URL[0]; }
    public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
    public Class<?> findClass(String name, boolean init) throws ClassNotFoundException { return Class.forName(name, init, getClass().getClassLoader()); }
    public Class<?> findAgentClass(String name, boolean init) throws ClassNotFoundException { return findClass(name, init); }
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, false); }
    public ClassNode getClassNode(String name, boolean transform) throws ClassNotFoundException, IOException {
        try (InputStream in = getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) throw new ClassNotFoundException(name);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.EXPAND_FRAMES);
            return node;
        }
    }
    public IMixinTransformer transformer() { return getInternal(IMixinTransformerFactory.class).createTransformer(); }
}

