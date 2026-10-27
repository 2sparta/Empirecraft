import java.lang.module.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Resolve the native JARs together, as ModLauncher does before any mixins run. */
public final class ModulePackages {
    public static void main(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Expected Create and Wonderous Sea JARs");
        ModuleFinder finder = ModuleFinder.of(Arrays.stream(args).map(Path::of).toArray(Path[]::new));
        Set<String> roots = finder.findAll().stream().map(m -> m.descriptor().name()).collect(Collectors.toSet());
        if (roots.size() != 2) throw new IllegalStateException("Expected two distinct modules: " + roots);
        ModuleLayer.boot().configuration().resolveAndBind(finder, ModuleFinder.of(), roots);
        System.out.println("PASS combined module resolution: " + roots);
    }
}
