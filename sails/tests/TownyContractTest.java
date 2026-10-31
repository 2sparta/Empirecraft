/** Check every reflected API against the actual shipped Towny and Mohist Bukkit classes (run on Java 21). */
public final class TownyContractTest {
    public static void main(String[] args) throws Exception {
        Class<?> api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",false,TownyContractTest.class.getClassLoader());
        Class<?> town=Class.forName("com.palmergames.bukkit.towny.object.Town",false,TownyContractTest.class.getClassLoader());
        Class<?> bukkit=Class.forName("org.bukkit.Bukkit",false,TownyContractTest.class.getClassLoader());
        api.getMethod("getInstance");api.getMethod("getTown",java.util.UUID.class);api.getMethod("getTown",String.class);
        api.getMethod("getTown",Class.forName("org.bukkit.Location"));api.getMethod("isTownyWorld",Class.forName("org.bukkit.World"));
        api.getMethod("getResident",java.util.UUID.class);api.getMethod("getResident",String.class);
        for(String method:new String[]{"getUUID","getName","getMayor","getNationOrNull"})town.getMethod(method);
        town.getMethod("isAlliedWith",town);
        Class.forName("com.palmergames.bukkit.towny.object.Resident").getMethod("getTownOrNull");
        Class.forName("com.palmergames.bukkit.towny.TownySettings").getMethod("getTownBlockSize");
        bukkit.getMethod("isPrimaryThread");bukkit.getMethod("getPlayer",java.util.UUID.class);bukkit.getMethod("getPluginManager");
        Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class);
        Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled");
        Class.forName("org.bukkit.permissions.Permissible").getMethod("hasPermission",String.class);
        System.out.println("TOWNY_ACTUAL_API_CONTRACT_PASS");
    }
}
