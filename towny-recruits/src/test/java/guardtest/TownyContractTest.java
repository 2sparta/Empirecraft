package guardtest;
import java.util.*;
public final class TownyContractTest {
 static Class<?> type(String n)throws Exception{return Class.forName(n,false,TownyContractTest.class.getClassLoader());}
 static void method(String c,String n,Class<?>... params)throws Exception{type(c).getMethod(n,params);}
 public static void main(String[] args)throws Exception{
  String base="com.palmergames.bukkit.towny.";
  for(String n:List.of("getNations","getInstance"))method(base+"TownyAPI",n);
  method(base+"TownyAPI","getTownBlock",type("org.bukkit.Location"));
  for(String n:List.of("getUUID","getName","getKing","getResidents","getTowns","getEnemies","getAllies"))method(base+"object.Nation",n);
  method(base+"object.Resident","getUUID");method(base+"object.Resident","getName");method(base+"object.TownBlock","getTownOrNull");method(base+"object.Town","getNationOrNull");
  method(base+"TownySettings","getTownBlockSize");method("org.bukkit.World","getWorldFolder");
  method(base+"TownyAPI","getResident",UUID.class);method(base+"object.Resident","getNationRanks");method(base+"object.Resident","isKing");
  method(base+"TownyCommandAddonAPI","addSubCommand",type(base+"object.AddonCommand"));
  method(base+"TownyCommandAddonAPI","getAddonCommand",type(base+"TownyCommandAddonAPI$CommandType"),String.class);
  method(base+"TownyCommandAddonAPI","removeSubCommand",type(base+"TownyCommandAddonAPI$CommandType"),String.class);
  type(base+"object.AddonCommand").getConstructor(type(base+"TownyCommandAddonAPI$CommandType"),String.class,type("org.bukkit.command.CommandExecutor"));
  for(String n:List.of("NewNationEvent","DeleteNationEvent","DeleteTownEvent","NationAddTownEvent","NationRemoveTownEvent","TownAddResidentEvent","TownRemoveResidentEvent","NationAddEnemyEvent","NationRemoveEnemyEvent","NationAcceptAllyRequestEvent","NationRemoveAllyEvent","TownClaimEvent","town.TownUnclaimEvent","nation.NationMergeEvent","town.TownMergeEvent","nation.NationKingChangeEvent"))method(base+"event."+n,"getHandlerList");
  System.out.println("PASS reflection contracts and territory/membership/diplomacy event classes in real Towny 0.101.2.0");
 }
}
