package guardtest;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Remap only the original ClientLevel fixture. Minecraft binaries stay in ignored build outputs. */
public final class ClientClassRemapper {
    private record Parents(String parent, String[] interfaces) {}
    public static void main(String[] args) throws Exception {
        Map<String, String> classes = new HashMap<>(), methods = new HashMap<>(), fields = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            if (line.isEmpty() || line.startsWith("#") || Character.isWhitespace(line.charAt(0)) || !line.endsWith(":")) continue;
            var parts = line.substring(0, line.length()-1).split(" -> ");
            classes.put(parts[1], parts[0].replace('.', '/'));
        }
        String owner = "";
        for (String line : Files.readAllLines(Path.of(args[2]))) {
            if (line.startsWith("tsrg2") || line.isBlank() || line.startsWith("\t\t")) continue;
            var parts = line.trim().split(" +");
            if (!line.startsWith("\t")) owner = parts[0];
            else if (parts.length >= 3 && parts[1].startsWith("(")) methods.put(owner+"."+parts[0]+parts[1], parts[2]);
            else if (parts.length >= 2) fields.put(owner+"."+parts[0], parts[1]);
        }
        Map<String, Parents> parents = new HashMap<>();
        try (var source = new ZipFile(args[0])) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                var reader = new ClassReader(source.getInputStream(entry));
                parents.put(reader.getClassName(), new Parents(reader.getSuperName(), reader.getInterfaces()));
            }
            Remapper mapping = new Remapper() {
                @Override public String map(String name) { return classes.getOrDefault(name, name); }
                private String member(Map<String, String> map, String owner, String suffix, Set<String> visited) {
                    if (owner == null || !visited.add(owner)) return null;
                    String value = map.get(owner+"."+suffix);
                    if (value != null) return value;
                    var info = parents.get(owner);
                    if (info == null) return null;
                    value = member(map, info.parent, suffix, visited);
                    if (value != null) return value;
                    for (String parent : info.interfaces) {
                        value = member(map, parent, suffix, visited);
                        if (value != null) return value;
                    }
                    return null;
                }
                @Override public String mapMethodName(String owner, String name, String descriptor) {
                    String result = member(methods, owner, name+descriptor, new HashSet<>());
                    return result == null ? name : result;
                }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    String result = member(fields, owner, name, new HashSet<>());
                    return result == null ? name : result;
                }
            };
            try (var output = new ZipOutputStream(Files.newOutputStream(Path.of(args[3])))) {
                for (var entry : classes.entrySet()) {
                    if (!entry.getValue().equals("net/minecraft/client/multiplayer/ClientLevel") && !entry.getValue().startsWith("net/minecraft/client/multiplayer/ClientLevel$")) continue;
                    var reader = new ClassReader(source.getInputStream(source.getEntry(entry.getKey()+".class")));
                    var writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, mapping), 0);
                    output.putNextEntry(new ZipEntry(entry.getValue()+".class"));
                    output.write(writer.toByteArray()); output.closeEntry();
                }
            }
        }
        System.out.println("PASS remapped original Minecraft 1.20.1 ClientLevel fixture using pinned Mojang and MCP mappings");
    }
}
