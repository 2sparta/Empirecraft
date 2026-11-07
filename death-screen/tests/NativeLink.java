public final class NativeLink {
    public static void main(String[] args) throws Exception {
        int count=0;
        for(String name:new String[]{"com.epicdeathscreen.client.CinematicDeathScreen","com.epicdeathscreen.client.ClientFiles","com.epicdeathscreen.client.EmpirecraftDeaths","com.epicdeathscreen.client.SceneCue","com.epicdeathscreen.client.DeathScreens"}) {
            Class<?> type=Class.forName(name,false,NativeLink.class.getClassLoader());
            type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();count++;
        }
        System.out.println("DEATH_NATIVE_CLIENT_LINK_PASS classes="+count);
    }
}
