package ua.empirecraft.worldrules;

/** Match registry IDs, never vanilla doors or decorative models inside another block. */
public final class ContentPolicy {
    public static boolean createDoor(String id) {
        return id!=null && id.startsWith("create:") && (id.endsWith("_door") || id.endsWith("_trapdoor"));
    }
    private ContentPolicy() {}
}
