package ua.empirecraft.tfcprotocoltest;
import java.lang.reflect.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
@Mod("empirecraft_tfc_protocol_probe")
public final class Probe {
 public Probe(){FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);}
 void setup(FMLCommonSetupEvent e){e.enqueueWork(()->{try{
  String declared=ModList.get().getModContainerById("tfc").orElseThrow().getModInfo().getVersion().toString();String file=ModList.get().getModFileById("tfc").versionString();
  Class<?> handler=Class.forName("net.dries007.tfc.network.PacketHandler");var field=handler.getDeclaredField("VERSION");field.setAccessible(true);String network=(String)field.get(null);var channel=handler.getDeclaredField("CHANNEL");channel.setAccessible(true);Object simple=channel.get(null);var instance=simple.getClass().getDeclaredField("instance");instance.setAccessible(true);Object n=instance.get(simple);
  String mode=System.getProperty("tfc.protocol.test.mode");if(!file.equals("0.0NONE"))throw new AssertionError("Invalid regression fixture declared="+declared+" file="+file);
  String expected=mode.equals("control")?"0.0NONE":"3.2.30";if(!network.equals(expected))throw new AssertionError("Protocol "+network+" expected "+expected);
  for(String method:new String[]{"tryServerVersionOnClient","tryClientVersionOnServer"}){var m=n.getClass().getDeclaredMethod(method,String.class);m.setAccessible(true);if(!(Boolean)m.invoke(n,expected)||(Boolean)m.invoke(n,"3.2.29")||(Boolean)m.invoke(n,"ABSENT"))throw new AssertionError("Version guard "+method);boolean client=(Boolean)m.invoke(n,"3.2.30");if(client!=mode.equals("patched"))throw new AssertionError("Real client version accepted="+client);}
  System.out.println("TFC_PROTOCOL_REGRESSION_PASS mode="+mode+" declared="+declared+" file="+file+" network="+network+" strict=true");System.exit(0);
 }catch(Throwable t){System.out.println("TFC_PROTOCOL_REGRESSION_FAIL "+t);t.printStackTrace();System.exit(3);}});}
}
