package ua.empirecraft.townyforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ua.empirecraft.townyforge.TownyGovernance.Actor;
import ua.empirecraft.townyforge.TownyGovernance.Here;

final class TownyCommands {
    private final Supplier<TownyStore> store;
    private final TownyTravel travel;

    TownyCommands(Supplier<TownyStore> store) {
        this.store = store;
        travel = new TownyTravel(store);
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var town = root("town").executes(c -> showTown(c.getSource(), snapshot().townOf(c.getSource().m_81375_().m_20148_())))
                .then(root("list").executes(c -> say(c.getSource(), "Міста • " + String.join(", ", snapshot().towns()))))
                .then(root("here").executes(c -> here(c.getSource())))
                .then(root("reslist")
                        .executes(c -> townResidents(c.getSource(), snapshot().townOf(c.getSource().m_81375_().m_20148_())))
                        .then(Commands.m_82129_("query", StringArgumentType.greedyString()).executes(c ->
                                townResidents(c.getSource(), StringArgumentType.getString(c, "query")))))
                .then(argument("new", (g, here, text) -> g.foundTown(text, here)))
                .then(action("claim", (g, here, text) -> g.claimTown(here))
                        .then(action("outpost", (g, here, text) -> g.claimTown(here, true))))
                .then(action("unclaim", (g, here, text) -> g.unclaimTown(here)))
                .then(root("spawn").executes(c -> travel(c.getSource(), TownyTravel.Kind.TOWN, 0)))
                .then(root("outpost").then(Commands.m_82129_("number", IntegerArgumentType.integer(1))
                        .executes(c -> travel(c.getSource(), TownyTravel.Kind.OUTPOST, IntegerArgumentType.getInteger(c, "number")))))
                .then(argument("join", (g, here, text) -> g.joinTown(text, false)))
                .then(action("leave", (g, here, text) -> g.leaveTown()))
                .then(argument("kick", (g, here, text) -> g.kickResident(text)))
                .then(root("invite").then(argument("add", (g, here, text) -> g.inviteResident(text))))
                .then(root("set")
                        .then(action("spawn", (g, here, text) -> g.setTownSpawn(here)))
                        .then(argument("mayor", (g, here, text) -> g.transferMayor(text)))
                        .then(argument("taxes", (g, here, text) -> g.setTax("town", "taxes", text)))
                        .then(argument("plottax", (g, here, text) -> g.setTax("town", "plottax", text)))
                        .then(argument("shoptax", (g, here, text) -> g.setTax("town", "shoptax", text)))
                        .then(argument("embassytax", (g, here, text) -> g.setTax("town", "embassytax", text)))
                        .then(argument("perm", permissions("town"))))
                .then(argument("toggle", (g, here, text) -> g.toggle("town", lower(text))))
                .then(rank("town"))
                .then(root("delete").then(action("confirm", (g, here, text) -> g.deleteTown())))
                .then(root("balance").executes(c -> balance(c.getSource(), "town")))
                .then(argument("deposit", (g, here, text) -> g.bank("town", text, false, here)))
                .then(argument("withdraw", (g, here, text) -> g.bank("town", text, true, here)))
                .then(Commands.m_82129_("name", StringArgumentType.greedyString())
                        .executes(c -> showTown(c.getSource(), StringArgumentType.getString(c, "name"))));
        var nation = root("nation").executes(c -> {
            var city = snapshot().town(snapshot().townOf(c.getSource().m_81375_().m_20148_()));
            return showNation(c.getSource(), city == null ? "" : city.getOrDefault("nation", ""));
        }).then(root("list").executes(c -> say(c.getSource(), "Нації • " + String.join(", ", snapshot().nations()))))
                .then(root("spawn").executes(c -> travel(c.getSource(), TownyTravel.Kind.NATION, 0)))
                .then(argument("new", (g, here, text) -> g.foundNation(text)))
                .then(argument("join", (g, here, text) -> g.joinNation(text)))
                .then(action("leave", (g, here, text) -> g.leaveNation()))
                .then(root("townlist").then(Commands.m_82129_("query", StringArgumentType.greedyString()).executes(c ->
                        nationTowns(c.getSource(), StringArgumentType.getString(c, "query")))))
                .then(argument("kick", (g, here, text) -> g.kickTown(text)))
                .then(root("invite").then(argument("add", (g, here, text) -> g.inviteTown(text))))
                .then(root("set").then(argument("capital", (g, here, text) -> g.transferCapital(text)))
                        .then(argument("taxes", (g, here, text) -> g.setTax("nation", "taxes", text))))
                .then(argument("toggle", (g, here, text) -> g.toggle("nation", lower(text))))
                .then(rank("nation"))
                .then(root("ally")
                        .then(argument("add", (g, here, text) -> g.diplomacy("allyAdd", text)))
                        .then(argument("accept", (g, here, text) -> g.diplomacy("allyAccept", text)))
                        .then(argument("remove", (g, here, text) -> g.diplomacy("allyRemove", text))))
                .then(root("enemy")
                        .then(argument("add", (g, here, text) -> g.diplomacy("enemyAdd", text)))
                        .then(argument("remove", (g, here, text) -> g.diplomacy("enemyRemove", text))))
                .then(root("delete").then(action("confirm", (g, here, text) -> g.deleteNation())))
                .then(root("balance").executes(c -> balance(c.getSource(), "nation")))
                .then(argument("deposit", (g, here, text) -> g.bank("nation", text, false, here)))
                .then(argument("withdraw", (g, here, text) -> g.bank("nation", text, true, here)))
                .then(Commands.m_82129_("name", StringArgumentType.greedyString())
                        .executes(c -> showNation(c.getSource(), StringArgumentType.getString(c, "name"))));
        var townNode = dispatcher.register(town);
        var nationNode = dispatcher.register(nation);
        dispatcher.register(root("t").executes(townNode.getCommand()).redirect(townNode));
        dispatcher.register(root("n").executes(nationNode.getCommand()).redirect(nationNode));
        dispatcher.register(root("plot").executes(c -> here(c.getSource()))
                .then(argument("forsale", (g, here, text) -> g.sellPlot(here, text)))
                .then(action("notforsale", (g, here, text) -> g.notForSale(here)))
                .then(action("claim", (g, here, text) -> g.claimPlot(here)))
                .then(action("unclaim", (g, here, text) -> g.unclaimPlot(here)))
                .then(root("set").then(argument("perm", permissions("plot")))
                        .then(argument("type", (g, here, text) -> g.setPlotType(here, lower(text))))));
        dispatcher.register(root("accept").then(argument("town", (g, here, text) -> g.joinTown(text, true)))
                .then(argument("nation", (g, here, text) -> g.joinNation(text))));
        dispatcher.register(root("invite").then(root("list").executes(c -> inbox(c.getSource()))));
        dispatcher.register(root("towny").executes(c ->
                say(c.getSource(), "Towny • " + snapshot().towns().size() + " міст, " + snapshot().nations().size() + " націй"))
                .then(root("taxcycle").requires(source -> source.m_6761_(2))
                        .then(root("on").executes(c -> dailyTaxes(c.getSource(), true)))
                        .then(root("off").executes(c -> dailyTaxes(c.getSource(), false))))
                .then(root("setbalance").requires(source -> source.m_6761_(2))
                        .then(Commands.m_82129_("target", StringArgumentType.greedyString()).executes(c ->
                                adminBalance(c.getSource(), StringArgumentType.getString(c, "target"))))));
    }

