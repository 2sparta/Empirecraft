package ua.empirecraft.feudal;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Executes the compiled citizenship adapter with boundary fixtures, without a Bukkit/Minecraft boot. */
public final class RecruitTownyRuntimeTests {
    static final UUID CAPTAIN=UUID.randomUUID();static final Map<String,Town> TOWNS=new HashMap<>();static Resident resident;
    public static final class Town {final String id;final Set<Resident> people=new HashSet<>();Town(String id){this.id=id;}public void removeResident(Resident r){people.remove(r);r.joined=0;}public void save(){}}
    public static final class Resident {
        Town town;List<String> townRanks=new ArrayList<>(List.of("general")),nationRanks=new ArrayList<>(List.of("assistant"));String title="Captain",surname="Home";long joined=1234;boolean fail;final Set<String> plots=new HashSet<>(Set.of("home:3,5","home:3,6"));
        public Town getTownOrNull(){return town;}public boolean hasTown(){return town!=null;}public boolean isMayor(){return false;}public boolean isKing(){return false;}
        public List<String> getTownRanks(){return townRanks;}public List<String> getNationRanks(){return nationRanks;}public String getTitle(){return title;}public String getSurname(){return surname;}public long getJoinedTownAt(){return joined;}
        public void setTownRanks(List<String> values){townRanks=new ArrayList<>(values);}public void setNationRanks(List<String> values){nationRanks=new ArrayList<>(values);}public void setTitle(String value){title=value;}public void setSurname(String value){surname=value;}public void setJoinedTownAt(long value){joined=value;}
        public void setTown(Town value,boolean events){town=value;title="";surname="";if(value!=null)value.people.add(this);}
        public void save(){if(fail){fail=false;throw new IllegalStateException("Simulated Towny save failure");}}
        // Deliberately no removeTown(): production must not call the API that strips ordinary plots.
    }
    public static final class API {public static API getInstance(){return new API();}public Resident getResident(UUID id){return id.equals(CAPTAIN)?resident:null;}}
    public static final class Fleet {public static Town town(String id){return TOWNS.get(id);}}
    public static final class Towny {public static String townId(Town town){return town.id;}}
    public static class Event {}
    public static final class Removed extends Event {public Removed(Resident resident,Town town){}}
    public static final class Added extends Event {public Added(Resident resident,Town town){}}
    public interface Events {void callEvent(Event event);}
    public static final class EventLog implements Events {int count;public void callEvent(Event event){count++;}}
    public static final class Bukkit {static final EventLog EVENTS=new EventLog();public static Events getPluginManager(){return EVENTS;}public static boolean isPrimaryThread(){return true;}}
    static final class Loader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}}
    /** Constant-pool type substitution only: production bytecode, control flow and exception tables stay intact. */
    static byte[] remap(byte[] source,Map<String,String> names)throws IOException {
        var input=new DataInputStream(new ByteArrayInputStream(source));var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(input.readInt());out.writeShort(input.readUnsignedShort());out.writeShort(input.readUnsignedShort());int count=input.readUnsignedShort();out.writeShort(count);
        var keys=names.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for(int i=1;i<count;i++){int tag=input.readUnsignedByte();out.writeByte(tag);if(tag==1){String value=input.readUTF();for(String name:keys)value=value.replace(name,names.get(name));out.writeUTF(value);}else {int size=switch(tag){case 3,4,9,10,11,12,17,18->4;case 5,6->8;case 7,8,16,19,20->2;case 15->3;default->throw new IOException("Unexpected constant tag "+tag);};out.write(input.readNBytes(size));if(tag==5||tag==6)i++;}}
        input.transferTo(out);return bytes.toByteArray();
    }
    static Object adapter()throws Exception {
        String source="ua/empirecraft/feudal/RecruitWorld",f="ua/empirecraft/feudal/RecruitTownyRuntimeTests$";Map<String,String> names=new HashMap<>();
        names.put(source,"ua/empirecraft/feudal/RecruitWorldRuntime");names.put("ua/empirecraft/feudal/FleetWorld",f+"Fleet");names.put("ua/empirecraft/feudal/TownyBridge",f+"Towny");
        names.put("com/palmergames/bukkit/towny/TownyAPI",f+"API");names.put("com/palmergames/bukkit/towny/object/Resident",f+"Resident");names.put("com/palmergames/bukkit/towny/object/Town",f+"Town");
        names.put("com/palmergames/bukkit/towny/event/TownRemoveResidentEvent",f+"Removed");names.put("com/palmergames/bukkit/towny/event/TownAddResidentEvent",f+"Added");names.put("org/bukkit/Bukkit",f+"Bukkit");names.put("org/bukkit/plugin/PluginManager",f+"Events");names.put("org/bukkit/event/Event",f+"Event");
        byte[] raw;try(var in=RecruitTownyRuntimeTests.class.getClassLoader().getResourceAsStream(source+".class")){raw=in.readAllBytes();}
        Class<?> type=new Loader().define(remap(raw,names));var constructor=type.getDeclaredConstructor(Fleet.class);constructor.setAccessible(true);return constructor.newInstance(new Fleet());
    }
    static void invoke(Object adapter,String method,Class<?>[] types,Object...args)throws Exception {var m=adapter.getClass().getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(adapter,args);}
    public static void main(String[] args)throws Exception {
        Town home=new Town("t:"+UUID.randomUUID()),buyer=new Town("t:"+UUID.randomUUID());TOWNS.put(home.id,home);TOWNS.put(buyer.id,buyer);resident=new Resident();resident.town=home;home.people.add(resident);
        Object adapter=adapter();Method capture=adapter.getClass().getDeclaredMethod("citizenship",String.class);capture.setAccessible(true);String saved=(String)capture.invoke(adapter,CAPTAIN.toString());
        Class<?>[] signature={String.class,String.class,String.class,boolean.class};
        invoke(adapter,"move",signature,CAPTAIN.toString(),buyer.id,saved,false);
        if(resident.town!=buyer||home.people.contains(resident)||!buyer.people.contains(resident)||!resident.townRanks.isEmpty()||!resident.nationRanks.isEmpty()||resident.plots.size()!=2)throw new AssertionError("Bad temporary transfer");
        invoke(adapter,"move",signature,CAPTAIN.toString(),buyer.id,saved,false); // Recovery replay does not consume or reset data.
        invoke(adapter,"move",signature,CAPTAIN.toString(),home.id,saved,true);
        if(resident.town!=home||!home.people.contains(resident)||buyer.people.contains(resident)||!resident.townRanks.equals(List.of("general"))||!resident.nationRanks.equals(List.of("assistant"))||!resident.title.equals("Captain")||!resident.surname.equals("Home")||resident.joined!=1234||resident.plots.size()!=2)throw new AssertionError("Bad restoration");
        resident.fail=true;try{invoke(adapter,"move",signature,CAPTAIN.toString(),buyer.id,saved,false);throw new AssertionError("Save failure accepted");}catch(InvocationTargetException expected){if(!(expected.getCause() instanceof IllegalStateException))throw expected;}
        if(resident.town!=home||buyer.people.contains(resident)||!home.people.contains(resident)||!resident.townRanks.equals(List.of("general"))||resident.joined!=1234||resident.plots.size()!=2)throw new AssertionError("Failed move was not compensated");
        var moving=adapter.getClass().getDeclaredField("moving");moving.setAccessible(true);if(!((Set<?>)moving.get(adapter)).isEmpty())throw new AssertionError("Transition guard leaked");
        if(Bukkit.EVENTS.count!=4)throw new AssertionError("Unexpected membership events: "+Bukkit.EVENTS.count);
        for(String event:List.of("com.palmergames.bukkit.towny.event.town.TownLeaveEvent","com.palmergames.bukkit.towny.event.town.TownKickEvent","com.palmergames.bukkit.towny.event.TownPreAddResidentEvent","com.palmergames.bukkit.towny.event.PreDeleteTownEvent","com.palmergames.bukkit.towny.event.town.TownPreMergeEvent")){
            Class<?> type=Class.forName(event);if(!org.bukkit.event.Cancellable.class.isAssignableFrom(type))throw new AssertionError(event);
            boolean found=false;for(var method:RecruitTownyGuard.class.getDeclaredMethods())if(Arrays.equals(method.getParameterTypes(),new Class<?>[]{type})){var annotation=method.getAnnotation(org.bukkit.event.EventHandler.class);found=annotation!=null&&annotation.priority()==org.bukkit.event.EventPriority.HIGHEST&&annotation.ignoreCancelled();}if(!found)throw new AssertionError("Missing native Towny guard: "+event);
        }
        System.out.println("PASS executed production Towny citizenship adapter: real JSON snapshot, plot retention, temporary rank removal, origin/rank/title/join-date restoration, idempotent replay, compensated save failure, guard cleanup and real cancellable-event bindings; no server boot simulated");
    }
}
