import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import ua.empirecraft.armor.client.*;

/** Actual Minecraft language loading, pack selection and component cache reload. No API stubs. */
public final class ArmorLanguageTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();
        var title=ArmorLabels.title();var padding=ArmorLabels.rank(0);
        try(var empty=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of())){
            Language.inject(ClientLanguage.loadFrom(empty,List.of("en_us"),false));
            check(title.getString().equals("Armor layers"),"Missing resources expose a technical title key");
            check(padding.getString().equals("Padding"),"Missing resources expose a technical layer key");
        }
        try(var fs=FileSystems.newFileSystem(Path.of(args[0]),Map.of())){
            var pack=ArmorLanguages.create(fs.getPath("/"));
            var repository=new PackRepository(consumer->consumer.accept(pack));
            repository.reload();repository.setSelected(List.of());
            check(repository.getSelectedIds().contains(ArmorLanguages.PACK_ID),"Languages require manual resource-pack selection");
            check(pack.isRequired()&&pack.getDefaultPosition()==Pack.Position.TOP,"Embedded languages may be silently disabled");
            try(var resources=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,repository.openAllSelected())){
                check(resources.getNamespaces().contains("empirecraft_armor"),"Armor language namespace is not discoverable");
                for(String locale:List.of("en_us","uk_ua","ru_ru","uk_ua","de_de")){
                    Language.inject(ClientLanguage.loadFrom(resources,List.of("en_us",locale),false));
                    String expected=switch(locale){case "uk_ua"->"Шари броні";case "ru_ru"->"Слои брони";default->"Armor layers";};
                    check(title.getString().equals(expected),"Title translation/reload failed: "+locale+" = "+title.getString());
                    String expectedPadding=switch(locale){case "uk_ua"->"Підкладка";case "ru_ru"->"Подкладка";default->"Padding";};
                    check(padding.getString().equals(expectedPadding),"Cached layer label failed to reload: "+locale);
                    for(int rank=0;rank<3;rank++)check(!ArmorLabels.rank(rank).getString().contains("empirecraft.armor"),"Raw layer key in "+locale);
                    for(String key:List.of("title","layers","order","rank.0","rank.1","rank.2"))check(Language.getInstance().has("empirecraft.armor."+key),"Missing embedded key: "+locale+"/"+key);
                }
            }
        }
        System.out.println("ARMOR_LANGUAGE_PASS assertions="+checks+" default-enabled pack, en/uk/ru, unsupported language fallback, missing resources and live language reload");
    }
}