    private int dailyTaxes(CommandSourceStack source, boolean enabled) {
        try {
            store.get().change(state -> {
                state.settings().put("economy.daily_taxes.enabled", Boolean.toString(enabled));
                if (enabled) state.settings().put(TownyDaily.LAST_CYCLE, Long.toString(TownyDaily.cycle(state.settings(),
                        java.time.Instant.now().getEpochSecond())));
                return null;
            });
            return say(source, enabled ? "Щоденні податки ввімкнено." : "Щоденні податки вимкнено.");
        } catch (IOException failure) {
            LogUtils.getLogger().error("Could not store the tax cycle policy", failure);
            return error(source, "Не вдалося зберегти налаштування.");
        }
    }

    private int adminBalance(CommandSourceStack source, String input) {
        try {
            String[] args = fields(input, 3, "<resident|town|nation> <назва> <сума>");
            if (!args[2].matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) throw new TownyProblem("Потрібна невід’ємна сума.");
            String kind = lower(args[0]);
            store.get().change(state -> {
                var records = switch (kind) {
                    case "resident" -> state.residents();
                    case "town" -> state.towns();
                    case "nation" -> state.nations();
                    default -> throw new TownyProblem("Вкажіть resident, town або nation.");
                };
                String name = TownyGovernance.find(records, args[1]);
                if (name == null) throw new TownyProblem("Рахунок не знайдено.");
                state.balances().put(TownyEconomy.account(kind, records.get(name)), new java.math.BigDecimal(args[2]).toPlainString());
                return null;
            });
            return say(source, "Баланс Towny оновлено.");
        } catch (TownyProblem problem) {
            return error(source, problem.getMessage());
        } catch (IOException failure) {
            LogUtils.getLogger().error("Could not store the account balance", failure);
            return error(source, "Не вдалося зберегти баланс.");
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.m_82127_(name);
    }

    private LiteralArgumentBuilder<CommandSourceStack> rank(String scope) {
        return root("rank").then(argument("add", rank(scope, true))).then(argument("remove", rank(scope, false)));
    }

    private static Edit rank(String scope, boolean add) {
        return (g, here, text) -> {
            String[] fields = fields(text, 2, "<гравець> <ранг>");
            return g.rank(scope, fields[0], lower(fields[1]), add);
        };
    }

    private static Edit permissions(String scope) {
        return (g, here, text) -> {
            String[] fields = fields(lower(text), 3, "<група> <дія> <on|off>");
            if (!fields[2].equals("on") && !fields[2].equals("off")) throw new TownyProblem("Вкажіть on або off.");
            return g.setPermissions(scope, here, fields[0], fields[1], fields[2].equals("on"));
        };
    }

    private static String[] fields(String text, int count, String usage) {
        String[] parts = text.strip().split("\\s+");
        if (parts.length != count) throw new TownyProblem("Формат • " + usage);
        return parts;
    }

    private static String lower(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private LiteralArgumentBuilder<CommandSourceStack> argument(String command, Edit operation) {
        return root(command).then(Commands.m_82129_("input", StringArgumentType.greedyString())
                .executes(ctx -> edit(ctx.getSource(), operation, StringArgumentType.getString(ctx, "input"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> action(String command, Edit operation) {
        return root(command).executes(ctx -> edit(ctx.getSource(), operation, ""));
    }

    private TownySnapshot snapshot() {
        return store.get().snapshot();
    }

    private int showTown(CommandSourceStack source, String name) {
        var record = snapshot().town(name);
        if (record == null) return error(source, "Місто не знайдено.");
        return say(source, "Місто " + record.getOrDefault("name", name) + " • Мер " + record.get("mayor")
                + " • Нація " + record.getOrDefault("nation", ""));
    }

    private int showNation(CommandSourceStack source, String name) {
        var record = snapshot().nation(name);
        if (record == null) return error(source, "Націю не знайдено.");
        return say(source, "Нація " + name + " • Столиця " + record.get("capital"));
    }

    private int townResidents(CommandSourceStack source, String name) {
        if (snapshot().town(name) == null) return error(source, "Місто не знайдено.");
        return say(source, "Жителі • " + String.join(", ", snapshot().townResidents(name)));
    }

    private int nationTowns(CommandSourceStack source, String name) {
        if (snapshot().nation(name) == null) return error(source, "Націю не знайдено.");
        return say(source, "Міста нації • " + String.join(", ", snapshot().nationTowns(name)));
    }

    private int here(CommandSourceStack source) {
        Here here = position(source);
        var claim = snapshot().claim(here.dimension(), here.cx() * 16, here.cz() * 16);
        if (claim == null) return say(source, "Тут немає приватів Towny.");
        return say(source, "Ділянка міста " + claim.town() + " • Чанк " + claim.x() + ", " + claim.z());
    }

    private int balance(CommandSourceStack source, String scope) throws CommandSyntaxException {
        if (!snapshot().editable()) return error(source, "Потрібно оновити імпорт Towny.");
        try {
            return say(source, new TownyGovernance(snapshot().editableCopy(), actor(source.m_81375_())).balance(scope));
        } catch (TownyProblem problem) {
            return error(source, problem.getMessage());
        }
    }

    private int inbox(CommandSourceStack source) throws CommandSyntaxException {
        if (!snapshot().editable()) return error(source, "Потрібно оновити імпорт Towny.");
        try {
            return say(source, new TownyGovernance(snapshot().editableCopy(), actor(source.m_81375_())).inbox());
        } catch (TownyProblem problem) {
            return error(source, problem.getMessage());
        }
    }

    private int edit(CommandSourceStack source, Edit operation, String arguments) throws CommandSyntaxException {
        Actor player = actor(source.m_81375_());
        Here position = position(source);
        try {
            return say(source, store.get().change(data -> operation.run(new TownyGovernance(data, player), position, arguments)));
        } catch (TownyProblem problem) {
            return error(source, problem.getMessage());
        } catch (IOException failure) {
            LogUtils.getLogger().error("Could not commit a Towny command", failure);
            return error(source, "Не вдалося зберегти дію Towny.");
        }
    }

    static Actor actor(ServerPlayer player) {
        return new Actor(player.m_20148_(), player.m_36316_().getName());
    }

    static Here position(CommandSourceStack source) {
        var pos = source.m_81371_();
        var rotation = source.m_81376_();
        return new Here(source.m_81372_().m_46472_().m_135782_().toString(), pos.f_82479_, pos.f_82480_, pos.f_82481_,
                rotation.f_82471_, rotation.f_82470_);
    }

    private int travel(CommandSourceStack source, TownyTravel.Kind kind, int number) throws CommandSyntaxException {
        try {
            return say(source, travel.arrive(source, kind, number));
        } catch (TownyProblem problem) {
            return error(source, problem.getMessage());
        }
    }

    private static int say(CommandSourceStack source, String message) {
        source.m_288197_(() -> Component.m_237113_(message), false);
        return 1;
    }

    private static int error(CommandSourceStack source, String message) {
        source.m_81352_(Component.m_237113_(message));
        return 0;
    }

    private interface Edit {
        String run(TownyGovernance governance, Here position, String arguments);
    }
}
