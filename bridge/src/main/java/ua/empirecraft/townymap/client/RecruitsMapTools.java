package ua.empirecraft.townymap.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import static ua.empirecraft.townymap.client.ClientAccess.*;

/** Uses the original routes, save format and server-checked packets. It never opens the old world map. */
public final class RecruitsMapTools {
    private static final String PREFIX="com.talhanation.recruits.";
    private static Object selected;
    private static boolean drawing;
    private static int sequence;
    private static int movingPoint=-1;
    public static boolean available(){try{type(PREFIX+"client.ClientManager");return true;}catch(IllegalStateException missing){return false;}}
    private static Class<?> manager(){return type(PREFIX+"client.ClientManager");}
    public static List<?> routes(){return available()?List.copyOf((List<?>)call(manager(),"getRoutesList")):List.of();}
    public static Object selected(){return selected;}
    public static void select(Object route){selected=route;drawing=false;movingPoint=-1;}
    public static boolean drawing(){return drawing||movingPoint>=0;}
    public static void drawing(boolean value){drawing=value&&selected!=null;if(!value)movingPoint=-1;}
    public static String name(Object route){return Objects.toString(call(route,"getName"));}
    public static void createRoute(String name){selected=create(PREFIX+"world.RecruitsRoute",safeName(name));save();drawing=true;}
    public static void rename(String name){if(selected!=null)call(manager(),"renameRoute",selected,safeName(name));}
    private static String safeName(String value){String name=value.strip();if(name.isEmpty()||name.length()>64)throw new IllegalArgumentException("Назва: 1–64 символи");return name;}
    private static void save(){if(selected!=null)call(manager(),"saveRoute",selected);}
    @SuppressWarnings("unchecked") public static List<Object> points(){return selected==null?List.of():(List<Object>)call(selected,"getWaypoints");}
    public static BlockPos position(Object point){return (BlockPos)call(point,"getPosition");}
    public static int waitSeconds(Object point){Object action=call(point,"getAction");return action==null?0:((Number)call(action,"getWaitSeconds")).intValue();}
    public static void add(int x,int z){
        if(selected==null)return;
        int y=((Number)call(player(),"m_146904_")).intValue();
        if(movingPoint>=0&&movingPoint<points().size()) {
            call(points().get(movingPoint),"setPosition",new BlockPos(x,y,z));movingPoint=-1;save();return;
        }
        if(points().size()>=512)return;
        call(selected,"addWaypoint",create(PREFIX+"world.RecruitsRoute$Waypoint","Точка "+(points().size()+1),new BlockPos(x,y,z),null));save();
    }
    public static int nearbyPoint(int x,int z) {
        int nearest=-1;double best=12*12;
        for(int i=0;i<points().size();i++) {var position=position(points().get(i));double dx=position.getX()-x,dz=position.getZ()-z,distance=dx*dx+dz*dz;
            if(distance<=best){best=distance;nearest=i;}}
        return nearest;
    }
    public static void movePoint(int index){if(index>=0&&index<points().size()){movingPoint=index;drawing=false;}}
    public static void renamePoint(int index,String name){
        if(index>=0&&index<points().size()) {Object old=points().get(index);
            points().set(index,create(PREFIX+"world.RecruitsRoute$Waypoint",safeName(name),position(old),call(old,"getAction")));save();}
    }
    public static void remove(int index){if(index>=0&&index<points().size()){call(selected,"removeWaypoint",points().get(index));save();}}
    public static void move(int index,int delta){int next=index+delta;if(index>=0&&next>=0&&index<points().size()&&next<points().size()){Collections.swap(points(),index,next);save();}}
    @SuppressWarnings({"unchecked","rawtypes"}) public static void waitSeconds(int index,int seconds){
        if(index<0||index>=points().size()||seconds<0||seconds>86400)return;
        Object type=Enum.valueOf((Class)type(PREFIX+"world.RecruitsRoute$WaypointAction$Type"),"WAIT");
        call(points().get(index),"setAction",create(PREFIX+"world.RecruitsRoute$WaypointAction",type,seconds));save();
    }
    public static void delete(){if(selected!=null)call(manager(),"deleteRoute",selected);selected=null;drawing=false;movingPoint=-1;}
    public static void transfer(String username){
        if(selected==null)return;Object connection=call(client(),"m_91403_");UUID target=null;
        for(Object info:(Collection<?>)call(connection,"m_105142_")){Object profile=call(info,"m_105312_");
            if(username.equalsIgnoreCase(Objects.toString(call(profile,"getName")))){target=(UUID)call(profile,"getId");break;}}
        if(target==null)throw new IllegalArgumentException("Гравець має бути онлайн");
        send(create(PREFIX+"network.MessageTransferRoute",target,selected));
    }
    private static void send(Object packet){call(field(type(PREFIX+"Main"),"SIMPLE_CHANNEL"),"sendToServer",packet);}
    public static void moveGroups(int x,int z){
        var ids=new ArrayList<UUID>();for(Object group:(List<?>)field(manager(),"groups"))
            if(ids.size()<16&&!Boolean.TRUE.equals(field(group,"removed"))&&!Boolean.TRUE.equals(call(group,"isDisabled"))&&((Number)call(group,"getCount")).intValue()>0)ids.add((UUID)call(group,"getUUID"));
        if(ids.isEmpty())throw new IllegalArgumentException("Спочатку оберіть загони в меню наказів Recruits");
        // Reuse Towny Recruits' permission, navigation, formation and range validation.
        Object mode=enumValue("ua.townyrecruits.tactics.TacticalOrder$Mode","MOVE");
        Object shape=enumValue("ua.townyrecruits.tactics.TacticalOrder$Formation","KEEP");
        var at=new net.minecraft.world.phys.Vec3(x,((Number)call(player(),"m_20186_")).doubleValue(),z);
        Object order=create("ua.townyrecruits.tactics.TacticalOrder",mode,shape,List.copyOf(ids),at,0f,false);
        call(field(type("ua.townyrecruits.tactics.TacticalNetwork"),"CHANNEL"),"sendToServer",
                create("ua.townyrecruits.tactics.TacticalNetwork$Request",++sequence,1,order));
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static Object enumValue(String name,String value){return Enum.valueOf((Class)type(name),value);}
    public static void patrol(UUID leader){
        if(selected==null||points().isEmpty())throw new IllegalArgumentException("Оберіть непорожній маршрут");
        send(create(PREFIX+"network.MessagePatrolLeaderSetRoute",leader,call(selected,"getId"),
                points().stream().map(RecruitsMapTools::position).toList(),points().stream().map(RecruitsMapTools::waitSeconds).toList()));
    }
    public static void armyVisible(boolean value){
        try{call(type("ua.townyrecruits.client.JourneyMapArmyLayer"),"visible",value);}catch(IllegalStateException oldVersion){/* Older army layer remains independently selectable in JourneyMap. */}
    }
    public static void clear(){selected=null;drawing=false;movingPoint=-1;}
}
