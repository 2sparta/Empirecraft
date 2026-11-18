package net.fretux.knockedback;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.fretux.knockedback.client.ClientKnockedState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
@Mod.EventBusSubscriber(modid="knockedback", value=Dist.CLIENT)
public final class DeathEffects {
    private static final SoundEvent HEART = SoundEvent.createVariableRangeEvent(new ResourceLocation("knockedback", "heartbeat"));
    private static Boolean enabled;
    private static int pulse;
    private static SimpleSoundInstance sound;
    private static boolean enabled() {
        if (enabled == null) {
            enabled = true;
            var file = FMLPaths.CONFIGDIR.get().resolve("empirecraft-launcher-profile.json");
            if (Files.isRegularFile(file)) try (var reader = Files.newBufferedReader(file)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("deathEffects")) enabled = json.get("deathEffects").getAsBoolean();
            } catch (Exception ignored) { /* Missing/invalid preference retains default effects. */ }
        }
        return enabled;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        int left = ClientKnockedState.getTimeLeft();
        if (mc.player == null || !mc.player.isAlive() || !enabled() || !BleedoutPolicy.heartbeat(left)) {
            pulse = 0;
            if (sound != null) { mc.getSoundManager().stop(sound); sound = null; }
            if (mc.player == null) ClientKnockedState.setTimeLeft(0);
            return;
        }
        if (pulse-- <= 0) {
            sound = SimpleSoundInstance.forUI(HEART, 1f, .7f);
            mc.getSoundManager().play(sound);
            pulse = left <= 160 ? 11 : 17;
        }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null || !mc.player.isAlive()) return;
        float darkness = BleedoutPolicy.darkness(ClientKnockedState.getTimeLeft());
        if (darkness > 0) event.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), ((int)(255 * darkness) << 24));
    }
}
