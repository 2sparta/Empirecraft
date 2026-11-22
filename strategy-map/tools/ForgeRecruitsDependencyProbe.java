import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.ModSorter;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator;
import net.minecraftforge.forgespi.Environment;

public final class ForgeRecruitsDependencyProbe {
    public static void main(String[] args) throws Exception {
        // Supply the state normally provided by the launch handler before discovery.
        Dist side = Dist.valueOf(args[0]);
        var dist = FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, side);
        var version = FMLLoader.class.getDeclaredField("versionInfo");
        version.setAccessible(true);
        version.set(null, new VersionInfo("47.4.23", "1.20.1", "20230612.114412", "net.minecraftforge"));
        var constructor = cpw.mods.modlauncher.Environment.class.getDeclaredConstructor(Launcher.class);
        constructor.setAccessible(true);
        IEnvironment environment = constructor.newInstance((Launcher) null);
        environment.computePropertyIfAbsent(Environment.Keys.DIST.get(), key -> side);
        environment.computePropertyIfAbsent(Environment.Keys.MODFILEFACTORY.get(), key -> ModFile::new);
        Environment.build(environment);

        var locator = new ModsFolderLocator();
        var files = Arrays.stream(args).skip(2).map(name -> {
            var file = new ModFile(SecureJar.from(Path.of(name)), locator, ModFileParser::modsTomlParser);
            if (!file.identifyMods()) throw new AssertionError("Could not read mod descriptor: " + name);
            return file;
        }).toList();
        var details = ModSorter.sort(files, List.of()).getErrors().stream()
                .flatMap(error -> error.getAllData().stream()).toList();
        var dependencyArguments = details.stream()
                .map(detail -> Arrays.stream(detail.getArgs()).map(String::valueOf).toList()).toList();
        String bridgeVersion=files.stream().flatMap(file->file.getModInfos().stream())
                .filter(info->info.getModId().equals("empirecraft_townymap")).findFirst().orElseThrow().getVersion().toString();
        if (args[1].equals("reject")) {
            if (details.size() != 1 || !details.get(0).getI18message().equals("fml.modloading.missingdependency")
                    || !dependencyArguments.equals(
                            List.of(List.of("empirecraft_townymap", "recruits", "[1.13.0,1.14)", bridgeVersion)))) {
                throw new AssertionError("Expected the original Recruits dependency failure: " + dependencyArguments);
            }
        } else if (!details.isEmpty()) {
            throw new AssertionError("Forge rejected dependencies: " + dependencyArguments);
        }
        System.out.println(side + " " + args[1] + " " + Path.of(args[args.length - 1]).getFileName());
    }
}
