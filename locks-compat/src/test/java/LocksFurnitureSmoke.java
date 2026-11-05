import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.GsonBuilder;
import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.item.LockItem;
import melonslise.locks.common.item.KeyItem;
import melonslise.locks.common.item.LockingItem;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;

public class LocksFurnitureSmoke extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String,Object>> furniture = new ArrayList<>();
    private final List<BlockPos> sources = new ArrayList<>();
    private final List<String> sourceIds = new ArrayList<>();
    private final Map<BlockPos,String> savedItems = new HashMap<>();
    private final Map<BlockPos,IItemHandler> cachedHandlers = new HashMap<>();
    private ServerLevel level;
    private FakePlayer player;
    private ILockableHandler handler;
    private int failures;
    private final String[] cases = {
        "minecraft:chest", "minecraft:barrel", "minecraft:hopper",
        "handcrafted:oak_cupboard", "handcrafted:oak_drawer", "handcrafted:oak_counter",
        "handcrafted:oak_nightstand", "handcrafted:oak_chair", "timber_joinery:oak_cabinet",
        "timber_joinery:oak_nightstand", "timber_joinery:oak_shutter",
        "farmersdelight:oak_cabinet", "farmersdelight:bamboo_cabinet", "farmersdelight:basket",
        "vinery:dark_cherry_cabinet", "vinery:dark_cherry_drawer", "vinery:dark_cherry_shelf",
        "vinery:oak_wine_rack_big", "vinery:oak_wine_rack_small", "vinery:basket", "vinery:storage_pot",
        "meadow:shelf", "meadow:cheese_rack", "meadow:chair", "meadow:shutter_block",
        "supplementaries:sack", "supplementaries:safe", "supplementaries:item_shelf",
        "supplementaries:lunch_basket", "supplementaries:urn", "supplementaries:jar", "supplementaries:present",
        "dawnoftimebuilder:waxed_oak_chair", "dawnoftimebuilder:waxed_oak_table", "dawnoftimebuilder:waxed_oak_shutters",
        "decorative_blocks:oak_seat", "medieval_deco:barrel_mod", "medieval_deco:barrel_water",
        "chipped:carpenters_table", "etched:etching_table", "etched:radio"
    };
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try { runCases(); } catch (Throwable t) { getLogger().log(java.util.logging.Level.SEVERE,"SMOKE ERROR", t); check("uncaught " + t, false); finish(); }
        }, 30);
    }
    private void check(String text, boolean ok) { checks.add((ok ? "PASS " : "FAIL ") + text); if (!ok) { failures++; getLogger().warning(text); } }
    private UseOnContext context(BlockPos pos, ItemStack item) {
        player.m_21008_(InteractionHand.MAIN_HAND, item);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(pos.m_123341_()+0.5,pos.m_123342_()+0.5,pos.m_123343_()),Direction.NORTH,pos,false));
    }
    private ItemStack lock(BlockPos pos, int id) {
        ItemStack item = new ItemStack(LocksItems.STEEL_LOCK.get());
        item.m_41784_().m_128405_(LockingItem.KEY_ID,id);
        InteractionResult result = ((LockItem)item.m_41720_()).m_6225_(context(pos,item));
        check("lock placed " + pos, result == InteractionResult.SUCCESS && LocksUtil.locked(level,pos) && item.m_41619_());
        return item;
    }
    private void unlock(BlockPos pos, int id) {
        ItemStack key = new ItemStack(LocksItems.KEY.get());
        key.m_41784_().m_128405_(LockingItem.KEY_ID,id);
        ((KeyItem)key.m_41720_()).m_6225_(context(pos,key));
    }
    private Event.Result rightClick(BlockPos pos, InteractionHand hand) {
        player.m_21008_(hand,ItemStack.f_41583_);
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player,hand,pos,new BlockHitResult(new Vec3(pos.m_123341_()+0.5,pos.m_123342_()+0.5,pos.m_123343_()),Direction.NORTH,pos,false));
        MinecraftForge.EVENT_BUS.post(event);
        return event.getUseBlock();
    }
    private void runCases() throws Exception {
        Object world = Bukkit.getWorld("locks_test");
        level = (ServerLevel)world.getClass().getMethod("getHandle").invoke(world);
        player = FakePlayerFactory.getMinecraft(level);
        handler = level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElseThrow(IllegalStateException::new);
        List<String> eligible = new ArrayList<>();
        for (ResourceLocation id : ForgeRegistries.BLOCKS.getKeys()) {
            if (Arrays.stream(LocksServerConfig.lockableBlocks).anyMatch(p -> p.matcher(id.toString()).matches())) eligible.add(id.toString());
        }
        Collections.sort(eligible); Files.writeString(Path.of("eligible-furniture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(eligible));
        int n = 0;
        for (String id : cases) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block == null || block == Blocks.f_50016_) { check("registered " + id,false); continue; }
            BlockPos pos = new BlockPos((n%8)*5,120,(n/8)*5); ++n;
            ((org.bukkit.World)world).setChunkForceLoaded(pos.m_123341_() >> 4,pos.m_123343_() >> 4,true);
            for (var previous : LocksUtil.intersecting(level,pos).toList()) handler.remove(previous.id);
            if (level.m_7702_(pos) instanceof Container old) old.m_6211_();
            if (level.m_7702_(pos.m_7495_()) instanceof Container old) old.m_6211_();
            level.m_7731_(pos,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:air")).m_49966_(),3);
            level.m_7731_(pos.m_7495_(),ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:air")).m_49966_(),3);
            level.m_7731_(pos.m_7495_(),ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).m_49966_(),3);
            level.m_7731_(pos,block.m_49966_(),3);
            check("eligible " + id,LocksServerConfig.canLock(level,pos));
            Map<String,Object> record = new LinkedHashMap<>(); record.put("id",id);record.put("class",block.getClass().getName());
            BlockEntity be = level.m_7702_(pos);record.put("block_entity",be == null ? null : be.getClass().getName());furniture.add(record);
            if (be instanceof Container c && c.m_6643_() > 0) {
                c.m_6836_(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:diamond")),8));
                savedItems.put(pos,be.serializeNBT().m_128437_("Items",10).toString());
                be.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).ifPresent(h -> cachedHandlers.put(pos,h));
                sources.add(pos);sourceIds.add(id);
                level.m_7731_(pos.m_7495_(),ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:hopper")).m_49966_(),3);
            }
            int keyId = 1000 + n;
            lock(pos,keyId);
            check("right click denied " + id,rightClick(pos,InteractionHand.MAIN_HAND) == Event.Result.DENY);
            check("offhand denied " + id,rightClick(pos,InteractionHand.OFF_HAND) == Event.Result.DENY);
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level,pos,level.m_8055_(pos),player);MinecraftForge.EVENT_BUS.post(event);
            check("break denied " + id,event.isCanceled());
            unlock(pos,keyId+10000);check("wrong key rejected " + id,LocksUtil.locked(level,pos));
            unlock(pos,keyId);check("key unlocks " + id,!LocksUtil.locked(level,pos));
            check("right click allowed " + id,rightClick(pos,InteractionHand.MAIN_HAND) != Event.Result.DENY);
            unlock(pos,keyId);check("key relocks " + id,LocksUtil.locked(level,pos));
        }
        for (org.bukkit.entity.Entity entity : ((org.bukkit.World)world).getEntities()) if (entity instanceof org.bukkit.entity.Item) entity.remove();
        check("stone remains excluded", !Arrays.stream(LocksServerConfig.lockableBlocks).anyMatch(p -> p.matcher("minecraft:stone").matches()));
        getServer().getScheduler().runTaskLater(this, () -> { try { verifyHoppers(); } catch (Throwable t) { getLogger().log(java.util.logging.Level.SEVERE,"HOPPER ERROR",t);check("hopper exception",false);finish(); } }, 80);
    }
    private void verifyHoppers() {
        for (int i=0;i<sources.size();i++) {
            BlockPos pos=sources.get(i);String id=sourceIds.get(i);BlockEntity be=level.m_7702_(pos);BlockEntity hopper=level.m_7702_(pos.m_7495_());
            if (hopper instanceof Container c) check("hopper cannot extract " + id,c.m_7983_());
            IItemHandler cached = cachedHandlers.get(pos);
            if (cached != null && cached.getSlots() > 0) {
                ItemStack insert = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:stone")),1);
                check("cached handler simulated insert denied " + id,cached.insertItem(0,insert,true).m_41613_()==1);
                check("cached handler insert denied " + id,cached.insertItem(0,insert,false).m_41613_()==1);
                check("cached handler extraction denied " + id,cached.extractItem(0,1,false).m_41619_());
                check("cached handler simulated extraction denied " + id,cached.extractItem(0,1,true).m_41619_());
                if (cached instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(0,insert);
            }
            IItemHandlerModifiable direct = new InvWrapper((Container)be);
            check("direct wrapper insert denied " + id,direct.insertItem(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:stone")),1),false).m_41613_()==1);
            check("direct wrapper extract denied " + id,direct.extractItem(0,1,false).m_41619_());
            direct.setStackInSlot(0,ItemStack.f_41583_);
            CompoundTag saved = be.serializeNBT();check("stored items retained " + id,saved.m_128437_("Items",10).toString().equals(savedItems.get(pos)));
        }
        if (sources.size() >= 2) {
            IItemHandler combined = new InvWrapper(new CompoundContainer((Container)level.m_7702_(sources.get(0)),(Container)level.m_7702_(sources.get(1))));
            check("compound inventory blocks cached insert",combined.insertItem(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:stone")),1),false).m_41613_()==1);
            check("compound inventory blocks cached extract",combined.extractItem(0,1,false).m_41619_());
        }
        SimpleContainer unlocked = new SimpleContainer(1);
        IItemHandler open = new InvWrapper(unlocked);
        check("unrelated inventory insert works",open.insertItem(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:stone")),1),false).m_41619_());
        check("unrelated inventory extract works",open.extractItem(0,1,false).m_41613_()==1);
        BlockPos chest=sources.get(0);unlock(chest,1001);
        IItemHandler retained=cachedHandlers.get(chest);ItemStack withdrawn=retained.extractItem(0,1,false);
        check("cached access resumes after unlocking",withdrawn.m_41613_()==1);
        check("cached insertion resumes after unlocking",retained.insertItem(0,withdrawn,false).m_41619_());
        unlock(chest,1001);
        finish();
    }
    private void finish() {
        try {
            Map<String,Object> report = new LinkedHashMap<>(); report.put("failures",failures);report.put("checks",checks);report.put("furniture",furniture);
            Map<String,String> stored = new TreeMap<>();for(int i=0;i<sources.size();i++) stored.put(sourceIds.get(i),savedItems.get(sources.get(i)));report.put("saved_items",stored);
            Files.writeString(Path.of("locks-furniture-smoke.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception e) { getLogger().severe(e.toString()); }
        getLogger().info("LOCKS_FURNITURE_SMOKE failures="+failures+" checks="+checks.size());
        getServer().getScheduler().runTaskLater(this, () -> getServer().shutdown(),10);
    }
}
