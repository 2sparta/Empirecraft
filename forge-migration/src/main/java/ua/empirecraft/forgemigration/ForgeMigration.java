package ua.empirecraft.forgemigration;

import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("empirecraft_forge_migration")
public final class ForgeMigration {
    public ForgeMigration() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void load(ServerAboutToStartEvent event) {
        var world = event.getServer().m_129843_(LevelResource.f_78182_);
        try {
            if (!Files.isRegularFile(world.resolve("serverlogin/users.json"))) {
                throw new IOException("Missing migrated rlogin account database");
            }
            LoginMigration.load(world);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Empirecraft login data could not be loaded", failure);
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppedEvent event) {
        LoginMigration.stop();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void commands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var login = dispatcher.getRoot().getChild("login");
        if (login == null || login.getChild("credentials") == null) {
            throw new IllegalStateException("Expected commands from Server Login 1.0.3 are missing");
        }
        for (String alias : new String[]{"register", "reg"}) {
            dispatcher.register(Commands.m_82127_(alias).requires(login.getRequirement())
                    .then(login.getChild("credentials")));
        }
    }
}
