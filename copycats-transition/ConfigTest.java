import java.nio.file.*;
import ua.empirecraft.calendar.CalendarConfigRepair;
public class ConfigTest {
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("calendar-config-test");int checks=0;
  for(String original:new String[]{"", "[time_settings]\nday_duration = 24000\nsub_season_duration = 8\n[weather_settings]\nday_duration = 999\n", "[time_settings]\nday_duration = 57600\nsub_season_duration = 30\n", "[time_settings] # clock\n day_duration = 24000 # keep comment\n[dimension_settings]\nwhitelisted_dimensions = [\"minecraft:overworld\"]\n"}) {
   Path f=root.resolve("case"+checks+".toml");Files.writeString(f,original);CalendarConfigRepair.install(f);String repaired=Files.readString(f);
   if(!repaired.contains("day_duration = 57600")||!repaired.contains("sub_season_duration = 30"))throw new AssertionError(repaired);checks++;
   if(CalendarConfigRepair.install(f))throw new AssertionError("Not idempotent");checks++;
   if(original.contains("# keep comment")&&!repaired.contains("# keep comment"))throw new AssertionError("Lost comment");checks++;
   if(original.contains("day_duration = 999")&&!repaired.contains("day_duration = 999"))throw new AssertionError("Changed another section");checks++;
  }
  System.out.println("CALENDAR_CONFIG_PASS checks="+checks);
 }
}
