package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The city tribune: a 9x6 chest menu that mirrors the /town command set. */
final class TownyMenu {
    static final String TITLE = "Трибуна міста";
    private static final ItemStack FILLER = named(Items.GRAY_STAINED_GLASS_PANE, " ");
    private final TownyStore store;

    TownyMenu(TownyStore store) { this.store = store; }

    void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> ChestMenu.threeRows(id, inventory, build(player)), TITLE));
    }

    private ItemStack[] build(ServerPlayer player) {
        var actor = TownyCommands.actor(player);
        var snapshot = store.snapshot();
        var town = snapshot.townOf(actor.id());
        var cells = new ItemStack[54];
        java.util.Arrays.fill(cells, FILLER);
        cells[4] = named(Items.OAK_SIGN, "Місто: " + town);
        cells[10] = named(Items.PLAYER_HEAD, "Інформація");
        cells[11] = named(Items.WRITABLE_BOOK, "Резиденти");
        cells[12] = named(Items.GOLD_INGOT, "Баланс");
        cells[13] = named(Items.EMERALD, "Внести 10");
        cells[14] = named(Items.EMERALD_BLOCK, "Внести 100");
        cells[15] = named(Items.GOLD_BLOCK, "Внести 1000");
        cells[19] = named(Items.IRON_AXE, "Приват чанка");
        cells[20] = named(Items.WATER_BUCKET, "Зняти приват");
        cells[21] = named(Items.ENDER_PEARL, "Телепорт у спавн");
        cells[22] = named(Items.RESPAWN_ANCHOR, "Привʼязати спавн");
        cells[40] = named(Items.BARRIER, "Вийти з міста (2 кліки)");
        return cells;
    }

    boolean click(ServerPlayer player, int slot) {
        try {
            switch (slot) {
                case 10 -> say(player, store.snapshot().townOf(TownyCommands.actor(player).id()));
                case 11 -> say(player, store.snapshot().residents(TownyCommands.actor(player).id()));
                case 12 -> say(player, balance(player));
                case 13 -> deposit(player, "10");
                case 14 -> deposit(player, "100");
                case 15 -> deposit(player, "1000");
                case 19 -> store.change(data -> new TownyGovernance(data, TownyCommands.actor(player))
                        .claimTown(TownyCommands.position(player)));
                case 20 -> store.change(data -> new TownyGovernance(data, TownyCommands.actor(player))
                        .unclaimTown(TownyCommands.position(player)));
                case 21 -> {
                    var here = TownyCommands.position(player);
                    say(player, store.travel(player, TownyTravel.Kind.TOWN_SPAWN, here));
                }
                case 22 -> store.change(data -> new TownyGovernance(data, TownyCommands.actor(player))
                        .setTownSpawn(TownyCommands.position(player)));
                case 40 -> leave(player);
                default -> { return false; }
            }
            return true;
        } catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().error("Tribune click failed", failure);
            say(player, "Не вдалося: " + failure.getMessage());
            return true;
        }
    }

    private String balance(ServerPlayer player) {
        var snapshot = store.snapshot();
        var governance = new TownyGovernance(snapshot.editableCopy(), TownyCommands.actor(player));
        return governance.balance("town");
    }

    private void deposit(ServerPlayer player, String amount) {
        try {
            store.change(data -> new TownyGovernance(data, TownyCommands.actor(player)).bank(
                    "town", amount, false, TownyCommands.position(player)));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void leave(ServerPlayer player) {
        var actor = TownyCommands.actor(player);
        if (!confirmLeave.remove(actor.id())) {
            confirmLeave.put(actor.id(), true);
            say(player, "Натисніть ще раз, щоб вийти з міста.");
            return;
        }
        confirmLeave.remove(actor.id());
        store.change(data -> new TownyGovernance(data, actor).leaveTown(actor.id()));
    }

    private static final java.util.Map<UUID, Boolean> confirmLeave = new java.util.concurrent.ConcurrentHashMap<>();

    private static ItemStack named(net.minecraft.world.item.Item item, String label) {
        var stack = new ItemStack(item);
        stack.m_41714_(Component.literal(label).m_130940_(ChatFormatting.YELLOW));
        return stack;
    }

    private static void say(ServerPlayer player, String message) {
        if (message != null && !message.isBlank()) player.m_5446_(Component.literal(message));
    }
}
