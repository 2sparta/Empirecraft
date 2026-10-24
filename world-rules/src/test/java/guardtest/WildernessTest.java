package guardtest;
import java.util.Set;
import org.bukkit.*;
import org.bukkit.plugin.PluginManager;
import com.palmergames.bukkit.towny.TownyAPI;
import net.minecraft.core.BlockPos;
import ua.empirecraft.worldrules.TownyWilderness;

public final class WildernessTest {
    public record Level(World world){public World getWorld(){return world;}}
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var gate=new TownyWilderness(Set.of("world","world_europe")::contains);
        var world=new World("world");var europe=new World("world_europe");
        var a=new Level(world);var b=new Level(europe);var pos=new BlockPos(-17,-48,-1);
        check(gate.allowed(a,pos),"wilderness allowed");
        check(TownyAPI.last.x()==-17 && TownyAPI.last.y()==-48 && TownyAPI.last.z()==-1,"exact negative ore coordinates queried");
        world.claimed=true;check(!gate.allowed(a,pos),"claim created before mutation is protected");
        check(gate.allowed(b,pos),"other world has independent claims");
        europe.claimed=true;check(!gate.allowed(b,pos),"second world claim protected");
        world.claimed=false;check(gate.allowed(a,pos),"removed claim returns to wilderness");
        check(!gate.allowed(new Level(new World("other_world")),pos),"world whitelist");
        world.managed=false;check(!gate.allowed(a,pos),"unmanaged world is not assumed safe");world.managed=true;
        Bukkit.MANAGER.plugin=null;check(!gate.allowed(a,pos),"missing Towny fails closed");
        var replacement=new PluginManager.FakePlugin();replacement.enabled=false;Bukkit.MANAGER.plugin=replacement;
        check(!gate.allowed(a,pos),"disabled Towny fails closed");replacement.enabled=true;
        check(gate.allowed(a,pos),"replaced plugin recovers");
        TownyAPI.instance=null;check(!gate.allowed(a,pos),"API not ready fails closed");TownyAPI.instance=new TownyAPI();
        TownyAPI.broken=true;check(!gate.allowed(a,pos),"API exception fails closed");TownyAPI.broken=false;
        check(gate.allowed(a,pos),"API recovers");
        Bukkit.primary=false;check(!gate.allowed(a,pos),"off-thread world mutations denied");Bukkit.primary=true;
        System.out.println("PASS production Towny wilderness reflection: "+checks+" assertions");
    }
}
