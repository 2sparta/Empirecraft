package ua.empirecraft.armortest;

import com.mojang.authlib.GameProfile;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.armor.*;

@Mod("empirecraft_armor_probe")
public final class ArmorProbe {
    private int checks,ticks;private boolean pendingDeath;private ServerLevel level;private net.minecraft.server.MinecraftServer server;
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void near(double expected,double actual,String message){check(Math.abs(expected-actual)<.0001,message+" expected="+expected+" actual="+actual);}
    public ArmorProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,net.minecraftforge.event.entity.living.LivingDropsEvent.class,event->{if(event.getEntity() instanceof ServerPlayer)System.out.println("ARMOR_DROP_EVENT canceled="+event.isCanceled()+" items="+event.getDrops().stream().map(e->e.getItem()+" "+e.getItem().getTag()).toList());});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,event->{if(event.getEntity() instanceof ItemEntity item)System.out.println("ARMOR_ITEM_JOIN canceled="+event.isCanceled()+" item="+item.getItem()+" tag="+item.getItem().getTag()+" pos="+item.position());});
    }
    private ServerPlayer player(String name) throws Exception {
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),name));
        var channel=new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
            @Override public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){io.netty.util.ReferenceCountUtil.release(message);promise.trySuccess();}
            @Override public void flush(io.netty.channel.ChannelHandlerContext context){}
        });
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=channel;
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        player.connection=new ServerGamePacketListenerImpl(server,connection,player);
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
    private double defense(ItemStack stack,EquipmentSlot slot){return stack.getAttributeModifiers(slot).get(Attributes.ARMOR).stream().mapToDouble(AttributeModifier::getAmount).sum();}
    private void clear(ServerPlayer player){player.getInventory().clearContent();player.inventoryMenu.setCarried(ItemStack.EMPTY);}
    private void add(ServerPlayer player,ItemStack stack){player.inventoryMenu.setCarried(stack);player.inventoryMenu.clicked(5,0,ClickType.PICKUP,player);}
    private ItemStack three() {return LayerData.wrap(List.of(new ItemStack(Items.LEATHER_HELMET),new ItemStack(Items.CHAINMAIL_HELMET),new ItemStack(Items.IRON_HELMET)));}
    private void clicks(ServerPlayer p) {
        clear(p);var leather=new ItemStack(Items.LEATHER_HELMET);leather.getOrCreateTag().putString("LayerProbe","padding");leather.setDamageValue(3);
        p.setItemSlot(EquipmentSlot.HEAD,leather);add(p,new ItemStack(Items.CHAINMAIL_HELMET));add(p,new ItemStack(Items.IRON_HELMET));
        var worn=p.getItemBySlot(EquipmentSlot.HEAD);var all=LayerData.pieces(worn);
        check(all.size()==3&&p.inventoryMenu.getCarried().isEmpty(),"three layers enter the same native armor slot");
        check(all.get(0).getTag().getString("LayerProbe").equals("padding")&&all.get(0).getDamageValue()==3,"inner NBT and damage preserved");
        check(ItemStack.matches(worn,ItemStack.of(worn.save(new CompoundTag()))),"full layered equipment survives item NBT persistence");
        near(all.stream().mapToDouble(i->defense(i,EquipmentSlot.HEAD)).sum(),defense(worn,EquipmentSlot.HEAD),"real Forge armor modifiers sum all three pieces");
        check(worn.getAttributeModifiers(EquipmentSlot.CHEST).isEmpty(),"no armor attribute in the wrong slot");
        add(p,new ItemStack(Items.DIAMOND_HELMET));
        check(LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==3&&p.inventoryMenu.getCarried().is(Items.IRON_HELMET),"outer replacement preserves two inner pieces without a fourth layer");
        p.inventoryMenu.setCarried(new ItemStack(Items.CHAINMAIL_HELMET));p.inventoryMenu.clicked(5,0,ClickType.PICKUP,p);
        check(p.inventoryMenu.getCarried().is(Items.CHAINMAIL_HELMET)&&p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"inner insertion cannot replace or bury an outer layer");
        p.inventoryMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.clicked(5,0,ClickType.PICKUP,p);
        check(p.inventoryMenu.getCarried().is(Items.DIAMOND_HELMET)&&!LayerData.layered(p.inventoryMenu.getCarried()),"pickup removes exactly the outer piece");
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET)&&LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==2,"inner layers stay equipped");
        p.inventoryMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.clicked(5,0,ClickType.QUICK_MOVE,p);
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)&&p.getInventory().contains(new ItemStack(Items.CHAINMAIL_HELMET)),"shift click removes only mail");
        clear(p);p.setItemSlot(EquipmentSlot.HEAD,three());
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.inventoryMenu.clicked(5,0,ClickType.QUICK_MOVE,p);
        check(LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==3,"full inventory neither deletes nor duplicates a layer");
        p.getInventory().setItem(0,ItemStack.EMPTY);p.inventoryMenu.clicked(5,0,ClickType.SWAP,p);
        check(p.getInventory().getItem(0).is(Items.IRON_HELMET)&&!LayerData.layered(p.getInventory().getItem(0)),"number key extracts only outer armor");
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET),"number key keeps mail equipped");
        clear(p);var bound=new ItemStack(Items.CHAINMAIL_HELMET);bound.enchant(Enchantments.BINDING_CURSE,1);
        boolean binding=EnchantmentHelper.hasBindingCurse(bound);
        p.setItemSlot(EquipmentSlot.HEAD,LayerData.wrap(List.of(bound,new ItemStack(Items.IRON_HELMET))));
        p.inventoryMenu.clicked(5,0,ClickType.PICKUP,p);p.inventoryMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.clicked(5,0,ClickType.PICKUP,p);
        check(binding?p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET):p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"inner binding follows the native enchantment policy without restoring removed enchantments");
        clear(p);
    }
    private void menu(ServerPlayer p) {
        clear(p);p.setItemSlot(EquipmentSlot.HEAD,three());AbstractContainerMenu menu=new LayerMenu(7,p.getInventory());
        check(menu.getSlot(0).getItem().is(Items.LEATHER_HELMET)&&menu.getSlot(4).getItem().is(Items.CHAINMAIL_HELMET)&&menu.getSlot(8).getItem().is(Items.IRON_HELMET),"expanded menu shows each real layer separately");
        check(!menu.getSlot(0).mayPickup(p)&&!menu.getSlot(4).mayPickup(p)&&menu.getSlot(8).mayPickup(p),"expanded menu enforces outer-first removal");
        menu.clicked(8,0,ClickType.PICKUP,p);
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET)&&menu.getCarried().is(Items.IRON_HELMET),"expanded slot extraction changes the real equipment atomically");
        menu.setCarried(ItemStack.EMPTY);p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(7);menu.broadcastChanges();
        check(menu.getSlot(4).getItem().getDamageValue()==7,"open menu refreshes combat damage instead of restoring stale snapshots");
        menu.clicked(4,0,ClickType.PICKUP,p);check(menu.getCarried().getDamageValue()==7,"taking damaged mail cannot duplicate its old durability");
        menu.removed(p);check(java.util.stream.IntStream.range(0,36).anyMatch(i->p.getInventory().getItem(i).is(Items.CHAINMAIL_HELMET)),"closing the expanded view returns the carried layer to inventory");
        clear(p);
    }
    private void durability(ServerPlayer p) throws Exception {
        clear(p);p.setItemSlot(EquipmentSlot.HEAD,three());
        p.getInventory().hurtArmor(level.damageSources().generic(),8,new int[]{3});
        var all=LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD));
        check(all.get(0).getDamageValue()>0&&all.get(1).getDamageValue()>0&&all.get(2).getDamageValue()>0,"each layer takes independent real durability damage");
        var fragile=new ItemStack(Items.IRON_HELMET);fragile.getOrCreateTag().putString("ForgingQuality","poor");fragile.setDamageValue(fragile.getMaxDamage()-1);
        p.setItemSlot(EquipmentSlot.HEAD,LayerData.wrap(List.of(new ItemStack(Items.CHAINMAIL_HELMET),fragile)));
        p.getInventory().hurtArmor(level.damageSources().generic(),100,new int[]{3});
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET),"breaking outer armor promotes the surviving inner piece");
        var protectedMail=new ItemStack(Items.CHAINMAIL_HELMET);protectedMail.enchant(Enchantments.ALL_DAMAGE_PROTECTION,4);
        p.setItemSlot(EquipmentSlot.HEAD,LayerData.wrap(List.of(protectedMail,new ItemStack(Items.IRON_HELMET))));
        var magic=LivingEntity.class.getDeclaredMethod("m_6515_",net.minecraft.world.damagesource.DamageSource.class,float.class);magic.setAccessible(true);
        int protection=EnchantmentHelper.getDamageProtection(LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)),level.damageSources().generic());
        near(10*(1-Math.min(20,protection)/25d),((Number)magic.invoke(p,level.damageSources().generic(),10f)).doubleValue(),"all layers use native enchantment protection and pack restrictions");
        clear(p);
    }
    private void recipes() {
        var manager=server.getRecipeManager();
        for(String id:List.of("stone_slab","smooth_stone","smooth_stone_slab","stone_slab_from_stone_stonecutting","smooth_stone_slab_from_smooth_stone_stonecutting"))check(manager.byKey(new net.minecraft.resources.ResourceLocation("minecraft",id)).isPresent(),"restored recipe exists: "+id);
        var container=new TransientCraftingContainer(new AbstractContainerMenu(null,0){public boolean stillValid(Player p){return true;}public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}},3,3);
        for(var pair:List.of(Map.entry(Items.STONE,Items.STONE_SLAB),Map.entry(Items.SMOOTH_STONE,Items.SMOOTH_STONE_SLAB))) {
            container.clearContent();for(int i=0;i<3;i++)container.setItem(i,new ItemStack(pair.getKey()));
            var recipe=manager.getRecipeFor(RecipeType.CRAFTING,container,level);
            check(recipe.isPresent(),"actual crafting grid matches slabs");var output=recipe.orElseThrow().assemble(container,level.registryAccess());
            check(output.is(pair.getValue())&&output.getCount()==6,"three stones craft exactly six correct slabs");
        }
    }
    private void baseArmor() {
        int count=0;
        for(var item:ForgeRegistries.ITEMS)if(item instanceof ArmorItem armor) {
            var stack=new ItemStack(item);var values=stack.getAttributeModifiers(armor.getEquipmentSlot());
            check(values.values().stream().noneMatch(m->m.getName().equals("Empirecraft base armor")),"removed flat defense modifier stays absent: "+ForgeRegistries.ITEMS.getKey(item));count++;
        }
        check(count>100,"audited the modded armor registry");
        near(6,defense(new ItemStack(Items.IRON_CHESTPLATE),EquipmentSlot.CHEST),"unforged iron chestplate has its original defense");
        var master=new ItemStack(Items.IRON_CHESTPLATE);master.getOrCreateTag().putString("ForgingQuality","master");
        near(7,defense(master,EquipmentSlot.CHEST),"forging quality defense remains enabled");
    }
    private void death() throws Exception {
        var p=player("LayerDeath");clear(p);var all=LayerData.pieces(three());
        for(var item:all)item.getOrCreateTag().putString("LayerDeathProbe",item.getItem().toString());
        p.setItemSlot(EquipmentSlot.HEAD,LayerData.wrap(all));level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false,server);
        p.invulnerableTime=0;check(p.hurt(level.damageSources().genericKill(),10000)&&!p.isAlive(),"real registered player dies from native damage");
        System.out.println("ARMOR_DEATH_SETUP pos="+p.position()+" capture="+p.captureDrops());
        pendingDeath=true;
    }
    private void checkDeath() throws Exception {
        var dropped=level.getEntitiesOfClass(ItemEntity.class,new AABB(-15,70,-15,15,110,15),item->item.getItem().hasTag()&&item.getItem().getTag().contains("LayerDeathProbe"));
        System.out.println("ARMOR_ALL_ITEMS "+java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->e instanceof ItemEntity).map(e->((ItemEntity)e).getItem()+" "+((ItemEntity)e).getItem().getTag()+" pos="+e.position()).toList());
        System.out.println("ARMOR_DEATH_DIAG dropped="+dropped.stream().map(e->e.getItem()+" "+e.getItem().getTag()).toList());
        var recovered=new ArrayList<ItemStack>();for(var item:dropped)recovered.add(item.getItem());
        for(var entity:level.getAllEntities())if("corpse:corpse".equals(Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()),""))) {
            Object death=entity.getClass().getMethod("getDeath").invoke(entity);
            for(Object raw:(Iterable<?>)death.getClass().getMethod("getAllItems").invoke(death)) {
                ItemStack item=(ItemStack)raw;
                if(item.hasTag()&&item.getTag().contains("LayerDeathProbe"))recovered.add(item);
            }
        }
        check(recovered.size()==3,"death retains each armor piece exactly once through actual Corpse loot");
        check(recovered.stream().noneMatch(LayerData::layered),"Corpse contains three plain independent armor pieces");
    }
    private void calendar() throws Exception {
        Object world=level.getClass().getMethod("getWorld").invoke(level);
        var worldType=Class.forName("org.bukkit.World");String name=worldType.getMethod("getName").invoke(world).toString();
        check(System.getProperty("empirecraft.calendar.wallclock.v1.world."+name)!=null,"calendar publishes the actual named Bukkit world's clock");
        check(System.getProperty("empirecraft.calendar.wallclock.v1.world."+worldType.getMethod("getUID").invoke(world))!=null,"calendar also publishes UUID identity");
    }
    private void inlineAndUse(ServerPlayer p) {
        clear(p);check(p.inventoryMenu.slots.size()==58,"native player menu keeps 46 original slots and 12 layer views");
        var state=((LayerInventoryAccess)p.inventoryMenu).empirecraft$layers();
        check(!state.expanded()&&!p.inventoryMenu.getSlot(46).isActive(),"layer slots start hidden");
        state.expanded(true);check(p.inventoryMenu.getSlot(46).isActive(),"button exposes inline slots");
        p.inventoryMenu.setCarried(new ItemStack(Items.LEATHER_HELMET));
        p.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(0,p.inventoryMenu.getStateId(),46,0,ClickType.PICKUP,ItemStack.EMPTY,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),"actual server click packet accepts inline armor slot through Bukkit view");
        p.inventoryMenu.setCarried(ItemStack.EMPTY);
        p.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(0,p.inventoryMenu.getStateId(),46,0,ClickType.PICKUP,new ItemStack(Items.LEATHER_HELMET),new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
        check(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()&&p.inventoryMenu.getCarried().is(Items.LEATHER_HELMET),"actual packet removes inline armor exactly once");
        clear(p);

        p.inventoryMenu.setCarried(new ItemStack(Items.LEATHER_HELMET));p.inventoryMenu.clicked(46,0,ClickType.PICKUP,p);
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),"inline padded slot equips the real helmet slot");
        p.inventoryMenu.setCarried(new ItemStack(Items.CHAINMAIL_HELMET));p.inventoryMenu.clicked(50,0,ClickType.PICKUP,p);
        p.inventoryMenu.setCarried(new ItemStack(Items.IRON_HELMET));p.inventoryMenu.clicked(54,0,ClickType.PICKUP,p);
        check(LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==3,"three inline columns share the real worn bundle");
        p.inventoryMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.clicked(46,0,ClickType.PICKUP,p);
        check(p.inventoryMenu.getCarried().isEmpty(),"covered inner layer cannot be extracted");
        p.inventoryMenu.clicked(54,0,ClickType.QUICK_MOVE,p);
        check(p.getInventory().contains(new ItemStack(Items.IRON_HELMET))&&p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET),"inline shift click extracts only outer layer");
        state.expanded(false);p.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND_HELMET));p.inventoryMenu.clicked(54,0,ClickType.PICKUP,p);
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET)&&p.inventoryMenu.getCarried().is(Items.DIAMOND_HELMET),"hidden slots reject forged clicks");
        clear(p);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CHAINMAIL_HELMET));
        var r=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(p,InteractionHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(r);
        check(r.isCanceled()&&LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==2&&p.getMainHandItem().isEmpty(),"right click adds mail above padding");
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.IRON_HELMET));
        r=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(p,InteractionHand.OFF_HAND);MinecraftForge.EVENT_BUS.post(r);
        check(LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==3&&p.getOffhandItem().isEmpty(),"offhand right click adds outer armor");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_HELMET));
        r=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(p,InteractionHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(r);
        check(p.getMainHandItem().is(Items.IRON_HELMET)&&p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)&&LayerData.pieces(p.getItemBySlot(EquipmentSlot.HEAD)).size()==3,"right click swaps only outer piece back into the hand");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.LEATHER_HELMET));
        r=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(p,InteractionHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(r);
        check(p.getMainHandItem().is(Items.LEATHER_HELMET)&&p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"right click rejects covered inner replacement");
        clear(p);
    }
    private void start(ServerStartedEvent event) {
        server=event.getServer();level=server.overworld();
        try {
            level.getChunk(0,0);level.setBlockAndUpdate(new BlockPos(0,89,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            var p=player("LayerProbe");baseArmor();recipes();calendar();clicks(p);menu(p);inlineAndUse(p);durability(p);death();
        }catch(Throwable error){error.printStackTrace();if(error instanceof net.minecraft.ReportedException report)System.out.println(report.getReport().getFriendlyReport());System.out.println("LAYERED_ARMOR_NATIVE_FAIL "+error);server.halt(false);}
    }
    private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!pendingDeath||++ticks<10)return;
        pendingDeath=false;
        try {
            checkDeath();
            var keeper=player("LayerKeep");keeper.setItemSlot(EquipmentSlot.HEAD,three());
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);keeper.invulnerableTime=0;
            check(keeper.hurt(level.damageSources().genericKill(),10000)&&!keeper.isAlive(),"keepInventory test uses an actual death");
            check(LayerData.pieces(keeper.getItemBySlot(EquipmentSlot.HEAD)).size()==3,"keepInventory preserves all three layers");
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false,server);
        var report=Map.of("assertions",checks,"graphical_client_tested",false,"real_inventory_clicks",true,"native_damage_and_death",true,"recipes",5);
        Files.writeString(Path.of("armor-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("LAYERED_ARMOR_NATIVE_PASS assertions="+checks);
        }catch(Throwable error){error.printStackTrace();System.out.println("LAYERED_ARMOR_NATIVE_FAIL "+error);}
        server.halt(false);
    }
}
