package ua.empirecraft.interactionstest;

import com.mctabletop.neoforge.block.GameTableBlockEntity;
import com.mctabletop.neoforge.item.BoardInsertItem;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import me.mss1r.siegeworks.gameplay.ballistics.SiegeBallisticsEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import ua.empirecraft.armory.AssemblyRecipe;
import ua.empirecraft.interactions.ThrownImpacts;

@Mod("gameplayprobe")
public final class GameplayProbe {
    private int checks;
    private int ticksBeforeShutdown;

    public GameplayProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::shutdown);
    }

    private void check(boolean success, String label) {
        if (!success) throw new AssertionError(label);
        checks++;
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    private static Block block(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
    }

    static ServerPlayer player(MinecraftServer server, ServerLevel level, String name) throws Exception {
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = Connection.class.getDeclaredField("f_129468_");
        channel.setAccessible(true);
        channel.set(connection, new EmbeddedChannel());
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var immunity = ServerPlayer.class.getDeclaredField("f_8921_");
        immunity.setAccessible(true);
        immunity.setInt(player, 0);
        return player;
    }

    private void started(ServerStartedEvent event) {
        var server = event.getServer();
        ServerPlayer testPlayer = null;
        try {
            var level = server.overworld();
            level.getChunk(2, 2);
            var user = player(server, level, "PlayerProbe");
            testPlayer = user;
            user.setPos(36.5, 76, 36.5);
            level.addNewPlayer(user);
            check(!user.hasPermissions(2) && !user.isCreative(), "Fixture is ordinary survival");
            verify("fire/Alex blocks", () -> fire(level,user));
            verify("tabletop", () -> tabletop(level,user));
            verify("dark armor", () -> armor(level));
            verify("material properties", () -> blocks(level));
            verify("wildlife", () -> wildlife(level,user));
            verify("projectile", () -> projectile(level,user,server));
            verifyExtension("seasonal world", "SeasonalProbe", level, user);
            verifyExtension("pavise and TFC glassware", "ShieldGlasswareProbe", level, user);
            if (failures != 0) throw new AssertionError(failures + " check groups failed");
            System.out.println("GAMEPLAY_NATIVE_PASS checks=" + checks);
        } catch (Throwable failure) {
            System.out.println("GAMEPLAY_NATIVE_FAIL " + failure);
            failure.printStackTrace();
        } finally {
            if (testPlayer != null) server.overworld().removePlayerImmediately(testPlayer, Entity.RemovalReason.DISCARDED);
            // Drain startup chunk tasks before stopping the disposable server.
            ticksBeforeShutdown = 20;
        }
    }

    private void shutdown(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ticksBeforeShutdown > 0 && --ticksBeforeShutdown == 0) {
            event.getServer().halt(false);
        }
    }

    private void verifyExtension(String name, String type, ServerLevel level, ServerPlayer player) {
        try {
            var extension = Class.forName("ua.empirecraft.interactionstest." + type);
            verify(name, () -> extension.getMethod("run", ServerLevel.class, ServerPlayer.class).invoke(null, level, player));
        } catch (ClassNotFoundException standalone) {
            // The baseline probe can also run without either extension.
        }
    }

    @FunctionalInterface private interface Scenario { void run() throws Exception; }
    private int failures;
    private void verify(String name, Scenario scenario) {
        try {
            scenario.run();
            System.out.println("GAMEPLAY_SCENARIO_PASS " + name);
        } catch (Throwable failure) {
            failures++;
            System.out.println("GAMEPLAY_SCENARIO_FAIL " + name + " " + failure);
            failure.printStackTrace();
        }
    }

    private static BlockHitResult top(BlockPos block) {
        return new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false);
    }

    private void fire(ServerLevel level, ServerPlayer user) {
        var stone = new BlockPos(34, 72, 34);
        var wood = stone.offset(3, 0, 0);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(wood, Blocks.OAK_PLANKS.defaultBlockState());
        for (Item igniter : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(igniter));
            check(!igniter.useOn(new UseOnContext(user, InteractionHand.MAIN_HAND, top(stone))).consumesAction(), "Stone ignition denied");
            check(igniter.useOn(new UseOnContext(user, InteractionHand.MAIN_HAND, top(wood))).consumesAction(), "Wood can be ignited");
            check(level.getBlockState(wood.above()).is(Blocks.FIRE), "Actual arrow/tool fire on wood");
            level.removeBlock(wood.above(), false);
        }
        check(!level.setBlockAndUpdate(stone.above(), Blocks.FIRE.defaultBlockState()), "Unsupported direct fire denied");
        check(level.setBlockAndUpdate(wood.above(), Blocks.FIRE.defaultBlockState()), "Supported direct fire admitted");
        for (String name : List.of("transmutation_table", "ender_residue", "sculk_boomer"))
            check(!level.setBlockAndUpdate(stone.above(), block("alexsmobs:" + name).defaultBlockState()), "Disabled Alex block "+name);
    }

    private void tabletop(ServerLevel level, ServerPlayer user) throws Exception {
        var legacyPos = new BlockPos(36, 74, 46);
        level.setBlockAndUpdate(legacyPos, block("mctabletop:small_game_table").defaultBlockState());
        var legacy = (GameTableBlockEntity) level.getBlockEntity(legacyPos);
        check(legacy.owner() == null, "Older ownerless table reconstructed");
        var legacyInsert = new ItemStack(item("mctabletop:chess_board"));
        String error = legacy.fitBoard(user, legacyInsert);
        check(error == null && legacy.isOwner(user.getUUID()) && legacyInsert.isEmpty(), "Survival first insertion claims legacy table, "
            + "error=" + error + " owner=" + legacy.owner() + " player=" + user.getUUID() + " count=" + legacyInsert.getCount());

        var towny = new TownyFixture(level);
        Object town = towny.newTown("Tabletown");
        var residentType = towny.loader.loadClass(TownyFixture.NAMESPACE + "object.Resident");
        Object dataSource = towny.universe.getClass().getMethod("getDataSource").invoke(towny.universe);
        Object resident = dataSource.getClass().getMethod("newResident", String.class, UUID.class)
            .invoke(dataSource, user.getGameProfile().getName(), user.getUUID());
        residentType.getMethod("setTown", towny.townType, boolean.class).invoke(resident, town, false);
        towny.townType.getMethod("setMayor", residentType, boolean.class).invoke(town, resident, false);
        Object plot = towny.claim(town, 37, 74, 40);
        towny.plotType.getMethod("setResident", residentType, boolean.class).invoke(plot, resident, false);
        try {
            playTables(level, user);
        } finally {
            towny.remove(plot);
        }
    }

    private void playTables(ServerLevel level, ServerPlayer user) throws Exception {
        String[] ids = {"small_game_table", "large_game_table"};
        for (int n = 0; n < ids.length; n++) {
            String tableId = "mctabletop:" + ids[n];
            var tableStack = craft(level, user, tableId);
            check(tableStack.is(item(tableId)), "Recipe output " + tableId);
            var base = new BlockPos(37 + 6*n, 73, 40);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
            user.setPos(base.getX()+0.5, base.getY()+2, base.getZ()-2.5);
            user.setShiftKeyDown(false);
            user.setItemInHand(InteractionHand.MAIN_HAND, tableStack);
            var placed = user.gameMode.useItemOn(user, level, user.getMainHandItem(), InteractionHand.MAIN_HAND, top(base));
            check(placed.consumesAction(), "Survival placement " + tableId + " result=" + placed
                + " hand=" + user.getMainHandItem() + " base=" + level.getBlockState(base) + " Bukkit=" + bukkitInteraction(user));
            var at = base.above();
            var table = com.mctabletop.neoforge.block.BoardInteraction.table(level, at);
            check(table != null, "Table frame resolves " + tableId);
            boolean large = n != 0;
            var insert = craft(level, user, large ? "mctabletop:go_19_board" : "mctabletop:chess_board");
            check(insert.getItem() instanceof BoardInsertItem board && board.large() == large, "Craft matches frame size " + tableId);
            user.setItemInHand(InteractionHand.MAIN_HAND, insert);
            user.setPos(at.getX()+0.5, at.getY()+1, at.getZ()-2.5);
            var hand = user.getMainHandItem();
            int count = hand.getCount();
            check(user.gameMode.useItemOn(user, level, hand, InteractionHand.MAIN_HAND, top(at)).consumesAction(), "Right-click fits " + tableId);
            var fitted = com.mctabletop.neoforge.block.BoardInteraction.table(level, at);
            check(fitted.isPlayable() && user.getMainHandItem().getCount() == count - 1 && fitted.isOwner(user.getUUID()),
                "Playable board, survival consumption and ownership " + tableId + " fitted=" + fitted.isPlayable()
                + " before=" + count + " after=" + user.getMainHandItem() + " owner=" + fitted.owner() + " player=" + user.getUUID()
                + " Bukkit=" + bukkitInteraction(user));
        }
    }

    private static String bukkitInteraction(ServerPlayer user) throws ReflectiveOperationException {
        Object event = user.gameMode.getClass().getField("eventCB").get(user.gameMode);
        return event.getClass().getMethod("useInteractedBlock").invoke(event) + "/" + event.getClass().getMethod("useItemInHand").invoke(event);
    }

    private ItemStack craft(ServerLevel level, ServerPlayer user, String id) {
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow();
        var grid = new TransientCraftingContainer(user.inventoryMenu, 3, 3);
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var ingredient = recipe.getIngredients().get(i);
            if (!ingredient.isEmpty()) grid.setItem(i, ingredient.getItems()[0].copy());
        }
        check(recipe.matches(grid, level), "Reachable recipe " + id);
        return recipe.assemble(grid, level.registryAccess());
    }

    private void armor(ServerLevel level) {
        int dark = 0;
        for (var recipe : level.getRecipeManager().getRecipes()) {
            var output = recipe.getResultItem(level.registryAccess());
            var id = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (id == null || !id.getNamespace().equals("magistuarmoryaddon")
                || !id.getPath().matches("(silvered_|gilded_)?dark(blued)?_.*")
                || !(output.getItem() instanceof net.minecraft.world.item.ArmorItem)
                || id.getPath().endsWith("_decoration") || id.getPath().endsWith("_model")) continue;
            check(recipe instanceof AssemblyRecipe, "Dark armor is workshop recipe " + id);
            var assembly = (AssemblyRecipe) recipe;
            check(assembly.profile.metal().equals("black_steel") && assembly.base.isEmpty(), "Dark recipe builds from black steel " + id);
            check(assembly.extras.stream().flatMap(ingredient -> java.util.Arrays.stream(ingredient.getItems()))
                .noneMatch(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath().endsWith("_template")), "Dark recipe has no template " + id);
            dark++;
        }
        check(dark >= 30, "All active dark armor variants processed: " + dark);
    }

    private void blocks(ServerLevel level) {
        double previous = 0;
        double siegeStrength = SiegeBallisticsEnvironment.IMPACTS.material(level, BlockPos.ZERO, Blocks.STONE.defaultBlockState()).strength();
        for (String id : List.of("minecraft:granite", "overgeared:steel_block", "tfc:metal/block/black_steel")) {
            var candidate = block(id);
            var state = candidate.defaultBlockState();
            check(state.getDestroySpeed(level, BlockPos.ZERO) >= (previous == 0 ? 2.5f : 7f), "Higher mining hardness " + id);
            double toughness = BlockArmorPropertiesHandler.getProperties(state).toughness(level, state, BlockPos.ZERO, true);
            check(toughness > previous && toughness >= 10, "Ordered native cannon toughness " + id + ": " + toughness);
            previous = toughness;
            double strength = SiegeBallisticsEnvironment.IMPACTS.material(level, BlockPos.ZERO, state).strength();
            check(strength > siegeStrength, "Ordered Siegeworks impact strength " + id + ": " + strength);
            siegeStrength = strength;
        }
        check(Blocks.GRANITE.getExplosionResistance() > Blocks.STONE.getExplosionResistance(), "Granite withstands more blast");
    }

    private void wildlife(ServerLevel level, ServerPlayer user) throws Exception {
        user.setPos(36.5, 76, 36.5);
        var cow = (Mob) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragnlivestock:o_cow")).create(level);
        cow.setPos(user.getX() + 5, user.getY(), user.getZ());
        cow.getPersistentData().putBoolean("EmpirecraftFaunaExisting", true);
        check(level.addFreshEntity(cow), "Existing cow can be loaded");
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(cow, level.damageSources().playerAttack(user), 2));
        check(cow.getPersistentData().hasUUID("EmpirecraftThreatAttacker"), "Cow remembers attacker");
        check(cow.getPersistentData().getLong("EmpirecraftThreatUntil") - level.getGameTime() == 168000, "Fear persists for seven game-days");
        var goalField = net.minecraft.world.entity.Mob.class.getDeclaredField("f_21345_");
        goalField.setAccessible(true);
        var goals = (GoalSelector) goalField.get(cow);
        check(goals.getAvailableGoals().stream().anyMatch(g -> g.getGoal().getClass().getName().endsWith("AnimalThreats$Reaction") && g.getGoal().canUse()), "Cow movement goal reacts to its attacker");
        var witness = (Mob) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragnlivestock:o_cow")).create(level);
        witness.setPos(user.getX() + 15, user.getY(), user.getZ());
        witness.getPersistentData().putBoolean("EmpirecraftFaunaExisting", true);
        check(level.addFreshEntity(witness), "Second existing cow can be loaded");
        level.explode(null, user.getX()+8, user.getY(), user.getZ(), 1, Level.ExplosionInteraction.NONE);
        var witnessGoals = (GoalSelector) goalField.get(witness);
        check(!witness.getPersistentData().hasUUID("EmpirecraftThreatAttacker") && witnessGoals.getAvailableGoals().stream()
            .anyMatch(g -> g.getGoal().getClass().getName().endsWith("AnimalThreats$Reaction") && g.getGoal().canUse()), "Nearby unhurt cow reacts to explosion");
    }

    private void projectile(ServerLevel level, ServerPlayer user, MinecraftServer server) throws Exception {
        user.setPos(36.5, 100, 36.5);
        var projectile = returningRock(level, user);
        check(ThrownImpacts.tracked(projectile), "Real throwable constructs");
        var canHit = AbstractArrow.class.getDeclaredMethod("m_5603_", Entity.class);
        canHit.setAccessible(true);
        server.setPvpAllowed(true);
        check((boolean) canHit.invoke(projectile, user), "Returning throw can hit owner in wilderness");
        float before = user.getHealth();
        projectile.tick();
        check(user.getHealth() < before, "Returning throw actually damages owner");
        var protectedThrow = returningRock(level, user);
        protectedClaim(level, user, protectedThrow, canHit);
        server.setPvpAllowed(false);
        check(!(boolean) canHit.invoke(protectedThrow, user), "Disabled server PvP prevents collision");
    }

    private AbstractArrow returningRock(ServerLevel level, ServerPlayer user) throws ReflectiveOperationException {
        var entityClass = Class.forName("com.TBK.medieval_boomsticks.server.entity.ThrowableSmallRock");
        var constructor = entityClass.getConstructor(Level.class, net.minecraft.world.entity.LivingEntity.class, ItemStack.class);
        var rock = (AbstractArrow) constructor.newInstance(level, user, new ItemStack(Items.FLINT));
        var leftOwner = Projectile.class.getDeclaredField("f_37246_");
        leftOwner.setAccessible(true);
        leftOwner.setBoolean(rock, true);
        rock.setPos(user.getX()-2, user.getY()+1, user.getZ());
        rock.setDeltaMovement(4, 0, 0);
        return rock;
    }

    private void protectedClaim(ServerLevel level, ServerPlayer user, AbstractArrow rock, java.lang.reflect.Method canHit) throws Exception {
        var towny = new TownyFixture(level);
        Object town = towny.newTown("ImpactTown");
        Object plot = towny.claim(town, user.getX(), user.getY(), user.getZ());
        Object permissions = towny.plotType.getMethod("getPermissions").invoke(plot);
        permissions.getClass().getField("pvp").setBoolean(permissions, false);
        Object location = towny.location(user.getX(), user.getY(), user.getZ());
        try {
            check(towny.api.getClass().getMethod("getTownBlock", towny.locationType).invoke(towny.api, location) == plot
                && !(boolean) towny.api.getClass().getMethod("isPVP", towny.locationType).invoke(towny.api, location), "Real Towny claim with PvP disabled");
            check(!(boolean) canHit.invoke(rock, user), "Towny protected target skipped before impact");
            rock.tick();
            check(!rock.isRemoved() && rock.getX() > user.getX(), "Throw goes through Towny protected target");
        } finally {
            towny.remove(plot);
        }
    }

    static final class TownyFixture {
        static final String NAMESPACE = "com.palmergames.bukkit.towny.";
        final Class<?> locationType, plotType, coordType, townType;
        final ClassLoader loader;
        final Object api, universe, world;

        TownyFixture(ServerLevel level) throws ReflectiveOperationException {
            var bukkit = Class.forName("org.bukkit.Bukkit");
            var manager = bukkit.getMethod("getPluginManager").invoke(null);
            var plugin = Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin", String.class).invoke(manager, "Towny");
            loader = plugin.getClass().getClassLoader();
            locationType = Class.forName("org.bukkit.Location");
            plotType = loader.loadClass(NAMESPACE+"object.TownBlock");
            coordType = loader.loadClass(NAMESPACE+"object.WorldCoord");
            townType = loader.loadClass(NAMESPACE+"object.Town");
            api = loader.loadClass(NAMESPACE+"TownyAPI").getMethod("getInstance").invoke(null);
            universe = loader.loadClass(NAMESPACE+"TownyUniverse").getMethod("getInstance").invoke(null);
            world = level.getClass().getMethod("getWorld").invoke(level);
        }

        Object newTown(String name) throws ReflectiveOperationException { return townType.getConstructor(String.class).newInstance(name); }
        Object location(double x, double y, double z) throws ReflectiveOperationException {
            return locationType.getConstructor(Class.forName("org.bukkit.World"), double.class, double.class, double.class).newInstance(world, x, y, z);
        }
        Object claim(Object town, double x, double y, double z) throws ReflectiveOperationException {
            Object coord = coordType.getMethod("parseWorldCoord", locationType).invoke(null, location(x,y,z));
            Object plot = plotType.getConstructor(coordType).newInstance(coord);
            plotType.getMethod("setTown", townType, boolean.class).invoke(plot, town, false);
            return plot;
        }
        void remove(Object plot) throws ReflectiveOperationException { universe.getClass().getMethod("removeTownBlock", plotType).invoke(universe, plot); }
    }
}
