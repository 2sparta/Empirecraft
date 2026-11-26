package ua.empirecraft.feudal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.feudal.CityMarketService.Box;
import ua.empirecraft.feudal.CityMarketService.Department;
import ua.empirecraft.feudal.FeudalService.Actor;
import ua.empirecraft.feudal.ShipRegistrationService.Anchor;
import ua.empirecraft.townyforge.TownyForge;
import ua.empirecraft.townyforge.TownyMenu;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

final class ForgeFeudalCommands {
    private final ForgeFeudal f;
    private final ForgeFairCommands fairs;
    private final ForgeMarketManagement marketManagement;
    private final ForgeCivicCommands civic;

    ForgeFeudalCommands(ForgeFeudal f) {
        this.f = f;
        fairs = new ForgeFairCommands(f);
        marketManagement = new ForgeMarketManagement(f);
        civic = new ForgeCivicCommands(f);
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ForgeFeudalMenu.register(dispatcher);
        dispatcher.register(root("town").then(command("treaty", this::treaty)).then(command("treasury", this::treasury))
                .then(command("fleet", this::fleet)).then(command("provisions", this::provision))
                .then(command("state", this::state)).then(command("castle", this::castle))
                .then(command("diplomacy", civic::diplomacy)).then(command("union", civic::union)).then(command("dynasty", civic::dynasty))
                .then(root("menu").executes(ctx -> {
                    menu(ctx.getSource(), true);
                    return 1;
                })));
        dispatcher.register(command("feudal", (user, ignored) -> ForgeFeudalMenu.open(user.player)));
        dispatcher.register(command("treasury", this::treasury));
        var currency = dispatcher.register(command("currency", this::currency));
        dispatcher.register(root("cu").executes(currency.getCommand()).redirect(currency));
        dispatcher.register(command("market", this::market));
        dispatcher.register(command("provisions", this::provision));
        dispatcher.register(command("fleet", this::fleet));
        dispatcher.register(command("shiprequest", this::shipRequest));
        dispatcher.register(command("shipadmin", this::shipAdmin).requires(source -> source.m_6761_(2)));
        dispatcher.register(command("commerce", this::recover));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.m_82127_(name);
    }

