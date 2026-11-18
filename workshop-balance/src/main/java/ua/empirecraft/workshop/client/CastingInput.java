package ua.empirecraft.workshop.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.workshop.*;

/** Reuse the actual Minecraft EditBox and Siegeworks screen. Like FleetUiHooks,
 * reflective widget access supports both named development and installed SRG APIs.
 * No substitute client APIs are compiled or shipped.
 */
@Mod.EventBusSubscriber(modid=Workshop.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class CastingInput {
    private static final Map<Object,Object> BOXES=new WeakHashMap<>();
    private static boolean updating;
    private CastingInput(){}
    private static Field field(Object object,String...names)throws ReflectiveOperationException{
        for(var type=object.getClass();type!=null;type=type.getSuperclass())for(var name:names)try{var field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(Arrays.toString(names));
    }
    private static Object get(Object object,String...names)throws ReflectiveOperationException{return field(object,names).get(object);}
    private static Object call(Object object,Class<?>[] types,Object[] values,String...names)throws ReflectiveOperationException{
        for(var type=object.getClass();type!=null;type=type.getSuperclass())for(var name:names)try{var method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,values);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(Arrays.toString(names));
    }
    private static Object call(Object object,String...names)throws ReflectiveOperationException{return call(object,new Class<?>[0],new Object[0],names);}
    private static void functional(Object box,Class<?> type,Object function)throws ReflectiveOperationException{
        var candidates=Arrays.stream(box.getClass().getDeclaredMethods()).filter(m->m.getReturnType()==void.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{type})).toList();
        if(candidates.size()!=1)throw new NoSuchMethodException("Ambiguous EditBox "+type.getName());candidates.get(0).invoke(box,function);
    }
    private static boolean nativeScreen(Object screen){return screen.getClass().getName().equals("me.mss1r.siegeworks.client.CastingScreen");}
    private static AbstractContainerMenu menu(Object screen)throws ReflectiveOperationException{return (AbstractContainerMenu)call(screen,"getMenu","m_6262_");}
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event){try{
        Object screen=event.getScreen();if(!nativeScreen(screen))return;
        var menu=menu(screen);Object font=get(screen,"font","f_96547_");
        int x=((Number)call(screen,"getGuiLeft")).intValue(),y=((Number)call(screen,"getGuiTop")).intValue();
        var box=Class.forName("net.minecraft.client.gui.components.EditBox").getConstructor(Class.forName("net.minecraft.client.gui.Font"),int.class,int.class,int.class,int.class,Component.class).newInstance(font,x+99,y+25,45,18,Component.m_237115_("workshop.casting.diameter"));
        functional(box,Predicate.class,(Predicate<String>)CastRules::numericInput);
        call(box,new Class<?>[]{String.class},new Object[]{Integer.toString(((Number)call(menu,"caliber")).intValue())},"setValue","m_94144_");
        functional(box,Consumer.class,(Consumer<String>)value->{if(updating||value.isEmpty())return;int number=Integer.parseInt(value);if(CastRules.validDiameter(number))Workshop.diameter(menu.f_38840_,number);});
        call(screen,new Class<?>[]{Class.forName("net.minecraft.client.gui.components.events.GuiEventListener")},new Object[]{box},"addRenderableWidget","m_142416_");BOXES.put(screen,box);
    }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot add Siegeworks diameter field",ex);}}
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event){try{
        Object screen=event.getScreen(),box=BOXES.get(screen);if(box==null||event.getKeyCode()==256||!(Boolean)call(box,"isFocused","m_93696_"))return;
        if(event.getKeyCode()==257||event.getKeyCode()==335){
            call(box,new Class<?>[]{boolean.class},new Object[]{false},"setFocused","m_93692_");
        }else call(box,new Class<?>[]{int.class,int.class,int.class},new Object[]{event.getKeyCode(),event.getScanCode(),event.getModifiers()},"keyPressed","m_7933_");
        event.setCanceled(true);
    }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot edit Siegeworks diameter",ex);}}
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event){try{
        Object screen=event.getScreen(),box=BOXES.get(screen);if(box==null)return;var menu=menu(screen);var cast=(CastAccess)menu;
        boolean focused=(Boolean)call(box,"isFocused","m_93696_");
        if(!focused){updating=true;try{call(box,new Class<?>[]{String.class},new Object[]{Integer.toString(((Number)call(menu,"caliber")).intValue())},"setValue","m_94144_");}finally{updating=false;}}
        Component label=cast.workshop$status()==1?Component.m_237110_("workshop.casting.progress",Math.min(100,cast.workshop$progress()*100/Math.max(1,cast.workshop$duration()))):Component.m_237115_(cast.workshop$status()==2?"workshop.casting.collect":"workshop.casting.start");
        Object button=get(screen,"cast");call(button,new Class<?>[]{Component.class},new Object[]{label},"setMessage","m_93666_");
        // Native renderLabels runs after Screen's widgets. Draw the real field once more
        // above the original caliber label so the typed value stays readable.
        call(box,new Class<?>[]{Class.forName("net.minecraft.client.gui.GuiGraphics"),int.class,int.class,float.class},new Object[]{event.getGuiGraphics(),event.getMouseX(),event.getMouseY(),event.getPartialTick()},"render","m_88315_");
    }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot update Siegeworks casting status",ex);}}
}
