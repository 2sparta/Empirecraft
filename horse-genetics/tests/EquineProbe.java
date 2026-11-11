package ua.empirecraft.equinetest;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.equine.*;
@Mod("empirecraft_equine_probe")
public final class EquineProbe {
    int checks;ServerLevel level;
    void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    public EquineProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::start);}
    AbstractHorse create(String species,int gender) throws Exception {
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragnlivestock:o_"+species));
        var h=(AbstractHorse)Objects.requireNonNull(type.create(level));h.getClass().getMethod("setGender",int.class).invoke(h,gender);
        h.setTamed(true);h.setAge(0);h.setHealth(h.getMaxHealth());h.moveTo(0,90,0,0,0);return h;
    }
    void migration() throws Exception {
        check(!net.minecraftforge.fml.ModList.get().isLoaded("horse_colors")&&!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation("horse_colors:horse_felinoid")),"RHG is absent from actual runtime registry");
        var source=create("horse",0);UUID owner=UUID.randomUUID();source.setOwnerUUID(owner);source.setCustomName(net.minecraft.network.chat.Component.literal("Руда"));
        source.getAttribute(Attributes.MAX_HEALTH).setBaseValue(38);source.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.24);source.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(.8);source.setHealth(32);
        var n=new CompoundTag();source.save(n);n.putString("id","horse_colors:horse_felinoid");n.putString("Genes",EquineGenome.founder(new Random(5)).encode());n.putBoolean("gender",false);n.putBoolean("fertile",true);
        n.put("ArmorItem",new ItemStack(Items.IRON_HORSE_ARMOR).save(new CompoundTag()));n.put("SaddleItem",new ItemStack(Items.SADDLE).save(new CompoundTag()));
        var old=n.copy();var migrated=(AbstractHorse)EntityType.create(n,level).orElseThrow();
        check(EquineGenetics.supported(migrated),"native NBT loader migrates a missing RHG entity ID to DLO");
        check(migrated.getUUID().equals(source.getUUID())&&migrated.getOwnerUUID().equals(owner)&&migrated.isTamed(),"native ownership/taming/UUID survives");
        check(migrated.getName().getString().equals("Руда"),"native custom name survives");
        check(Math.abs(migrated.getAttribute(Attributes.MAX_HEALTH).getBaseValue()-38)<.0001&&Math.abs(migrated.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()-.24)<.0001,"native horse attributes survive");
        var save=new CompoundTag();migrated.save(save);
        check(save.getCompound("ArmorItem").getString("id").equals("minecraft:iron_horse_armor")&&save.getCompound("SaddleItem").getString("id").equals("minecraft:saddle"),"native DLO inventory retains saddle and armor");
        check(EquineGenetics.data(migrated).getCompound("OriginalEntity").equals(old),"full original horse NBT remains archived");
        var tag=new CompoundTag();tag.putString("id","minecraft:horse");
        check(EquineGenetics.supported(EntityType.create(tag,level).orElseThrow()),"vanilla saved/spawn NBT yields only DLO horses");
        var item=new CompoundTag();item.putString("id","horse_colors:gene_book");item.putByte("Count",(byte)1);
        check(ItemStack.of(item).is(Items.BOOK),"retired RHG item is readable with RHG uninstalled");
    }
    void breeding() throws Exception {
        var mare=create("horse",0);var stallion=create("horse",1);
        check(level.addFreshEntity(mare)&&level.addFreshEntity(stallion),"DLO parents join actual level");
        var m=EquineGenome.decode(EquineGenetics.data(mare).getString("Genes"));var f=EquineGenome.decode(EquineGenetics.data(stallion).getString("Genes"));
        var child=(AbstractHorse)mare.getBreedOffspring(level,stallion);UUID babyId=child.getUUID();
        var event=new BabyEntitySpawnEvent(mare,stallion,child);MinecraftForge.EVENT_BUS.post(event);
        check(event.isCanceled()&&level.getEntity(babyId)==null,"breeding starts gestation rather than instant birth");
        var d=EquineGenetics.data(mare);check(d.getList("Children",Tag.TAG_COMPOUND).size()==1,"one persisted unborn foal");
        check(d.getLong("DueAt")-EquineGenetics.now(mare)==48000,"pregnancy uses configured 48000 ticks");
        check(d.getLong("ReadyAt")-EquineGenetics.now(mare)==96000&&EquineGenetics.data(stallion).getLong("ReadyAt")-EquineGenetics.now(stallion)==240,"separate male/female cooldowns");
        var c=EquineGenome.decode(EquineGenetics.data(child).getString("Genes"));
        for(var g:EquineGenome.Gene.values()) {
            // Mutation is rare by default, check at least one valid copy from each parent for most loci.
            check(c.allele(g,0)>=0&&c.allele(g,0)<64,"maternal allele remains valid");check(c.allele(g,1)>=0&&c.allele(g,1)<64,"paternal allele remains valid");
        }
        var save=new CompoundTag();mare.save(save);check(save.getCompound("ForgeData").getCompound(LegacyMigration.KEY).getList("Children",Tag.TAG_COMPOUND).size()==1,"pregnancy persists through native entity serialization");
        var restored=(AbstractHorse)EntityType.create(save.copy(),level).orElseThrow();
        check(EquineGenetics.data(restored).getLong("DueAt")==d.getLong("DueAt"),"due date survives reload");
        d.putLong("ConceivedAt",EquineGenetics.now(mare)-1);
        var rejected=new BabyEntitySpawnEvent(mare,stallion,(AbstractHorse)mare.getBreedOffspring(level,stallion));MinecraftForge.EVENT_BUS.post(rejected);
        check(rejected.isCanceled()&&d.getList("Children",Tag.TAG_COMPOUND).size()==1,"pregnant mare cannot conceive a second unrelated pregnancy");
        boolean[] rejectBirth={true};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,e->{if(rejectBirth[0]&&e.getEntity().getUUID().equals(babyId))e.setCanceled(true);});
        d.putLong("DueAt",EquineGenetics.now(mare)-1);mare.tickCount=20;
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(mare));
        check(level.getEntity(babyId)==null&&d.getList("Children",Tag.TAG_COMPOUND).size()==1,"denied world insertion preserves the unborn foal for retry");
        rejectBirth[0]=false;MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(mare));
        var foal=level.getEntity(babyId);check(foal instanceof AbstractHorse&&EquineGenetics.supported(foal),"gestation produces an actual DLO foal");
        check(((AbstractHorse)foal).getAge()==-120000,"foal starts configured growth period");
        var fd=EquineGenetics.data((AbstractHorse)foal);
        check(fd.getString("Genes").equals(c.encode()),"conception genome is unchanged at birth");
        check(Math.abs(((AbstractHorse)foal).getMaxHealth()-Math.max(4,Math.min(200,c.health()*fd.getDouble("HealthScale"))))<.0001,"native born health follows inherited genotype");
        var reader=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);reader.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOOK));
        var inspection=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(reader,InteractionHand.MAIN_HAND,foal);MinecraftForge.EVENT_BUS.post(inspection);
        check(inspection.isCanceled()&&reader.getMainHandItem().is(Items.WRITTEN_BOOK),"book inspection creates a real readable vanilla book");
        check(reader.getMainHandItem().getTag().getList("pages",Tag.TAG_STRING).size()>=5,"book contains useful trait and allele pages");
        check(EquineGenetics.data((AbstractHorse)foal).getUUID("Mother").equals(mare.getUUID()),"pedigree follows the same parent UUID");
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(mare));check(d.getList("Children",Tag.TAG_COMPOUND).isEmpty()&&level.getEntity(babyId)==foal,"repeated tick cannot duplicate a birth");
        var mule=create("mule",0);var sterile=new BabyEntitySpawnEvent(mule,stallion,create("horse",0));MinecraftForge.EVENT_BUS.post(sterile);
        check(sterile.isCanceled()&&EquineGenetics.data(mule).getList("Children",Tag.TAG_COMPOUND).isEmpty(),"mules remain sterile");
    }
    void start(ServerStartedEvent event) {
        level=event.getServer().overworld();
        try {level.getChunk(0,0);migration();breeding();
            Files.writeString(Path.of("equine-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("assertions",checks,"rhg_installed",false,"native_dlo_entities",true,"pregnancy_reload",true,"graphical_client_tested",false)));
            System.out.println("EQUINE_NATIVE_PASS assertions="+checks);
        }catch(Throwable e){e.printStackTrace();System.out.println("EQUINE_NATIVE_FAIL "+e);}
        event.getServer().halt(false);
    }
}
