package ua.townyrecruits.client;

import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.client.gui.commandscreen.ICommandCategory;
import com.talhanation.recruits.world.RecruitsGroup;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private static int page;
    private static Speed speed = Speed.NORMAL;
    private static double leash = 2, distance = 16;
    public static Speed speed() { return speed; }
    public static double leash() { return leash; }
    public static double distance() { return distance; }
    @Override public Component getToolTipName() { return Component.m_237115_("tactics.townyrecruits.menu"); }
    @Override public ItemStack getIcon() { return new ItemStack(Items.f_42583_); }
    @Override public void createButtons(CommandScreen screen, int x, int y, List<RecruitsGroup> groups, Player player) {
        ClientTactics.inspect(groups);
        Mode[][] pages = {
            {Mode.MARCH, Mode.MOVE, Mode.HOLD, Mode.RETURN, Mode.ADVANCE, Mode.RETREAT, Mode.FACE, Mode.ROUTE},
            {Mode.HOLD_FRONT, Mode.RELEASE_FRONT, Mode.KEEP_DISTANCE, Mode.CLOSE_DISTANCE, Mode.FIRE_HOLD, Mode.FREE_FIRE, Mode.VOLLEY, Mode.RANK_FIRE},
            {Mode.MOVE, Mode.HIT_AND_RUN, Mode.CAROUSEL, Mode.END_MANOEUVRE},
            {Mode.BOMB_AREA, Mode.BOMB_LINE}
        };
        String[] titles = {"movement", "battle", "cavalry", "bombs"};
        List<List<AbstractWidget>> widgets = new ArrayList<>();
        for (int i = 0; i < pages.length; i++) widgets.add(new ArrayList<>());
        for (int i = 0; i < pages.length; i++) {
            final int selected = i;
            screen.m_142416_(new ExtendedButton(x - 138 + i * 70, y - 75, 68, 20,
                label("page", titles[i]), b -> {
                    page = selected;
                    for (int p = 0; p < widgets.size(); p++) for (var widget : widgets.get(p))
                        widget.f_93624_ = p == page && (!ClientTactics.bombButtons.contains(widget) || ClientTactics.hasBombs);
                }));
        }
        ClientTactics.bombButtons.clear();
        for (int p = 0; p < pages.length; p++) for (int i = 0; i < pages[p].length; i++) {
            Mode mode = pages[p][i]; final boolean cavalry = p == 2 && mode == Mode.MOVE;
            var button = new ExtendedButton(x - 135 + (i % 2) * 140, y - 49 + (i / 2) * 24,
                130, 20, cavalry ? label("formation", "CAVALRY_WEDGE") : label("mode", mode.name()),
                b -> ClientTactics.begin(screen, mode, cavalry ? Formation.CAVALRY_WEDGE : shape, tight));
            button.m_257544_(Tooltip.m_257550_(Component.m_237115_("tactics.townyrecruits.tip." + mode.name().toLowerCase(java.util.Locale.ROOT))));
            button.f_93623_ = groups.stream().anyMatch(g -> !g.isDisabled());
            if (mode.bomb()) {
                ClientTactics.bombButtons.add(button);
            }
            button.f_93624_ = p == page && (!mode.bomb() || ClientTactics.hasBombs);
            widgets.get(p).add(button);
            screen.m_142416_(button);
        }
        var formation = new ExtendedButton(x - 105, y + 50, 210, 20, label("formation", shape.name()), b -> {});
        screen.m_142416_(formation);
        screen.m_142416_(new ExtendedButton(x - 135, y + 50, 25, 20, Component.m_237113_("<"), b -> {
            shape = Formation.values()[Math.floorMod(shape.ordinal() - 1, Formation.values().length)];
            formation.m_93666_(label("formation", shape.name()));
        }));
        screen.m_142416_(new ExtendedButton(x + 110, y + 50, 25, 20, Component.m_237113_(">"), b -> {
            shape = Formation.values()[(shape.ordinal() + 1) % Formation.values().length];
            formation.m_93666_(label("formation", shape.name()));
        }));
        formation.f_93623_ = false;
        screen.m_142416_(new ExtendedButton(x - 40, y + 74, 80, 20,
            Component.m_237110_("tactics.townyrecruits.tight", tight ? "✓" : "—"), b -> {
                tight = !tight; b.m_93666_(Component.m_237110_("tactics.townyrecruits.tight", tight ? "✓" : "—"));
            }));
        screen.m_142416_(new ExtendedButton(x - 135, y + 74, 90, 20, label("speed", speed.name()), b -> {
            speed = Speed.values()[(speed.ordinal() + 1) % Speed.values().length];
            b.m_93666_(label("speed", speed.name())); ClientTactics.issue(screen, Mode.SPEED);
        }));
        screen.m_142416_(new ExtendedButton(x + 45, y + 74, 90, 20, label("mode", "CANCEL"),
            b -> ClientTactics.issue(screen, Mode.CANCEL)));
        screen.m_142416_(new ExtendedButton(x - 135, y + 98, 130, 20, Component.m_237110_("tactics.townyrecruits.leash", (int)leash), b -> {
            leash = next(leash, new double[]{1, 2, 4, 6, 8, 12, 16});
            b.m_93666_(Component.m_237110_("tactics.townyrecruits.leash", (int)leash));
        }));
        screen.m_142416_(new ExtendedButton(x + 5, y + 98, 130, 20, Component.m_237110_("tactics.townyrecruits.distance", (int)distance), b -> {
            distance = next(distance, new double[]{8, 12, 16, 20, 24, 32});
            b.m_93666_(Component.m_237110_("tactics.townyrecruits.distance", (int)distance));
        }));
    }
    public static boolean bombPage() { return page == 3; }
    private static double next(double value, double[] choices) {
        for (double choice : choices) if (choice > value) return choice;
        return choices[0];
    }
    public static Component label(String group, String name) {
        return Component.m_237115_("tactics.townyrecruits." + group + "." + name.toLowerCase(java.util.Locale.ROOT));
    }
}
