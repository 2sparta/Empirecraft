import cpw.mods.jarhandling.SecureJar;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.fml.loading.moddiscovery.*;
import ua.empirecraft.armor.client.*;

/** Real Forge ModFile/ModList and the actual client AddPackFindersEvent callback. */
public final class ArmorPackFinderTest {
    private static int checks;
    private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();
        // Supply the real bootstrap metadata normally installed by ModLauncher;
        // no game window is needed to exercise Forge's actual mod file/event APIs.
        var version=FMLLoader.class.getDeclaredField("versionInfo");version.setAccessible(true);
        version.set(null,new VersionInfo(args[2],"1.20.1","20230612.114412","net.minecraftforge"));
        check(ModFile.class.getProtectionDomain().getCodeSource().getLocation().toString().contains(args[2]),"Requested Forge loader is the loaded implementation");
        List<RepositorySource> serverSources=new ArrayList<>();
        ArmorLanguages.packs(new AddPackFindersEvent(PackType.SERVER_DATA,serverSources::add));
        check(serverSources.isEmpty(),"Server data must not register a client language pack");
        Path path=Path.of(args[0]).toAbsolutePath();
        var jar=SecureJar.from(path);
        var file=new ModFile(jar,null,ModFileParser::modsTomlParser,"MOD");
        check(file.identifyMods(),"Actual Forge mod file parser discovers the armor mod");
        var infos=file.getModInfos().stream().map(info->(ModInfo)info).toList();
        ModList.of(List.of(file),infos);
        check(ModList.get().getModFileById("empirecraft_armor").getFile()==file,"Production callback resolves the real mod file");
        List<RepositorySource> sources=new ArrayList<>();
        if(args[1].equals("baseline")){
            try{
                ArmorLanguages.packs(new AddPackFindersEvent(PackType.CLIENT_RESOURCES,sources::add));
                throw new AssertionError("Old client registration unexpectedly succeeded");
            }catch(IllegalArgumentException expected){
                check("Missing path".equals(expected.getMessage()),"Reproduce the uploaded crash, not another exception");
                check(sources.isEmpty(),"Old callback fails before repository registration");
                System.out.println("ARMOR_PACK_BASELINE_REPRODUCED assertions="+checks+" exception=Missing path loader="+ModFile.class.getProtectionDomain().getCodeSource().getLocation());
                return;
            }
        }
        ArmorLanguages.packs(new AddPackFindersEvent(PackType.CLIENT_RESOURCES,sources::add));
        check(sources.size()==1,"Actual client callback registers exactly one source");
        var repository=new PackRepository(sources.toArray(RepositorySource[]::new));
        repository.reload();repository.setSelected(List.of());
        check(repository.getSelectedIds().contains(ArmorLanguages.PACK_ID),"Client pack remains enabled without a manual selection");
        try(var resources=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,repository.openAllSelected())){
            for(String locale:List.of("en_us","uk_ua","ru_ru","de_de","uk_ua")){
                Language.inject(ClientLanguage.loadFrom(resources,List.of("en_us",locale),false));
                String expected=switch(locale){case "uk_ua"->"Шари броні";case "ru_ru"->"Слои брони";default->"Armor layers";};
                check(ArmorLabels.title().getString().equals(expected),"Production registration loads the expected language "+locale);
                for(int rank=0;rank<3;rank++)check(!ArmorLabels.rank(rank).getString().contains("empirecraft.armor"),"No untranslated layer keys "+locale);
            }
        }
        System.out.println("ARMOR_PACK_FINDER_PASS assertions="+checks+" path_with_spaces="+path.toString().contains(" ")+" loader="+ModFile.class.getProtectionDomain().getCodeSource().getLocation());
    }
}
