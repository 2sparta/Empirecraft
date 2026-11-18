package gunsmithtest;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.EquipmentSlot;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
@Mod("empirecraft_gunsmith_test")
public class GunsmithNative {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Item item(String id){var key=new ResourceLocation(id);check(ForgeRegistries.ITEMS.containsKey(key),"registered "+id);return ForgeRegistries.ITEMS.getValue(key);}
    public GunsmithNative(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    void start(ServerStartedEvent event){
        var server=event.getServer();
        try{
            var level=server.m_129783_();var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
            var pos=new BlockPos(0,70,0);var table=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("empirecraft_forging:gunsmith_table"));
            level.m_7731_(pos,table.m_49966_(),3);
            Method use=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredMethod("use",PlayerInteractEvent.RightClickBlock.class);use.setAccessible(true);
            player.m_21008_(InteractionHand.MAIN_HAND,new ItemStack(item("minecraft:paper")));
            var hit=new BlockHitResult(new Vec3(.5,71,.5),Direction.UP,pos,false);
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            check(player.m_21205_().m_41720_()==item("minecraft:paper"),"unpaid work cannot start");
            player.m_150109_().m_6836_(1,new ItemStack(item("minecraft:iron_ingot"),3));
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            var work=player.m_21205_();check(work.m_41720_()==item("empirecraft_forging:gun_workpiece"),"paid work begins");
            check(player.m_150109_().m_8020_(1).m_41619_(),"materials consumed once");
            check(work.m_41784_().m_128451_("Steps")==12,"barrel requires twelve operations");
            work.m_41784_().m_128356_("Next",0);
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            check(work.m_41784_().m_128451_("Progress")==0,"wrong tool cannot advance");
            String[] tools={"tfc:metal/hammer/wrought_iron","tfc:metal/chisel/wrought_iron","create:sand_paper"};
            for(int n=0;n<12;n++){
                player.m_21008_(InteractionHand.OFF_HAND,new ItemStack(item(tools[n%3])));
                work.m_41784_().m_128356_("Next",level.m_46467_());
                use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
                if(n<11)check(work.m_41784_().m_128451_("Progress")==n+1,"native tagged tool step "+n);
            }
            check(player.m_21205_().m_41720_()==item("tfc_muskets:musket_barrel"),"finished barrel delivered");
            player.getPersistentData().m_128405_("ecGunRecipe",3);
            player.m_21008_(InteractionHand.MAIN_HAND,new ItemStack(item("minecraft:paper")));
            player.m_150109_().m_6836_(1,new ItemStack(item("overgeared:steel_plate")));
            player.m_150109_().m_6836_(2,new ItemStack(item("create:cogwheel")));
            player.m_150109_().m_6836_(3,new ItemStack(item("minecraft:flint")));
            player.m_150109_().m_6836_(4,new ItemStack(item("empirecraft_forging:flintlock_blueprint")));
            player.m_150109_().m_6836_(5,new ItemStack(item("siegeworks:firearm_lock")));
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            var easy=player.m_21205_();check(easy.m_41784_().m_128451_("Steps")==6,"real blueprint reduces required operations");
            check(player.m_150109_().m_8020_(4).m_41720_()==item("empirecraft_forging:flintlock_blueprint"),"blueprint not consumed");
            check(ItemStack.m_41712_(easy.m_41739_(new net.minecraft.nbt.CompoundTag())).m_41784_().m_128451_("Steps")==6,"paid work survives native NBT save/load");
            easy.m_41784_().m_128405_("Progress",5);easy.m_41784_().m_128356_("Next",level.m_46467_()-41);
            player.m_21008_(InteractionHand.OFF_HAND,new ItemStack(item("create:sand_paper")));
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            check(easy.m_41784_().m_128451_("Progress")==4,"late operation regresses without duplicating materials");
            var dated = new ItemStack(item("thirtyyearswar:baroque_matchlock_musket")).m_41786_().m_214077_();
            check(dated instanceof net.minecraft.network.chat.contents.TranslatableContents datedGun
                    && Arrays.asList(datedGun.m_237523_()).contains(1630), "localized gun date");
            var costsField=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredField("COSTS");costsField.setAccessible(true);
            for(String[] costs:(String[][])costsField.get(null))for(String cost:costs)item(cost);
            var manager=server.m_129894_();
            check(manager.m_44043_(new ResourceLocation("empirecraft_forging:gunsmith_table")).isPresent(),"table recipe survived KubeJS reload");
            check(manager.m_44043_(new ResourceLocation("empirecraft_forging:assembly/musketmod/musket")).isEmpty(),"retired musket recipe absent");
            check(manager.m_44043_(new ResourceLocation("empirecraft_forging:wheellock")).isEmpty(),"instant wheellock crafting is removed");
            var costs=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredField("COSTS");costs.setAccessible(true);
            check(Arrays.asList(((String[][])costs.get(null))[6]).contains("tfc:ore/pyrite"),"workshop wheellock uses pyrite");
            var pistol = manager.m_44043_(new ResourceLocation("empirecraft_forging:assembly/thirtyyearswar/knights_baroque_wheellock_pistol")).orElseThrow();
            check(pistol.m_7527_().stream().anyMatch(i -> i.test(new ItemStack(item("empirecraft_forging:wheellock")))), "pistol uses wheellock");
            for (String id : List.of("medieval_boomsticks:arquebus", "medieval_boomsticks:handgonne", "medieval_boomsticks:round_ball", "medieval_boomsticks:upgrade_kit", "musketmod:musket")) {
                check(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)), "removed item " + id);
            }
            check(!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation("medieval_boomsticks:round_ball")), "removed round ball entity");
            check(com.TBK.medieval_boomsticks.common.items.RechargeItem.getModifySpeedRecharge(new ItemStack(item("medieval_boomsticks:arbalest"))) > 0, "arbalest reload is usable");
            check(manager.m_44043_(new ResourceLocation("thirtyyearswar:knights_baroque_matchlock_musket")).isEmpty(),"previous added gun recipe removed");
            check(manager.m_44043_(new ResourceLocation("empirecraft_forging:assembly/thirtyyearswar/knights_baroque_matchlock_musket")).isPresent(),"latest gun replacement registered");
            Method hurt=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredMethod("hurt",LivingHurtEvent.class);hurt.setAccessible(true);
            var source=new net.minecraft.world.damagesource.DamageSource(level.m_9598_().m_175515_(net.minecraft.core.registries.Registries.f_268580_).m_246971_(ResourceKey.m_135785_(net.minecraft.core.registries.Registries.f_268580_,new ResourceLocation("thirtyyearswar:bullet"))),player);
            var unarmored=new LivingHurtEvent(player,source,20);hurt.invoke(null,unarmored);check(Math.abs(unarmored.getAmount()-27)<.001,"hitscan damage tier applies without projectile entity");
            player.m_8061_(EquipmentSlot.CHEST,new ItemStack(item("thirtyyearswar:baroque_cuirassier_armour")));
            var armored=new LivingHurtEvent(player,source,20);hurt.invoke(null,armored);check(Math.abs(armored.getAmount()-21.6)<.001,"cuirassier chest reduces bullets by weighted 20 percent");
            System.out.println("GUNSMITH_NATIVE_PASS checks="+checks);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("GUNSMITH_NATIVE_FAIL "+failure);}
        server.m_7570_(false);
    }
}
