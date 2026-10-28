package TEMPLATE;

import java.io.*;
import java.lang.reflect.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Embedded in each patched native JAR. Exclusions happen BEFORE registration. */
public final class PolicyHooks {
    static final Map<String,String> ALIASES=new HashMap<>();
    static final Map<Object,Map<Object,Set<String>>> DISPLAYED=Collections.synchronizedMap(new WeakHashMap<>());
    static {
        try(var in=PolicyHooks.class.getResourceAsStream("aliases.tsv")) {
            if(in==null)throw new IOException("Missing native exclusion policy");
            for(String line:new String(in.readAllBytes(),StandardCharsets.UTF_8).split("\\n")) {
                String[] pair=line.split("\\t");if(pair.length==2)ALIASES.put(pair[0],pair[1]);
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    static RuntimeException failure(Throwable e){
        if(e instanceof InvocationTargetException x)e=x.getCause();
        return e instanceof RuntimeException r?r:new IllegalStateException("Native exclusion failed",e);
    }
    static Object call(Object object,String name,Object...args) {
        try {
            for(Method m:object.getClass().getMethods()) {
                if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;
                boolean match=true;
                for(int i=0;i<args.length;i++)if(args[i]!=null&&!m.getParameterTypes()[i].isInstance(args[i]))match=false;
                if(match){m.setAccessible(true);return m.invoke(object,args);}
            }
            throw new NoSuchMethodException(object.getClass()+"#"+name);
        }catch(ReflectiveOperationException e){throw failure(e);}
    }
    static Object field(Object object,String name) {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);
        }catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException e){throw failure(e);}
        throw new IllegalStateException("Missing field "+name);
    }
    static Object location(String value) {
        try{return Class.forName("net.minecraft.resources.ResourceLocation").getConstructor(String.class).newInstance(value);}
        catch(ReflectiveOperationException e){throw failure(e);}
    }
    static Object object(String target,Object key,String namespace) {
        try {
            Class<?> ro=Class.forName("net.minecraftforge.registries.RegistryObject");
            return ro.getMethod("create",Class.forName("net.minecraft.resources.ResourceLocation"),Class.forName("net.minecraft.resources.ResourceKey"),String.class).invoke(null,location(target),key,namespace);
        }catch(ReflectiveOperationException e){throw failure(e);}
    }
    static Object track(Object value,String identity) {
        try{return Class.forName("ua.empirecraft.noend.NativeAliases").getMethod("track",Object.class,Object.class).invoke(null,value,location(identity));}
        catch(ReflectiveOperationException e){throw failure(e);}
    }
    public static Supplier<?> itemSupplierAlias(String namespace,String name) {
        String target=ALIASES.get(namespace+":"+name);if(target==null)return null;
        try{return (Supplier<?>)object(target,Class.forName("net.minecraftforge.registries.ForgeRegistries$Keys").getField("ITEMS").get(null),namespace);}
        catch(ReflectiveOperationException e){throw failure(e);}
    }
    /** Native block-item factories need the source name, while RegistryObject.get() resolves the retained entry. */
    public static Object sourceId(Object value) {
        try {
            Object identity=Class.forName("ua.empirecraft.noend.NativeAliases").getMethod("sourceId",Object.class).invoke(null,value);
            return identity==null?call(value,"getId"):identity;
        }catch(ReflectiveOperationException e){throw failure(e);}
    }
    public static Object forge(Object register,String name,Supplier<?> factory) {
        String namespace=String.valueOf(field(register,"modid"));
        Object key=call(register,"getRegistryKey");
        if(!gameplay(key))return call(register,"register",name,factory);
        String target=ALIASES.get(namespace+":"+name);
        return target==null?call(register,"register",name,factory):track(object(target,key,namespace),namespace+":"+name);
    }
    static boolean gameplay(Object key) {
        String text=String.valueOf(key);
        return text.contains("minecraft:item]")||text.contains("minecraft:block]")||text.contains("minecraft:fluid]")||text.contains("forge:fluid_type]")||text.contains("minecraft:entity_type]");
    }
    /** Called from Architectury's native DeferredRegister, before its entry list is mutated. */
    public static Object architecturyAlias(Object register,Object name) {
        String identity=String.valueOf(name);
        if(!identity.contains(":"))identity=field(register,"modId")+":"+identity;
        String target=ALIASES.get(identity);if(target==null)return null;
        Object registrar=call(register,"getRegistrar");
        if(!gameplay(call(registrar,"key")))return null;
        return track(call(registrar,"delegate",location(target)),identity);
    }
    /** Called in the bundled Registrate AbstractBuilder, before BuilderCallback.accept. */
    public static Object registrateAlias(Object builder) {
        Object owner=call(builder,"getOwner");
        String identity=call(owner,"getModid")+":"+call(builder,"getName");
        String target=ALIASES.get(identity);if(target==null)return null;
        if(!gameplay(call(builder,"getRegistryKey")))return null;
        // Builder.onRegister queues callbacks before register(). Retire those callbacks
        // together with their entry, so they cannot mutate an aliased ordinary block.
        try {
            Object pair=Class.forName("org.apache.commons.lang3.tuple.Pair").getMethod("of",Object.class,Object.class).invoke(null,call(builder,"getName"),call(builder,"getRegistryKey"));
            call(field(owner,"registerCallbacks"),"removeAll",pair);
        }catch(ReflectiveOperationException e){throw failure(e);}
        Object delegate=object(target,call(builder,"getRegistryKey"),String.valueOf(call(owner,"getModid")));
        try {
            Class<?> base=Class.forName("com.tterrag.registrate.builders.AbstractBuilder");
            MethodType signature=MethodType.methodType(Class.forName("com.tterrag.registrate.util.entry.RegistryEntry"),Class.forName("net.minecraftforge.registries.RegistryObject"));
            // Exact lookup avoids resolving client-only lambda signatures on a dedicated server.
            Object wrapper=MethodHandles.privateLookupIn(base,MethodHandles.lookup()).findVirtual(base,"createEntryWrapper",signature).invoke(builder,delegate);
            return track(wrapper,identity);
        }catch(Throwable e){throw failure(e);}
    }
    static boolean first(Object output,Object value) {
        Object item=value;String tag="";
        if(value.getClass().getName().equals("net.minecraft.world.item.ItemStack")) {
            item=call(value,"m_41720_");Object nbt=call(value,"m_41783_");tag=nbt==null?"":nbt.toString();
        }else {
            try{item=call(value,"m_5456_");}catch(IllegalStateException ignored){}
        }
        return DISPLAYED.computeIfAbsent(output,k->new IdentityHashMap<>()).computeIfAbsent(item,k->new HashSet<>()).add(tag);
    }
    public static void creative(Object output,Object value,String method) {
        if(first(output,value))call(output,method,value);
    }
    public static void creative(Object output,Object value,Object visibility,String method) {
        if(first(output,value))call(output,method,value,visibility);
    }
    public static void creativeAll(Object output,Collection<?> values,String method) {
        for(Object value:values)creative(output,value,"m_246342_");
    }
    public static void creativeAll(Object output,Collection<?> values,Object visibility,String method) {
        for(Object value:values)creative(output,value,visibility,"m_246267_");
    }
    /** Missing fluid/entity IDs are part of Forge's saved registry snapshot too. */
    public static void migrateExtraRegistries(Object event) {
        try {
            Class<?> keys=Class.forName("net.minecraftforge.registries.ForgeRegistries$Keys");
            Class<?> registries=Class.forName("net.minecraftforge.registries.ForgeRegistries");
            for(String kind:List.of("FLUIDS","FLUID_TYPES")) {
                Object fluids=registries.getField(kind).get(null);
                for(Object mapping:(Iterable<?>)call(event,"getAllMappings",keys.getField(kind).get(null))) {
                    String target=ALIASES.get(String.valueOf(call(mapping,"getKey")));if(target==null)continue;
                    Object id=location(target);
                    if(!Boolean.TRUE.equals(call(fluids,"containsKey",id)))throw new IllegalStateException("Missing canonical fluid: "+target);
                    call(mapping,"remap",call(fluids,"getValue",id));
                }
            }
            for(Object mapping:(Iterable<?>)call(event,"getAllMappings",keys.getField("ENTITY_TYPES").get(null)))
                if(ALIASES.containsKey(String.valueOf(call(mapping,"getKey"))))call(mapping,"ignore");
        }catch(ReflectiveOperationException e){throw failure(e);}
    }
}
