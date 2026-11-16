package ua.empirecraft.forging;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.tags.*;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** All workshop state travels in the paid workpiece; no client supplied progress. */
public final class Gunsmith {
    static final String NS="empirecraft_forging";
    static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,NS);
    static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,NS);
    static final RegistryObject<Block> TABLE=BLOCKS.register("gunsmith_table",()->new Block(BlockBehaviour.Properties.m_60926_(Blocks.f_50099_)));
    static final RegistryObject<Item> TABLE_ITEM=ITEMS.register("gunsmith_table",()->new BlockItem(TABLE.get(),new Item.Properties()));
    static final RegistryObject<Item> WORK=ITEMS.register("gun_workpiece",()->new Item(new Item.Properties().m_41487_(1)));
    static final RegistryObject<Item> LOCK=ITEMS.register("flintlock",()->new Item(new Item.Properties()));
    static final RegistryObject<Item> CORE=ITEMS.register("lockwork",()->new Item(new Item.Properties()));
    static final RegistryObject<Item> PLAN=ITEMS.register("flintlock_blueprint",()->new Item(new Item.Properties().m_41487_(1)));
    static final String[] TOOLS={"tfc:hammers","tfc:chisels","forge:sandpaper"};
    static final String[] OUTPUT={"tfc_muskets:musket_barrel","tfc_muskets:wooden_stock","empirecraft_casting:matchlock",NS+":flintlock",NS+":flintlock_blueprint",NS+":lockwork"};
    static final String[][] COSTS={
        {"minecraft:iron_ingot","minecraft:iron_ingot","minecraft:iron_ingot"},
        {"minecraft:oak_planks","minecraft:oak_planks","minecraft:string"},
        {"minecraft:iron_ingot","minecraft:iron_nugget","minecraft:string"},
        {"overgeared:steel_plate","overgeared:steel_plate","overgeared:steel_plate","overgeared:steel_plate","create:cogwheel","create:cogwheel",NS+":lockwork","minecraft:flint","minecraft:flint"},
        {NS+":flintlock","minecraft:paper","minecraft:paper","minecraft:paper","minecraft:ink_sac",NS+":lockwork"},
        {"overgeared:steel_plate","overgeared:steel_plate","create:cogwheel","create:cogwheel","create:brass_sheet","minecraft:gold_ingot"}};
    static final String[] EASY={"overgeared:steel_plate","create:cogwheel","minecraft:flint"};
    public Gunsmith(){
        var bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==net.minecraft.world.item.CreativeModeTabs.f_256869_)for(var i:ITEMS.getEntries())e.accept(i);});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,Gunsmith::use);
        MinecraftForge.EVENT_BUS.addListener(Gunsmith::tooltip);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW,Gunsmith::hurt);
    }
    static String id(ItemStack s){return Objects.toString(ForgeRegistries.ITEMS.getKey(s.m_41720_()),"");}
    static void tell(net.minecraft.world.entity.player.Player p,String text){p.m_5661_(Component.m_237113_(text),true);}
    static boolean has(net.minecraft.world.entity.player.Player p,String name){for(int i=0;i<36;i++)if(id(p.m_150109_().m_8020_(i)).equals(name))return true;return false;}
    static boolean pay(net.minecraft.world.entity.player.Player p,String[] costs){
        Map<String,Integer> required=new HashMap<>();for(String c:costs)required.merge(c,1,Integer::sum);
        for(var c:required.entrySet()){int n=0;for(int i=0;i<36;i++){var s=p.m_150109_().m_8020_(i);if(id(s).equals(c.getKey()))n+=s.m_41613_();}if(n<c.getValue()){tell(p,"Потрібно: "+c.getValue()+" × "+c.getKey());return false;}}
        for(String c:costs)for(int i=0;i<36;i++){var s=p.m_150109_().m_8020_(i);if(id(s).equals(c)){s.m_41774_(1);break;}}return true;
    }
    static void use(PlayerInteractEvent.RightClickBlock e){
        if(e.getLevel().m_8055_(e.getPos()).m_60734_()!=TABLE.get())return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        if(e.getLevel().f_46443_||e.getHand()!=InteractionHand.MAIN_HAND)return;
        var p=e.getEntity();var held=e.getItemStack();var data=p.getPersistentData();int selected=Math.floorMod(data.m_128451_("ecGunRecipe"),6);
        if(held.m_41619_()){
            if(p.m_6144_()){selected=(selected+1)%6;data.m_128405_("ecGunRecipe",selected);}
            tell(p,"Стіл зброяра: "+new String[]{"ствол","ложа","ґнотовий замок","кременевий замок","дослідження креслення","точний замковий механізм"}[selected]+". Shift+ПКМ порожньою рукою — вибір; папір — почати. Інструмент у другій руці.");return;
        }
        if(id(held).equals("minecraft:paper")){
            if(held.m_41613_()!=1){tell(p,"Візьми один аркуш у головну руку; матеріали — в інвентар.");return;}
            boolean easy=selected==3&&has(p,NS+":flintlock_blueprint");
            if(!pay(p,easy?EASY:COSTS[selected]))return;
            var work=new ItemStack(WORK.get());var tag=work.m_41784_();tag.m_128405_("Recipe",selected);tag.m_128405_("Steps",GunsmithRules.steps(selected,easy));
            tag.m_128356_("Next",e.getLevel().m_46467_()+20);p.m_21008_(InteractionHand.MAIN_HAND,work);
            tell(p,"Заготовку оплачено. Операція 1: молот; далі зубило, наждачний папір, повторити. Інтервал 1–3 с.");return;
        }
        if(held.m_41720_()!=WORK.get())return;
        var tag=held.m_41784_();int recipe=tag.m_128451_("Recipe"),step=tag.m_128451_("Progress"),steps=tag.m_128451_("Steps");
        if(recipe<0||recipe>=6||steps<1||steps>24)return;
        long now=e.getLevel().m_46467_(),next=tag.m_128454_("Next");
        var tool=p.m_21120_(InteractionHand.OFF_HAND);String required=TOOLS[step%3];
        if(!tool.m_204117_(TagKey.m_203882_(Registries.f_256913_,new ResourceLocation(required)))){tell(p,"Потрібний інструмент у другій руці: "+required);return;}
        if(now<next){tell(p,"Зачекай охолодження деталі (1 с).");return;}
        if(now>next+40&&step>0){step=GunsmithRules.operation(step,now,next);tag.m_128405_("Progress",step);tag.m_128356_("Next",now+20);tell(p,"Втрачено точність: повернення на одну операцію. Потрібно: "+TOOLS[step%3]);return;}
        tool.m_41622_(1,p,x->x.m_21190_(InteractionHand.OFF_HAND));
        step=GunsmithRules.operation(step,now,next);tag.m_128405_("Progress",step);tag.m_128356_("Next",now+20);
        if(step==steps){var result=ForgeRegistries.ITEMS.getValue(new ResourceLocation(OUTPUT[recipe]));p.m_21008_(InteractionHand.MAIN_HAND,new ItemStack(result));tell(p,"Роботу завершено!");}
        else tell(p,"Операція "+step+"/"+steps+". Далі: "+TOOLS[step%3]);
    }
    static String signed(float value){return (value>0?"+":"")+Math.round(value*100);}
    static void tooltip(ItemTooltipEvent e){
        var s=e.getItemStack();
        if(s.m_41720_() instanceof ArmorItem){float[] a={GunsmithRules.modifier(GunsmithRules.profile(id(s)),0),GunsmithRules.modifier(GunsmithRules.profile(id(s)),1),GunsmithRules.modifier(GunsmithRules.profile(id(s)),2)};e.getToolTip().add(Component.m_237113_("Спеціалізація (повний комплект): кулі "+signed(a[0])+"%; різальні/стріли "+signed(a[1])+"%; ударні "+signed(a[2])+"%."));}
        if(s.m_41720_()==PLAN.get())e.getToolTip().add(Component.m_237113_("Багаторазове: кременевий замок — 6 замість 24 операцій, 1 пластина + шестерня + кремінь."));
        if(s.m_41720_()==WORK.get())e.getToolTip().add(Component.m_237113_("Прогрес: "+s.m_41784_().m_128451_("Progress")+"/"+s.m_41784_().m_128451_("Steps")+". ПКМ по столу; інструмент у другій руці."));
    }
    static void hurt(LivingHurtEvent e){
        var source=e.getSource();if(source.m_269533_(TagKey.m_203882_(Registries.f_268580_,new ResourceLocation("minecraft:bypasses_armor"))))return;
        var direct=source.m_7640_();String projectile=direct==null?"":Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(direct.m_6095_()),"");
        String damage=source.m_19385_().toLowerCase(Locale.ROOT);
        if(source.m_276093_(net.minecraft.resources.ResourceKey.m_135785_(Registries.f_268580_,new ResourceLocation("thirtyyearswar:bullet"))))projectile="thirtyyearswar:bullet";
        boolean bullet=GunsmithRules.bullet(projectile,damage);
        if(bullet)e.setAmount(e.getAmount()*GunsmithRules.tier(projectile));
        int kind=bullet?0:direct instanceof net.minecraft.world.entity.projectile.AbstractArrow?1:-1;
        if(kind<0&&direct==source.m_7639_()&&source.m_7639_() instanceof net.minecraft.world.entity.LivingEntity attacker){String weapon=id(attacker.m_21205_());if(weapon.contains("hammer")||weapon.contains("mace")||weapon.contains("club"))kind=2;else if(weapon.contains("sword")||weapon.contains("saber")||weapon.contains("axe"))kind=1;}
        if(kind<0)return;
        float modifier=0;
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())if(slot.m_20743_()==net.minecraft.world.entity.EquipmentSlot.Type.ARMOR){var stack=e.getEntity().m_6844_(slot);if(stack.m_41720_() instanceof ArmorItem){float weight=switch(slot){case CHEST->.4f;case LEGS->.3f;case HEAD->.2f;default->.1f;};modifier+=GunsmithRules.modifier(GunsmithRules.profile(id(stack)),kind)*weight;}}
        e.setAmount(e.getAmount()*(1+modifier));
    }
}
