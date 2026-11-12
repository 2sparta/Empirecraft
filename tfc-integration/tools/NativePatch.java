import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact-version binary changes; each named target must exist exactly once. */
public final class NativePatch implements Opcodes {
    private static final String P = "net/dries007/tfc/";
    private static final Map<String, Map<String, String>> RULES = new LinkedHashMap<>();
    private static final Set<String> SEEN = new LinkedHashSet<>();
    private static void rule(String owner, String result, String... methods) {
        var map = RULES.computeIfAbsent(P + owner, k -> new LinkedHashMap<>());
        for (String method : methods) map.put(method, result);
    }
    static {
        rule("common/capabilities/food/TFCFoodData", "void", "replaceFoodStats", "restoreFoodStatsAfterDeath");
        rule("common/capabilities/food/FoodHandler", "never_created", "getCreationDate");
        rule("common/capabilities/food/FoodHandler", "never_rots", "getRottenDate");
        rule("common/capabilities/food/IFood", "false", "isRotten");
        rule("common/capabilities/food/FoodCapability", "void", "addTooltipInfo");
        rule("common/capabilities/size/ItemSizeManager", "void", "applyItemStackSizeOverrides", "setupItemStackSizeOverrides", "addTooltipInfo");
        rule("common/capabilities/size/ItemSizeManager", "size", "get");
        rule("ForgeEventHandler", "void", "onWorldLoad", "onPlayerTick", "onSelectClimateModel", "onExplosionDetonate");
        rule("util/tracker/WorldTracker", "void", "addLandslidePos", "addCollapsePositions", "addCollapseData");
        rule("common/recipes/CollapseRecipe", "false", "tryTriggerCollapse", "collapseBlock");
        rule("common/recipes/LandslideRecipe", "false", "tryLandslide");
        rule("common/blockentities/PowderkegBlockEntity", "void", "serverTick");
        rule("common/blockentities/AnvilBlockEntity", "false", "workRemotely");
        rule("common/blockentities/AnvilBlockEntity", "fail", "work", "weld");
        rule("util/calendar/ServerCalendar", "void", "overrideDoDaylightCycleCallback", "setDoDaylightCycleWithNoCallback");
        rule("util/calendar/ServerCalendar", "calendar", "onOverworldTick");
        rule("util/calendar/CalendarEventHandler", "void", "onPlayerWakeUp");
        rule("util/tracker/WeatherHelpers", "void", "preAdvancedWeatherCycle");
        rule("client/IngameOverlays", "void", "checkGuiOverlays", "renderHealth", "renderMountHealth", "renderFood", "renderThirst", "renderExperience", "renderJumpBar", "moveLeftAndRightHeights");
        rule("client/ClientForgeEventHandler", "void", "onInitGuiPost");
    }
    private static void body(MethodNode method, String kind) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        InsnList code = method.instructions;
        switch (kind) {
            case "void" -> code.add(new InsnNode(RETURN));
            case "false" -> { code.add(new InsnNode(ICONST_0)); code.add(new InsnNode(IRETURN)); }
            case "never_created", "never_rots" -> {
                code.add(new LdcInsnNode(kind.equals("never_created") ? -2L : Long.MAX_VALUE));
                code.add(new InsnNode(LRETURN));
            }
            case "size" -> {
                code.add(new FieldInsnNode(GETSTATIC, P+"common/capabilities/size/ItemSizeManager", "DEFAULT_SIZE", "L"+P+"common/capabilities/size/ItemSize;"));
                code.add(new InsnNode(ARETURN));
            }
            case "fail" -> {
                code.add(new LdcInsnNode(Type.getObjectType("net/minecraft/world/InteractionResult")));
                code.add(new LdcInsnNode("FAIL"));
                code.add(new MethodInsnNode(INVOKESTATIC,"java/lang/Enum","valueOf","(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;",false));
                code.add(new TypeInsnNode(CHECKCAST,"net/minecraft/world/InteractionResult"));
                code.add(new InsnNode(ARETURN));
            }
            case "calendar" -> {
                // Read existing world time. Never set world time or a game rule.
                code.add(new VarInsnNode(ALOAD,0)); code.add(new VarInsnNode(ALOAD,1));
                code.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/world/level/Level","m_46468_","()J",false));
                code.add(new FieldInsnNode(PUTFIELD,P+"util/calendar/Calendar","calendarTicks","J"));
                code.add(new InsnNode(RETURN));
            }
            default -> throw new IllegalArgumentException(kind);
        }
        method.maxStack = 4;
    }
    public static void main(String[] args) throws Exception {
        List<String> changes = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(Path.of(args[0])));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for (ZipEntry entry; (entry=in.getNextEntry())!=null;) {
                byte[] bytes = in.readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    String owner = entry.getName().substring(0,entry.getName().length()-6);
                    if (RULES.containsKey(owner) || owner.equals(P+"util/PhysicalDamageType") || owner.equals(P+"mixin/BiomeMixin")) {
                        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node,0);
                        if(owner.equals(P+"mixin/BiomeMixin")){
                            int before=node.methods.size();
                            node.methods.removeIf(m->Set.of("betterShouldSnowCheck","shouldFreezeWithClimate","getPrecipitationFromClimate").contains(m.name));
                            if(before-node.methods.size()!=3)throw new IllegalStateException("Unexpected biome weather mixins");
                            changes.add(owner+"#removeWeatherRedirects:3");
                        }
                        Map<String,String> rules = RULES.getOrDefault(owner,Map.of());
                        for (MethodNode method : node.methods) {
                            if (rules.containsKey(method.name)) {
                                body(method,rules.get(method.name));
                                SEEN.add(owner+"#"+method.name); changes.add(owner+"#"+method.name+method.desc);
                            }
                            if (owner.equals(P+"util/PhysicalDamageType") && method.name.equals("getTypeForSource")) {
                                boolean slash = false; int fixed = 0;
                                for (AbstractInsnNode insn : method.instructions) if (insn instanceof FieldInsnNode f) {
                                    if (f.name.equals("IS_SLASHING")) slash = true;
                                    else if (slash && f.owner.equals(owner) && f.name.equals("CRUSHING")) { f.name="SLASHING"; slash=false; fixed++; }
                                }
                                if (fixed!=1) throw new IllegalStateException("Unexpected slashing dispatch: "+fixed);
                                changes.add(owner+"#getTypeForSource:slashing");
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        node.accept(writer); bytes=writer.toByteArray();
                    }
                }
                ZipEntry e = new ZipEntry(entry.getName()); e.setTime(0); out.putNextEntry(e); out.write(bytes); out.closeEntry();
            }
        }
        for (var owner : RULES.entrySet()) for (String method : owner.getValue().keySet())
            if (!SEEN.contains(owner.getKey()+"#"+method)) throw new IllegalStateException("Missing patch target: "+owner.getKey()+"#"+method);
        Files.write(Path.of(args[2]), changes);
        System.out.println("Native TFC methods patched: "+changes.size());
    }
}
