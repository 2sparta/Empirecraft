package TEMPLATE;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Embedded in each patched native JAR. Exclusions happen BEFORE registration. */
public final class PolicyHooks {
    static final Map<String,String> ALIASES=new HashMap<>();
    static final Map<Object,Set<String>> DISPLAYED=Collections.synchronizedMap(new WeakHashMap<>());
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
    public static Object forge(Object register,String name,Supplier<?> factory) {
        String namespace=String.valueOf(field(register,"modid"));
        Object key=call(register,"getRegistryKey");
        if(!gameplay(key))return call(register,"register",name,factory);
        String target=ALIASES.get(namespace+":"+name);
        return target==null?call(register,"register",name,factory):object(target,key,namespace);
    }
    static boolean gameplay(Object key) {
        String text=String.valueOf(key);
        return text.contains("minecraft:item]")||text.contains("minecraft:block]")||text.contains("minecraft:fluid]")||text.contains("minecraft:entity_type]");
    }
    /** Called from Architectury's native DeferredRegister, before its entry list is mutated. */
    public static Object architecturyAlias(Object register,Object name) {
        String identity=String.valueOf(name);
        if(!identity.contains(":"))identity=field(register,"modId")+":"+identity;
        String target=ALIASES.get(identity);if(target==null)return null;
        Object registrar=call(register,"getRegistrar");
        if(!gameplay(call(registrar,"key")))return null;
        return call(registrar,"delegate",location(target));
    }
    /** Called in the bundled Registrate AbstractBuilder, before BuilderCallback.accept. */
    public static Object registrateAlias(Object builder) {
        Object owner=call(builder,"getOwner");
        String identity=call(owner,"getModid")+":"+call(builder,"getName");
        String target=ALIASES.get(identity);if(target==null)return null;
        if(!gameplay(call(builder,"getRegistryKey")))return null;
        Object delegate=object(target,call(builder,"getRegistryKey"),String.valueOf(call(owner,"getModid")));
        for(Class<?> c=builder.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()) {
            if(!m.getName().equals("createEntryWrapper")||m.getParameterCount()!=1)continue;
            try{m.setAccessible(true);return m.invoke(builder,delegate);}catch(ReflectiveOperationException e){throw failure(e);}
        }
        throw new IllegalStateException("Missing Registrate entry wrapper");
    }
    static boolean first(Object output,Object value) {
        Object item=value;String tag="";
        if(value.getClass().getName().equals("net.minecraft.world.item.ItemStack")) {
            item=call(value,"m_41720_");Object nbt=call(value,"m_41783_");tag=nbt==null?"":nbt.toString();
        }else {
            try{item=call(value,"m_5456_");}catch(IllegalStateException ignored){}
        }
        String key=System.identityHashCode(item)+":"+tag;
        return DISPLAYED.computeIfAbsent(output,k->new HashSet<>()).add(key);
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
}
