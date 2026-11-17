package ua.empirecraft.townymap.client;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.townymap.*;
import static ua.empirecraft.townymap.client.ClientAccess.*;

/** Compact tools and an in-place political sidebar; the JourneyMap screen keeps rendering and panning. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID,value=Dist.CLIENT)
public final class StrategyMapUi {
    private enum Panel { NONE, COUNTRY, ROUTES, FLEET }
    private record Hit(int x,int y,int width,int height,Runnable action) {
        boolean contains(double mx,double my){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    }
    private static final Gson JSON=new Gson();
    private static final List<Hit> hits=new ArrayList<>();
    private static Object bound;
    private static Panel panel=Panel.NONE;
    private static MapMode mode=MapMode.KINGDOMS;
    private static boolean modes,layers,ships=true,army=true,routes=true;
    private static boolean nativeTools;
    private static final Map<Object,Boolean> nativeVisibility=new IdentityHashMap<>();
    private static String request="",message="",input="",edit="";
    private static MapHistoryNetwork.Reply reply;
    private static long requestedAt,focus=-1;
    private static int scroll,entry,ticks,retries;
    private static String routeDimension="";
    private static int lastX,lastZ;
    private static String lastDimension="minecraft:overworld";
    private static List<ShipObservation> observations=List.of();
    private static Connection observationConnection;
    private static Connection sessionConnection;
    private static final ShipObservation.Pages pages=new ShipObservation.Pages();

    public static void bind(IClientAPI api) {
        ShipMapLayer.bind(api);RouteMapLayer.bind(api);
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(EmpireTownyMap.MOD_ID,event->{
            var menu=event.getPopupMenu();
            menu.addMenuItem("Відомості про ділянку",position->mapClick(lastDimension,position.getX(),position.getZ()));
            if(RecruitsMapTools.available()) {
                menu.addMenuItem("Recruits · маршрути",position->{panel=Panel.ROUTES;scroll=0;});
                menu.addMenuItem("Recruits · додати точку маршруту",position->act(()->{RecruitsMapTools.add(position.getX(),position.getZ());RouteMapLayer.refresh();}));
                menu.addMenuItem("Recruits · перемістити обрані загони сюди",position->act(()->RecruitsMapTools.moveGroups(position.getX(),position.getZ())));
                menu.addMenuItem("Recruits · назва найближчої точки",position->act(()->{
                    int index=RecruitsMapTools.nearbyPoint(position.getX(),position.getZ());
                    if(index<0)throw new IllegalArgumentException("Поруч немає точки обраного маршруту");
                    edit="point/"+index;input=Objects.toString(call(RecruitsMapTools.points().get(index),"getName"));panel=Panel.ROUTES;
                }));
                menu.addMenuItem("Recruits · перемістити точку",position->act(()->{
                    int index=RecruitsMapTools.nearbyPoint(position.getX(),position.getZ());
                    if(index<0)throw new IllegalArgumentException("Поруч немає точки обраного маршруту");
                    RecruitsMapTools.movePoint(index);message="Натисніть на нове місце точки на мапі";
                }));
            }
        });
    }
    public static void opened(Object screen) {
        if(!isMap(screen))return;bound=screen;hits.clear();
        nativeTools=false;
        nativeVisibility.clear();
        if(connected(CurrencyNetwork.CHANNEL))CurrencyNetwork.CHANNEL.sendToServer(new CurrencyNetwork.MapQuery());
        Object player=player();if(player!=null){Object level=field(client(),"f_91073_");Object key=call(level,"m_46472_");lastDimension=Objects.toString(call(key,"m_135782_"));}
        RouteMapLayer.refresh();
    }
    private static boolean connected(net.minecraftforge.network.simple.SimpleChannel channel) {
        Object connection=call(client(),"m_91403_");return connection!=null&&channel.isRemotePresent((Connection)call(connection,"m_104910_"));
    }
    public static void selected(MapMode next){mode=next;}
    private static void choose(MapMode next){
        if(!connected(StrategyNetwork.CHANNEL)){message="Потрібен Bridge 1.13 на клієнті й сервері";return;}
        mode=next;modes=false;CurrencyNetwork.CHANNEL.sendToServer(new CurrencyNetwork.MapSelection(mode));
        if(panel==Panel.COUNTRY)query(lastDimension,lastX,lastZ);
    }
    public static void mapClick(String dimension,int x,int z) {
        if(RecruitsMapTools.drawing()) {act(()->{RecruitsMapTools.add(x,z);RouteMapLayer.refresh();});return;}
        lastX=x;lastZ=z;lastDimension=dimension;panel=Panel.COUNTRY;entry=0;scroll=0;query(dimension,x,z);
    }
    private static void query(String dimension,int x,int z) {
        retries=0;sendQuery(dimension,x,z);
    }
    private static void sendQuery(String dimension,int x,int z) {
        reply=null;requestedAt=System.currentTimeMillis();request=UUID.randomUUID().toString();
        if(connected(MapHistoryNetwork.CHANNEL))MapHistoryNetwork.CHANNEL.sendToServer(new MapHistoryNetwork.Query(request,dimension,x,z));
        else message="Сервер не підтримує цю версію панелі";
    }
    public static void receive(MapHistoryNetwork.Reply next){if(next.request().equals(request)){reply=next;scroll=0;}}
    public static void observations(String json,long selected,long batch,int part,int total,Connection source) {
        try {
            Object connection=call(client(),"m_91403_");
            if(connection==null||call(connection,"m_104910_")!=source)return;
            session(source);
            if(observationConnection!=source){pages.clear();observationConnection=source;observations=List.of();ShipMapLayer.clear();}
            List<ShipObservation> rows=JSON.fromJson(json,new TypeToken<List<ShipObservation>>(){}.getType());
            pages.accept(batch,part,total,rows).ifPresent(view->{observations=view;focus=selected;ShipMapLayer.observations(observations);});
        }catch(RuntimeException invalid){message="Пошкоджені відомості про кораблі";}
    }
    private static void act(Runnable action){try{action.run();message="";}catch(RuntimeException failure){message=Objects.toString(failure.getMessage(),"Дія недоступна");}}
    private static void button(Object g,String label,int x,int y,int width,boolean active,Runnable action,int mx,int my) {
        boolean hover=mx>=x&&mx<x+width&&my>=y&&my<y+18;
        fill(g,x,y,x+width,y+18,active?0xf04d574d:hover?0xf0474034:0xee252b2b);
        fill(g,x,y,x+width,y+1,active?0xffd4ba7e:0xff76654b);
        text(g,trim(label,width-8),x+4,y+5,active?0xffe9d5a5:0xffd5cfbe);
        hits.add(new Hit(x,y,width,18,action));
    }
    private static void label(Object g,String value,int y,int width,int color){text(g,trim(value,width-20),16,y,color);}
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        Object screen=call(event,"getScreen");if(!isMap(screen))return;if(bound!=screen)opened(screen);
        Object g=call(event,"getGuiGraphics");int mx=event.getMouseX(),my=event.getMouseY();var layout=StrategyLayout.of(width(screen),height(screen));hits.clear();
        if(!nativeTools) {
            if(Boolean.TRUE.equals(call(screen,"isButtonsVisable")))call(screen,"hideButtons");
            for(Object widget:(List<?>)call(screen,"getRenderables")) {
                if(!type("net.minecraft.client.gui.components.AbstractWidget").isInstance(widget))continue;
                nativeVisibility.putIfAbsent(widget,Boolean.TRUE.equals(field(widget,"f_93623_")));
                write(widget,"f_93623_",false);
            }
        }
        button(g,mode.label+" ▾",6,6,126,modes,()->{modes=!modes;layers=false;},mx,my);
        button(g,"Шари",136,6,45,layers,()->{layers=!layers;modes=false;},mx,my);
        button(g,"Війська",185,6,58,panel==Panel.ROUTES,()->{panel=panel==Panel.ROUTES?Panel.NONE:Panel.ROUTES;scroll=0;},mx,my);
        button(g,"Флот",247,6,43,panel==Panel.FLEET,()->{panel=panel==Panel.FLEET?Panel.NONE:Panel.FLEET;scroll=0;},mx,my);
        if(panel!=Panel.NONE) {
            fill(g,6,layout.panelTop(),layout.panelWidth()+6,layout.panelBottom(),0xf2162226);
            fill(g,6,layout.panelTop(),8,layout.panelBottom(),0xffb99a61);
            String title=switch(panel){case COUNTRY->mode==MapMode.CLIMATE||mode==MapMode.RAINFALL?mode.label:"Володіння та правителі";case ROUTES->"Накази та маршрути";case FLEET->"Морська розвідка";default->"";};
            label(g,title,43,layout.panelWidth()-25,0xffd8bc83);
            button(g,"×",layout.panelWidth()-19,37,20,false,()->{panel=Panel.NONE;edit="";},mx,my);
            if(panel==Panel.COUNTRY)country(g,layout,mx,my);
            if(panel==Panel.ROUTES)routePanel(g,layout,mx,my);
            if(panel==Panel.FLEET)fleetPanel(g,layout,mx,my);
        }
        if(modes)for(int i=0;i<MapMode.values().length;i++){var next=MapMode.values()[i];button(g,next.label,6,30+i*20,160,next==mode,()->choose(next),mx,my);}
        if(layers) {
            button(g,(ships?"✓ ":"  ")+"Кораблі",136,30,145,ships,()->{ships=!ships;ShipMapLayer.enabled(ships);},mx,my);
            button(g,(army?"✓ ":"  ")+"Війська та знамена",136,50,145,army,()->{army=!army;RecruitsMapTools.armyVisible(army);},mx,my);
            button(g,(routes?"✓ ":"  ")+"Маршрути",136,70,145,routes,()->{routes=!routes;RouteMapLayer.enabled(routes);},mx,my);
            button(g,"+ Наблизити",136,92,145,false,()->call(bound,"zoomIn"),mx,my);
            button(g,"− Віддалити",136,112,145,false,()->call(bound,"zoomOut"),mx,my);
            button(g,"До гравця",136,132,145,false,()->{Object p=player();call(bound,"centerOn",((Number)call(p,"m_20185_")).doubleValue(),((Number)call(p,"m_20189_")).doubleValue());},mx,my);
            button(g,"Панелі JourneyMap",136,152,145,nativeTools,()->{nativeTools=!nativeTools;if(nativeTools){nativeVisibility.forEach((widget,visible)->write(widget,"f_93623_",visible));if(!Boolean.TRUE.equals(call(bound,"isButtonsVisable")))call(bound,"hideButtons");}},mx,my);
        }
        if(!message.isEmpty()) {
            int y=layout.height()-45;fill(g,6,y-3,Math.min(layout.width()-6,480),y+14,0xe02d2020);text(g,trim(message,layout.width()-30),12,y,0xffdfb28e);
        }
    }
    private static void country(Object g,StrategyLayout layout,int mx,int my) {
        int w=layout.panelWidth();
        if(reply==null){label(g,System.currentTimeMillis()-requestedAt>5000?"Немає відповіді сервера":"Читаємо збережені відомості…",70,w,0xffbbb29d);return;}
        if(reply.status()!=MapHistoryNetwork.Status.KNOWN||reply.entries().isEmpty()) {
            label(g,reply.status()==MapHistoryNetwork.Status.UNKNOWN?"Територію ще не розвідано":mode==MapMode.CLIMATE||mode==MapMode.RAINFALL?"Відвідайте чанк для вимірювання":"Відомий вільний чанк",70,w,0xffbbb29d);return;
        }
        entry=Math.min(entry,reply.entries().size()-1);var selected=reply.entries().get(entry);
        if(mode==MapMode.CLIMATE||mode==MapMode.RAINFALL) {
            label(g,selected.name(),72,w,0xffefdca9);int y=95;
            for(String line:wrap(selected.detail(),w-22)){label(g,line,y,w,0xffb8ccc2);y+=15;}
            y+=14;for(String line:wrap("Джерело: TerraFirmaCraft. Показані лише виміряні під час відвідування чанки.",w-22)){label(g,line,y,w,0xff9caaa3);y+=15;}
            return;
        }
        if(reply.entries().size()>1)button(g,(entry+1)+"/"+reply.entries().size()+" ›",w-63,62,61,false,()->{entry=(entry+1)%reply.entries().size();scroll=0;},mx,my);
        JsonObject profile;
        try{profile=selected.profile().isBlank()?new JsonObject():JsonParser.parseString(selected.profile()).getAsJsonObject();}
        catch(RuntimeException malformed){profile=new JsonObject();}
        label(g,selected.name(),67,w-60,0xffefdcaa);
        int color=profile.has("color")?profile.get("color").getAsInt():0xa68d57;
        var flag=profile.has("flag")?profile.getAsJsonObject("flag"):null;
        if(!HeraldryIcon.flag(g,flag,16,86,3)) {
            fill(g,19,88,49,126,0xff000000|color);fill(g,23,92,45,122,0xff263137);
            text(g,flag==null?"◆":"⚑",29,103,0xff000000|color);
        }
        text(g,trim(value(profile,"rank",mode.label),w-80),69,90,0xffdbca9b);
        text(g,trim(government(value(profile,"government","")),w-80),69,105,0xffa9c6c4);
        text(g,flag==null?"Колір володіння":"Державне знамено",69,120,0xff9eaa9f);
        var lines=new ArrayList<String>();
        if(profile.has("leaders")) {
            lines.add("КЕРІВНИЦТВО");
            for(var element:profile.getAsJsonArray("leaders")){var leader=element.getAsJsonObject();lines.add("@"+value(leader,"uuid","")+"|"+value(leader,"name","?")+"|"+value(leader,"role","Правитель"));}
            lines.add("");lines.add("Країна: "+value(profile,"country",selected.name()));
            lines.add("Столиця: "+value(profile,"capital","—"));lines.add("Сюзерен: "+value(profile,"overlord","—"));
            if(profile.has("towns"))lines.add("Міст: "+profile.get("towns").getAsString());
            if(profile.has("residents"))lines.add("Мешканців: "+profile.get("residents").getAsString());
            lines.add("Території: "+territory(value(profile,"territory","")));
        }
        lines.add("");lines.add("ВІДОМОСТІ СПОСТЕРЕЖЕННЯ");lines.addAll(wrap(selected.detail(),w-22));
        int y=143;scroll=Math.min(scroll,Math.max(0,lines.size()-1));
        for(int i=scroll;i<lines.size()&&y<layout.panelBottom()-18;i++) {
            String line=lines.get(i);
            if(line.startsWith("@")){String[] person=line.substring(1).split("\\|",3);if(person.length==3){
                HeraldryIcon.head(g,person[0],person[1],16,y);text(g,trim(person[1],w-48),37,y,0xffe8d4ac);
                text(g,trim(person[2],w-48),37,y+10,0xff96b1af);y+=27;continue;}}
            label(g,line,y,w,line.equals(line.toUpperCase(Locale.ROOT))?0xffb99a61:0xffc6cebf);y+=17;
        }
    }
    private static String value(JsonObject row,String key,String fallback){return row.has(key)&&!row.get(key).isJsonNull()?row.get(key).getAsString():fallback;}
    private static String government(String key){return switch(key){case "REPUBLIC"->"Республіка";case "TRIBAL_COUNCIL"->"Племінна рада";case "MONARCHY"->"Монархія";case "ABSOLUTE_MONARCHY"->"Абсолютна монархія";case "SULTANATE"->"Султанат";case "MILITARY_ORDER"->"Військовий орден";default->key.isEmpty()?"Збережена розвідка":key;};}
    private static String territory(String key){return switch(key){case "FEUDAL"->"Феодальні";case "DIRECT"->"Пряме управління";case "AUTONOMOUS"->"Автономні";case "FEDERAL"->"Федеративні";default->key;};}
    private static List<String> wrap(String value,int width){
        var lines=new ArrayList<String>();for(String paragraph:value.replace('§',' ').split("\\n")) {
            String remaining=paragraph;while(!remaining.isEmpty()){String part=trim(remaining,width);if(part.isEmpty())break;
                int space=part.lastIndexOf(' ');if(part.length()<remaining.length()&&space>part.length()/2)part=part.substring(0,space);
                lines.add(part);remaining=remaining.substring(part.length()).stripLeading();}
        }return lines;
    }
    private static void routePanel(Object g,StrategyLayout layout,int mx,int my) {
        int w=layout.panelWidth();
        if(!RecruitsMapTools.available()){label(g,"Потрібен Recruits",70,w,0xffbbb29d);return;}
        if(!edit.isEmpty()) {
            label(g,edit.equals("transfer")?"Передати гравцеві онлайн:":edit.equals("delete")?"Для видалення введіть ТАК:":edit.startsWith("point/")?"Назва точки:":"Назва маршруту:",67,w,0xffc1cdbb);
            fill(g,14,82,w-2,102,0xff101b20);text(g,trim(input+"_",w-30),19,88,0xffefd8a0);
            int half=(w-32)/2;
            button(g,"Зберегти",14,108,half,false,StrategyMapUi::finishEdit,mx,my);
            button(g,"Скасувати",18+half,108,half,false,()->edit="",mx,my);return;
        }
        button(g,"+ Маршрут",14,64,100,false,()->{edit="new";input="Новий маршрут";},mx,my);
        button(g,"Завершити вибір",118,64,w-128,false,()->{RecruitsMapTools.drawing(false);RecruitsMapTools.select(null);RouteMapLayer.refresh();},mx,my);
        if(RecruitsMapTools.selected()==null) {
            int y=92;var rows=RecruitsMapTools.routes();
            if(rows.isEmpty())label(g,"Створіть маршрут і додайте точки на мапі",y,w,0xffa8b7a7);
            for(int i=scroll;i<rows.size()&&y<layout.panelBottom()-22;i++){Object route=rows.get(i);button(g,RecruitsMapTools.name(route),14,y,w-23,false,
                    ()->{RecruitsMapTools.select(route);scroll=0;RouteMapLayer.refresh();},mx,my);y+=22;}
            return;
        }
        label(g,RecruitsMapTools.name(RecruitsMapTools.selected()),91,w,0xffe1c58f);
        int compact=Math.max(35,(w-36)/3);
        button(g,RecruitsMapTools.drawing()?"✓ Точки на мапі":"Додати точки",14,106,compact,RecruitsMapTools.drawing(),
                ()->RecruitsMapTools.drawing(!RecruitsMapTools.drawing()),mx,my);
        button(g,"Назва",14+compact+4,106,compact,false,()->{edit="rename";input=RecruitsMapTools.name(RecruitsMapTools.selected());},mx,my);
        button(g,"Передати",14+2*(compact+4),106,compact,false,()->{edit="transfer";input="";},mx,my);
        button(g,"Призначити патрулю",14,129,2*compact+4,false,StrategyMapUi::assignPatrol,mx,my);
        button(g,"Видалити",14+2*(compact+4),129,compact,false,()->{edit="delete";input="";},mx,my);
        int y=155;var points=RecruitsMapTools.points();scroll=Math.min(scroll,Math.max(0,points.size()-layout.rows(y,40)));
        for(int i=scroll;i<points.size()&&y<layout.panelBottom()-40;i++) {
            final int index=i;var point=points.get(i);var position=RecruitsMapTools.position(point);int seconds=RecruitsMapTools.waitSeconds(point);
            label(g,(i+1)+". "+position.getX()+", "+position.getZ()+" · "+seconds+" с",y,w,0xffbbcabc);
            int step=(w-24)/5,bw=step-3;
            button(g,"↑",14,y+13,bw,false,()->{RecruitsMapTools.move(index,-1);RouteMapLayer.refresh();},mx,my);
            button(g,"↓",14+step,y+13,bw,false,()->{RecruitsMapTools.move(index,1);RouteMapLayer.refresh();},mx,my);
            button(g,"+10 с",14+2*step,y+13,bw,false,()->{RecruitsMapTools.waitSeconds(index,Math.min(86400,seconds+10));RouteMapLayer.refresh();},mx,my);
            button(g,"0 с",14+3*step,y+13,bw,false,()->{RecruitsMapTools.waitSeconds(index,0);RouteMapLayer.refresh();},mx,my);
            button(g,"−",14+4*step,y+13,bw,false,()->{RecruitsMapTools.remove(index);RouteMapLayer.refresh();},mx,my);y+=40;
        }
    }
    private static void assignPatrol(){
        Object entity=field(client(),"f_91076_");
        if(entity==null)throw new IllegalArgumentException("Наведіться на свого командира патруля перед відкриттям мапи");
        RecruitsMapTools.patrol((UUID)call(entity,"m_20148_"));message="Маршрут надіслано командиру; права перевіряє сервер";
    }
    private static void finishEdit(){act(()->{
        if(edit.startsWith("point/")){RecruitsMapTools.renamePoint(Integer.parseInt(edit.substring(6)),input);edit="";RouteMapLayer.refresh();return;}
        switch(edit){case "new"->RecruitsMapTools.createRoute(input);case "rename"->RecruitsMapTools.rename(input);
            case "transfer"->RecruitsMapTools.transfer(input);case "delete"->{if(!input.equals("ТАК"))throw new IllegalArgumentException("Для видалення введіть ТАК");RecruitsMapTools.delete();}}
        edit="";scroll=0;RouteMapLayer.refresh();
    });}
    private static void fleetPanel(Object g,StrategyLayout layout,int mx,int my) {
        int y=68,w=layout.panelWidth();label(g,"Стрілка — останній курс руху",y,w,0xffaac4c3);y+=15;
        label(g,"Бліда позначка — дані вже застаріли",y,w,0xffa3aa9b);y+=22;
        if(observations.isEmpty()){label(g,"Подивіться на корабель у підзорну трубу",y,w,0xffd7c18c);return;}
        scroll=Math.min(scroll,Math.max(0,observations.size()-layout.rows(y,72)));
        for(int i=scroll;i<observations.size()&&y<layout.panelBottom()-66;i++){
            var row=observations.get(i);button(g,row.name(),14,y,w-24,false,()->call(bound,"centerOn",row.x(),row.z()),mx,my);
            label(g,row.country()+" · "+ShipMapLayer.flag(row.flagMode()),y+22,w,0xffd0bd92);
            label(g,ShipMapLayer.motion(row),y+36,w,0xffa7c3c1);
            label(g,FleetWaypoints.description(row.observed()).replace("Останнє спостереження: ",""),y+50,w,0xff8b9a93);y+=72;
        }
    }
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        Object screen=call(event,"getScreen");if(!isMap(screen))return;
        double x=event.getMouseX(),y=event.getMouseY();
        if(event.getButton()==0)for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)) {
            act(hits.get(i).action());event.setCanceled(true);return;
        }
        var layout=StrategyLayout.of(width(screen),height(screen));
        if(panel!=Panel.NONE&&layout.panel(x,y)){event.setCanceled(true);return;}
        modes=false;layers=false;
    }
    @SubscribeEvent public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        Object screen=call(event,"getScreen");if(!isMap(screen)||panel==Panel.NONE)return;
        var layout=StrategyLayout.of(width(screen),height(screen));
        if(layout.panel(event.getMouseX(),event.getMouseY())){scroll=Math.max(0,scroll+(event.getScrollDelta()>0?-1:1));event.setCanceled(true);}
    }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
        if(!isMap(call(event,"getScreen")))return;
        if(event.getKeyCode()==256&&(panel!=Panel.NONE||modes||layers)) {panel=Panel.NONE;modes=false;layers=false;edit="";event.setCanceled(true);return;}
        if(!edit.isEmpty()) {
            if(event.getKeyCode()==259&&!input.isEmpty())input=input.substring(0,input.offsetByCodePoints(input.length(),-1));
            else if(event.getKeyCode()==257)finishEdit();
            else if(event.getKeyCode()==261)input="";
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void character(ScreenEvent.CharacterTyped.Pre event) {
        if(isMap(call(event,"getScreen"))&&!edit.isEmpty()) {
            if(input.length()<64&&!Character.isISOControl(event.getCodePoint()))input+=event.getCodePoint();event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Object connection=call(client(),"m_91403_");
        session(connection==null?null:(Connection)call(connection,"m_104910_"));
        if(connection==null)return;
        Object level=field(client(),"f_91073_");
        if(level!=null) {
            String dimension=Objects.toString(call(call(level,"m_46472_"),"m_135782_"));
            if(!routeDimension.equals(dimension)) {
                RecruitsMapTools.clear();RouteMapLayer.clear();routeDimension=dimension;lastDimension=dimension;
                panel=Panel.NONE;edit="";request="";reply=null;
            }
        }
        // Rapid mode changes can encounter the server's 500 ms query limit.
        if(isMap(screen())&&panel==Panel.COUNTRY&&reply==null&&!request.isEmpty()
                &&retries<3&&System.currentTimeMillis()-requestedAt>=1500) {
            retries++;sendQuery(lastDimension,lastX,lastZ);
        }
        if(++ticks%20==0)ShipMapLayer.refresh();
    }
    private static void session(Connection next) {
        if(sessionConnection==next)return;
        bound=null;panel=Panel.NONE;request="";reply=null;observations=List.of();focus=-1;message="";edit="";
        pages.clear();observationConnection=null;nativeVisibility.clear();hits.clear();routeDimension="";retries=0;
        ShipMapLayer.clear();RouteMapLayer.clear();RecruitsMapTools.clear();HeraldryIcon.clear();sessionConnection=next;
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if(focus<0||player()==null)return;
        if(!Boolean.TRUE.equals(call(player(),"m_6117_")))return;
        var row=observations.stream().filter(s->s.ship()==focus).findFirst().orElse(null);if(row==null)return;
        Object window=call(event,"getWindow"),g=call(event,"getGuiGraphics");int width=((Number)call(window,"m_85445_")).intValue();
        int left=Math.max(8,width/2-120);fill(g,left,32,left+240,92,0xd5162226);
        text(g,trim(row.name()+" · "+row.country(),225),left+8,40,0xffe4c88e);
        text(g,"Прапор: "+ShipMapLayer.flag(row.flagMode()),left+8,56,0xffc1d7cd);
        text(g,ShipMapLayer.motion(row),left+8,72,0xff96c0c7);
    }
}
