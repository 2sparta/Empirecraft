package ua.empirecraft.armory;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** KubeJS may bypass RecipeManager.fromJson; transform the final authoritative recipe set. */
@Mod.EventBusSubscriber(modid=Armory.ID)
public final class AssemblyCatalog {
    public static synchronized int install(MinecraftServer server){
        var manager=server.getRecipeManager();List<Recipe<?>> recipes=new ArrayList<>();int changed=0;
        for(var original:manager.getRecipes()){Recipe<?> converted=AssemblyRecipe.convert(original.getId(),original,server.registryAccess());if(original!=converted)changed++;recipes.add(converted);}
        if(changed>0){manager.replaceRecipes(recipes);AssemblyRecipe.invalidate(manager);System.out.println("ARMORY_CATALOG converted="+changed+" total="+recipes.size());}return changed;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void started(ServerStartedEvent event){install(event.getServer());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void sync(OnDatapackSyncEvent event){
        if(install(event.getPlayerList().getServer())==0)return;
        var packet=new ClientboundUpdateRecipesPacket(event.getPlayerList().getServer().getRecipeManager().getRecipes());
        if(event.getPlayer()!=null)event.getPlayer().connection.send(packet);else for(var player:event.getPlayerList().getPlayers())player.connection.send(packet);
    }
}
