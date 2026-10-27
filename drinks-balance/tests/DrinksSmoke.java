package ua.empirecraft.drinkstest;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_drinks_test")
public final class DrinksSmoke {
    static final long MONTH=2_592_000_000L, EPOCH=1_800_000_000_000L;
    static final BlockPos POS=new BlockPos(1000,100,1000), OLD=new BlockPos(4,100,4);
    static long now=EPOCH;
    static net.minecraft.server.MinecraftServer server;
    static Class<?> years;
    static ResourceLocation id(String s){return new ResourceLocation(s);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ItemStack item(String s){return new ItemStack(ForgeRegistries.ITEMS.getValue(id(s)));}
    static int call(String name,ItemStack s)throws Exception{return (int)years.getMethod(name,ItemStack.class,net.minecraft.world.level.Level.class).invoke(null,s,server.overworld());}
    static void stamp(ItemStack s)throws Exception{years.getMethod("setWineYear",ItemStack.class,net.minecraft.world.level.Level.class).invoke(null,s,server.overworld());}
    public DrinksSmoke(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    void started(ServerStartedEvent event){
        server=event.getServer();int phase=Integer.getInteger("drinks.phase",1);
        try{
            var level=server.overworld();
            if(phase==0){
                level.setBlockAndUpdate(OLD,Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity)level.getBlockEntity(OLD)).setItem(0,item("vinery:clark_wine"));
                level.setBlockAndUpdate(OLD.east(),ForgeRegistries.BLOCKS.getValue(id("vinery:chorus_wine")).defaultBlockState());
            }else{
                years=Class.forName("net.satisfy.vinery.core.util.WineYears");
                var clock=years.getDeclaredField("clock");clock.setAccessible(true);clock.set(null,(LongSupplier)()->now);
                for(String s:List.of("vinery:clark_wine","vinery:chorus_wine")){
                    check(!ForgeRegistries.ITEMS.getKeys().contains(id(s)),"removed item "+s);
                    check(!ForgeRegistries.BLOCKS.getKeys().contains(id(s)),"removed block "+s);
                    check(server.getRecipeManager().byKey(id("vinery:wine_fermentation/"+s.split(":")[1])).isEmpty(),"removed recipe "+s);
                }
                check(((ChestBlockEntity)level.getBlockEntity(OLD)).getItem(0).is(ForgeRegistries.ITEMS.getValue(id("vinery:red_wine"))),"saved item migration");
                check(level.getBlockState(OLD.east()).is(ForgeRegistries.BLOCKS.getValue(id("vinery:red_wine"))),"saved block migration");
                CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(),true,server.registryAccess());
                if(phase==1){
                    ItemStack wine=item("vinery:red_wine");stamp(wine);
                    long bottled=wine.getTag().getLong("EmpirecraftBottledAtMs");
                    check(bottled==EPOCH,"timestamp creation");check(call("getEffectLevel",wine)==0,"fresh level I");check(call("getEffectDuration",wine)==1600,"fresh duration");
                    level.setDayTime(24_000L*1_000_000L);check(call("getEffectLevel",wine)==0,"game time cannot age wine");
                    now=EPOCH+MONTH-1;check(call("getEffectLevel",wine)==0,"before month I");
                    now=EPOCH+MONTH;check(call("getEffectLevel",wine)==1,"exact month II");check(call("getEffectDuration",wine)==14400,"12 minute cap");
                    now+=MONTH*100;check(call("getEffectLevel",wine)==1&&call("getEffectDuration",wine)==14400,"old wine capped");
                    now=EPOCH;check(call("getEffectLevel",wine)==1,"clock rollback cannot un-age wine");
                    ItemStack legacy=item("vinery:red_wine");legacy.getOrCreateTag().putInt("Year",Integer.MIN_VALUE);stamp(legacy);check(call("getEffectLevel",legacy)==0,"legacy game age reset safely");
                    stamp(legacy);check(legacy.getTag().getLong("EmpirecraftBottledAtMs")==EPOCH,"stamping idempotent");
                    ItemStack stored=item("vinery:red_wine");stamp(stored);
                    stored.getOrCreateTag().putString("CustomNameTest","kept");
                    CompoundTag share=(CompoundTag)years.getMethod("getShareTag",ItemStack.class).invoke(null,stored);
                    check(share.getString("CustomNameTest").equals("kept"),"share tag preserves custom NBT");
                    var saved=stored.save(new CompoundTag());check(ItemStack.of(saved).getTag().getLong("EmpirecraftBottledAtMs")==EPOCH,"NBT roundtrip");
                    // Exercise the actual native fermentation output rather than just the clock helper.
                    var barrelPos=new BlockPos(8,100,8);
                    level.setBlockAndUpdate(barrelPos,ForgeRegistries.BLOCKS.getValue(id("vinery:fermentation_barrel")).defaultBlockState());
                    var barrel=level.getBlockEntity(barrelPos);var type=barrel.getClass();
                    type.getMethod("setJuiceType",String.class).invoke(barrel,"red_general");
                    type.getMethod("setFluidLevel",int.class).invoke(barrel,100);
                    @SuppressWarnings("unchecked") var inventory=(java.util.List<ItemStack>)type.getMethod("getItems").invoke(barrel);
                    inventory.set(1,new ItemStack(Items.SUGAR,2));inventory.set(4,item("vinery:wine_bottle"));inventory.get(4).setCount(2);
                    var recipe=server.getRecipeManager().byKey(id("vinery:wine_fermentation/red_wine")).orElseThrow();
                    var craft=type.getDeclaredMethod("craft",recipe.getClass(),net.minecraft.core.RegistryAccess.class);craft.setAccessible(true);craft.invoke(barrel,recipe,server.registryAccess());
                    check(!inventory.get(5).isEmpty()&&inventory.get(5).getTag().getLong("EmpirecraftBottledAtMs")==EPOCH,"native bottled output has timestamp");
                    now=EPOCH+MONTH;craft.invoke(barrel,recipe,server.registryAccess());
                    check(inventory.get(5).getTag().getLong("EmpirecraftBottledAtMs")==EPOCH,"second batch cannot overwrite first bottling date");
                    var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(barrelPos).inflate(3));
                    check(drops.stream().anyMatch(e->e.getItem().hasTag()&&e.getItem().getTag().getLong("EmpirecraftBottledAtMs")==EPOCH+MONTH),"overflow vintage keeps its own bottling date");
                    now=EPOCH;
                    level.setBlockAndUpdate(POS,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(POS)).setItem(0,stored);
                    System.out.println("DRINKS_CLOCK_PASS boundary rollback legacy nbt share game-time");
                }else{
                    // No bottle inventoryTick or cache update has run while this chunk was unloaded and server stopped.
                    now=EPOCH+MONTH;
                    var stored=((ChestBlockEntity)level.getBlockEntity(POS)).getItem(0);
                    check(stored.getTag().getLong("EmpirecraftBottledAtMs")==EPOCH,"bottling timestamp survives restart");
                    check(call("getEffectLevel",stored)==1&&call("getEffectDuration",stored)==14400,"unloaded chunk ages through restart");
                    var sheep=EntityType.SHEEP.create(level);stored.finishUsingItem(level,sheep);
                    check(sheep.getActiveEffects().stream().anyMatch(e->e.getAmplifier()==1&&e.getDuration()==14400),"actual consumed effect II 12min");
                    System.out.println("DRINKS_UNLOADED_RESTART_CONSUMPTION_PASS");
                }
            }
            System.out.println("DRINKS_SMOKE_PASS phase="+phase);
            java.nio.file.Files.writeString(java.nio.file.Path.of("test-result-"+phase+".txt"),"DRINKS_SMOKE_PASS phase="+phase+"\n");
        }catch(Throwable e){System.err.println("DRINKS_SMOKE_FAIL");e.printStackTrace();
            try{var text=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(text));java.nio.file.Files.writeString(java.nio.file.Path.of("test-result-"+phase+".txt"),"DRINKS_SMOKE_FAIL\n"+text);}catch(Exception ignored){}
        }
        server.halt(false);
    }
}
