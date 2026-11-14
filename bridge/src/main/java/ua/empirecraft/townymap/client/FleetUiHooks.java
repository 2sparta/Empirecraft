package ua.empirecraft.townymap.client;

import com.google.gson.*;
import java.lang.reflect.*;
import java.text.Normalizer;
import java.util.*;

/** Searchable local selectors reuse the native fleet screen; only the final form goes to the server.
 * Reflection supports both development names and the pinned 1.20.1 runtime names.
 * No replacement Minecraft widgets or API copies are shipped.
 */
public final class FleetUiHooks {
    public record Choice(String value,String name,String detail) {}
    private record Selection(String display,String id) {}
    private static final Map<Object,Map<Integer,Selection>> SELECTED=new WeakHashMap<>();
    private static final class Picker {
        final Object parent;final String locale;final int field;final boolean ship;final List<Choice> choices;
        final String id=UUID.randomUUID().toString();String search="";int page=1;
        Picker(Object parent,int field,boolean ship,List<Choice> choices){this.parent=parent;this.locale=locale(parent);this.field=field;this.ship=ship;this.choices=choices;}
    }
    private static final Map<Object,Picker> PICKERS=new WeakHashMap<>();
    private static final Map<String,Picker> OPENING=new HashMap<>();
    private FleetUiHooks(){}
    private static String text(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private static JsonArray array(JsonObject data,String key){return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray();}
    private static String locale(Object screen){try{return text((JsonObject)get(screen,"data"),"locale","en");}catch(ReflectiveOperationException ex){return "en";}}
    private static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    public static List<Choice> choices(JsonObject data,JsonObject schema,boolean ship){
        List<Choice> out=new ArrayList<>();
        if(!ship && Set.of("captain","admiral").contains(text(schema,"id","")))out.add(new Choice("-",ClientText.tr("Зняти призначення",text(data,"locale","en")),ClientText.tr("Капітан або адмірал більше не призначений.",text(data,"locale","en"))));
        for(var raw:array(data,ship?"ships":"players")){
            var row=raw.getAsJsonObject();String name=text(row,"name",""),id=text(row,"id","");
            if(ship){
                String use=text(schema,"shipUse","");boolean allow=false;for(var item:array(row,"uses"))if(item.getAsString().equals(use))allow=true;if(!allow)continue;
                out.add(new Choice(id,name,ClientText.tr("Місто: ",text(data,"locale","en"))+text(row,"town","")+" · #"+id));
            }else out.add(new Choice(name,name,ClientText.tr("Мешканець Towny",text(data,"locale","en"))));
        }return List.copyOf(out);
    }
    public static List<Integer> matches(List<Choice> choices,String search){
        String key=normalized(search.strip());List<Integer> result=new ArrayList<>();
        for(int i=0;i<choices.size();i++){var choice=choices.get(i);if(normalized(choice.name()+" "+choice.detail()).contains(key))result.add(i);}return result;
    }
    private static Field field(Object target,String...names)throws ReflectiveOperationException {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(String name:names)try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(String.join("/",names));
    }
    private static Object get(Object target,String...names)throws ReflectiveOperationException {return field(target,names).get(target);}
    private static void set(Object target,String name,Object value)throws ReflectiveOperationException {field(target,name).set(target,value);}
    private static Object invoke(Object target,Class<?>[] types,Object[] values,String...names)throws ReflectiveOperationException {
        for(Class<?> owner=target.getClass();owner!=null;owner=owner.getSuperclass())for(String name:names)try{var method=owner.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,values);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(String.join("/",names));
    }
    private static String value(Object box)throws ReflectiveOperationException {return (String)invoke(box,new Class<?>[0],new Object[0],"getValue","m_94155_");}
    private static void value(Object box,String value)throws ReflectiveOperationException {invoke(box,new Class<?>[]{String.class},new Object[]{value},"setValue","m_94144_");}
    private static void remember(Object screen,int index,String display,String id){SELECTED.computeIfAbsent(screen,key->new HashMap<>()).put(index,new Selection(display,id));}
    public static List<String> values(Object screen,String form,List<String> source){
        try{
            JsonObject schema=(JsonObject)get(screen,"schema");if(schema==null||!form.equals(text(schema,"id","")))return source;
            var selections=SELECTED.get(screen);if(selections==null)return source;List<String> result=new ArrayList<>(source);
            for(var entry:selections.entrySet())if(entry.getKey()<result.size()&&result.get(entry.getKey()).equals(entry.getValue().display()))result.set(entry.getKey(),entry.getValue().id());return result;
        }catch(ReflectiveOperationException unavailable){return source;}
    }
    private static void refresh(Object screen)throws ReflectiveOperationException {invoke(screen,new Class<?>[0],new Object[0],"init","m_7856_","m_7861_");}
    private static void show(Object screen)throws ReflectiveOperationException {
        Class<?> minecraft=Class.forName("net.minecraft.client.Minecraft");Object client;
        try{client=minecraft.getMethod("getInstance").invoke(null);}catch(NoSuchMethodException production){client=minecraft.getMethod("m_91087_").invoke(null);}
        invoke(client,new Class<?>[]{Class.forName("net.minecraft.client.gui.screens.Screen")},new Object[]{screen},"setScreen","m_91152_");
    }
    private static void error(Object screen,Exception ex){try{((JsonObject)get(screen,"data")).addProperty("error",ClientText.tr("Не вдалося відкрити вибір. Можна ввести повну назву корабля вручну.",locale(screen)));}catch(ReflectiveOperationException ignored){}System.err.println("[Empirecraft fleet selector] "+ex);}
    public static void init(Object screen){try{
        JsonObject data=(JsonObject)get(screen,"data");String pending=text(data,"pickerSession","");if(!pending.isEmpty()&&OPENING.containsKey(pending))PICKERS.put(screen,OPENING.remove(pending));
        if(PICKERS.containsKey(screen))return;
        JsonObject schema=(JsonObject)get(screen,"schema");if(schema==null)return;
        List<?> fields=(List<?>)get(screen,"fields");int left=((Number)invoke(screen,new Class<?>[0],new Object[0],"left")).intValue();
        int height=((Number)get(screen,"height","f_96544_")).intValue(),visible=Math.max(1,(height-145)/39);
        for(boolean ship:new boolean[]{true,false}){
            String marker=ship?"shipField":"personField";if(!schema.has(marker))continue;int index=schema.get(marker).getAsInt();if(index<0||index>=fields.size())continue;Object box=fields.get(index);
            if(!((Boolean)get(box,"visible","f_93624_")))continue;
            if(ship){String current=value(box),selected=current.isEmpty()?text(data,"selectedShip",""):current;
                for(var raw:array(data,"ships")){var row=raw.getAsJsonObject();if(text(row,"id","").equals(selected)){String display=text(row,"name","")+" (#"+selected+")";value(box,display);remember(screen,index,display,selected);break;}}
                java.util.function.Consumer<String> edited=ignored->{var selections=SELECTED.get(screen);if(selections!=null)selections.remove(index);};
                invoke(box,new Class<?>[]{java.util.function.Consumer.class},new Object[]{edited},"setResponder","m_94151_");
            }
            int width=((Number)get(box,"width","f_93618_")).intValue();field(box,"width","f_93618_").setInt(box,Math.max(40,width-86));
            var options=choices(data,schema,ship);int y=77+(index%visible)*39;
            invoke(screen,new Class<?>[]{String.class,int.class,int.class,int.class,Runnable.class},new Object[]{"Обрати",left+10+width-82,y,80,(Runnable)()->open(screen,index,ship,options)},"button");
        }
    }catch(ReflectiveOperationException|RuntimeException ex){error(screen,ex);}}
    private static JsonObject row(String id,String title,String detail,String label,String action,String value){
        JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("title",title);row.addProperty("detail",detail);
        JsonArray buttons=new JsonArray();JsonObject button=new JsonObject();button.addProperty("label",label);button.addProperty("action",action);button.addProperty("form","");button.addProperty("value",value);buttons.add(button);row.add("buttons",buttons);return row;
    }
    private static JsonObject data(Picker picker){
        var matches=matches(picker.choices,picker.search);int pages=Math.max(1,(matches.size()+8)/9);picker.page=Math.max(1,Math.min(pages,picker.page));
        JsonObject data=new JsonObject();data.addProperty("locale",picker.locale);data.addProperty("session","");data.addProperty("section","picker");data.addProperty("pickerSession",picker.id);data.addProperty("city",picker.ship?ClientText.tr("Вибір корабля",picker.locale):ClientText.tr("Вибір гравця",picker.locale));
        data.addProperty("page",picker.page);data.addProperty("pages",pages);data.add("forms",new JsonArray());JsonArray rows=new JsonArray();
        rows.add(row("search",ClientText.tr("Пошук: ",picker.locale)+(picker.search.isEmpty()?ClientText.tr("почни друкувати назву",picker.locale):picker.search),ClientText.tr("Назва або місто · Backspace стерти · Enter обрати · Esc назад. Знайдено: ",picker.locale)+matches.size(),ClientText.tr("Повернутися",picker.locale),"picker-cancel",""));
        for(int n=(picker.page-1)*9;n<Math.min(matches.size(),picker.page*9);n++){int index=matches.get(n);var choice=picker.choices.get(index);rows.add(row("choice-"+index,choice.name(),choice.detail(),ClientText.tr("Обрати",picker.locale),"picker-select",Integer.toString(index)));}
        data.add("rows",rows);return data;
    }
    private static void open(Object parent,int field,boolean ship,List<Choice> choices){try{
        Picker picker=new Picker(parent,field,ship,choices);OPENING.clear();OPENING.put(picker.id,picker);
        Class<?> reply=Class.forName("ua.empirecraft.townymap.FleetNetwork$Reply");Object packet=reply.getConstructor(String.class,boolean.class,String.class).newInstance(data(picker).toString(),true,"");
        Class.forName("ua.empirecraft.townymap.client.FleetScreen").getMethod("receive",reply).invoke(null,packet);
    }catch(ReflectiveOperationException|RuntimeException ex){OPENING.clear();error(parent,ex);}}
    private static void refresh(Object screen,Picker picker)throws ReflectiveOperationException {
        set(screen,"data",data(picker));set(screen,"section","picker");set(screen,"schema",null);set(screen,"detail",null);set(screen,"choosing",false);set(screen,"scroll",0);set(screen,"busy",false);refresh(screen);
    }
    private static void choose(Object screen,Picker picker,int index)throws ReflectiveOperationException {
        if(index<0||index>=picker.choices.size())return;
        List<?> fields=(List<?>)get(picker.parent,"fields");if(picker.field>=fields.size())return;Choice choice=picker.choices.get(index);
        String display=picker.ship?choice.name()+" (#"+choice.value()+")":choice.value();value(fields.get(picker.field),display);if(picker.ship)remember(picker.parent,picker.field,display,choice.value());
        PICKERS.remove(screen);show(picker.parent);
    }
    public static boolean send(Object screen,String action,String form,List<String> values,String token,int page){
        Picker picker=PICKERS.get(screen);if(picker==null)return false;
        try{
            if(action.equals("picker-select")&&!values.isEmpty())choose(screen,picker,Integer.parseInt(values.get(0)));
            else if(action.equals("picker-cancel")||!Objects.equals(get(screen,"section"),"picker"))close(screen);
            else if(page!=picker.page){picker.page=page;refresh(screen,picker);}
        }catch(ReflectiveOperationException|RuntimeException ex){error(screen,ex);}return true;
    }
    public static Boolean typed(Object screen,char character,int modifiers){
        Picker picker=PICKERS.get(screen);if(picker==null)return null;
        if(!Character.isISOControl(character)&&picker.search.length()<80){picker.search+=character;picker.page=1;try{refresh(screen,picker);}catch(ReflectiveOperationException ex){error(screen,ex);}}return true;
    }
    public static Boolean key(Object screen,int key,int scan,int modifiers){
        Picker picker=PICKERS.get(screen);if(picker==null)return null;
        try{
            if(key==256){close(screen);return true;}
            if(key==257||key==335){var matches=matches(picker.choices,picker.search);if(!matches.isEmpty())choose(screen,picker,matches.get(0));return true;}
            if(key==259&&!picker.search.isEmpty()){picker.search=picker.search.substring(0,picker.search.offsetByCodePoints(picker.search.length(),-1));picker.page=1;}
            else if(key==261){picker.search="";picker.page=1;}
            else if(key==262)picker.page++;else if(key==263)picker.page--;else return true;
            refresh(screen,picker);
        }catch(ReflectiveOperationException|RuntimeException ex){error(screen,ex);}return true;
    }
    public static boolean close(Object screen){Picker picker=PICKERS.remove(screen);if(picker==null)return false;try{show(picker.parent);}catch(ReflectiveOperationException ex){error(screen,ex);}return true;}
}
