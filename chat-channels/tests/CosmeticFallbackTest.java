import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;
import net.md_5.bungee.api.chat.TextComponent;
import ua.empirecraft.chat.*;

public final class CosmeticFallbackTest {
    public static void main(String[] args)throws Exception {
        int total=0,bitmap=0,fallback=0;
        try(var titles=new ZipFile(args[0]);var chat=new ZipFile(args[1])) {
            var catalog=YamlConfiguration.loadConfiguration(new InputStreamReader(titles.getInputStream(titles.getEntry("catalog.yml")),StandardCharsets.UTF_8));
            var icons=YamlConfiguration.loadConfiguration(new InputStreamReader(chat.getInputStream(chat.getEntry("cosmetics.yml")),StandardCharsets.UTF_8));
            for(String key:catalog.getKeys(false)) {
                String glyph=icons.getString(key+".glyph"),label=catalog.getString(key+".label");
                String display=CosmeticDisplay.text(glyph,label,key);
                var message=ChatFormat.message(Channel.GLOBAL,"Місто","Держава","Player","",display,"Повідомлення");
                var icon=message.getExtra().stream().filter(v->v instanceof TextComponent text&&text.getText().equals(display)).findFirst().orElseThrow();
                if(glyph!=null) {
                    if(!display.equals(glyph)||!"empirecraft_chat:titles".equals(icon.getFont()))throw new AssertionError(key+" bitmap changed");bitmap++;
                } else {
                    if(!display.equals("["+label+"]")||icon.getFont()!=null)throw new AssertionError(key+" missing readable fallback");fallback++;
                }
                total++;
            }
            if(!CosmeticDisplay.text(null,catalog.getString("art_tit2.label"),"art_tit2").equals("[Влучний стрілець]"))throw new AssertionError("Reported catalog failure");
        }
        if(total!=137||bitmap!=39||fallback!=98)throw new AssertionError("Catalog coverage "+total+" "+bitmap+" "+fallback);
        if(!CosmeticDisplay.text(null,null,"custom").equals("[custom]"))throw new AssertionError("Custom title fallback");
        System.out.println("CHAT_COSMETIC_FALLBACK_PASS titles="+total+" bitmaps="+bitmap+" text="+fallback);
    }
}
