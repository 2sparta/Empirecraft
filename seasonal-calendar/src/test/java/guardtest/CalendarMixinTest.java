package guardtest;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class CalendarMixinTest {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var env = MixinEnvironment.getDefaultEnvironment();
        boolean server = args.length == 0 || args[0].equals("SERVER");
        env.setSide(server ? MixinEnvironment.Side.SERVER : MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("empirecraft_calendar.mixins.json");
        var service = (OfflineMixinService)MixinService.getService();
        var transformer = service.transformer();
        for (String name : new String[] {"net.minecraft.server.level.ServerLevel", "sereneseasons.season.SeasonHandler", "ua.empirecraft.worldrules.NaturalRenewal", "sereneseasons.item.CalendarItem", "sereneseasons.season.SeasonalCropGrowthHandler"}) {
            if (!server && name.equals("ua.empirecraft.worldrules.NaturalRenewal")) continue;
            var node = service.getClassNode(name);
            if (!transformer.transformClass(env, name, node)) throw new AssertionError("No transformation: " + name);
            int hooks = 0;
            for (MethodNode method : node.methods) {
                if ((method.access & 0x0500) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                if (method.name.contains("empirecraft$")) hooks++;
            }
            assert hooks > 0 : name;
            System.out.println("PASS actual installed-JAR Mixin transformation and ASM verification: " + name);
        }
        if (!server) {
            String name = "net.minecraft.client.multiplayer.ClientLevel";
            var node = service.getClassNode(name);
            if (!transformer.transformClass(env, name, node)) throw new AssertionError("No client clock transformation");
            for (MethodNode method : node.methods)
                if ((method.access & 0x0500) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
            System.out.println("PASS actual Minecraft 1.20.1 ClientLevel clock transformation and ASM verification");
        }
    }
}