    private LiteralArgumentBuilder<CommandSourceStack> command(String name, Operation operation) {
        return root(name).executes(ctx -> run(ctx, operation, ""))
                .then(Commands.m_82129_("options", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, operation, StringArgumentType.getString(ctx, "options"))));
    }

    private int run(CommandContext<CommandSourceStack> ctx, Operation operation, String input) {
        var source = ctx.getSource();
        try {
            require(f.ready(), "Feudal ще завантажується.");
            require(input.length() <= 2048, "Команда задовга.");
            f.synchronizeTowns();
            var player = source.m_81375_();
            operation.run(new User(source, player, new Actor(player.m_20148_().toString(), source.m_6761_(2))),
                    input.isBlank() ? new String[]{"help"} : input.strip().split("\\s+"));
            return 1;
        } catch (IllegalArgumentException | com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            source.m_81352_(Component.m_237113_(error.getMessage()));
        } catch (Exception error) {
            LogUtils.getLogger().error("Feudal command failed", error);
            source.m_81352_(Component.m_237113_("Операцію не завершено. Деталі в журналі сервера."));
        }
        return 0;
    }

    private void menu(CommandSourceStack source, boolean town) {
        try {
            if (town) {
                var store = TownyForge.store();
                if (store == null) {
                    source.m_81352_(Component.m_237113_("Towny ще не завантажено."));
                    return;
                }
                TownyMenu.open(source.m_81375_(), store);
            } else {
                ForgeFeudalMenu.open(source.m_81375_());
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("Feudal menu failed to open", error);
            source.m_81352_(Component.m_237113_("Меню не відкрито. Деталі в журналі сервера."));
        }
    }

    private void treaty(User u, String[] a) throws Exception {
        if (a[0].equals("menu")) {
            u.say("Договори Feudal");
            u.link("Список договорів", "/town treaty list", false);
            u.link("Новий проєкт", "/town treaty create ", true);
            u.link("Правки, підписи та поставки", "/town treaty help", false);
            return;
        }
        if (a[0].equals("help")) {
            u.say("/town treaty list | create <васал> <сюзерен> | view <ID> <сторінка> | edit <ID> <редакція> <поле> <значення>");
            u.say("/town treaty agree|amend|reject <ID> <редакція> | vote <ID> <редакція> <володіння> <true|false> | sign <ID> <редакція> <токен>");
            u.say("/town treaty exit <ID> <true|false> | permit <ID> <право> | stock|collect <володіння> <кількість>");
            return;
        }
        if (a[0].equals("list")) {
            for (var row : f.db.query("SELECT * FROM vc_contract ORDER BY id DESC LIMIT 30")) {
                u.say("#" + number(row, "id") + " " + f.realms.estate(string(row, "child")).name() + " → "
                        + f.realms.estate(string(row, "parent")).name() + " · " + string(row, "status") + " · ред. " + number(row, "revision"));
            }
            return;
        }
        if (a[0].equals("create")) {
            u.say("Проєкт договору #" + f.treaties.create(u.actor, estate(at(a, 1)), estate(at(a, 2))));
            return;
        }
        if (Set.of("stock", "collect").contains(a[0])) {
            String holder = estate(at(a, 1));
            int amount = positive(a, 2);
            ItemStack held = u.player.m_21205_();
            require(!held.m_41619_(), "Візьміть зразок ресурсу в руку.");
            String material = ForgeRegistries.ITEMS.getKey(held.m_41720_()).toString();
            var plain = new ItemStack(held.m_41720_());
            require(ItemStack.m_150942_(plain, held), "Склад договору приймає звичайні ресурси без додаткових даних.");
            boolean collect = a[0].equals("collect");
            var change = ForgeFeudalItems.change(u.player, plain, amount, collect);
            String op = collect ? f.treaties.collect(u.actor, holder, material, amount, f.commerce, change.intent())
                    : f.treaties.stock(u.actor, holder, material, amount, f.commerce, change.intent());
            ForgeFeudalItems.commit(u.player, f.commerce, op, change);
            u.say("Склад договору оновлено.");
            return;
        }
        if (a[0].equals("legacy")) {
            String action = at(a, 1), proposal = at(a, 2);
            switch (action) {
                case "view" -> {
                    var page = f.markets.documents.view(u.actor, f.markets.documents.vassal(proposal), positive(a, 3));
                    page.lines().forEach(u::say);
                    u.say("Сторінка " + page.page() + " / " + page.pages());
                    if (!page.token().isEmpty()) u.say("/town treaty legacy sign " + proposal + " " + page.version() + " " + page.token());
                }
                case "sign" -> { f.markets.documents.acceptVassal(u.actor, proposal, positive(a, 3), at(a, 4)); u.say("Підпис збережено."); }
                case "exit" -> u.say("Пропозиція виходу " + f.realms.independence(u.actor, estate(proposal)));
                default -> throw new IllegalArgumentException("Доступні view, sign та exit.");
            }
            return;
        }
        long contract = Long.parseLong(at(a, 1));
        switch (a[0]) {
            case "view" -> {
                var doc = f.markets.documents.vassalBuilder(contract);
                var page = f.markets.documents.view(u.actor, doc, positive(a, 2));
                page.lines().forEach(u::say);
                u.say("Сторінка " + page.page() + " / " + page.pages());
                if (!page.token().isEmpty()) u.say("/town treaty sign " + contract + " " + page.version() + " " + page.token());
            }
            case "edit" -> {
                String field = at(a, 3).toLowerCase(Locale.ROOT);
                require(!Set.of("grace", "fine", "misses", "breach").contains(field), "Умова недоступна.");
                String value = tail(a, 4);
                if (Set.of("resources", "provisions").contains(field)) {
                    if (value.equals("-")) value = "";
                    var terms = f.treaties.terms(contract, positive(a, 2)).with(field, value);
                    for (String item : terms.items(field).keySet()) {
                        var id = net.minecraft.resources.ResourceLocation.m_135820_(item);
                        require(item.contains(":") && id != null && ForgeRegistries.ITEMS.containsKey(id),
                                "Укажіть зареєстрований ID ресурсу у форматі namespace:path=кількість.");
                    }
                }
                u.say("Редакція " + f.treaties.edit(u.actor, contract, positive(a, 2), field, value));
            }
            case "amend" -> u.say("Редакція " + f.treaties.amend(u.actor, contract, positive(a, 2)));
            case "reject" -> { f.treaties.reject(u.actor, contract, positive(a, 2)); u.say("Проєкт відхилено."); }
            case "agree" -> { f.treaties.agree(u.actor, contract, positive(a, 2)); u.say("Редакцію погоджено."); }
            case "vote" -> { f.treaties.vote(u.actor, contract, positive(a, 2), estate(at(a, 3)), flag(a, 4)); u.say("Голос зараховано."); }
            case "sign" -> {
                f.markets.documents.acceptBuilder(u.actor, contract, positive(a, 2), at(a, 3));
                u.say("Підпис збережено. Стан договору " + string(f.treaties.get(contract), "status"));
            }
            case "exit" -> { f.treaties.exit(u.actor, contract, flag(a, 2)); u.say("Вихід зареєстровано."); }
            case "permit" -> { f.treaties.permit(u.actor, contract, at(a, 2)); u.say("Разовий дозвіл видано."); }
            case "history" -> {
                f.treaties.party(u.actor, f.treaties.get(contract));
                for (var row : f.treaties.history(contract, Integer.parseInt(at(a, 2)))) u.say(string(row, "event") + " " + string(row, "detail"));
            }
            default -> throw new IllegalArgumentException("Див. /town treaty help.");
        }
    }

    private void treasury(User u, String[] a) throws Exception {
        if (a[0].equals("menu")) {
            u.say("Скарбниця Feudal");
            u.link("Кошти міста", "/treasury balance", false);
            u.link("Особистий гаманець", "/treasury wallet", false);
            u.link("Банкіри, перекази та бюджет", "/treasury help", false);
            u.link("Державні валюти", "/currency menu", false);
            return;
        }
        if (a[0].equals("help")) {
            u.say("/treasury balance [володіння] | wallet | deposit|withdraw <володіння> <код> <сума> | banker <володіння> <гравець> <true|false>");
            return;
        }
        String wallet = "p:" + u.actor.id();
        if (a[0].equals("wallet")) {
            balances(u, wallet);
            return;
        }
        if (a[0].equals("vote")) {
            String vote = at(a, 1);
            var decision = f.db.one("SELECT id FROM political_decisions WHERE id=? AND action='BUDGET'", vote);
            require(decision != null, "Бюджетне голосування не знайдено.");
            f.politics.vote(u.actor, vote, flag(a, 2));
            u.say("Голос зараховано.");
            return;
        }
        String holder = a.length == 1 ? home(u) : estate(a[1]);
        String account = "e:" + holder;
        switch (a[0]) {
            case "balance" -> { require(f.realms.controlsTreasury(u.actor, f.realms.estate(holder)), "Скарбниця недоступна."); balances(u, account); }
            case "deposit", "withdraw" -> {
                boolean deposit = a[0].equals("deposit");
                f.realms.transfer(u.actor, deposit ? wallet : account, deposit ? account : wallet, code(a, 2), money(at(a, 3)));
                u.say("Переказ збережено.");
            }
            case "budget" -> u.say("Голосування " + f.politics.decision(u.actor, holder, "BUDGET", "p:" + person(at(a, 2)), code(a, 3), money(at(a, 4))));
            case "banker" -> { f.currency.banker(u.actor, holder, person(at(a, 2)), flag(a, 3)); u.say("Повноваження банкіра оновлено."); }
            default -> throw new IllegalArgumentException("Див. /treasury help.");
        }
    }

    private void balances(User u, String account) throws Exception {
        var rows = f.db.query("SELECT code,balance FROM accounts WHERE owner=? AND balance>0 ORDER BY code", account);
        if (rows.isEmpty()) u.say("Баланс 0.");
        for (var row : rows) u.say(format(number(row, "balance")) + " " + string(row, "code"));
        var gold = f.realms.currencies.goldBalance(account);
        if (gold.signum() > 0) u.say("Золото " + CurrencyService.show(gold));
    }

    private void state(User u, String[] a) throws Exception {
        if (a[0].equals("election")) {
            civic.election(u, a);
            return;
        }
        if (a[0].equals("help")) {
            u.say("/town state status | progress|submit <володіння> <рівень> | confirm <володіння>");
            u.say("/town state charter <володіння> <статут> | capital <володіння> <місто> | vote <ID> <true|false>");
            u.say("/town state certify <володіння> <інституція> <true|false> <примітка> | /town castle help");
            u.say("/town state government|territory <володіння> <вид> | decisions|history <володіння> | execute <рішення>");
            u.say("/town state budget <володіння> <p:гравець|e:володіння|u:союз> <код> <сума> | election help");
            u.say("/town state unionvote|exitvote <володіння> <союз> | councilvote <володіння> <рішення_ради> <true|false>");
            return;
        }
        if (a[0].equals("vote")) {
            f.politics.vote(u.actor, at(a, 1), flag(a, 2));
            u.say("Голос зараховано.");
            return;
        }
        if (a[0].equals("execute")) {
            f.politics.executeDecision(u.actor, at(a, 1));
            u.say("Рішення застосовано.");
            return;
        }
        String id = a.length == 1 ? home(u) : estate(a[1]);
        switch (a[0]) {
            case "status" -> {
                var state = f.politics.state(id);
                u.say(f.realms.estate(id).name() + " · " + state.stage().label + " · " + state.government());
            }
            case "progress" -> {
                var progress = f.politics.progress(id, PoliticalModel.parse(PoliticalModel.Stage.class, at(a, 2)));
                for (var check : progress.checks()) u.say((check.met() ? "✓ " : "○ ") + check.name() + " · " + check.detail());
                if (progress.ready(f.realms.now())) u.say("Заявку можна подати.");
            }
            case "submit" -> { f.politics.submit(u.actor, id, PoliticalModel.parse(PoliticalModel.Stage.class, at(a, 2))); u.say("Заявку подано."); }
            case "confirm" -> { f.politics.confirm(u.actor, id); u.say("Заявку схвалено."); }
            case "government" -> stateDecision(u, id, "GOVERNMENT", PoliticalModel.parse(PoliticalModel.Government.class, at(a, 2)).name(), "", 0);
            case "territory" -> stateDecision(u, id, "TERRITORY", PoliticalModel.parse(PoliticalModel.Territory.class, at(a, 2)).name(), "", 0);
            case "budget" -> {
                String recipient = at(a, 2);
                if (!recipient.startsWith("u:")) recipient = civic.receiver(recipient);
                else f.politics.unions.get(recipient.substring(2));
                stateDecision(u, id, "BUDGET", recipient, code(a, 3), money(at(a, 4)));
            }
            case "unionvote", "exitvote" -> stateDecision(u, id, a[0].equals("unionvote") ? "UNION" : "EXIT", at(a, 2), "", 0);
            case "councilvote" -> stateDecision(u, id, "COUNCIL", at(a, 2), flag(a, 3) ? "YES" : "NO", 0);
            case "decisions" -> {
                for (var row : f.db.query("SELECT id,action,deadline FROM political_decisions WHERE state=? AND status='OPEN' AND deadline>? "
                        + "ORDER BY deadline LIMIT 25", id, f.realms.now())) {
                    String key = string(row, "id");
                    u.link(string(row, "action") + " · " + key, "/town state vote " + key + " ", true);
                }
            }
            case "history" -> {
                for (var row : f.politics.history(id).stream().limit(20).toList()) {
                    u.say(CityMarketService.time(number(row, "at_time")) + " · " + string(row, "event") + " · " + string(row, "detail"));
                }
            }
            case "charter" -> u.say("Голосування " + f.politics.decision(u.actor, id, "CHARTER", tail(a, 2), "", 0));
            case "capital" -> u.say("Голосування " + f.politics.decision(u.actor, id, "CAPITAL", estate(at(a, 2)), "", 0));
            case "certify" -> { f.politics.certify(u.actor, id, at(a, 2), tail(a, 4), flag(a, 3)); u.say("Підтвердження збережено."); }
            default -> throw new IllegalArgumentException("Див. /town state help.");
        }
    }

    private void stateDecision(User u, String id, String action, String first, String second, long amount) {
        String key = f.politics.decision(u.actor, id, action, first, second, amount);
        u.say("Голосування " + key);
        u.link("Підготувати голос", "/town state vote " + key + " ", true);
    }

    private void castle(User u, String[] a) throws Exception {
        if (a[0].equals("help")) {
            u.say("/town castle pos1|pos2|inspect|status [володіння] | review <володіння> <оцінка 0–4> <примітка>");
            return;
        }
        String estate = a.length == 1 ? home(u) : estate(a[1]);
        switch (a[0]) {
            case "pos1", "pos2" -> { f.castles.select(u.actor, estate, u.player, a[0].equals("pos1") ? 1 : 2); u.say("Кут двору позначено."); }
            case "inspect" -> { f.castles.inspect(u.actor, estate); u.say("Геометрію двору перевірено."); }
            case "status" -> u.say(f.politics.castleApproved(estate) ? "Замок підтверджено." : "Замок очікує перевірки.");
            case "review" -> { f.politics.castleReview(u.actor, estate, coord(a, 2), tail(a, 3)); u.say("Оцінку збережено."); }
            default -> throw new IllegalArgumentException("Див. /town castle help.");
        }
    }

    private void currency(User u, String[] a) throws Exception {
        var ledger = f.realms.currencies;
        var who = u.actor;
        var exchange = f.currency.exchange;
        switch (a[0]) {
            case "menu" -> {
                u.say("Державні валюти");
                u.link("Список валют", "/currency list", false);
                u.link("Мої заявки погашення", "/currency redemptions", false);
                u.link("Емісія, резерв і видача золота", "/currency help", false);
            }
            case "help" -> {
                u.say("/currency list | create <назва_валюти> <код> [володіння] | reserve <код> <злитки> | print <код> <монети>");
                u.say("/currency collect <злитки> | quote <код> <сума_монет> | redeem <код> <монети> <мін. золота> <підтвердження> | pending | recover <ID>");
            }
            case "list" -> {
                for (var row : f.db.query("SELECT code,name,supply FROM currencies ORDER BY code LIMIT 30")) {
                    u.say(string(row, "code") + " · " + string(row, "name") + " · " + format(number(row, "supply")));
                }
                u.say("Золото до видачі " + CurrencyService.show(ledger.payable(who.id())));
            }
            case "create" -> {
                String code = code(a, 2);
                f.realms.createCurrency(who, a.length > 3 ? estate(a[3]) : home(u), code,
                        at(a, 1).replace('_', ' '), code.substring(0, Math.min(4, code.length())));
                u.say("Валюту зареєстровано.");
            }
            case "print" -> { f.currency.start(who, code(a, 1), positive(a, 2)); u.say("Друк розпочато."); }
            case "resume" -> { f.currency.resume(who, code(a, 1)); u.say("Друк відновлено."); }
            case "cancel" -> { f.currency.cancel(who, code(a, 1)); u.say("Друк скасовано."); }
            case "reserve", "collect" -> {
                boolean out = a[0].equals("collect");
                String destination = out ? "d:" + who.id() : "r:" + code(a, 1);
                int amount = positive(a, out ? 1 : 2);
                var items = ForgeFeudalItems.change(u.player, new ItemStack(net.minecraft.world.item.Items.f_42417_), amount, out);
                String op = ledger.prepareGold(who, out ? "OUT" : "IN", destination, amount, items.intent().before(), items.intent().after());
                ForgeFeudalItems.commit(u.player, op, items, applied -> ledger.finishGold(who, op, applied));
                u.say("Облік золота оновлено.");
            }
            case "quote" -> {
                var quote = exchange.quote(code(a, 1), CurrencyExchange.GOLD, at(a, 2));
                u.say("Розрахунок погашення " + CurrencyService.show(quote.output()) + " злитків · підтвердження " + quote.stamp());
            }
            case "redeem" -> u.say("Заявка погашення " + exchange.request(who, "p:" + who.id(), code(a, 1),
                    money(at(a, 2)), CurrencyService.gold(at(a, 3)), at(a, 4)));
            case "redemptions" -> {
                var rows = exchange.requests(who);
                if (rows.isEmpty()) u.say("Заявок погашення немає.");
                for (var row : rows) u.say(string(row, "id") + " · " + string(row, "code") + " · " + string(row, "status"));
            }
            case "cancel-redemption" -> {
                exchange.cancel(who, at(a, 1));
                u.say("Заявку погашення скасовано.");
            }
            case "policy" -> {
                String code = code(a, 1);
                if (a.length == 2) u.say(exchange.policy(code).toString());
                else {
                    exchange.schedule(who, code, CurrencyExchange.parse(at(a, 2)));
                    u.say("Політику обміну заплановано.");
                }
            }
            case "pending" -> {
                for (var row : f.db.query("SELECT id,direction,amount FROM gold_ops WHERE actor=? AND status='PREPARED' LIMIT 30", who.id())) {
                    u.say(string(row, "id") + " · " + string(row, "direction") + " · " + number(row, "amount"));
                }
            }
            case "recover" -> {
                String id = at(a, 1);
                var row = f.db.one("SELECT * FROM gold_ops WHERE id=? AND actor=? AND status='PREPARED'", id, who.id());
                require(row != null, "Незавершену операцію не знайдено.");
                ForgeFeudalItems.recover(u.player, string(row, "before_items"), string(row, "after_items"),
                        applied -> ledger.finishGold(who, id, applied));
                u.say("Операцію із золотом узгоджено.");
            }
            default -> throw new IllegalArgumentException("Див. /currency help.");
        }
    }

    private void market(User u, String[] a) throws Exception {
        var m = f.markets;
        String req = request();
        var p = ForgeFeudal.place(u.player);
        switch (a[0]) {
            case "menu" -> {
                u.say("Міський ринок");
                u.link("Ринки", "/market list", false);
                u.link("Мої активи", "/market assets", false);
                u.link("Ярмарки", "/market fair list", false);
                u.link("Поштові розсилки", "/market mail status", false);
                u.link("Підготувати ярмарок", "/market fair help", false);
                u.link("Дошка оголошень", "/market board help", false);
                u.link("Міські закупівлі", "/market procurement help", false);
                u.link("Торгівля й керування", "/market help", false);
            }
            case "fair" -> fairs.fair(u, a);
            case "mail" -> fairs.mail(u, a);
            case "board" -> marketManagement.board(u, a);
            case "procurement" -> marketManagement.procurement(u, a);
            case "chart" -> marketManagement.chart(u, a);
            case "help" -> {
                u.say("/market list | create <назва> | submit <ринок> | book <ринок> <GOODS|FX|JOBS> | tariff <ринок>");
                u.say("/market sell <ринок> <валюта> <ціна> <кількість> <дні> <версія тарифу> | buy <ринок> <товар> <валюта> <ціна> <кількість> <дні> <версія тарифу>");
                u.say("/market fx <ринок> <buy|sell> <база> <валюта> <ціна> <кількість> <дні> <версія тарифу> | assets | collect <актив> [кількість] | cancel <заявка>");
                u.say("/market jobs help | /market auction help | /market treaty help | /market exchange help | /market staff <ринок> <гравець> <true|false>");
                u.say("/market fair help · /market mail status · /market board help · /market procurement help · /market chart <ринок> [пара години]");
                if (u.actor.admin()) u.say("/market claim|building|review|state|recovery-point <ринок> [аргументи]");
            }
            case "list" -> {
                for (var row : f.db.query("SELECT id,name,state FROM cm_markets WHERE state='OPEN' OR town=? ORDER BY name LIMIT 30", f.hometown(u.actor.id()))) {
                    u.say(string(row, "name") + " · " + string(row, "state") + " · " + string(row, "id"));
                }
            }
            case "create" -> u.say("Проєкт ринку " + m.create(u.actor, req, home(u), p.world(), tail(a, 1), ""));
            case "submit" -> { m.submit(u.actor, req, at(a, 1)); u.say("Проєкт подано на розгляд."); }
            case "book" -> {
                for (var row : m.book(at(a, 1), department(a, 2), p).stream().limit(30).toList()) {
                    u.say(string(row, "side") + " " + number(row, "quantity") + " "
                            + (string(row, "spec").isEmpty() ? string(row, "base") : string(row, "spec")) + " · "
                            + format(number(row, "price")) + " " + string(row, "code"));
                }
            }
            case "staff" -> { m.staff(u.actor, req, at(a, 1), person(at(a, 2)), flag(a, 3)); u.say("Права персоналу оновлено."); }
            case "recovery-point" -> {
                String market = at(a, 1);
                m.once(u.actor, req, "recovery|" + market, () -> {
                    m.server(u.actor);
                    require(m.inMarket(market, p), "Пункт повернення має бути на території початкового ринку.");
                    m.event(u.actor, market, "RECOVERY_POINT", p.toString());
                    return market;
                });
                u.say("Пункт повернення відкрито.");
            }
            case "tariff" -> {
                var tariff = m.tariff(at(a, 1));
                require(tariff != null, "Тариф ще не активний.");
                u.say("Тариф #" + number(tariff, "version") + " · купівля " + format(number(tariff, "buyer"))
                        + "% · продаж " + format(number(tariff, "seller")) + "% · робота " + format(number(tariff, "jobs")) + "%");
            }
            case "jobs" -> marketJobs(u, a);
            case "exchange" -> {
                var exchange = m.official;
                switch (at(a, 1)) {
                    case "enable" -> { exchange.configure(u.actor, req, at(a, 2), p, flag(a, 3)); u.say("Режим обміну оновлено."); }
                    case "capital" -> { exchange.capital(u.actor, req, at(a, 2), p, code(a, 3), money(at(a, 4)), flag(a, 5)); u.say("Резерв обміну оновлено."); }
                    case "quote" -> {
                        var quote = exchange.quote(u.actor, at(a, 2), p, code(a, 3), code(a, 4), money(at(a, 5)));
                        u.say("До видачі " + format(quote.output()) + " " + quote.to() + " · підтвердження " + quote.stamp());
                    }
                    case "confirm" -> { exchange.exchange(u.actor, req, at(a, 2), p, code(a, 3), code(a, 4), money(at(a, 5)), at(a, 6)); u.say("Обмін виконано."); }
                    default -> u.say("/market exchange quote|confirm <ринок> <звідки> <куди> <сума> [підтвердження]");
                }
            }
            case "auction" -> marketAuctions(u, a);
            case "treaty" -> marketTreaty(u, a);
            case "sell" -> {
                var item = u.player.m_21205_();
                String spec = marketSpec(item);
                int count = positive(a, 4);
                var change = ForgeFeudalItems.change(u.player, item, count, false);
                String op = m.order(u.actor, req, at(a, 1), p, Department.GOODS, spec, "", code(a, 2), "SELL", count,
                        money(at(a, 3)), positive(a, 5), positive(a, 6), change.intent());
                ForgeFeudalItems.commit(u.player, f.commerce, op, change);
                u.say("Товар виставлено за специфікацією " + spec);
            }
            case "buy" -> {
                String spec = at(a, 2);
                require(!ForgeFeudalItems.prototype(string(m.spec(spec), "item")).m_41619_(), "Предмет недоступний.");
                u.say("Заявка " + m.order(u.actor, req, at(a, 1), p, Department.GOODS, spec, "", code(a, 3), "BUY", positive(a, 5),
                        money(at(a, 4)), positive(a, 6), positive(a, 7), null));
            }
            case "fx" -> u.say("Заявка " + m.order(u.actor, req, at(a, 1), p, Department.FX, "", code(a, 3), code(a, 4), code(a, 2),
                    positive(a, 6), money(at(a, 5)), positive(a, 7), positive(a, 8), null));
            case "cancel" -> { m.cancel(u.actor, req, at(a, 1)); u.say("Заявку скасовано."); }
            case "reduce" -> { m.reduce(u.actor, req, at(a, 1), positive(a, 2)); u.say("Залишок заявки зменшено."); }
            case "orders" -> {
                for (var row : f.db.query("SELECT id,side,department,remaining,state FROM cm_orders WHERE owner=? AND state IN('OPEN','PREPARED') "
                        + "ORDER BY created DESC LIMIT 30", u.actor.id())) {
                    u.say(string(row, "id") + " · " + string(row, "side") + " · " + string(row, "department") + " · " + number(row, "remaining"));
                }
            }
            case "assets" -> {
                String owner = "p:" + u.actor.id();
                if (a.length > 1 && a[1].equals("city")) {
                    var estate = f.realms.estate(home(u));
                    require(f.realms.controlsTreasury(u.actor, estate), "Міські активи доступні лише керівнику скарбниці.");
                    owner = "e:" + estate.id();
                }
                for (var row : f.db.query("SELECT * FROM cm_assets WHERE owner=? AND quantity>0 ORDER BY market,id LIMIT 30", owner)) {
                    u.say(string(row, "id") + " · " + number(row, "quantity") + " " + string(row, "code") + " " + string(row, "spec"));
                }
            }
            case "collect", "recovery" -> {
                String id = at(a, 1);
                var asset = m.ownAsset(u.actor, id);
                boolean recovery = a[0].equals("recovery");
                if (string(asset, "code").isEmpty()) {
                    var item = ForgeFeudalItems.prototype(string(m.spec(string(asset, "spec")), "item"));
                    int count = positive(a, 2);
                    var change = ForgeFeudalItems.change(u.player, item, count, true);
                    var op = m.collectItems(u.actor, req, id, count, p, recovery, change.intent());
                    ForgeFeudalItems.commit(u.player, f.commerce, op, change);
                } else m.collectMoney(u.actor, req, id, p, recovery);
                u.say("Актив видано.");
            }
            case "claim" -> { m.claim(u.actor, req, at(a, 1), coord(a, 2), coord(a, 3), flag(a, 4), tail(a, 5)); u.say("Територію ринку оновлено."); }
            case "building" -> {
                require(u.actor.admin(), "Потрібен адміністратор.");
                String id = at(a, 1);
                var box = new Box(coord(a, 3), coord(a, 4), coord(a, 5), coord(a, 6), coord(a, 7), coord(a, 8));
                var counter = new net.minecraft.core.BlockPos(coord(a, 9), coord(a, 10), coord(a, 11));
                var level = f.level(string(m.market(id), "world"));
                require(level != null && level.m_6857_().m_61937_(counter), "Стійка має бути у завантаженому чанку.");
                var state = level.m_8055_(counter);
                require(!state.m_60795_(), "Потрібен блок стійки.");
                m.building(u.actor, req, id, department(a, 2), box, coord(a, 9), coord(a, 10), coord(a, 11),
                        ForgeMarketBuildings.blockId(state), tail(a, 12));
                u.say("Межі будівлі збережено.");
            }
            case "review" -> { m.reviewDepartment(u.actor, req, at(a, 1), department(a, 2), flag(a, 3), tail(a, 4)); u.say("Відділ розглянуто."); }
            case "state" -> { m.state(u.actor, req, at(a, 1), code(a, 2), tail(a, 3)); u.say("Стан ринку оновлено."); }
            case "permit" -> { m.permit(u.actor, req, at(a, 1), code(a, 2), at(a, 3).toLowerCase(Locale.ROOT), flag(a, 4)); u.say("Дозвіл оновлено."); }
            case "tariffs" -> { m.tariff(u.actor, req, at(a, 1), coord(a, 2), coord(a, 3), coord(a, 4)); u.say("Тарифи збережено."); }
            default -> throw new IllegalArgumentException("Див. /market help.");
        }
    }

    private String marketSpec(ItemStack item) throws Exception {
        require(!item.m_41619_(), "Візьміть предмет у головну руку.");
        var id = ForgeRegistries.ITEMS.getKey(item.m_41720_());
        var tag = item.m_41783_();
        require(id != null && !id.m_135815_().contains("shulker_box")
                && !Set.of("bundle", "filled_map").contains(id.m_135815_())
                && (tag == null || !tag.m_128441_("BlockEntityTag")), "Ринок приймає товари без зовнішнього вмісту.");
        return f.markets.registerSpec(ForgeFeudalItems.prototype(item), item.m_41741_() == 1);
    }

    private void marketJobs(User u, String[] a) throws Exception {
        var jobs = f.markets.contracts;
        var place = ForgeFeudal.place(u.player);
        String action = at(a, 1), req = request();
        switch (action) {
            case "supply" -> u.say("Контракт " + jobs.create(u.actor, req, at(a, 2), place, tail(a, 10), code(a, 3), money(at(a, 4)),
                    positive(a, 5), at(a, 6), positive(a, 7), flag(a, 8), List.of(), List.of(), positive(a, 9)));
            case "work" -> {
                var payouts = new ArrayList<Long>();
                var criteria = new ArrayList<String>();
                long total = 0;
                for (String line : tail(a, 6).split("\\|", -1)) {
                    String[] stage = line.strip().split(" ", 2);
                    long payout = money(at(stage, 0));
                    payouts.add(payout);
                    total = Math.addExact(total, payout);
                    criteria.add(at(stage, 1));
                }
                u.say("Контракт " + jobs.create(u.actor, req, at(a, 2), place, "Робота", code(a, 3), total,
                        positive(a, 4), "", 0, false, payouts, criteria, positive(a, 5)));
            }
            case "list" -> {
                String market = at(a, 2);
                f.markets.access(market, Department.JOBS, place);
                for (var row : f.db.query("SELECT * FROM cm_contracts WHERE market=? AND state='FUNDED' LIMIT 30", market)) {
                    u.say(string(row, "id") + " · " + string(row, "kind") + " · " + string(row, "description")
                            + " · " + format(number(row, "reward")) + " " + string(row, "code"));
                }
            }
            case "deliver" -> {
                String id = at(a, 2);
                var order = jobs.contract(id);
                var item = ForgeFeudalItems.prototype(string(f.markets.spec(string(order, "spec")), "item"));
                int quantity = positive(a, 3);
                var change = ForgeFeudalItems.change(u.player, item, quantity, false);
                var op = jobs.deliver(u.actor, req, id, quantity, place, false, change.intent());
                ForgeFeudalItems.commit(u.player, f.commerce, op, change);
                u.say("Поставку враховано.");
            }
            case "accept" -> { jobs.accept(u.actor, req, at(a, 2), place); u.say("Роботу прийнято."); }
            case "submit" -> { jobs.submit(u.actor, req, at(a, 2), positive(a, 3), tail(a, 4)); u.say("Етап подано."); }
            case "review" -> { jobs.review(u.actor, req, at(a, 2), flag(a, 3), a.length > 4 ? tail(a, 4) : ""); u.say("Етап розглянуто."); }
            case "dispute" -> { jobs.dispute(u.actor, req, at(a, 2), tail(a, 3)); u.say("Спір відкрито."); }
            case "arbitrate" -> { jobs.arbitrate(u.actor, req, at(a, 2), money(at(a, 3)), tail(a, 4)); u.say("Рішення записано."); }
            case "cancel" -> { jobs.cancel(u.actor, req, at(a, 2)); u.say("Роботу скасовано."); }
            default -> {
                u.say("/market jobs supply <ринок> <код> <винагорода> <дні> <товар> <кількість> <частково true|false> <тариф> <опис>");
                u.say("/market jobs work <ринок> <код> <дні> <тариф> <сума критерій | сума критерій> | list <ринок>");
                u.say("/market jobs deliver|accept|submit|review|dispute|arbitrate|cancel <контракт> [умови]");
            }
        }
    }

    private void marketTreaty(User u, String[] a) throws Exception {
        var treaties = f.markets.treaties;
        var place = ForgeFeudal.place(u.player);
        String request = request();
        switch (at(a, 1)) {
            case "create" -> {
                long starts = f.realms.now() + 60_000;
                long ends = starts + positive(a, 12) * FeudalService.DAY;
                u.say("Торговий договір " + treaties.create(u.actor, request, estate(at(a, 2)), estate(at(a, 3)),
                        at(a, 4), at(a, 5), place, coord(a, 6), coord(a, 7), coord(a, 8), money(at(a, 10)),
                        code(a, 9), positive(a, 11), at(a, 14), starts, ends, coord(a, 13)));
            }
            case "publish" -> { treaties.publish(u.actor, request, at(a, 2), place); u.say("Договір опубліковано."); }
            case "view" -> {
                String id = at(a, 2);
                var page = f.markets.documents.view(u.actor, treaties.document(id), positive(a, 3));
                page.lines().forEach(u::say);
                u.say("Сторінка " + page.page() + " / " + page.pages());
                if (!page.token().isEmpty()) u.say("/market treaty sign " + id + " " + page.version() + " " + page.token());
            }
            case "sign" -> { treaties.accept(u.actor, at(a, 2), positive(a, 3), at(a, 4)); u.say("Підпис збережено."); }
            case "amend" -> {
                long ends = f.realms.now() + positive(a, 7) * FeudalService.DAY;
                treaties.amend(u.actor, request, at(a, 2), coord(a, 3), coord(a, 4), coord(a, 5), money(at(a, 6)), ends);
                u.say("Поправку підготовлено.");
            }
            case "reject" -> { treaties.reject(u.actor, request, at(a, 2)); u.say("Проєкт відхилено."); }
            case "terminate" -> { treaties.terminate(u.actor, request, at(a, 2)); u.say("Припинення зареєстровано."); }
            default -> {
                u.say("/market treaty create <сторона1> <сторона2> <ринок1> <ринок2> <покупець_бп> <продавець_бп> <робота_бп> <код> <ліміт> <період_дні> <строк_дні> <попередження_год> <каталог>");
                u.say("/market treaty publish|view|sign|amend|reject|terminate <договір> [умови]");
            }
        }
    }

    private void marketAuctions(User u, String[] a) throws Exception {
        var auctions = f.markets.auctions;
        var place = ForgeFeudal.place(u.player);
        String owner = "p:" + u.actor.id();
        switch (at(a, 1)) {
            case "list" -> {
                String market = at(a, 2);
                f.markets.access(market, Department.GOODS, place);
                for (var row : f.db.query("SELECT * FROM cm_auctions WHERE market=? AND state='OPEN' LIMIT 30", market)) {
                    u.say(string(row, "id") + " · " + string(row, "spec") + " × " + number(row, "quantity")
                            + " · " + format(Math.max(number(row, "bid"), number(row, "start_price"))) + " " + string(row, "code"));
                }
            }
            case "sell" -> {
                var item = u.player.m_21205_();
                int quantity = positive(a, 4);
                var spec = marketSpec(item);
                var change = ForgeFeudalItems.change(u.player, item, quantity, false);
                String op = auctions.create(u.actor, request(), at(a, 2), place, spec, quantity, code(a, 3), money(at(a, 5)),
                        money(at(a, 6)), money(at(a, 7)), positive(a, 8), change.intent());
                ForgeFeudalItems.commit(u.player, f.commerce, op, change);
                u.say("Лот опубліковано.");
            }
            case "quote" -> {
                String id = at(a, 2);
                f.markets.access(string(auctions.auction(id), "market"), Department.GOODS, place);
                u.say("Підтвердження " + auctions.stamp(u.actor, id, owner, money(at(a, 3))));
            }
            case "bid" -> {
                auctions.bid(u.actor, request(), at(a, 2), place, owner, money(at(a, 3)), flag(a, 4), at(a, 5));
                u.say("Ставку прийнято.");
            }
            case "cancel" -> { auctions.cancel(u.actor, request(), at(a, 2)); u.say("Лот скасовано."); }
            default -> u.say("/market auction list <ринок> | sell <ринок> <код> <кількість> <старт> <крок> <викуп> <години> | quote <лот> <сума> | bid <лот> <сума> <викуп true|false> <підтвердження> | cancel <лот>");
        }
    }

    private void provision(User u, String[] a) throws Exception {
        if (a[0].equals("menu")) {
            u.say("Міське сховище провізії");
            u.link("Запаси міста", "/provisions status", false);
            u.link("Внести їжу з руки", "/provisions deposit ", true);
            u.link("Видати їжу зі складу", "/provisions withdraw ", true);
            return;
        }
        if (a[0].equals("help")) {
            u.say("/provisions status | deposit <комірка 1–45> <кількість> | withdraw <комірка 1–45> <кількість>");
            return;
        }
        String city = home(u);
        f.settleTown(UUID.fromString(city.substring(2)), TownyForge.view().territory().values().stream()
                .filter(e -> city.equals(ForgeFeudal.townId(e.town()))).count(), f.realms.now());
        var record = f.provisions.town(city);
        require(record != null, "Календар провізії ще не готовий.");
        if (a[0].equals("status")) {
            u.say("Запас ситості " + f.provisions.reserve(city).divide(ProvisionClock.UNIT) + " · чанків " + number(record, "chunks"));
            for (var row : f.provisions.stock(city)) {
                String encoded = string(row, "item");
                String label = encoded.startsWith("forge1:") ? ForgeFeudalItems.prototype(encoded).m_41720_().toString() : "предмет із Bukkit";
                u.say((number(row, "slot") + 1) + " · " + number(row, "quantity") + " × " + label);
            }
            return;
        }
        require(Set.of("deposit", "withdraw").contains(a[0]), "Див. /provisions help.");
        boolean withdraw = a[0].equals("withdraw");
        if (withdraw) f.realms.controlTreasury(u.actor, f.realms.estate(city));
        int slot = positive(a, 1) - 1, count = positive(a, 2);
        var stored = f.db.one("SELECT * FROM provision_stock WHERE town=? AND slot=?", city, slot);
        require(!withdraw || stored != null, "Комірка порожня.");
        var item = withdraw ? ForgeFeudalItems.prototype(string(stored, "item")) : u.player.m_21205_();
        var food = item.getFoodProperties(u.player);
        require(food != null, "Для провізії потрібна їжа.");
        String encoded = ForgeFeudalItems.prototype(item);
        var change = ForgeFeudalItems.change(u.player, item, count, withdraw);
        var op = f.provisions.transfer(u.actor, city, slot, stored == null ? 0 : number(stored, "version"), encoded,
                withdraw ? -count : count, withdraw ? (int) number(stored, "nutrition") : food.m_38744_(),
                withdraw ? (int) number(stored, "max_stack") : item.m_41741_(), change.intent());
        ForgeFeudalItems.commit(u.player, f.commerce, op, change);
        u.say("Провізію оновлено.");
    }

    private void fleet(User u, String[] a) throws Exception {
        var fleet = f.fleet.fleet;
        switch (a[0]) {
            case "menu" -> {
                u.say("Міський флот");
                u.link("Кораблі", "/fleet list", false);
                u.link("Мої заявки", "/shiprequest list", false);
                u.link("Подати заявку на корабель", "/shiprequest submit ", true);
                u.link("Екіпаж, прапори й торгівля", "/fleet help", false);
            }
            case "help" -> {
                u.say("/fleet list | name|captain|crew-add|crew-remove <корабель> <значення> | admiral <гравець> | port <ринок> <true|false>");
                u.say("/fleet offers <ринок> | offer <лот> | sell|auction <корабель> <ринок> <код> <ціна> <крок> <години> <опис>");
                u.say("/fleet buy <лот> <підтвердження> | bid <лот> <сума> <підтвердження> | cancel <лот> | /shiprequest help");
                u.say("/fleet flag status|prepare|apply · /fleet ensign help");
            }
            case "ensign" -> ensign(u, a);
            case "flag" -> {
                String action = at(a, 1);
                long ship = number(f.fleet.engagement("look", Map.of("player", u.actor.id())), "ship");
                var data = new java.util.HashMap<String, Object>(Map.of("player", u.actor.id(), "ship", ship));
                if (!action.equals("status")) data.put("mode", code(a, 2));
                if (action.equals("apply")) data.put("expected", at(a, 3));
                require(Set.of("status", "prepare", "apply").contains(action), "Доступні status, prepare та apply.");
                var result = f.fleet.engagement(action.equals("prepare") ? "inspect" : action, data);
                u.say(result.toString());
            }
            case "list" -> fleet.choices(u.actor).forEach(ship -> u.say("#" + ship.id() + " · " + ship.name() + " · " + ship.uses()));
            case "name", "captain", "crew-add", "crew-remove" -> {
                long ship = fleet.resolveShip(u.actor, at(a, 1), FleetService.ShipUse.MANAGE);
                String value = tail(a, 2);
                fleet.edit(u.actor, request(), ship, a[0], a[0].equals("name") ? value : value.equals("-") ? "" : person(value));
                f.fleet.project(ship);
                u.say("Корабель оновлено.");
            }
            case "admiral" -> { fleet.admiral(u.actor, request(), home(u), at(a, 1).equals("-") ? "" : person(a[1])); u.say("Адмірала оновлено."); }
            case "port" -> { fleet.port(u.actor, request(), at(a, 1), flag(a, 2)); u.say("Порт оновлено."); }
            case "offers" -> {
                String market = at(a, 1);
                f.markets.access(market, Department.GOODS, ForgeFeudal.place(u.player));
                for (var row : f.db.query("SELECT * FROM naval_offers WHERE market=? AND state='OPEN' LIMIT 30", market)) offer(u, row);
            }
            case "offer" -> {
                var row = fleet.offer(at(a, 1));
                fleetAccess(u, row);
                offer(u, row);
                u.say("Підтвердження " + fleet.stamp(a[1]));
            }
            case "sell", "auction", "direct" -> {
                long ship = fleet.resolveShip(u.actor, at(a, 1), FleetService.ShipUse.SALE);
                boolean direct = a[0].equals("direct");
                String market = direct ? "" : at(a, 2);
                if (!direct) f.markets.access(market, Department.GOODS, ForgeFeudal.place(u.player));
                u.say("Лот " + fleet.list(u.actor, request(), ship, market, a[0].equals("sell") ? "SALE" : code(a, 0),
                        direct ? estate(at(a, 2)) : "", code(a, 3), money(at(a, 4)), money(at(a, 5)), positive(a, 6), tail(a, 7)));
                f.fleet.project(ship);
            }
            case "buy", "bid", "cancel" -> {
                var row = fleet.offer(at(a, 1));
                if (!a[0].equals("cancel")) fleetAccess(u, row);
                switch (a[0]) {
                    case "buy" -> fleet.buy(u.actor, request(), a[1], home(u), at(a, 2));
                    case "bid" -> fleet.bid(u.actor, request(), a[1], home(u), money(at(a, 2)), at(a, 3));
                    default -> fleet.cancel(u.actor, request(), a[1]);
                }
                f.fleet.project(number(row, "ship"));
                u.say("Лот оновлено.");
            }
            default -> throw new IllegalArgumentException("Див. /fleet help.");
        }
    }

    private void ensign(User u, String[] a) throws SQLException {
        String action = at(a, 1);
        if (action.equals("help")) {
            u.say("/fleet ensign prepare <banner|painting> | apply <banner|painting> <підтвердження>");
            return;
        }
        require(Set.of("prepare", "apply").contains(action), "Див. /fleet ensign help.");
        String country = f.fleet.nation(home(u));
        require(!country.isEmpty(), "Місто має належати до нації.");
        var nation = TownyForge.view().nations().get(UUID.fromString(country));
        var capital = TownyForge.view().towns().get(nation.capital());
        require(capital != null && capital.mayor().equals(u.player.m_20148_()), "Державний прапор затверджує правитель нації.");
        String source = at(a, 2);
        require(Set.of("banner", "painting").contains(source), "Доступні banner та painting.");
        var design = f.fleet.engagement("design", Map.of("player", u.actor.id(), "source", source));
        var current = f.db.one("SELECT revision FROM naval_ensigns WHERE nation=?", country);
        long rev = current == null ? 0 : number(current, "revision");
        String stamp = CityMarketService.hash(u.actor.id() + "|" + country + "|" + design.get("hash") + "|" + rev);
        if (action.equals("prepare")) {
            u.link("Підтвердити державний прапор", "/fleet ensign apply " + source + " " + stamp, true);
            return;
        }
        require(stamp.equals(at(a, 3)), "Прапор або його редакція змінилися. Повторіть перегляд.");
        f.db.transaction(() -> {
            f.db.execute("MERGE INTO naval_ensigns KEY(nation) VALUES(?,?,?,?,?,?)", country, design.get("kind"),
                    design.get("payload"), design.get("hash"), rev + 1, u.actor.id());
            f.realms.audit(u.actor, "NAVAL_ENSIGN", country + " " + design.get("hash"));
            return null;
        });
        u.say("Державний прапор оновлено.");
    }

    private void fleetAccess(User u, Map<String, Object> offer) throws Exception {
        var market = string(offer, "market");
        if (market.isEmpty()) require(home(u).equals(string(offer, "seller")) || home(u).equals(string(offer, "buyer")), "Лот іншого міста.");
        else f.markets.access(market, Department.GOODS, ForgeFeudal.place(u.player));
    }

    private static void offer(User user, Map<String, Object> row) {
        user.say(string(row, "id") + " · #" + number(row, "ship") + " · " + string(row, "kind") + " · "
                + format(Math.max(number(row, "price"), number(row, "bid"))) + " " + string(row, "code") + " · " + string(row, "description"));
    }

    private void shipRequest(User u, String[] a) throws Exception {
        var service = f.fleet.registrations;
        switch (a[0]) {
            case "help" -> u.say("/shiprequest submit <назва> | list | withdraw <ID>");
            case "list" -> f.db.query("SELECT * FROM naval_applications WHERE applicant=? ORDER BY created DESC LIMIT 30", u.actor.id())
                    .forEach(row -> shipInfo(u, row));
            case "withdraw" -> { service.withdraw(u.actor, request(), at(a, 1)); u.say("Заявку відкликано."); }
            case "submit" -> {
                var hit = u.player.m_19907_(8, 1, false);
                require(hit instanceof BlockHitResult && hit.m_6662_() == HitResult.Type.BLOCK, "Наведіться на блок корабля.");
                var pos = ((BlockHitResult) hit).m_82425_();
                var anchor = new Anchor(ForgeFeudal.place(u.player).world(), pos.m_123341_(), pos.m_123342_(), pos.m_123343_());
                f.fleet.nearby(u.player, anchor);
                u.say("Заявка " + service.submit(u.actor, request(), tail(a, 1), anchor));
            }
            default -> throw new IllegalArgumentException("Див. /shiprequest help.");
        }
    }

    private void shipAdmin(User u, String[] a) throws Exception {
        var service = f.fleet.registrations;
        service.administrator(u.actor);
        switch (a[0]) {
            case "help" -> u.say("/shipadmin list | approve|reject <ID> [примітка] | finish <ID> <місто> <назва> | owner <корабель> <місто>");
            case "list" -> f.db.query("SELECT * FROM naval_applications WHERE state IN('PENDING','APPROVED','ASSEMBLING','ASSEMBLED') ORDER BY created LIMIT 30")
                    .forEach(row -> shipInfo(u, row));
            case "approve", "reject" -> {
                service.review(u.actor, request(), at(a, 1), a[0].equals("approve"), a.length > 2 ? tail(a, 2) : "");
                shipInfo(u, service.application(a[1]));
            }
            case "finish" -> {
                long ship = number(service.application(at(a, 1)), "ship");
                f.fleet.ensure(ship);
                service.finalizeShip(u.actor, request(), a[1], estate(at(a, 2)), tail(a, 3));
                f.fleet.project(ship);
                u.say("Корабель оформлено.");
            }
            case "owner" -> {
                long ship = Long.parseLong(at(a, 1));
                f.fleet.ensure(ship);
                require(!service.pending(ship), "Завершіть заявку через /shipadmin finish.");
                f.fleet.fleet.assign(u.actor, request(), ship, estate(at(a, 2)));
                f.fleet.project(ship);
                u.say("Власника оновлено.");
            }
            default -> throw new IllegalArgumentException("Див. /shipadmin help.");
        }
    }

    private static void shipInfo(User u, Map<String, Object> row) {
        u.say(string(row, "id") + " · " + string(row, "name") + " · " + string(row, "state") + " · "
                + number(row, "x") + " " + number(row, "y") + " " + number(row, "z"));
    }

    private void recover(User u, String[] a) throws Exception {
        var pending = f.db.query("SELECT p.* FROM item_participants p JOIN item_ops o ON p.op=o.id "
                + "WHERE p.person=? AND o.status='PREPARED' ORDER BY o.created LIMIT 30", u.actor.id());
        if (a[0].equals("pending")) {
            if (pending.isEmpty()) u.say("Незавершених передач немає.");
            for (var row : pending) u.say(string(row, "op"));
        } else if (a[0].equals("recover")) {
            String op = at(a, 1);
            var matched = pending.stream().filter(row -> op.equals(string(row, "op"))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Незавершену передачу не знайдено."));
            require(number(f.db.one("SELECT COUNT(*) AS n FROM item_participants WHERE op=?", op), "n") == 1,
                    "Передача з кількома учасниками потребує адміністратора.");
            ForgeFeudalItems.recover(u.player, f.commerce, op, string(matched, "before_items"), string(matched, "after_items"));
            u.say("Незавершену передачу узгоджено.");
        } else u.say("/commerce pending | recover <операція>");
    }

    private String estate(String text) throws Exception { return f.realms.resolve(text).id(); }

    private String home(User user) {
        String town = f.hometown(user.actor.id());
        require(town != null, "Для цієї дії потрібне місто.");
        return town;
    }

    static String person(String name) {
        return TownyForge.view().residents().values().stream().filter(player ->
                player.name().equalsIgnoreCase(name) || player.id().toString().equalsIgnoreCase(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Мешканця не знайдено.")).id().toString();
    }

    private static Department department(String[] a, int i) {
        var value = Department.valueOf(code(a, i));
        require(value != Department.CREDIT, "Доступні відділи GOODS, FX та JOBS.");
        return value;
    }

    static String request() { return UUID.randomUUID().toString(); }
    static String code(String[] a, int i) { return at(a, i).toUpperCase(Locale.ROOT); }
    static String at(String[] a, int i) {
        require(i < a.length, "Бракує аргументів. Див. help цієї команди.");
        return a[i];
    }
    static String tail(String[] a, int i) {
        at(a, i);
        return String.join(" ", Arrays.copyOfRange(a, i, a.length));
    }
    static int coord(String[] a, int i) {
        int value = Integer.parseInt(at(a, i));
        require(Math.abs((long) value) <= 30_000_000, "Число виходить за межі.");
        return value;
    }
    static int positive(String[] a, int i) {
        int value = coord(a, i);
        require(value > 0, "Потрібне додатне число.");
        return value;
    }
    static boolean flag(String[] a, int i) {
        String value = at(a, i);
        require(Set.of("true", "false").contains(value), "Вкажіть true або false.");
        return Boolean.parseBoolean(value);
    }

    record User(CommandSourceStack source, ServerPlayer player, Actor actor) {
        void say(String text) { source.m_288197_(() -> Component.m_237113_(text), false); }

        void link(String label, String command, boolean suggest) {
            var action = suggest ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.RUN_COMMAND;
            source.m_288197_(() -> Component.m_237113_("  • " + label).m_130938_(style -> style
                    .m_131140_(ChatFormatting.GOLD).m_131142_(new ClickEvent(action, command))), false);
        }
    }

    @FunctionalInterface
    private interface Operation {
        void run(User user, String[] arguments) throws Exception;
    }
}
