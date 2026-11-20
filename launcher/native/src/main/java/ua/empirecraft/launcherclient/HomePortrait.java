package ua.empirecraft.launcherclient;
/** Render-thread scope: preview changes cannot affect the skin editor or gameplay. */
public final class HomePortrait {
 private static final ThreadLocal<Boolean> active=ThreadLocal.withInitial(()->false);
 public static boolean active(){return active.get();}
 public static void render(Runnable draw){boolean previous=active.get();active.set(true);try{draw.run();}finally{if(previous)active.set(true);else active.remove();}}
}
