package ua.townyrecruits.client;

import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.client.gui.commandscreen.ICommandCategory;
import com.talhanation.recruits.world.RecruitsGroup;
import java.util.List;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.gui.widget.ExtendedButton;
import ua.townyrecruits.tactics.TacticalOrder.*;

/** An extra page inside the existing Recruits command menu; group selection stays native. */
public final class TacticalCategory implements ICommandCategory {
    private static Formation shape = Formation.KEEP;
    private static boolean tight;
    @Override public Component getToolTipName() { return Component.m_237115_("tactics.townyrecruits.menu"); }
    @Override public ItemStack getIcon() { return new ItemStack(Items.f_42583_); }
    @Override public void createButtons(CommandScreen screen, int x, int y, List<RecruitsGroup> groups, Player player) {
        ClientTactics.inspect(groups);
        Mode[] modes = {Mode.MARCH, Mode.MOVE, Mode.HOLD, Mode.RETURN, Mode.ADVANCE, Mode.RETREAT, Mode.FACE, Mode.BOMB_AREA, Mode.BOMB_LINE};
        ClientTactics.bombButtons.clear();
        for (int i = 0; i < modes.length; i++) {
            Mode mode = modes[i];
            var button = new ExtendedButton(x - 135 + (i % 2) * 140, y - 65 + (i / 2) * 24,
                130, 20, label("mode", mode.name()), b -> ClientTactics.begin(screen, mode, shape, tight));
            button.m_257544_(Tooltip.m_257550_(Component.m_237115_("tactics.townyrecruits.tip." + mode.name().toLowerCase(java.util.Locale.ROOT))));
            button.f_93623_ = groups.stream().anyMatch(g -> !g.isDisabled());
            if (mode.bomb()) {
                button.f_93624_ = ClientTactics.hasBombs; ClientTactics.bombButtons.add(button);
            }
            screen.m_142416_(button);
        }
        var formation = new ExtendedButton(x - 105, y + 60, 210, 20, label("formation", shape.name()), b -> {});
        screen.m_142416_(formation);
        screen.m_142416_(new ExtendedButton(x - 135, y + 60, 25, 20, Component.m_237113_("<"), b -> {
            shape = Formation.values()[Math.floorMod(shape.ordinal() - 1, Formation.values().length)];
            formation.m_93666_(label("formation", shape.name()));
        }));
        screen.m_142416_(new ExtendedButton(x + 110, y + 60, 25, 20, Component.m_237113_(">"), b -> {
            shape = Formation.values()[(shape.ordinal() + 1) % Formation.values().length];
            formation.m_93666_(label("formation", shape.name()));
        }));
        formation.f_93623_ = false;
        screen.m_142416_(new ExtendedButton(x + 5, y + 31, 130, 20,
            Component.m_237110_("tactics.townyrecruits.tight", tight ? "✓" : "—"), b -> {
                tight = !tight; b.m_93666_(Component.m_237110_("tactics.townyrecruits.tight", tight ? "✓" : "—"));
            }));
    }
    public static Component label(String group, String name) {
        return Component.m_237115_("tactics.townyrecruits." + group + "." + name.toLowerCase(java.util.Locale.ROOT));
    }
}
