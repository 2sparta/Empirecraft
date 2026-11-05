package ua.empirecraft.nirvanaaudit;

import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Test-only probe: runs the actual original and patched mod in a real saved world. */
@Mod("nirvana_audit")
public final class NirvanaAudit {
    private int checks;
    public NirvanaAudit() { MinecraftForge.EVENT_BUS.register(this); }
    private void check(boolean value,String text) { checks++;if(!value)throw new AssertionError(text); }
    private static Object call(Object receiver,String[] names,Class<?>[] types,Object...args) throws Exception {
        for(String name:names)try{return receiver.getClass().getMethod(name,types).invoke(receiver,args);}catch(NoSuchMethodException absent){}
        throw new NoSuchMethodException(Arrays.toString(names));
    }
    private static SortedSet<String> keys(IForgeRegistry<?> registry) {
        var result=new TreeSet<String>();for(var id:registry.getKeys())if(id.toString().startsWith("nirvana:"))result.add(id.toString());return result;
    }
    private static Entity create(EntityType<?> type,Level level) throws Exception {
        List<Method> matches=new ArrayList<>();
        for(var method:EntityType.class.getMethods())if(Arrays.equals(method.getParameterTypes(),new Class<?>[]{Level.class}) && Entity.class.isAssignableFrom(method.getReturnType()))matches.add(method);
        if(matches.size()!=1)throw new AssertionError("Unexpected actual EntityType.create methods: "+matches);
        return (Entity)matches.get(0).invoke(type,level);
    }
    private static int surface(ServerLevel level,int x,int z) throws Exception {
        var kind=Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types");Object selected=null;
        for(Object value:kind.getEnumConstants())if(((Enum<?>)value).name().equals("MOTION_BLOCKING_NO_LEAVES"))selected=value;
        if(selected==null)throw new AssertionError("Actual surface heightmap not found");
        List<Method> matches=new ArrayList<>();
        for(var method:level.getClass().getMethods())if(Arrays.equals(method.getParameterTypes(),new Class<?>[]{kind,int.class,int.class}) && method.getReturnType()==int.class)matches.add(method);
        if(matches.size()!=1)throw new AssertionError("Unexpected actual surface height methods: "+matches);
        return (int)matches.get(0).invoke(level,selected,x,z);
    }
    @SubscribeEvent public void started(ServerStartedEvent event) throws Exception {
        boolean patched=System.getProperty("nirvana.expected","patched").equals("patched");
        var result=new LinkedHashMap<String,Object>();result.put("phase",patched?"patched":"baseline");
        try {
            var blocks=keys(ForgeRegistries.BLOCKS);var items=keys(ForgeRegistries.ITEMS);var entities=keys(ForgeRegistries.ENTITY_TYPES);var effects=keys(ForgeRegistries.MOB_EFFECTS);
            check(entities.contains("nirvana:reefer")!=patched,"Incorrect Reefer registration");
            check(items.contains("nirvana:reefer_spawn_egg")!=patched,"Incorrect spawn egg registration");
            check(entities.containsAll(List.of("nirvana:thc","nirvana:thc_minecart")),"Other Nirvana entities changed");
            check(items.contains("nirvana:reefer_head") && blocks.contains("nirvana:reefer_head"),"Decorative head changed");
            if(patched) {
                var loader=Class.forName("galena.nirvana.index.NirvanaEntities").getClassLoader();
                check(loader.getResource("galena/nirvana/index/NirvanaEntities.class")!=null,"Probe cannot see actual mod class resources");
                for(String name:List.of("galena/nirvana/world/entity/Reefer.class","galena/nirvana/world/entity/renderer/ReeferRenderer.class","galena/nirvana/world/entity/ICustomCreeper.class","galena/nirvana/mixins/CreeperMixin.class")) {
                    // Mixin rejects direct class loading of *any* mixin-package member, including absent ones.
                    check(loader.getResource(name)==null,"Removed implementation remains: "+name);
                }
            }
            var level=(ServerLevel)call(event.getServer(),new String[]{"overworld","m_129783_"},new Class<?>[]{});
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)call(level,new String[]{"getChunk","m_6325_"},new Class<?>[]{int.class,int.class},x,z);
            int y=surface(level,4,4);
            var player=FakePlayerFactory.getMinecraft(level);
            call(player,new String[]{"setPos","m_6034_"},new Class<?>[]{double.class,double.class,double.class},4.0,(double)y,4.0);
            var creeper=create(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecraft:creeper")),level);
            check(creeper!=null,"Vanilla creeper missing");
            call(creeper,new String[]{"setPos","m_6034_"},new Class<?>[]{double.class,double.class,double.class},5.0,(double)y,4.0);
            check((boolean)call(level,new String[]{"addFreshEntity","m_7967_"},new Class<?>[]{Entity.class},creeper),"Could not add the real vanilla creeper");
            MobEffect peace=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("nirvana:peace"));check(peace!=null,"Peace effect missing");
            var instance=new MobEffectInstance(peace,200,20);
            var source=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("nirvana:hemp")));
            var increased=peace.getClass().getMethod("onIncreasedTo",MobEffectInstance.class,ItemStack.class,LivingEntity.class,Level.class);
            for(int i=0;i<48;i++)increased.invoke(peace,instance,source,player,level);
            boolean removed=(boolean)call(creeper,new String[]{"isRemoved","m_213877_"},new Class<?>[]{});
            check(removed!=patched,"Vanilla creeper conversion was not retired");
            MobEffect hunger=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft:hunger"));
            check(call(player,new String[]{"getEffect","m_21124_"},new Class<?>[]{MobEffect.class},hunger)!=null,"Unrelated hunger side effect changed");
            int reefers=0;var all=(Iterable<?>)call(level,new String[]{"getAllEntities","m_8583_"},new Class<?>[]{});
            for(Object entity:all)if(entity.getClass().getName().equals("galena.nirvana.world.entity.Reefer"))reefers++;
            // One original entity comes from conversion; more than one also proves random spawning.
            check(patched?reefers==0:reefers>1,"Unexpected actual Reefer creation count: "+reefers);
            result.put("actual_reefers",reefers);result.put("vanilla_creeper_preserved",!removed);result.put("hunger_preserved",true);
            result.put("blocks",blocks);result.put("items",items);result.put("entities",entities);result.put("effects",effects);result.put("passed",true);
        } catch(Throwable failure) { result.put("passed",false);result.put("error",failure.toString());failure.printStackTrace(); }
        result.put("checks",checks);Files.writeString(Path.of("nirvana-runtime-"+(patched?"patched":"baseline")+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("NIRVANA_RUNTIME_RESULT "+result);
    }
}
