public final class NativeLink {
    public static void main(String[] args) throws Exception {
        String[] classes = {"ua.empirecraft.locks.SteelLockPickItem", "ua.empirecraft.locks.SteelPickingMenu",
                "ua.empirecraft.locks.client.SteelLockPickingScreen", "ua.empirecraft.locks.client.PickingClient",
                "melonslise.locks.client.init.LocksScreens", "ua.empirecraft.locks.PickPackets"};
        for (String name : classes) {
            var type = Class.forName(name, false, NativeLink.class.getClassLoader());
            type.getDeclaredMethods(); type.getDeclaredConstructors(); type.getDeclaredFields();
        }
        System.out.println("STEEL_NATIVE_CLIENT_LINK_PASS classes=" + classes.length);
    }
}
