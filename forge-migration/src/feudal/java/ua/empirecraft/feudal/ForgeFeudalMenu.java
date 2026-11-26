package ua.empirecraft.feudal;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/** Chest GUI for the feudal sections; buttons run the same commands the old chat links did. */
public final class ForgeFeudalMenu extends SimpleContainer {
    private static CommandDispatcher<CommandSourceStack> dispatcher;

    public static void register(CommandDispatcher<CommandSourceStack> source) {
        dispatcher = source;
    }

    public static boolean click(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu, int slot) {
        if (!(menu instanceof ChestMenu chest) || !(chest.m_39261_() instanceof ForgeFeudalMenu feudal)) return false;
        return feudal.click(player, slot);
    }

    public static void closed(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu) {
    }

    public static void open(ServerPlayer player) {
        var menu = new ForgeFeudalMenu();
        var provider = new SimpleMenuProvider(
                (window, inventory, viewer) -> ChestMenu.m_39246_(window, inventory, menu),
                Component.m_237113_("Feudal").m_130940_(ChatFormatting.GOLD));
        player.m_5893_(provider);
        menu.render();
    }

    private ForgeFeudalMenu() {
        super(54);
        for (int slot = 0; slot < 54; slot++) {
            m_6836_(slot, new ItemStack(net.minecraft.world.item.Items.f_42183_));
        }
    }

    private void render() {
        m_6836_(4, button(new ItemStack(net.minecraft.world.item.Items.f_42777_), "Feudal", "Меню володіння"));
        m_6836_(10, button(new ItemStack(net.minecraft.world.item.Items.f_42614_), "Стан володіння", "Відомості про володіння"));
        m_6836_(11, button(new ItemStack(net.minecraft.world.item.Items.f_42438_), "Васальні договори", "Список договорів і новий проєкт"));
        m_6836_(12, button(new ItemStack(net.minecraft.world.item.Items.f_42616_), "Дипломатичні договори"));
        m_6836_(13, button(new ItemStack(net.minecraft.world.item.Items.f_42522_), "Унії й об’єднання"));
        m_6836_(14, button(new ItemStack(net.minecraft.world.item.Items.f_42415_), "Династія"));
        m_6836_(15, button(new ItemStack(net.minecraft.world.item.Items.f_42584_), "Вибори й державні рішення"));
        m_6836_(19, button(new ItemStack(net.minecraft.world.item.Items.f_42656_), "Скарбниця"));
        m_6836_(20, button(new ItemStack(net.minecraft.world.item.Items.f_42342_), "Ринки, ярмарки й пошта"));
        m_6836_(21, button(new ItemStack(net.minecraft.world.item.Items.f_42587_), "Провізія"));
        m_6836_(22, button(new ItemStack(net.minecraft.world.item.Items.f_42417_), "Флот"));
        m_6836_(49, button(new ItemStack(net.minecraft.world.item.Items.f_42127_), "Закрити"));
        m_6596_();
    }

    public boolean click(ServerPlayer player, int slot) {
        try {
            switch (slot) {
                case 10 -> run(player, "town state status");
                case 11 -> run(player, "town treaty menu");
                case 12 -> run(player, "town diplomacy menu");
                case 13 -> run(player, "town union list");
                case 14 -> run(player, "town dynasty menu");
                case 15 -> run(player, "town state help");
                case 19 -> run(player, "treasury menu");
                case 20 -> run(player, "market menu");
                case 21 -> run(player, "provisions menu");
                case 22 -> run(player, "fleet menu");
                case 49 -> player.m_6915_();
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().error("Feudal menu click failed", failure);
            player.m_213846_(Component.m_237113_("Не вдалося виконати дію меню."));
            return true;
        }
    }

    private void run(ServerPlayer player, String command) throws Exception {
        if (dispatcher == null) throw new IllegalStateException("Feudal команди ще не зареєстровані.");
        dispatcher.execute(command, player.m_20203_());
    }

    private static ItemStack button(ItemStack stack, String title, String... lore) {
        MutableComponent text = Component.m_237113_(title).m_130940_(ChatFormatting.AQUA);
        for (String line : lore) {
            text.m_7220_(Component.m_237113_(line).m_130940_(ChatFormatting.GRAY));
        }
        return stack.m_41714_(text);
    }
}
