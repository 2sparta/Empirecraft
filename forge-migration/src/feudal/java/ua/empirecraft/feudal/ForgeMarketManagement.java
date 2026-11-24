package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ua.empirecraft.feudal.CityMarketService.Place;
import ua.empirecraft.feudal.ForgeFeudalCommands.User;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.ForgeFeudalCommands.*;
import static ua.empirecraft.feudal.Model.*;

final class ForgeMarketManagement {
    private final ForgeFeudal f;

    ForgeMarketManagement(ForgeFeudal f) {
        this.f = f;
    }

    void board(User user, String[] a) throws SQLException {
        var board = f.markets.notices;
        var actor = user.actor();
        var place = ForgeFeudal.place(user.player());
        String action = at(a, 1), req = request();
        if (action.equals("help")) {
            user.say("/market board list <ринок> [сторінка] | read <оголошення> | fee <ринок>");
            user.say("/market board post <ринок> <дні> <версія_плати> <заголовок_через_підкреслення> <текст>");
            user.say("/market board bind <ринок> | setup <ринок> <плата> <код_або_-> <перевірка true|false>");
            user.say("/market board herald <ринок> <гравець> <true|false> | pending <ринок> | review <ID> <true|false> [примітка] | remove <ID> [примітка]");
            return;
        }
        String id = at(a, 2);
        switch (action) {
            case "bind" -> {
                var hit = user.player().m_19907_(8, 1, false);
                require(hit instanceof BlockHitResult && hit.m_6662_() == HitResult.Type.BLOCK, "Наведіться на табличку ринку.");
                var pos = ((BlockHitResult) hit).m_82425_();
                var location = new Place(place.world(), pos.m_123341_() + .5, pos.m_123342_() + .5, pos.m_123343_() + .5);
                require(sign(location.world(), pos), "Наведіться на табличку ринку.");
                board.bind(actor, req, id, location);
                user.say("Дошку оголошень прив’язано до таблички.");
            }
            case "setup" -> { board.configure(actor, req, id, money(at(a, 3)), code(a, 4), flag(a, 5)); user.say("Дошку налаштовано."); }
            case "herald" -> { board.appoint(actor, req, id, person(at(a, 3)), flag(a, 4)); user.say("Посаду вісника оновлено."); }
            case "fee" -> {
                board.present(id, place);
                var terms = board.settings(id);
                user.say("Плата " + format(number(terms, "fee")) + " " + string(terms, "code") + " · редакція " + number(terms, "version"));
            }
            case "post" -> user.say("Оголошення " + board.submit(actor, req, id, place, at(a, 5).replace('_', ' '), tail(a, 6),
                    positive(a, 3), positive(a, 4)));
            case "list" -> {
                var rows = board.publicEntries(id, place, a.length > 3 ? positive(a, 3) : 1);
                if (rows.isEmpty()) user.say("На цій сторінці оголошень немає.");
                for (var row : rows) notice(user, row);
            }
            case "read" -> {
                var row = board.read(actor, id, place);
                user.say(string(row, "title") + " · " + string(row, "state"));
                user.say(string(row, "body"));
            }
            case "pending" -> {
                board.present(id, place);
                require(board.moderator(actor, id), "Черга доступна меру або міському віснику.");
                for (var row : f.db.query("SELECT id,title FROM cm_notices WHERE market=? AND state='PENDING' AND ends>? "
                        + "ORDER BY created LIMIT 20", id, f.realms.now())) notice(user, row);
            }
            case "review" -> { board.review(actor, req, id, flag(a, 3), a.length > 4 ? tail(a, 4) : ""); user.say("Оголошення розглянуто."); }
            case "remove" -> { board.remove(actor, req, id, a.length > 3 ? tail(a, 3) : ""); user.say("Оголошення знято."); }
            default -> throw new IllegalArgumentException("Див. /market board help.");
        }
    }

    private static void notice(User user, Map<String, Object> row) {
        user.link(string(row, "title"), "/market board read " + string(row, "id"), false);
    }

    boolean marker(Map<String, Object> row) {
        return sign(string(row, "world"), new BlockPos((int) number(row, "x"), (int) number(row, "y"), (int) number(row, "z")));
    }

    private boolean sign(String world, BlockPos pos) {
        var level = f.level(world);
        return level != null && level.m_6857_().m_61937_(pos) && level.m_7702_(pos) instanceof SignBlockEntity;
    }

    void procurement(User user, String[] a) throws SQLException {
        var service = f.markets.procurement;
        var actor = user.actor();
        var place = ForgeFeudal.place(user.player());
        String action = at(a, 1), req = request();
        if (action.equals("help")) {
            user.say("/market procurement create <ринок> <товар> <код> <поріг> <ціль> <ціна> <фонд> <добовий_ліміт> <партія> <дні>");
            user.say("/market procurement list <ринок> | view|pause|resume|stop <доручення> | deposit <доручення> <сума>");
            user.say("/market procurement amend <доручення> <поріг> <ціль> <ціна> <добовий_ліміт> <партія>");
            return;
        }
        String id = at(a, 2);
        switch (action) {
            case "create" -> user.say("Міське доручення " + service.create(actor, req, id, place, at(a, 3), code(a, 4), coord(a, 5),
                    positive(a, 6), money(at(a, 7)), money(at(a, 8)), money(at(a, 9)), positive(a, 10), positive(a, 11)));
            case "list" -> {
                f.markets.manage(actor, id);
                for (var row : f.db.query("SELECT id,spec,state FROM cm_procurements WHERE market=? ORDER BY id LIMIT 30", id)) {
                    user.link(string(row, "state") + " · " + string(row, "spec"), "/market procurement view " + string(row, "id"), false);
                }
            }
            case "view" -> {
                var row = service.mandate(id);
                f.markets.manage(actor, string(row, "market"));
                user.say(string(row, "state") + " · " + string(row, "spec") + " · " + string(row, "reason"));
                user.say("Фонд " + format(f.realms.balance(MarketProcurement.fund(id), string(row, "code"))) + " " + string(row, "code"));
            }
            case "pause", "resume", "stop" -> {
                service.state(actor, req, id, switch (action) { case "pause" -> "PAUSED"; case "resume" -> "RUNNING"; default -> "STOPPED"; });
                user.say("Стан доручення оновлено.");
            }
            case "deposit" -> { service.deposit(actor, req, id, place, money(at(a, 3))); user.say("Фонд закупівлі поповнено."); }
            case "amend" -> {
                service.amend(actor, req, id, place, coord(a, 3), positive(a, 4), money(at(a, 5)), money(at(a, 6)), positive(a, 7));
                user.say("Умови доручення оновлено.");
            }
            default -> throw new IllegalArgumentException("Див. /market procurement help.");
        }
    }

    void chart(User user, String[] a) throws SQLException {
        var charts = f.markets.charts;
        var place = ForgeFeudal.place(user.player());
        String market = at(a, 1);
        if (a.length < 3) {
            for (String pair : charts.pairs(market, place)) user.link(pair, "/market chart " + market + " " + pair + " 24", false);
        } else user.say(charts.chart(market, place, code(a, 2), positive(a, 3)).toString());
    }
}
