import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import melonslise.locks.common.util.LocksUtil;
import melonslise.locks.common.item.KeyItem;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.init.LocksItems;

public final class LocksFurniturePersistence extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private int failures;
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try { verify(); } catch (Throwable t) { getLogger().log(java.util.logging.Level.SEVERE,"PERSISTENCE ERROR",t);check("unexpected exception",false); }
            try { Files.writeString(Path.of("locks-furniture-persistence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"checks",checks))); } catch (Exception e) { throw new RuntimeException(e); }
            getLogger().info("LOCKS_PERSISTENCE failures="+failures+" checks="+checks.size());
            getServer().getScheduler().runTaskLater(this, () -> getServer().shutdown(),10);
        }, 60);
    }
    private void check(String name, boolean ok) { checks.add((ok ? "PASS " : "FAIL ")+name);if(!ok) { failures++;getLogger().warning(name); } }
    private void verify() throws Exception {
        org.bukkit.World world=Objects.requireNonNull(Bukkit.getWorld("locks_test"));
        ServerLevel level=(ServerLevel)world.getClass().getMethod("getHandle").invoke(world);
        var player=FakePlayerFactory.getMinecraft(level);
        JsonObject report=JsonParser.parseString(Files.readString(Path.of("locks-furniture-smoke.json"))).getAsJsonObject();
        JsonObject items=report.getAsJsonObject("saved_items");int i=0;
        for(JsonElement entry:report.getAsJsonArray("furniture")) {
            JsonObject row=entry.getAsJsonObject();String id=row.get("id").getAsString();
            BlockPos pos=new BlockPos((i%8)*5,120,(i/8)*5);i++;
            world.setChunkForceLoaded(pos.m_123341_()>>4,pos.m_123343_()>>4,true);level.m_46745_(pos);
            check("locked after restart "+id,LocksUtil.locked(level,pos));
            var lock=LocksUtil.intersecting(level,pos).findFirst().orElseThrow();
            check("lock ID preserved "+id,lock.lock.id==1000+i);
            player.m_21008_(InteractionHand.MAIN_HAND,ItemStack.f_41583_);
            BlockHitResult hit=new BlockHitResult(new Vec3(pos.m_123341_()+0.5,pos.m_123342_()+0.5,pos.m_123343_()),Direction.NORTH,pos,false);
            var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit);MinecraftForge.EVENT_BUS.post(event);
            check("interaction denied after restart "+id,event.getUseBlock()==Event.Result.DENY);
            if(items.has(id)) check("items preserved after restart "+id,level.m_7702_(pos).serializeNBT().m_128437_("Items",10).toString().equals(items.get(id).getAsString()));
            ItemStack key=new ItemStack(LocksItems.KEY.get());key.m_41784_().m_128405_(LockingItem.KEY_ID,1000+i);player.m_21008_(InteractionHand.MAIN_HAND,key);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
            ((KeyItem)key.m_41720_()).m_6225_(context);check("original key opens after restart "+id,!LocksUtil.locked(level,pos));
            ((KeyItem)key.m_41720_()).m_6225_(context);check("original key relocks after restart "+id,LocksUtil.locked(level,pos));
        }
    }
}
