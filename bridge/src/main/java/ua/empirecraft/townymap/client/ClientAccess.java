package ua.empirecraft.townymap.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Version-scoped client boundary. Dedicated servers never resolve Minecraft screen classes. */
public final class ClientAccess {
    private record Key(Class<?> owner,String name,List<Class<?>> arguments) {}
    private static final Map<Key,Method> METHODS=new ConcurrentHashMap<>();
    private static final Map<String,Field> FIELDS=new ConcurrentHashMap<>();
    private static final Map<Class<?>,Class<?>> BOX=Map.of(int.class,Integer.class,long.class,Long.class,
            double.class,Double.class,float.class,Float.class,boolean.class,Boolean.class,char.class,Character.class,
            byte.class,Byte.class,short.class,Short.class);
    private ClientAccess(){}
    private static boolean accepts(Class<?>[] parameters,Object[] arguments) {
        if(parameters.length!=arguments.length)return false;
        for(int i=0;i<parameters.length;i++)if(arguments[i]!=null && !BOX.getOrDefault(parameters[i],parameters[i]).isInstance(arguments[i]))return false;
        return true;
    }
    public static Class<?> type(String name) {
        try{return Class.forName(name);}catch(ClassNotFoundException error){throw new IllegalStateException(name,error);}
    }
    public static Object call(Object target,String name,Object... arguments) {
        Class<?> owner=target instanceof Class<?> c?c:target.getClass();
        var key=new Key(owner,name,Arrays.stream(arguments).map(a->a==null?Void.class:a.getClass()).toList());
        Method method=METHODS.computeIfAbsent(key,k->Arrays.stream(owner.getMethods())
                .filter(m->m.getName().equals(name)&&accepts(m.getParameterTypes(),arguments)&&!m.isBridge())
                .findFirst().orElseThrow(()->new IllegalStateException("Missing client API: "+owner.getName()+"."+name+key.arguments())));
        try{return method.invoke(target instanceof Class<?>?null:target,arguments);}
        catch(InvocationTargetException error){throw new IllegalStateException(name,error.getCause());}
        catch(ReflectiveOperationException error){throw new IllegalStateException(name,error);}
    }
    public static Object create(String name,Object... arguments) {
        var constructor=Arrays.stream(type(name).getConstructors()).filter(c->accepts(c.getParameterTypes(),arguments)).findFirst()
                .orElseThrow(()->new IllegalStateException("Missing client constructor: "+name));
        try{return constructor.newInstance(arguments);}catch(ReflectiveOperationException error){throw new IllegalStateException(name,error);}
    }
    public static Object field(Object target,String name) {
        Class<?> owner=target instanceof Class<?> c?c:target.getClass();String key=owner.getName()+"#"+name;
        Field field=FIELDS.computeIfAbsent(key,k->{
            for(Class<?> cursor=owner;cursor!=null;cursor=cursor.getSuperclass())try{var f=cursor.getDeclaredField(name);f.setAccessible(true);return f;}
            catch(NoSuchFieldException ignored){}
            throw new IllegalStateException("Missing client field: "+key);
        });
        try{return field.get(target instanceof Class<?>?null:target);}catch(ReflectiveOperationException error){throw new IllegalStateException(key,error);}
    }
    public static void write(Object target,String name,Object value) {
        field(target,name);
        Class<?> owner=target instanceof Class<?> c?c:target.getClass();
        try{FIELDS.get(owner.getName()+"#"+name).set(target instanceof Class<?>?null:target,value);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(name,error);}
    }
    public static Object client(){return call(type("net.minecraft.client.Minecraft"),"m_91087_");}
    public static Object screen(){return field(client(),"f_91080_");}
    public static Object player(){return field(client(),"f_91074_");}
    public static Object font(){return field(client(),"f_91062_");}
    public static int width(Object screen){return ((Number)field(screen,"f_96543_")).intValue();}
    public static int height(Object screen){return ((Number)field(screen,"f_96544_")).intValue();}
    public static boolean isMap(Object screen){return screen!=null&&screen.getClass().getName().equals("journeymap.client.ui.fullscreen.Fullscreen");}
    public static void fill(Object graphics,int x,int y,int right,int bottom,int color){call(graphics,"m_280509_",x,y,right,bottom,color);}
    public static void text(Object graphics,String text,int x,int y,int color){call(graphics,"m_280488_",font(),text,x,y,color);}
    public static String trim(String text,int width){
        String value=text==null?"":text;
        while(value.length()>1&&((Number)call(font(),"m_92895_",value)).intValue()>width)value=value.substring(0,value.length()-1);
        return value;
    }
    public static Object newMapScreen(){return create("journeymap.client.ui.fullscreen.Fullscreen");}
    public static void openMap(){call(client(),"m_91152_",newMapScreen());}
}
