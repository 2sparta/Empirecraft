import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.ClientBookRegistry;
import vazkii.patchouli.client.book.page.PageTemplate;
import vazkii.patchouli.client.book.template.BookTemplate;
import vazkii.patchouli.client.book.template.component.ComponentText;

/** Uses the real Patchouli client adapters; no graphics or replacement APIs. */
public final class ClientTemplateTest {
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();
        var registry=ClientBookRegistry.INSTANCE;registry.init();int pages=0;
        try(var zip=new ZipFile(Path.of(args[0]).toFile())) {
            for(String locale:new String[]{"uk_ua","en_us"}) {
                String prefix="assets/empirecraft_guidebook/patchouli_books/handbook/"+locale+"/";
                var template=registry.gson.fromJson(new String(zip.getInputStream(zip.getEntry(prefix+"templates/wrapped_text.json")).readAllBytes(),StandardCharsets.UTF_8),BookTemplate.class);
                var components=BookTemplate.class.getDeclaredField("components");components.setAccessible(true);
                var list=(java.util.List<?>)components.get(template);
                if(list.size()!=1||!(list.get(0) instanceof ComponentText))throw new AssertionError("Native text component adapter");
                var width=ComponentText.class.getDeclaredField("maxWidth");width.setAccessible(true);
                if(width.getInt(list.get(0))!=112)throw new AssertionError("Native component width");
                for(var entry:java.util.Collections.list(zip.entries())) {
                    if(!entry.getName().startsWith(prefix+"entries/"))continue;
                    var data=JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                    for(var page:data.getAsJsonArray("pages")) {
                        if(!page.getAsJsonObject().get("type").getAsString().equals("empirecraft_guidebook:wrapped_text"))continue;
                        BookPage parsed=registry.gson.fromJson(page,BookPage.class);
                        if(!(parsed instanceof PageTemplate))throw new AssertionError("Native custom page resolution");pages++;
                    }
                }
            }
        }
        if(pages<600)throw new AssertionError("Coverage");
        System.out.println("HANDBOOK_CLIENT_TEMPLATE_PASS pages="+pages+" locales=uk_ua,en_us width=112");
    }
}
