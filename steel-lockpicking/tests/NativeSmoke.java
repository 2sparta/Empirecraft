package locktest;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import melonslise.locks.common.init.*;
import melonslise.locks.common.item.*;
import melonslise.locks.common.util.*;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import ua.empirecraft.locks.*;

public final class NativeSmoke extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private ServerLevel level;
    private FakePlayer player;
    private org.bukkit.entity.Player bukkitPlayer;
    private TownBlock plot;
    private int menuId = 40;
    private final BlockPos pos = new BlockPos(0, 80, 0);
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean passed = false;
            try { run(); passed = true; System.out.println("STEEL_NATIVE_PASS checks=" + checks.size()); }
            catch (Throwable error) { System.err.println("STEEL_NATIVE_FAIL"); error.printStackTrace(); }
            try { Files.writeString(Path.of("steel-picking-result.json"), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(Map.of("passed", passed, "checks", checks))); }
            catch (Exception error) { throw new RuntimeException(error); }
            Bukkit.shutdown();
        }, 40);
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks.add(message); }
    private Lockable target() { return LocksUtil.intersecting(level, pos).findFirst().orElseThrow(); }
    private ItemStack newPick() { var pick = new ItemStack(LocksItems.STEEL_LOCK_PICK.get()); player.setItemInHand(InteractionHand.MAIN_HAND, pick); return pick; }
    private SteelPickingMenu open() {
        var menu = new SteelPickingMenu(++menuId, player, InteractionHand.MAIN_HAND, target(), UUID.randomUUID());
        player.containerMenu = menu; check(menu.stillValid(player), "Opened menu is valid " + menuId); return menu;
    }
    private int[] order(SteelPickingMenu menu) throws Exception {
        var session = SteelPickingMenu.class.getDeclaredField("session"); session.setAccessible(true);
        var order = PickingSession.class.getDeclaredField("order"); order.setAccessible(true);
        return ((int[])order.get(session.get(menu))).clone();
    }
    private PickPackets.Result reply(SteelPickingMenu menu) throws Exception {
        var field = SteelPickingMenu.class.getDeclaredField("lastReply"); field.setAccessible(true); return (PickPackets.Result)field.get(menu);
    }
    private void attempt(SteelPickingMenu menu, int sequence, int pin) { menu.receive(new PickPackets.Attempt(menu.containerId, menu.nonce, sequence, pin)); }
    private void pause() { ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime() + 20); }
    private void permissions(boolean allowed) {
        plot.getPermissions().setAll(allowed);
        PlayerCacheUtil.getCache(bukkitPlayer).resetAndUpdate(WorldCoord.parseWorldCoord(bukkitPlayer.getLocation()));
    }
    private void run() throws Exception {
        var world = Bukkit.getWorld("locks_test"); level = (ServerLevel)world.getClass().getMethod("getHandle").invoke(world);
        player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7209c764-53a2-4bfb-915b-a4951a159731"), "Locksmith"));
        for (var field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof ParameterizedType type
                    && Arrays.equals(type.getActualTypeArguments(), new Type[]{UUID.class, net.minecraft.server.level.ServerPlayer.class})) {
                field.setAccessible(true); ((Map)field.get(level.getServer().getPlayerList())).put(player.getUUID(), player);
            }
        }
        player.moveTo(0.5, 80, -1, 0, 0); bukkitPlayer = Bukkit.getPlayer(player.getUUID());
        check(bukkitPlayer != null, "Real Bukkit UUID lookup sees the fixture player");
        var universe = TownyUniverse.getInstance();
        var resident = new Resident("Locksmith"); resident.setUUID(player.getUUID()); universe.registerResident(resident);
        var town = new Town("LockTest"); universe.registerTown(town);
        plot = new TownBlock(0, 0, universe.getWorld(world.getUID())); plot.setTown(town); universe.addTownBlock(plot);
        permissions(true);
        check(Bukkit.getPluginManager().isPluginEnabled("Towny"), "Towny plugin is actually enabled");

        String[] blocks = {"minecraft:chest", "farmersdelight:oak_cabinet", "handcrafted:oak_cupboard"};
        for (int i = 0; i < blocks.length; i++) {
            var location = new BlockPos(i * 4, 80, 0); level.getChunkAt(location);
            check(LocksUtil.locked(level, location), "Old lock survives the mod upgrade: " + blocks[i]);
            var lock = LocksUtil.intersecting(level, location).findFirst().orElseThrow();
            check(lock.lock.id == 1001 + i && lock.lock.getLength() == 9, "Original ID and pin count preserved: " + blocks[i]);
            var inventory = (Container)level.getBlockEntity(location); var wrapper = new InvWrapper(inventory);
            check(wrapper.extractItem(0, 1, false).isEmpty(), "Furniture wrapper still blocks extraction: " + blocks[i]);
            var key = new ItemStack(LocksItems.KEY.get()); key.getOrCreateTag().putInt(LockingItem.KEY_ID, 1001 + i);
            player.setItemInHand(InteractionHand.MAIN_HAND, key);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(location), Direction.NORTH, location, false));
            key.useOn(context); check(!lock.lock.isLocked(), "Original key opens upgraded lock: " + blocks[i]);
            check(wrapper.extractItem(0, 1, true).getCount() == 1, "Unlocked furniture permits extraction: " + blocks[i]);
            key.useOn(context); check(lock.lock.isLocked(), "Original key relocks upgraded lock: " + blocks[i]);
            // Locks intentionally masks getItem() on locked vanilla containers. Inspect saved contents.
            var savedItems = level.getBlockEntity(location).serializeNBT().getList("Items", 10);
            check(savedItems.size() == 1 && savedItems.getCompound(0).getString("id").equals("minecraft:iron_ingot")
                    && savedItems.getCompound(0).getByte("Count") == 12, "Stored contents survive upgrade: " + blocks[i]);
        }
        var pick = newPick(); check(pick.getMaxDamage() == 96 && pick.getMaxStackSize() == 1 && pick.is(LocksItemTags.LOCK_PICKS), "Steel-only durable pick is registered and tagged");
        check(level.getServer().getRecipeManager().byKey(new ResourceLocation("locks:steel_lock_pick")).isPresent(), "Forge loads the steel pick crafting recipe");
        for (String material : new String[]{"wood", "iron", "gold", "diamond"}) check(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation("locks:" + material + "_lock_pick")), "Removed tier stays absent: " + material);
        check(ClaimPermission.allowed(player, target()), "Towny allows lockpicking on a switch-enabled plot");
        permissions(false); check(!ClaimPermission.allowed(player, target()), "Towny refuses lockpicking on a switch-disabled plot");
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        check(pick.useOn(context) == InteractionResult.FAIL, "Item use cannot bypass Towny denial"); permissions(true);
        check(pick.useOn(context) == InteractionResult.CONSUME && player.containerMenu instanceof SteelPickingMenu,
                "Actual item use opens the secured menu through NetworkHooks");
        player.closeContainer();

        var menu = open(); var secret = order(menu); var oldNbt = Lockable.toNbt(target());
        menu.tryPin(secret[0]); check(reply(menu) == null, "Legacy byte-only requests cannot advance the new menu");
        menu.receive(new PickPackets.Attempt(menu.containerId, UUID.randomUUID(), 1, secret[0]));
        menu.receive(new PickPackets.Attempt(menu.containerId + 1, menu.nonce, 1, secret[0]));
        attempt(menu, 1, 255); check(reply(menu) == null && pick.getDamageValue() == 0, "Wrong nonce, menu and out-of-range pin rejected");
        attempt(menu, 1, secret[0]); check(reply(menu).progress() == 1, "Authenticated pin advances once");
        attempt(menu, 1, secret[0]); check(reply(menu).progress() == 1 && pick.getDamageValue() == 0, "Duplicate request is idempotent");
        attempt(menu, 2, secret[1]); check(reply(menu).progress() == 1 && !reply(menu).correct() && !reply(menu).reset(), "Server rejects rapid attempts without wear");
        pause(); attempt(menu, 3, secret[0]); check(reply(menu).progress() == 0 && reply(menu).reset() && pick.getDamageValue() == 4, "Wrong pin resets all progress and costs four durability");
        attempt(menu, 3, secret[0]); check(pick.getDamageValue() == 4, "Replayed mistake cannot apply wear twice");
        check(Lockable.toNbt(target()).equals(oldNbt), "Failed attempts leave existing lock NBT unchanged");
        int seq = 3;
        for (int pin : secret) { pause(); attempt(menu, ++seq, pin); }
        check(reply(menu).open() && !target().lock.isLocked(), "Final pin commits an actual unlock on the server");
        menu.removed(player); check(!target().lock.isLocked(), "Closing a completed menu cannot relock it");
        check(new InvWrapper((Container)level.getBlockEntity(pos)).extractItem(0, 1, true).getCount() == 1, "Picking releases the real furniture guard");
        target().lock.setLocked(true); newPick(); var second = open();
        var sessionField = SteelPickingMenu.class.getDeclaredField("session"); sessionField.setAccessible(true);
        check(!second.nonce.equals(menu.nonce) && sessionField.get(second) != sessionField.get(menu), "Reopening creates a fresh nonce and an independent server-only session");
        second.receive(new PickPackets.Attempt(second.containerId, menu.nonce, 1, order(second)[0]));
        check(reply(second) == null, "Old session cannot act on reopened menu");
        var snapshot = Unpooled.buffer(); var buf = new FriendlyByteBuf(snapshot); Lockable.toBuf(buf, target());
        var clone = Lockable.fromBuf(buf); check(clone.id == target().id && clone.lock.id == target().lock.id, "Full opening snapshot preserves legacy wire data"); buf.release();
        buf = new FriendlyByteBuf(Unpooled.buffer()); buf.writeEnum(InteractionHand.MAIN_HAND); Lockable.toBuf(buf, target()); buf.writeUUID(second.nonce);
        var decodedMenu = melonslise.locks.common.container.LockPickingContainer.FACTORY.create(91, player.getInventory(), buf);
        check(decodedMenu instanceof SteelPickingMenu secure && secure.nonce.equals(second.nonce) && !buf.isReadable(), "Registered menu factory consumes the complete opening snapshot and nonce");
        decodedMenu.removed(player); buf.release();
        permissions(false); check(!second.stillValid(player), "Permission revocation invalidates an open session");
        attempt(second, 1, order(second)[0]); check(target().lock.isLocked(), "Denied open session cannot unlock"); permissions(true);

        newPick(); var distant = open(); player.moveTo(100, 80, 100, 0, 0); check(!distant.stillValid(player), "Walking away invalidates the session");
        player.moveTo(0.5, 80, -1, 0, 0); newPick(); var switched = open(); newPick(); check(!switched.stillValid(player), "Replacing the held pick invalidates the session");
        newPick(); var keyed = open(); target().lock.setLocked(false); target().lock.setLocked(true); check(!keyed.stillValid(player), "Another key open/relock invalidates the old session");
        newPick(); var removed = open(); level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElseThrow(IllegalStateException::new).remove(target().id); check(!removed.stillValid(player), "Removed lock invalidates the canonical target");
        var restored = Lockable.fromNbt(oldNbt); level.getCapability(LocksCapabilities.LOCKABLE_HANDLER).orElseThrow(IllegalStateException::new).add(restored);
        var fragile = newPick(); fragile.setDamageValue(95); var breaking = open(); int first = order(breaking)[0];
        attempt(breaking, 1, (first + 1) % 9); check(fragile.isEmpty() && reply(breaking).broken() && target().lock.isLocked(), "A worn pick breaks while the lock stays locked");
        check(!breaking.stillValid(player), "Broken pick cannot continue");
        var enchanted = target(); enchanted.stack.enchant(LocksEnchantments.COMPLEXITY.get(), 3); enchanted.stack.enchant(LocksEnchantments.STURDY.get(), 3);
        var expensive = newPick(); var hard = open(); int start = order(hard)[0]; attempt(hard, 1, (start + 1) % 9);
        check(expensive.getDamageValue() == 16 && reply(hard).cooldown() == 16, "Enchanted steel lock increases wear and cooldown without becoming impossible");
        packetRoundTrip();
        player.containerMenu = player.inventoryMenu;
        // Test UUID registration belongs only to this fixture, not to the persisted world.
    }

    private void packetRoundTrip() {
        var attempt = new PickPackets.Attempt(62, UUID.randomUUID(), 105, 8); var buf = new FriendlyByteBuf(Unpooled.buffer());
        PickPackets.encodeAttempt(attempt, buf); check(PickPackets.decodeAttempt(buf).equals(attempt) && !buf.isReadable(), "Attempt codec round-trip uses the complete nonce and sequence"); buf.release();
        var result = new PickPackets.Result(62, UUID.randomUUID(), 105, 8, 9, true, false, false, true, 16, 56); buf = new FriendlyByteBuf(Unpooled.buffer());
        PickPackets.encodeResult(result, buf); check(PickPackets.decodeResult(buf).equals(result) && !buf.isReadable(), "Result codec round-trip preserves status and durability"); buf.release();
    }
}
