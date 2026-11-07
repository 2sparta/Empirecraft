package ua.empirecraft.tradertest;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.trading.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.worldrules.TradePolicy;
import ua.empirecraft.worldrules.TradeStack;
import ua.empirecraft.worldrules.mixin.UseItemGoalAccess;

/** Only installed in disposable native test servers, never in the release. */
@Mod("empirecraft_trader_test")
public final class NativeTrades {
    private final List<String> checks=new ArrayList<>();
    private final List<Map<String,Object>> listingAudit=new ArrayList<>();
    private final List<String> skipped=new ArrayList<>();
    private MinecraftServer server;
    private ServerLevel level;
    private WanderingTrader trader;
    private int ticks,generated,rejected;
    private boolean finished;
    private static final int PHASE=Integer.getInteger("empirecraft.trader.phase",1);
    public NativeTrades() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::started); MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void check(boolean value,String text) { if(!value)throw new AssertionError(text);checks.add(text); }
    private void started(ServerStartedEvent event) {
        try {
            server=event.getServer();level=server.overworld();level.setChunkForced(0,0,true);level.getChunkAt(new BlockPos(0,4,0));
            level.setDayTime(14000);level.setWeatherParameters(0,0,false,false);
            var edit=Class.forName("ua.empirecraft.calendar.CalendarEdit");
            Object midnight=edit.getConstructor(Integer.class,Integer.class,Long.class,Integer.class).newInstance(null,null,null,14000);
            Class.forName("ua.empirecraft.calendar.WallClockCalendar").getMethod("edit",ServerLevel.class,edit).invoke(null,level,midnight);
            trader=EntityType.WANDERING_TRADER.create(level);trader.moveTo(0.5,4,0.5,0,0);trader.setPersistenceRequired();level.addFreshEntity(trader);
            check(trader.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof UseItemGoal<?> &&
                    ((UseItemGoalAccess)g.getGoal()).empirecraft$useItem().getItem() instanceof PotionItem),"Night potion goal is absent on a real trader");
            check(trader.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof UseItemGoal<?> &&
                    ((UseItemGoalAccess)g.getGoal()).empirecraft$useItem().is(Items.MILK_BUCKET)),"Daytime milk goal is retained");
            core();
            if(PHASE==1)audit(); else restore();
        }catch(Throwable error){fail(error);}
    }
    private ItemStack item(String id) { return new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id))); }
    private MerchantOffer sale(ItemStack result) { return new MerchantOffer(new ItemStack(Items.EMERALD,2),result,7,3,.05f); }
    private void core() throws Exception {
        var steel=item("overgeared:steel_arrow");steel.getOrCreateTag().putString("Quality","EXPERT");
        var name=steel.getHoverName().getContents();
        check(name instanceof TranslatableContents text && text.getKey().equals("item.overgeared.steel_arrow"),"Quality NBT keeps the plain steel arrow name");
        for(String material:List.of("iron","steel")) {
            var arrow=item("overgeared:"+material+"_arrow");arrow.getOrCreateTag().putInt("CustomMarker",5);
            check(arrow.getHoverName().getContents() instanceof TranslatableContents text && !text.getKey().endsWith("no_effect"),"Unrelated NBT is not an uncraftable "+material+" arrow");
            check(TradePolicy.valid(sale(arrow)),"Ordinary "+material+" arrow offer remains available");
            var forbidden=item("overgeared:"+material+"_arrow");var data=new CompoundTag();data.putString("Potion","minecraft:poison");forbidden.setTag(data);
            check(!TradePolicy.valid(sale(forbidden)),"Potion "+material+" arrow offer is rejected before migration hides its origin");
        }
        var water=PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER);
        check(TradePolicy.valid(sale(water)),"Real water-bottle trades remain valid");
        var invisible=PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.INVISIBILITY);
        check(!TradePolicy.valid(sale(invisible)),"Invisibility potion is not sold as a disguised water bottle");
        check(((TradeStack)(Object)invisible).empirecraft$retiredTradeItem(),"Origin marker is present after potion cleanup");
        check(((TradeStack)(Object)invisible.copy()).empirecraft$retiredTradeItem(),"Copies retain the retired origin marker");
        check(!TradePolicy.valid(sale(new ItemStack(Items.TIPPED_ARROW))),"Vanilla tipped-arrow templates are rejected");
        check(!TradePolicy.valid(sale(new ItemStack(Items.ENCHANTED_BOOK))),"Enchanted books cannot turn into ordinary book offers");
        check(!TradePolicy.valid(sale(new ItemStack(Items.SPLASH_POTION))),"Splash potions cannot turn into empty-bottle offers");
        check(!TradePolicy.valid(sale(new ItemStack(Items.LINGERING_POTION))),"Lingering potion offers are rejected");
        check(!TradePolicy.valid(new MerchantOffer(new ItemStack(Items.TIPPED_ARROW),new ItemStack(Items.EMERALD),4,2,.05f)),"Retired primary trade costs are rejected");
        check(!TradePolicy.valid(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.SPLASH_POTION),new ItemStack(Items.BREAD),4,2,.05f)),"Retired secondary trade costs are rejected");
        check(!TradePolicy.valid(sale(item("overgeared:steel_helmet"))),"Retired ordinary steel armor is rejected");
        var custom=item("vinery:red_wine");
        if(!custom.isEmpty()) {
            custom.getOrCreateTag().putInt("WineAge",10);custom.getOrCreateTag().put("CustomPotionEffects",new ListTag());
            check(TradePolicy.valid(sale(custom)),"Wine age and mod drink effects are preserved");
        }
        check(!TradePolicy.valid(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BOOK),new ItemStack(Items.BOOK),4,2,.05f)),"Already-migrated book-for-the-same-book offer is rejected");
        check(!TradePolicy.valid(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.ARROW,5),new ItemStack(Items.ARROW,5),4,2,.05f)),"Already-migrated tipped-arrow round trip is rejected");
        check(TradePolicy.valid(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.ARROW),new ItemStack(Items.ARROW,8),4,2,.05f)),"A real bulk exchange is retained");
        var old=new CompoundTag();trader.saveWithoutId(old);
        var hands=new ListTag();hands.add(water.save(new CompoundTag()));hands.add(new CompoundTag());old.put("HandItems",hands);
        var restored=EntityType.WANDERING_TRADER.create(level);restored.load(old);
        check(restored.getMainHandItem().isEmpty() && !restored.isUsingItem(),"A trader saved holding its old night bottle loads with an empty hand");
        var persisted=savedOffers();var actor=EntityType.WANDERING_TRADER.create(level);var entity=new CompoundTag();actor.saveWithoutId(entity);entity.put("Offers",persisted);actor.load(entity);
        var offers=actor.getOffers();check(offers.size()==1,"Existing saved trades lose retired templates without deleting the legitimate offer");
        var valid=offers.get(0);check(valid.getUses()==2 && valid.getDemand()==3 && valid.getSpecialPriceDiff()==-1 && valid.getMaxUses()==7,"Uses, demand, discount and stock of retained offers are unchanged");
        if(PHASE==1) {
            var data=new CompoundTag();data.put("Offers",offers.createTag());NbtIo.writeCompressed(data,Path.of("trader-retained.dat").toFile());
        }
    }
    private CompoundTag stack(String id,int count) {var tag=new CompoundTag();tag.putString("id",id);tag.putByte("Count",(byte)count);return tag;}
    private CompoundTag savedOffers() {
        var list=new ListTag();
        var good=sale(item("overgeared:steel_arrow")).createTag();good.putInt("uses",2);good.putInt("demand",3);good.putInt("specialPrice",-1);list.add(good);
        for(String id:List.of("minecraft:enchanted_book","minecraft:tipped_arrow","minecraft:splash_potion","minecraft:lingering_potion")) {
            var offer=good.copy();offer.put("sell",stack(id,1));list.add(offer);
        }
        var invisible=good.copy();var potion=stack("minecraft:potion",1);var tag=new CompoundTag();tag.putString("Potion","minecraft:invisibility");potion.put("tag",tag);invisible.put("sell",potion);list.add(invisible);
        var retired=good.copy();retired.put("sell",stack("overgeared:steel_helmet",1));list.add(retired);
        var result=new CompoundTag();result.put("Recipes",list);return result;
    }
    private void restore() throws Exception {
        var saved=NbtIo.readCompressed(Path.of("trader-retained.dat").toFile());var actor=EntityType.WANDERING_TRADER.create(level);var data=new CompoundTag();actor.saveWithoutId(data);data.put("Offers",saved.getCompound("Offers"));actor.load(data);
        check(actor.getOffers().size()==1 && actor.getOffers().get(0).getUses()==2,"The legitimate offer and its stock survive a real server restart");
    }
    private void audit() {
        int ordinal=0;
        for(var profession:VillagerTrades.TRADES.entrySet()) {
            var villager=EntityType.VILLAGER.create(level);villager.moveTo(0.5,4,0.5,0,0);
            for(var levelEntry:profession.getValue().int2ObjectEntrySet()) {
                villager.setVillagerData(villager.getVillagerData().setProfession(profession.getKey()).setLevel(levelEntry.getIntKey()));
                for(var listing:levelEntry.getValue())probe(listing,villager,"villager:"+ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession.getKey())+":"+levelEntry.getIntKey(),++ordinal);
            }
        }
        for(var entry:VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet())for(var listing:entry.getValue())probe(listing,trader,"wanderer:"+entry.getIntKey(),++ordinal);
        check(generated>100,"Trade audit exercises the installed trade providers");
        check(rejected>0,"Trade audit actually finds retired or malformed templates");
        // Exercise the actual patched selection path, including the trader's direct rare-list call.
        for(int i=0;i<24;i++) {
            var actor=EntityType.WANDERING_TRADER.create(level);
            check(actor.getOffers().stream().allMatch(TradePolicy::valid),"Generated wandering stock is safe "+i);
        }
    }
    private void probe(VillagerTrades.ItemListing listing,Entity actor,String pool,int seed) {
        String name=listing.getClass().getName();
        if(mapProvider(listing,Collections.newSetFromMap(new IdentityHashMap<>()))) {skipped.add(pool+" "+name+" (map generation avoided)");return;}
        int allowed=0,blocked=0;var rejectedViews=new TreeSet<String>();
        for(int repeat=0;repeat<4;repeat++) {
            MerchantOffer value=listing.getOffer(actor,RandomSource.create(seed*1009L+repeat));generated++;
            if(value==null)continue;
            if(TradePolicy.valid(value))allowed++;else {
                blocked++;rejected++;
                rejectedViews.add(value.getBaseCostA()+" + "+value.getCostB()+" -> "+value.getResult());
            }
        }
        listingAudit.add(Map.of("pool",pool,"provider",name,"allowed",allowed,"retired_or_invalid",blocked,"rejected_views_after_migration",rejectedViews));
    }
    private boolean mapProvider(VillagerTrades.ItemListing listing,Set<Object> seen) {
        if(!seen.add(listing))return false;
        if(listing.getClass().getName().toLowerCase(Locale.ROOT).matches(".*(treasure|explorer|mapfor|maptrade).*"))return true;
        for(var field:listing.getClass().getDeclaredFields())if(VillagerTrades.ItemListing.class.isAssignableFrom(field.getType())) {
            try {field.setAccessible(true);var child=(VillagerTrades.ItemListing)field.get(listing);if(child!=null && mapProvider(child,seen))return true;}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot inspect trade wrapper",error);}
        }
        return false;
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished || event.phase!=TickEvent.Phase.END || trader==null)return;
        try {
            check(level.isNight(),"Real-time calendar keeps the test at night "+ticks);
            check(!trader.isUsingItem() && !(trader.getMainHandItem().getItem() instanceof PotionItem),"No night drinking at tick "+ticks);
            if(++ticks==180)finish(true);
        }catch(Throwable error){fail(error);}
    }
    private void fail(Throwable error) {error.printStackTrace();System.err.println("TRADER_NATIVE_FAIL phase="+PHASE);finish(false);}
    private void finish(boolean passed) {
        if(finished)return;finished=true;
        try {Files.writeString(Path.of("trader-phase-"+PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("passed",passed,"phase",PHASE,"checks",checks,"generated_offers",generated,"rejected_offers",rejected,"provider_audit",listingAudit,"skipped_map_generation",skipped)));}
        catch(Exception error){throw new RuntimeException(error);}
        System.out.println("TRADER_NATIVE_"+(passed?"PASS":"FAIL")+" phase="+PHASE+" checks="+checks.size()+" generated="+generated+" rejected="+rejected);
        if(server!=null)server.halt(false);
    }
}
