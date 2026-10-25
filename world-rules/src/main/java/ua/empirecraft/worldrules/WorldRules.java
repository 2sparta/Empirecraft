package ua.empirecraft.worldrules;

import java.util.logging.Logger;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_world_rules")
public final class WorldRules {
    public static final Logger LOG=Logger.getLogger("EmpireCraftWorldRules");
    private final NaturalRenewal renewal=new NaturalRenewal();
    private long ticks;
    public WorldRules() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,RulesConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::beforeStart);
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::interact);
        MinecraftForge.EVENT_BUS.addListener(this::place);
        MinecraftForge.EVENT_BUS.addListener(this::command);
        MinecraftForge.EVENT_BUS.addListener(SeedMarket::trades);
        MinecraftForge.EVENT_BUS.addListener(this::villagerTrades);
    }
    private void beforeStart(ServerAboutToStartEvent event) { ItemPolicy.initialize();RulesState.active=true; }
    private void started(ServerStartedEvent event) { ticks=0;renewal.initialize(); }
    private void stopped(ServerStoppedEvent event) { RulesState.active=false; }
    private void villagerTrades(VillagerTradesEvent event) {
        // This vanilla nested class is package-private. Its binary name is unchanged in SRG.
        for(var offers:event.getTrades().values()) offers.removeIf(offer -> offer.getClass().getName().equals(
            "net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds"));
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !RulesState.active || ++ticks%RulesConfig.INTERVAL.get()!=0)return;
        for(var level:event.getServer().m_129785_())renewal.sample(level);
    }
    private void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getLevel().m_5776_())return;
        var id=ForgeRegistries.BLOCKS.getKey(event.getLevel().m_8055_(event.getPos()).m_60734_());
        var held=ForgeRegistries.ITEMS.getKey(event.getItemStack().m_41720_());
        if(ContentPolicy.createDoor(id==null?null:id.toString()) || ContentPolicy.createDoor(held==null?null:held.toString())) {
            event.setCanceled(true);
            return;
        }
        if(id!=null && (id.toString().equals("minecraft:enchanting_table") || id.toString().equals("minecraft:brewing_stand"))) {
            event.setCanceled(true);
            event.getEntity().m_5661_(Component.m_237113_("Зачарування й зіллеваріння вимкнені. Використовуйте вино, пиво та звичайне ремесло."),true);
        }
    }
    private void place(BlockEvent.EntityPlaceEvent event) {
        var id=ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().m_60734_());
        if(ContentPolicy.createDoor(id==null?null:id.toString()))event.setCanceled(true);
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for(var snapshot:multi.getReplacedBlockSnapshots()) {
                var block=ForgeRegistries.BLOCKS.getKey(snapshot.getCurrentBlock().m_60734_());
                if(ContentPolicy.createDoor(block==null?null:block.toString()))event.setCanceled(true);
            }
        }
    }
    private void command(CommandEvent event) {
        var context=event.getParseResults().getContext();
        if(context.getNodes().isEmpty())return;
        String root=context.getNodes().get(0).getNode().getName();
        if(root.equals("enchant") || root.equals("minecraft:enchant")) {
            event.setCanceled(true);
            context.getSource().m_81352_(Component.m_237113_("Зачарування на сервері вимкнені."));
        }
    }
}

