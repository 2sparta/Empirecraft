import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import ua.empirecraft.packfixes.client.TranslationPack;

public final class TranslationPackTest {
  public static void main(String[] args)throws Exception {
    SharedConstants.tryDetectVersion();
    try(var fs=FileSystems.newFileSystem(Path.of(args[0]),Map.of())){
      var pack=TranslationPack.create(fs.getPath("/builtin/translations"));
      {
        var repo=new PackRepository(consumer->consumer.accept(pack));
        repo.reload();repo.setSelected(List.of());
        if(!repo.getSelectedIds().contains("empirecraft_builtin_translations"))throw new AssertionError("Translations require manual selection");
        if(!pack.isRequired()||pack.getDefaultPosition()!=Pack.Position.TOP)throw new AssertionError("Native-language overlay can lose its priority");
        try(var resources=pack.open()){
          int count=0;
          for(String locale:List.of("uk_ua","ru_ru"))for(String ns:List.of("overgeared","vs_sails","betsyross","empirecraft_engagement")){
            var supplier=resources.getResource(PackType.CLIENT_RESOURCES,new ResourceLocation(ns,"lang/"+locale+".json"));
            if(supplier==null)throw new AssertionError("Missing translation: "+ns+"/"+locale);
            try(var reader=new java.io.InputStreamReader(supplier.get(),java.nio.charset.StandardCharsets.UTF_8)){
              var values=JsonParser.parseReader(reader).getAsJsonObject();if(values.size()==0)throw new AssertionError("Empty "+ns);count+=values.size();
              if(ns.equals("overgeared")&&locale.equals("uk_ua")&&!values.get("item.overgeared.steel_pickaxe").getAsString().contains("кирк"))throw new AssertionError("Ukrainian steel pickaxe missing");
            }
          }
          System.out.println("BUILTIN_TRANSLATIONS_PASS default-enabled, top-priority pack with "+count+" flag/Overgeared language entries; no external resource pack");
        }
      }
    }
  }
}
