package ua.empirecraft.survivaltest;
import com.mojang.authlib.GameProfile;
import com.google.gson.GsonBuilder;
import java.nio.file.*;import java.util.*;
import net.minecraft.core.*;import net.minecraft.nbt.*;import net.minecraft.network.*;import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;import net.minecraft.world.entity.*;import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;import net.minecraft.world.item.*;import net.minecraft.world.item.crafting.*;import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;import net.minecraftforge.event.server.ServerStartedEvent;import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.health.HealthProvider;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.bodydamage.BodyDamageProvider;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.registry.AttributeRegistry;
import sfiomn.legendarysurvivaloverhaul.common.containers.SewingTableContainer;
import ua.empirecraft.armor.*;
import ua.empirecraft.survival.*;
@Mod("empirecraft_survival_probe")
public final class SurvivalProbe {
    private int checks,ticks;private ServerLevel level;private net.minecraft.server.MinecraftServer server;
    private void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    public SurvivalProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private ServerPlayer player(String name) throws Exception {
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),name));
        var channel=new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
            @Override public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){io.netty.util.ReferenceCountUtil.release(message);promise.trySuccess();}
            @Override public void flush(io.netty.channel.ChannelHandlerContext context){}
        });
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=channel;
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        player.connection=new ServerGamePacketListenerImpl(server,connection,player);
        var spawnImmunity=ServerPlayer.class.getDeclaredField("f_8921_");spawnImmunity.setAccessible(true);spawnImmunity.setInt(player,0);
        player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.moveTo(0,90,0);level.addNewPlayer(player);
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            field.setAccessible(true);
            if(field.getType()==List.class)try {((List)field.get(server.getPlayerList())).add(player);break;}catch(UnsupportedOperationException ignored){}
        }
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())&&field.getGenericType().getTypeName().contains("java.util.UUID")&&field.getGenericType().getTypeName().contains("ServerPlayer")) {
            field.setAccessible(true);((Map)field.get(server.getPlayerList())).put(player.getUUID(),player);
        }
        return player;
    }

    private void start(ServerStartedEvent event){server=event.getServer();level=server.overworld();}
    private ItemStack item(String id){return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));}
    private void near(double expected,double actual,String text){check(Math.abs(expected-actual)<0.0001,text+" expected="+expected+" actual="+actual);}
    private void health(ServerPlayer p) {
        p.setHealth(20);p.invulnerableTime=0;p.hurt(level.damageSources().generic(),4);near(16,p.getHealth(),"native damage uses vanilla hearts");
        p.heal(2);near(18,p.getHealth(),"ordinary vanilla healing remains available");
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.healthOverhaulEnabled=true;
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.localizedBodyDamageEnabled=true;
        p.setHealth(20);p.invulnerableTime=0;p.hurt(level.damageSources().generic(),4);near(16,p.getHealth(),"enabling stale flags cannot restore the removed health system");
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.bakeCommon();
        check(!sfiomn.legendarysurvivaloverhaul.config.Config.Baked.healthOverhaulEnabled&&!sfiomn.legendarysurvivaloverhaul.config.Config.Baked.localizedBodyDamageEnabled,"config bake locks disabled systems");
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,server);
        sfiomn.legendarysurvivaloverhaul.common.events.CommonForgeEvents.onLevelLoad(new net.minecraftforge.event.level.LevelEvent.Load(level));
        check(!level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),"an administrator's vanilla regeneration rule is preserved");
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true,server);p.setHealth(20);
    }
    private void packets() throws Exception {
        Object channel=sfiomn.legendarysurvivaloverhaul.network.NetworkHandler.INSTANCE;int types=0;
        for(var field:channel.getClass().getDeclaredFields())if(field.getType().getSimpleName().equals("IndexedMessageCodec")){
            field.setAccessible(true);Object codec=field.get(channel);var typeField=codec.getClass().getDeclaredField("types");typeField.setAccessible(true);
            for(Object value:((Map<?,?>)typeField.get(codec)).keySet()){
                String name=((Class<?>)value).getSimpleName();types++;check(!name.contains("Body")&&!name.contains("Heart"),"health/limb packets are not registered: "+name);
            }
        }
        check(types>=10,"temperature, wetness and thirst retain their network messages");
        for(String command:List.of("healthoverhaul","bodydamage"))check(server.getCommands().getDispatcher().getRoot().getChild(command)==null,"removed command absent: "+command);
    }
    private void sewing(ServerPlayer p) {
        var previews=new ArrayList<sfiomn.legendarysurvivaloverhaul.common.recipe.SewingRecipe>();SurvivalRules.jeiSewing(Items.IRON_CHESTPLATE,previews);
        check(previews.isEmpty(),"JEI does not advertise lining on plate armor");SurvivalRules.jeiSewing(Items.LEATHER_CHESTPLATE,previews);check(previews.size()==3,"JEI offers the three physical linings on leather");
        ItemCombinerMenu menu=new SewingTableContainer(91,p.getInventory(),ContainerLevelAccess.NULL);
        var cloth=new ItemStack(Items.LEATHER_CHESTPLATE);cloth.setDamageValue(12);cloth.getOrCreateTag().putString("QualityProof","preserved");
        menu.getSlot(0).set(cloth);menu.getSlot(1).set(item(SurvivalRules.ID+":heating_coat_1"));menu.createResult();
        var sewn=menu.getSlot(2).getItem();check(!sewn.isEmpty()&&TemperatureUtil.getArmorCoatTag(sewn).equals("heating1"),"real sewing menu produces a wool-lined leather piece");
        check(sewn.getDamageValue()==12&&sewn.getTag().getString("QualityProof").equals("preserved"),"sewing retains item wear and arbitrary quality NBT");
        p.containerMenu=menu;menu.setCarried(ItemStack.EMPTY);menu.clicked(2,0,ClickType.PICKUP,p);
        check(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&!menu.getCarried().isEmpty(),"taking the result consumes exactly the armor and one lining");
        menu.setCarried(ItemStack.EMPTY);menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));menu.getSlot(1).set(item(SurvivalRules.ID+":heating_coat_1"));menu.createResult();
        check(menu.getSlot(2).getItem().isEmpty(),"plate cannot be sewn in the real menu");
        menu.clicked(2,0,ClickType.PICKUP,p);check(menu.getSlot(0).hasItem()&&menu.getSlot(1).hasItem(),"an invalid sewing attempt consumes neither input");
        p.containerMenu=p.inventoryMenu;
    }
    private void layers(ServerPlayer p) {
        var cloth=new ItemStack(Items.LEATHER_CHESTPLATE);TemperatureUtil.setArmorCoatTag(cloth,"heating1");
        var parts=List.of(cloth,new ItemStack(Items.CHAINMAIL_CHESTPLATE),new ItemStack(Items.IRON_CHESTPLATE));double expected=0;
        for(var piece:parts)for(var modifier:piece.getAttributeModifiers(EquipmentSlot.CHEST).get(AttributeRegistry.COLD_RESISTANCE.get()))expected+=modifier.getAmount();
        var all=LayerData.wrap(parts);p.setItemSlot(EquipmentSlot.CHEST,all);p.doTick();
        near(expected,p.getAttributeValue(AttributeRegistry.COLD_RESISTANCE.get()),"inner lining contributes exactly once through the existing layered armor attributes");
        check(expected>=3.5,"lining and the underlying leather both insulate");
        for(var slot:LayerData.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);
        int bare=0;for(int i=0;i<100;i++)bare+=SurvivalRules.rainGain(1,p);check(bare==100,"unlined clothing does not reduce rain");
        for(var armor:List.of(Items.LEATHER_HELMET,Items.LEATHER_CHESTPLATE,Items.LEATHER_LEGGINGS,Items.LEATHER_BOOTS)){
            var stack=new ItemStack(armor);TemperatureUtil.setArmorCoatTag(stack,"thermal1");p.setItemSlot(((ArmorItem)armor).getEquipmentSlot(),stack);
        }
        p.getPersistentData().remove("EmpirecraftRainRemainder");int waxed=0;for(int i=0;i<100;i++)waxed+=SurvivalRules.rainGain(1,p);
        check(waxed==35,"full waxed coverage reduces rain wetting by sixty-five percent without immunity");
        var wet=CapabilityUtil.getWetnessCapability(p);wet.setWetness(0);wet.addWetness(100);check(wet.getWetness()==100,"waxed clothing does not block ordinary wetness from immersion");
        for(var slot:LayerData.SLOTS)p.setItemSlot(slot,ItemStack.EMPTY);
    }
    private void drink(ServerPlayer p,String id,int hydration) {
        var stack=item(id);check(!stack.isEmpty(),"drink exists: "+id);p.getInventory().clearContent();
        var cap=CapabilityUtil.getThirstCapability(p);cap.setHydrationLevel(3);cap.setSaturation(0);
        var original=stack.copy();var remainder=stack.finishUsingItem(level,p);
        net.minecraftforge.event.ForgeEventFactory.onItemUseFinish(p,original,32,remainder);
        near(3+hydration,cap.getHydrationLevel(),"real consumption hydration: "+id);
        int containers=remainder.isEmpty()?0:remainder.getCount();for(int i=0;i<p.getInventory().getContainerSize();i++)containers+=p.getInventory().getItem(i).getCount();
        check(containers==1,"native consumption returns exactly one container: "+id+" count="+containers);
    }
    private void bottles(ServerPlayer p) {
        p.getInventory().clearContent();p.moveTo(0.5,6,0.5);p.setYRot(0);p.setXRot(0);
        var water=new net.minecraft.core.BlockPos(0,7,3);level.setBlockAndUpdate(water,net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,3));
        var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(p,InteractionHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(event);
        check(event.isCanceled(),"water filling claims the interaction before the removed vanilla potion can be created");
        check(p.getMainHandItem().getCount()==2&&p.getInventory().countItem(SurvivalRules.RAW_WATER.get())==1,"water filling consumes one bottle and returns one untreated-water item");
        check(level.getFluidState(water).is(net.minecraft.tags.FluidTags.WATER),"bottle filling leaves the source water intact");
        p.getInventory().clearContent();
    }
    private void recipes() {
        for(String name:List.of("heating_coat_1","cooling_coat_1","thermal_coat_1","canteen","large_canteen","sewing_table","boiled_water","remove_coat"))
            check(level.getRecipeManager().byKey(new ResourceLocation(SurvivalRules.ID,name)).isPresent(),"obtainable native recipe: "+name);
        var recipe=level.getRecipeManager().byKey(new ResourceLocation(SurvivalRules.ID,"boiled_water")).orElseThrow();
        var source=new SimpleContainer(item(SurvivalRules.ID+":raw_water_bottle"));
        check(((Recipe)recipe).matches(source,level),"ordinary furnace accepts untreated water");
        var result=((Recipe)recipe).assemble(source,level.registryAccess());check(result.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation(SurvivalRules.ID,"purified_water_bottle"))),"boiling yields a real clean-water item");
        var canteen=item(SurvivalRules.ID+":canteen");ThirstUtil.setCapacityTag(canteen,3);ThirstUtil.setHydrationEnumTag(canteen,sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum.NORMAL);canteen.getOrCreateTag().putString("CapacityProof","keep");
        var purification=level.getRecipeManager().byKey(new ResourceLocation(SurvivalRules.ID,"purified_canteen_from_purification_smelting_canteen")).orElseThrow();
        var purified=((Recipe)purification).assemble(new SimpleContainer(canteen),level.registryAccess());
        check(ThirstUtil.getHydrationEnumTag(purified)==sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum.PURIFIED,"native canteen boiling remains obtainable without potions");
        check(ThirstUtil.getCapacityTag(purified)==3&&purified.getTag().getString("CapacityProof").equals("keep"),"canteen boiling preserves water quantity and other NBT");
        for(var entry:level.getRecipeManager().getRecipes())if(entry.getId().getNamespace().equals(SurvivalRules.ID)){
            for(var ingredient:entry.getIngredients())for(var stack:ingredient.getItems()){
                var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
                check(!id.toString().matches("minecraft:(diamond|blaze_.*|nether.*|potion|magma_.*|chorus_.*)"),"no magical or Nether recipe materials: "+id);
            }
        }
    }
    private void run() throws Exception {
        var p=player("SurvivalProbe");
        check(p.getMaxHealth()==20,"vanilla max health is twenty");
        check(!p.getCapability(HealthProvider.HEALTH_CAPABILITY).isPresent(),"no additional-heart capability attached");
        check(!p.getCapability(BodyDamageProvider.BODY_DAMAGE_CAPABILITY).isPresent(),"no limb health capability attached");
        check(CapabilityUtil.getThirstCapability(p)!=null,"real thirst capability attached");
        check(CapabilityUtil.getWetnessCapability(p)!=null,"real wetness capability attached");
        check(level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),"vanilla regeneration gamerule untouched");
        for(String id:SurvivalRules.REMOVED_ITEMS)check(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(SurvivalRules.ID,id)),"removed item absent: "+id);
        for(String id:SurvivalRules.REMOVED_EFFECTS)check(!ForgeRegistries.MOB_EFFECTS.containsKey(new ResourceLocation(SurvivalRules.ID,id)),"removed injury effect absent: "+id);
        for(var id:ForgeRegistries.ATTRIBUTES.getKeys())if(id.getNamespace().equals(SurvivalRules.ID))check(SurvivalRules.TEMPERATURE_ATTRIBUTES.contains(id.getPath()),"no health attributes remain: "+id);
        for(var id:ForgeRegistries.POTIONS.getKeys())check(!id.getNamespace().equals(SurvivalRules.ID),"no survival potions: "+id);
        var cloth=new ItemStack(Items.LEATHER_CHESTPLATE);TemperatureUtil.setArmorCoatTag(cloth,"heating1");
        check(TemperatureUtil.getArmorCoatTag(cloth).equals("heating1"),"leather accepts wool lining");
        var iron=new ItemStack(Items.IRON_CHESTPLATE);TemperatureUtil.setArmorCoatTag(iron,"heating1");
        check(TemperatureUtil.getArmorCoatTag(iron).isEmpty(),"plate refuses lining");
        iron.getOrCreateTag().putString("Coat","heating1");check(SurvivalRules.lining(iron).coldResistance==0,"forged NBT cannot grant plate lining resistance");
        var layered=LayerData.wrap(List.of(cloth,new ItemStack(Items.CHAINMAIL_CHESTPLATE),new ItemStack(Items.IRON_CHESTPLATE)));
        check(LayerData.pieces(layered).get(0).getTag().getString("Coat").equals("heating1"),"lining survives inside three armor layers");
        health(p);packets();sewing(p);layers(p);recipes();bottles(p);
        for(var entry:Map.of(SurvivalRules.ID+":purified_water_bottle",4,SurvivalRules.ID+":raw_water_bottle",3,"farmersdelight:milk_bottle",4,"farmersdelight:melon_juice",4,"create:builders_tea",4,"farmersrespite:black_tea",4,"vinery:apple_juice",4,"brewinandchewin:beer",1,"farmersdelight:chicken_soup",2).entrySet())drink(p,entry.getKey(),entry.getValue());
        var consumables=new TreeMap<String,Map<String,Object>>();
        for(var item:ForgeRegistries.ITEMS.getValues())try{
            var stack=new ItemStack(item);var animation=item.getUseAnimation(stack);
            if(item.isEdible()||animation==UseAnim.DRINK)consumables.put(ForgeRegistries.ITEMS.getKey(item).toString(),Map.of("edible",item.isEdible(),"animation",animation.toString()));
        }catch(RuntimeException|LinkageError error){System.out.println("SURVIVAL_DUMP_SKIP "+ForgeRegistries.ITEMS.getKey(item)+" "+error);}
        Files.writeString(Path.of("survival-consumables.json"),new GsonBuilder().setPrettyPrinting().create().toJson(consumables));
        Files.writeString(Path.of("survival-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("assertions",checks,"consumables",consumables,"health_capability_absent",true,"limb_capability_absent",true,"graphical_client_tested",false)));
        System.out.println("SURVIVAL_PROBE_PASS assertions="+checks+" consumables="+consumables.size());
    }
    private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||server==null||++ticks!=15)return;try{run();server.halt(false);}catch(Throwable error){System.out.println("SURVIVAL_PROBE_FAIL");error.printStackTrace();server.halt(false);}server=null;}
}
