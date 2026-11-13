package ua.empirecraft.tfc.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.worldrules.RulesConfig;
import ua.empirecraft.worldrules.SeedMarket;

/** Actual mod loading, registry state and recipe ingredients after KubeJS reload. */
final class ServerCleanupChecks {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static void run(MinecraftServer server){
        Set<String> removed=Set.of(System.getProperty("empirecraft.removed.mods").split(","));
        for(String id:removed){
            check(!ModList.get().isLoaded(id),"removed mod is absent "+id);
            check(ForgeRegistries.ITEMS.getKeys().stream().noneMatch(k->k.getNamespace().equals(id)),"no removed item registrations "+id);
            check(ForgeRegistries.BLOCKS.getKeys().stream().noneMatch(k->k.getNamespace().equals(id)),"no removed block registrations "+id);
            check(RulesConfig.PLANTS.get().stream().noneMatch(s->s.startsWith(id+":")),"no removed plant renewal entries "+id);
            check(RulesConfig.TREES.get().stream().noneMatch(s->s.startsWith(id+":")),"no removed tree renewal entries "+id);
            check(SeedMarket.STOCK.stream().noneMatch(s->s.startsWith(id+":")),"no removed seed market entries "+id);
        }
        Set<String> owners=Set.of("empirecraft_tfc","tfc_muskets","musketmod","medieval_boomsticks");
        for(var recipe:server.getRecipeManager().getRecipes()){
            check(!removed.contains(recipe.getId().getNamespace()),"no recipes owned by removed mods");
            if(!owners.contains(recipe.getId().getNamespace()))continue;
            for(var ingredient:recipe.getIngredients())if(ingredient!=Ingredient.EMPTY)
                check(ingredient.getItems().length>0,"integration recipe has an available ingredient "+recipe.getId());
        }
        int modules=0;
        try(var jars=Files.list(Path.of("mods"))){
            for(var path:jars.filter(p->p.toString().endsWith(".jar")&&p.getFileName().toString().startsWith("empirecraft-")).toList()){
                try(var z=new ZipFile(path.toFile())){
                    for(var entry:z.stream().filter(e->e.getName().endsWith(".json")||e.getName().endsWith(".class")).toList()){
                        byte[] raw=z.getInputStream(entry).readAllBytes();String text=new String(raw,StandardCharsets.ISO_8859_1);
                        for(String id:removed)check(!text.contains(id+":"),"no live removed namespace reference "+path.getFileName()+" "+entry.getName());
                    }
                }
                modules++;
            }
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        System.out.println("SERVER_CLEANUP_NATIVE_PASS assertions="+checks+" modules="+modules+" plants="+RulesConfig.PLANTS.get().size()+" trees="+RulesConfig.TREES.get().size()+" removed="+removed);
    }
}
