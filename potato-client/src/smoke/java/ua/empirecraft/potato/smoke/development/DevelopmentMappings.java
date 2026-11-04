package ua.empirecraft.potato.smoke.development;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test-only namespace adapter: production SRG shadows use ForgeGradle's actual official mappings. */
public final class DevelopmentMappings implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {
        Map<String, String> names = new HashMap<>();
        try {
            for (String line : Files.readAllLines(Path.of(System.getProperty("mixin.env.refMapRemappingFile")))) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3 || !(parts[0].equals("FD:") || parts[0].equals("MD:"))) continue;
                String source = simple(parts[1]), target = simple(parts[parts[0].equals("MD:") ? 3 : 2]);
                String previous = names.put(source, target);
                if (previous != null && !previous.equals(target)) throw new IllegalStateException("Ambiguous mapping: " + source);
            }
        } catch (Exception failure) { throw new IllegalStateException("Cannot load real Minecraft development mappings", failure); }
        if (!names.containsKey("f_96164_") || !names.containsKey("m_231551_"))
            throw new IllegalStateException("Missing actual SRG to official mappings");
        IRemapper adapter = new IRemapper() {
            @Override public String mapFieldName(String owner, String name, String desc) { return names.getOrDefault(name, name); }
            @Override public String mapMethodName(String owner, String name, String desc) { return names.getOrDefault(name, name); }
            @Override public String map(String name) { return name; }
            @Override public String unmap(String name) { return name; }
            @Override public String mapDesc(String desc) { return desc; }
            @Override public String unmapDesc(String desc) { return desc; }
        };
        MixinEnvironment current = MixinEnvironment.getCurrentEnvironment(), defaults = MixinEnvironment.getDefaultEnvironment();
        current.getRemappers().add(adapter);
        if (defaults != current) defaults.getRemappers().add(adapter);
        System.out.println("POTATO_DEVELOPMENT_MAPPINGS " + names.size() + " actual member names");
    }
    private static String simple(String member) { return member.substring(member.lastIndexOf('/') + 1); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
