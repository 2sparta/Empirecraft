package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import ua.empirecraft.townyforge.TownyGovernance.Actor;
import ua.empirecraft.townyforge.TownyGovernance.Here;

/** Town tribune chest GUI for the Forge port. Buttons mirror the /town commands; every click runs the same governance code path. */
public final class TownyMenu {

    public static void open(ServerPlayer player, TownyStore store) {
        var menu = new Tribune(store);
        var provider = new SimpleMenuProvider(
                (window, inventory, viewer) -> ChestMenu.m_39246_(window, inventory, menu),
                Component.m_237113_("Трибуна міста").m_130940_(ChatFormatting.GOLD));
        player.m_5893_(provider);
        menu.render();
    }

    public static void closed(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest && chest.m_39261_() instanceof Tribune tribune) tribune.forget();
    }

    public static boolean click(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu, int slot) {
        if (!(menu instanceof ChestMenu chest) || !(chest.m_39261_() instanceof Tribune tribune)) return false;
        return tribune.click(player, slot);
    }

    private static ItemStack button(ItemStack stack, String title, String... lore) {
        MutableComponent text = Component.m_237113_(title).m_130940_(ChatFormatting.AQUA);
        for (String line : lore) {
            text.m_7220_(Component.m_237113_(line).m_130940_(ChatFormatting.GRAY));
        }
        return stack.m_41714_(text);
    }

    static final class Tribune extends net.minecraft.world.SimpleContainer {
        private final TownyStore store;
        private boolean confirmLeave;

        Tribune(TownyStore store) {
            super(54);
            this.store = store;
            for (int slot = 0; slot < 54; slot++) {
                m_6836_(slot, new ItemStack(net.minecraft.world.item.Items.f_42183_));
            }
        }

        void render() {
            m_6836_(4, button(new ItemStack(net.minecraft.world.item.Items.f_42777_),
                    "Трибуна міста", "Керуй містом без команд"));
            m_6836_(10, button(new ItemStack(net.minecraft.world.item.Items.f_42614_),
                    "Про місто", "Відомості про твоє місто"));
            m_6836_(11, button(new ItemStack(net.minecraft.world.item.Items.f_42438_),
                    "Жителі", "Список резидентів міста"));
            m_6836_(12, button(new ItemStack(net.minecraft.world.item.Items.f_42616_),
                    "Баланс міста", "Скарбниця міста"));
            m_6836_(20, button(new ItemStack(net.minecraft.world.item.Items.f_42522_),
                    "Приватити чанк", "Захопити ділянку, де стоїш"));
            m_6836_(21, button(new ItemStack(net.minecraft.world.item.Items.f_42415_),
                    "Зняти приват", "Звільнити ділянку, де стоїш"));
            m_6836_(29, button(new ItemStack(net.minecraft.world.item.Items.f_42584_),
                    "На спавн міста", "Телепорт до точки спавну"));
            m_6836_(30, button(new ItemStack(net.minecraft.world.item.Items.f_42656_),
                    "Прив'язати спавн", "Поставити спавн там, де стоїш"));
            m_6836_(31, button(new ItemStack(net.minecraft.world.item.Items.f_42342_),
                    confirmLeave ? "Підтвердити вихід?" : "Покинути місто",
                    confirmLeave ? "Клацни ще раз" : "Два кліки для безпеки"));
            m_6836_(38, button(new ItemStack(net.minecraft.world.item.Items.f_42587_),
                    "Внести 10", "Скарбниця міста"));
            m_6836_(39, button(new ItemStack(net.minecraft.world.item.Items.f_42417_),
                    "Внести 100", "Скарбниця міста"));
            m_6836_(40, button(new ItemStack(net.minecraft.world.item.Items.f_41912_),
                    "Внести 1000", "Скарбниця міста"));
            m_6836_(49, button(new ItemStack(net.minecraft.world.item.Items.f_42127_),
                    "Закрити трибуну"));
            m_6596_();
        }

        boolean click(ServerPlayer player, int slot) {
            try {
                switch (slot) {
                    case 10 -> say(player, store.snapshot().townOf(TownyCommands.actor(player).id()));
                    case 11 -> {
                        var snap = store.snapshot();
                        String town = snap.townOf(TownyCommands.actor(player).id());
                        List<String> residents = snap.townResidents(town);
                        say(player, "Жителі • " + String.join(", ", residents));
                    }
                    case 12 -> say(player, "Баланс • " + new TownyGovernance(store.snapshot().editableCopy(),
                            TownyCommands.actor(player)).balance("town"));
                    case 20 -> store.change(data -> new TownyGovernance(data, TownyCommands.actor(player))
                            .claimTown(TownyCommands.position(player)));
                    case 21 -> store.change(data -> new TownyGovernance(data, TownyCommands.actor(player))
                            .unclaimTown(TownyCommands.position(player)));
                    case 30 -> store.change(data -> new TownyGovernance(data, TownyCommands.actor(player))
                            .setTownSpawn(TownyCommands.position(player)));
                    case 31 -> {
                        if (!confirmLeave) {
                            confirmLeave = true;
                            render();
                            say(player, "Клацни ще раз, щоб покинути місто.");
                            return true;
                        }
                        store.change(data -> new TownyGovernance(data, TownyCommands.actor(player)).leaveTown());
                        player.m_6915_();
                    }
                    case 38 -> deposit(player, "10");
                    case 39 -> deposit(player, "100");
                    case 40 -> deposit(player, "1000");
                    case 49 -> player.m_6915_();
                    default -> {
                        return false;
                    }
                }
                if (slot != 31) {
                    confirmLeave = false;
                    render();
                }
                return true;
            } catch (TownyProblem problem) {
                say(player, problem.getMessage());
                return true;
            } catch (Exception failure) {
                com.mojang.logging.LogUtils.getLogger().error("Tribune click failed", failure);
                say(player, "Не вдалося зберегти дію Towny.");
                return true;
            }
        }

        private void deposit(ServerPlayer player, String amount) {
            try {
                store.change(data -> new TownyGovernance(data, TownyCommands.actor(player)).bank(
                        "town", amount, false, TownyCommands.position(player)));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        private void say(ServerPlayer player, String message) {
            if (message != null && !message.isBlank()) {
                player.m_213846_(Component.m_237113_(message));
            }
        }

        void forget() {
            confirmLeave = false;
        }
    }

    private TownyMenu() { }
}
