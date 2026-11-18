package ua.empirecraft.calendar;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
/** Fix only calendar timing, retaining local weather, dimension and fertility settings. */
public final class CalendarConfigRepair {
 public static boolean install(Path file) throws IOException {
  String original=Files.exists(file)?Files.readString(file):"";
  List<String> out=new ArrayList<>();boolean section=false,seen=false,day=false,month=false;
  for(String line:original.split("\\R",-1)){
   String t=line.trim();
   if(t.startsWith("[")){
    if(section){if(!day)out.add("day_duration = 57600");if(!month)out.add("sub_season_duration = 30");}
    section=t.matches("\\[time_settings\\]\\s*(#.*)?");if(section)seen=true;
   }
   if(section&&t.matches("day_duration\\s*=.*")){line=line.replaceFirst("=\\s*[^#]*","= 57600"+(line.contains("#")?" ":""));day=true;}
   if(section&&t.matches("sub_season_duration\\s*=.*")){line=line.replaceFirst("=\\s*[^#]*","= 30"+(line.contains("#")?" ":""));month=true;}
   out.add(line);
  }
  if(!seen){out.add("[time_settings]");section=true;}
  if(section){if(!day)out.add("day_duration = 57600");if(!month)out.add("sub_season_duration = 30");}
  String result=String.join("\n",out);if(!result.endsWith("\n"))result+="\n";
  if(result.equals(original))return false;
  Files.createDirectories(file.toAbsolutePath().getParent());
  if(Files.exists(file)){Path backup=file.resolveSibling(file.getFileName()+".before-empirecraft-1.2.4");if(!Files.exists(backup))Files.copy(file,backup);}
  Path temporary=Files.createTempFile(file.toAbsolutePath().getParent(),"calendar-",".tmp");
  try{Files.writeString(temporary,result);Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
  return true;
 }
}
