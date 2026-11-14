package ua.empirecraft.packfixes;

import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Captures a stalled main thread when the host disables Minecraft's watchdog. */
@Mod.EventBusSubscriber(modid=PackFixes.ID)
public final class TickStallReporter {
    private static volatile long lastTick;
    private static volatile Thread serverThread;
    private static ScheduledExecutorService sampler;
    private static boolean reported;
    @SubscribeEvent public static synchronized void started(ServerStartedEvent event) {
        if(!event.getServer().isDedicatedServer())return;
        lastTick=0;serverThread=null;reported=false;
        sampler=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"Empirecraft tick diagnostic");thread.setDaemon(true);return thread;});
        sampler.scheduleWithFixedDelay(TickStallReporter::sample,5,5,TimeUnit.SECONDS);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(sampler!=null&&event.phase==TickEvent.Phase.END){serverThread=Thread.currentThread();lastTick=System.nanoTime();}
    }
    private static void sample() {
        var thread=serverThread;long previous=lastTick;
        if(thread==null||previous==0)return;
        long delay=System.nanoTime()-previous;
        if(delay<TimeUnit.SECONDS.toNanos(30)){reported=false;return;}
        if(reported)return;reported=true;
        var message=new StringBuilder("Server tick stalled for ").append(TimeUnit.NANOSECONDS.toSeconds(delay)).append("s; main thread state=").append(thread.getState());
        var stack=thread.getStackTrace();
        for(int i=0;i<Math.min(192,stack.length);i++)message.append("\n\tat ").append(stack[i]);
        LogManager.getLogger("Empirecraft tick diagnostic").error(message.toString());
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event) {
        if(sampler!=null){sampler.shutdownNow();sampler=null;}serverThread=null;lastTick=0;
    }
}
