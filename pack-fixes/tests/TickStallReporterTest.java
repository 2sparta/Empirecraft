import java.lang.reflect.*;
import java.util.concurrent.TimeUnit;
import ua.empirecraft.packfixes.TickStallReporter;

public final class TickStallReporterTest {
    public static void main(String[] args)throws Exception {
        Class<?> type=TickStallReporter.class;
        Field last=type.getDeclaredField("lastTick"),thread=type.getDeclaredField("serverThread"),reported=type.getDeclaredField("reported");
        last.setAccessible(true);thread.setAccessible(true);reported.setAccessible(true);
        Method sample=type.getDeclaredMethod("sample");sample.setAccessible(true);
        thread.set(null,Thread.currentThread());last.setLong(null,System.nanoTime());sample.invoke(null);
        if(reported.getBoolean(null))throw new AssertionError("Healthy tick reported");
        last.setLong(null,System.nanoTime()-TimeUnit.SECONDS.toNanos(35));sample.invoke(null);
        if(!reported.getBoolean(null))throw new AssertionError("Stall not captured");
        sample.invoke(null);if(!reported.getBoolean(null))throw new AssertionError("Duplicate reporting state lost");
        last.setLong(null,System.nanoTime());sample.invoke(null);
        if(reported.getBoolean(null))throw new AssertionError("Recovery did not reset reporting");
        thread.set(null,null);last.setLong(null,0);sample.invoke(null);
        System.out.println("TICK_STALL_REPORTER_PASS healthy=quiet stall=captured recovery=reset");
    }
}
