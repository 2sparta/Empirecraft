package ua.empirecraft.potato.smoke;

import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.event.RenderLivingEvent;
import ua.empirecraft.potato.*;
import ua.empirecraft.potato.client.PotatoClient;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Executes inside real Minecraft/Forge with real options, serialization, renderer and input. */
public final class ClientProbe {
    private final String phase = System.getProperty("empirecraft.potato.smoke", "enabled");
    private int ticks, step, checks, moveStart, rendered;
    private boolean done, attacked;
    private Map<String,String> baseline;
    private CompletableFuture<Void> reload;
    private String lastScreen = "";
    public static void register() { MinecraftForge.EVENT_BUS.register(new ClientProbe()); }
    @SubscribeEvent public void rendered(RenderLivingEvent.Post<?,?> event) {
        if (event.getEntity() instanceof Zombie && event.getEntity().getName().getString().equals("Potato target")) rendered++;
    }
    private void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private Map<String,String> snapshot(boolean raw) throws Exception {
        if (raw) try (var ignored = OriginalOptions.read()) { return snapshot(false); }
        var values = new TreeMap<String,String>();
        for (Field field : Options.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true); Object value = field.get(Minecraft.getInstance().options);
            if (value instanceof OptionInstance<?> option) values.put(field.getName(), String.valueOf(option.get()));
            if (value instanceof Map<?,?> map) for (var entry : map.entrySet())
                if (entry.getValue() instanceof OptionInstance<?> option) values.put(field.getName()+"/"+entry.getKey(), String.valueOf(option.get()));
        }
        return values;
    }
    private void verifyPreset() throws Exception {
        var options = Minecraft.getInstance().options; Set<String> controlled = new HashSet<>();
        for (Field field : Options.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true); Object value = field.get(options);
            if (!(value instanceof OptionInstance<?> option)) continue;
            check((Object)option instanceof OptionKey, "Real OptionInstance Mixin not applied");
            String key = ((OptionKey)(Object)option).potato$key(); check(key != null, "Constructor key was not captured for "+field.getName());
            Object stored; try (var ignored = OriginalOptions.read()) { stored = option.get(); }
            switch(key) {
                case "options.mipmapLevels" -> check(stored.equals(4),"Original mipmap preference overwritten");
                case "options.biomeBlendRadius" -> check(stored.equals(5),"Original biome blend overwritten");
                case "options.ao", "options.entityShadows" -> check(stored.equals(true),"Original preference overwritten: "+key);
                case "options.graphics" -> check(stored==GraphicsStatus.FANCY,"Original graphics preference overwritten");
            }
            Object expected=switch(key) {
                case "options.ao", "options.entityShadows" -> false;
                case "options.biomeBlendRadius", "options.mipmapLevels" -> 0;
                case "options.renderClouds" -> CloudStatus.OFF;
                case "options.graphics" -> stored==GraphicsStatus.FAST?GraphicsStatus.FAST:GraphicsStatus.FANCY;
                default -> stored;
            };
            check(Objects.equals(expected, option.get()), "Incorrect native value: "+key+" expected="+expected+" actual="+option.get());
            if (RenderPolicy.KEYS.contains(key)) controlled.add(key);
            else check(String.valueOf(option.get()).equals(baseline.get(field.getName())), "Gameplay option changed: "+key);
        }
        check(controlled.equals(RenderPolicy.KEYS), "Native keys missing: "+controlled);
    }
    private void verifySave() throws Exception {
        var mc = Minecraft.getInstance(); Path file = mc.gameDirectory.toPath().resolve("options.txt");
        try (var ignored = OriginalOptions.read()) { mc.options.save(); }
        String expected = Files.readString(file);
        mc.options.save();
        check(Files.readString(file).equals(expected), "Preset leaked into options.txt");
        check(snapshot(true).equals(baseline), "Stored preferences were changed");
    }
    private void finish(Throwable failure) {
        if (done) return; done = true;
        var mc = Minecraft.getInstance(); mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false);
        try {
            var result = new LinkedHashMap<String,Object>(); result.put("phase",phase); result.put("passed",failure==null);
            result.put("checks",checks); result.put("rendered_target_frames",rendered);
            result.put("addon_loaded",ModList.get().isLoaded("empirecraft_potato"));
            result.put("embeddium_loaded",ModList.get().isLoaded("embeddium"));
            result.put("dynamic_fps_loaded",ModList.get().isLoaded("dynamic_fps"));
            result.put("screen",mc.screen==null?"null":mc.screen.getClass().getName());
            result.put("overlay",mc.getOverlay()==null?"null":mc.getOverlay().getClass().getName());
            if(failure!=null) result.put("error",failure.toString());
            Files.writeString(mc.gameDirectory.toPath().resolve("potato-smoke-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            if(failure!=null) failure.printStackTrace();
            System.out.println("POTATO_SMOKE_"+(failure==null?"PASS":"FAIL")+" "+phase+" checks="+checks);
        } catch(Exception error) { error.printStackTrace(); }
        mc.stop();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        var mc = Minecraft.getInstance(); if (++ticks > 3600) { finish(new AssertionError("Client acceptance timeout")); return; }
        String screen = mc.screen==null?"null":mc.screen.getClass().getName();
        if(!screen.equals(lastScreen)) { System.out.println("POTATO_STARTUP_SCREEN "+screen); lastScreen=screen; }
        // Only these ordinary first-run setup screens may be acknowledged by the test driver.
        // Error/loading-failure screens are never dismissed.
        if(mc.getOverlay()==null && mc.screen!=null && (screen.endsWith("AccessibilityOnboardingScreen") || screen.endsWith("ModLoadingWarningScreen"))) {
            for(var child:mc.screen.children()) if(child instanceof Button button && button.active) {
                String label=button.getMessage().getString();
                if(label.equals("Done") || label.equals("Proceed") || label.equals("Continue")) {
                    System.out.println("POTATO_SETUP_ACK "+screen+" "+label); button.onPress(); return;
                }
            }
        }
        if(mc.getOverlay()==null && (screen.endsWith("ModLoadingErrorScreen") || screen.endsWith("ErrorScreen"))) {
            finish(new AssertionError("Game stopped on "+screen)); return;
        }
        if(mc.getOverlay()!=null || mc.options==null || !(mc.screen instanceof TitleScreen || mc.level!=null)) return;
        try {
            if (phase.equals("absent")) {
                check(!ModList.get().isLoaded("empirecraft_potato"), "Addon is still installed in uninstall test");
                @SuppressWarnings("unchecked") Map<String,String> saved = new Gson().fromJson(Files.readString(mc.gameDirectory.toPath().resolve("potato-baseline.json")),Map.class);
                check(snapshot(false).equals(saved), "Removing addon did not restore preferences"); finish(null); return;
            }
            if (step == 0) {
                check(ModList.get().isLoaded("empirecraft_potato"), "Addon missing");
                check(PotatoState.enabled()!=phase.equals("off"), "Wrong default/persisted state");
                baseline=snapshot(true); check(baseline.size()>60,"Did not enumerate real native options");
                if(phase.equals("off")) check(snapshot(false).equals(baseline),"Disabled addon changed options"); else verifyPreset();
                verifySave();
                Files.writeString(mc.gameDirectory.toPath().resolve("potato-baseline.json"),new Gson().toJson(baseline));
                if(phase.equals("multiplayer")) { step=10; return; }
                reload=PotatoClient.setEnabled(phase.equals("off")); step=1; return;
            }
            if(step==1 && reload.isDone()) {
                reload.join();
                if(phase.equals("off")) verifyPreset(); else check(snapshot(false).equals(baseline),"Disabling did not restore native options");
                verifySave(); finish(null); return;
            }
            if(step==10 && mc.level!=null && mc.player!=null && mc.gameMode!=null) {
                Zombie target=null;
                for(var entity:mc.level.entitiesForRendering()) if(entity instanceof Zombie z && z.getName().getString().equals("Potato target")) target=z;
                if(target==null) return;
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,target.getEyePosition());
                if(rendered<2) return;
                mc.gameMode.attack(mc.player,target); attacked=true;
                mc.options.keyUp.setDown(true); moveStart=ticks; step=11; return;
            }
            if(step==11 && ticks>=moveStart+20) {
                mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(true); moveStart=ticks; step=12; return;
            }
            if(step==12 && ticks>=moveStart+2) { mc.options.keyJump.setDown(false); step=13; return; }
            if(step==13 && ticks>=moveStart+30) {
                check(attacked && rendered>=2,"Normal entity render/attack path not exercised");
                verifyPreset(); verifySave(); finish(null);
            }
        } catch(Throwable failure) { finish(failure); }
    }
}
