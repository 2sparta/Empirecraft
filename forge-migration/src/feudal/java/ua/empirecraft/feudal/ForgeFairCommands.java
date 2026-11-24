package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import ua.empirecraft.feudal.ForgeFeudalCommands.User;
import ua.empirecraft.feudal.ForgeFeudalItems.Movement;
import static ua.empirecraft.feudal.CityMarketService.time;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.ForgeFeudalCommands.*;
import static ua.empirecraft.feudal.Model.require;

final class ForgeFairCommands {
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private final ForgeFeudal f;

    ForgeFairCommands(ForgeFeudal f) {
        this.f = f;
    }

    void fair(User u, String[] a) throws Exception {
        var fairs = f.markets.fairs;
        var place = ForgeFeudal.place(u.player());
        switch (at(a, 1)) {
            case "list" -> list(u);
            case "view" -> {
                var row = fairForViewer(u, at(a, 2));
                u.say(string(row, "name") + " · " + string(row, "theme"));
                u.say("Початок " + time(number(row, "starts")) + " · завершення " + time(number(row, "ends")));
                u.say("Відділи " + string(row, "departments") + " · каталог " + string(row, "catalog"));
                u.say("Збори в б.п. · покупець " + number(row, "buyer") + " · продавець " + number(row, "seller") + " · послуги " + number(row, "jobs"));
            }
            case "create" -> {
                String departments = code(a, 5);
                require(Arrays.stream(departments.split(",", -1)).allMatch(Set.of("GOODS", "FX", "JOBS")::contains),
                        "Виберіть відділи GOODS, FX або JOBS через кому.");
                String id = fairs.create(u.actor(), request(), at(a, 2), place, at(a, 10).replace('_', ' '), tail(a, 11),
                        date(at(a, 3)), positive(a, 4), departments, at(a, 6), coord(a, 7), coord(a, 8), coord(a, 9));
                u.say("Чернетка ярмарку " + id);
                u.link("Написати запрошення", "/market fair letter edit " + id + " ", true);
                u.link("Внести текст із книги", "/market fair letter editbook " + id, true);
            }
            case "cancel" -> {
                fairs.cancel(u.actor(), request(), at(a, 2));
                u.say("Ярмарок скасовано.");
            }
            case "letter" -> letter(u, a);
            default -> help(u);
        }
    }

    void mail(User u, String[] a) throws SQLException {
        if (a.length <= 2 && (a.length == 1 || a[1].equals("status"))) {
            f.markets.fairs.mail.status(u.actor()).forEach(u::say);
            return;
        }
        if (a[1].equals("status")) {
            String fair = at(a, 2);
            var row = f.markets.fairs.fair(fair);
            f.markets.manage(u.actor(), string(row, "market"));
            u.say(string(row, "name") + " · " + string(row, "state"));
            for (var count : f.db.query("SELECT j.state,COUNT(*) AS n FROM cm_mail_jobs j JOIN cm_mail_samples s "
                    + "ON s.id=j.sample WHERE s.fair=? AND s.funded=TRUE GROUP BY j.state ORDER BY j.state", fair)) {
                u.say(string(count, "state") + " · " + number(count, "n"));
            }
            return;
        }
        u.say("/market mail status [ярмарок]");
        u.link("Підготовка листівок", "/market fair help", false);
        u.link("Отримати повернений папір", "/market assets", false);
    }

    private void letter(User u, String[] a) throws Exception {
        var fairs = f.markets.fairs;
        var mail = fairs.mail;
        var place = ForgeFeudal.place(u.player());
        String id = at(a, 3);
        switch (at(a, 2)) {
            case "edit", "editbook" -> {
                fairs.edit(u.actor(), request(), id, a[2].equals("editbook") ? book(u) : tail(a, 4));
                u.say("Текст запрошення збережено.");
                u.link("Створити листівку-зразок за 1 папір", "/market fair letter preview " + id, true);
            }
            case "preview" -> {
                String token = fairs.preview(u.actor(), id, place);
                var sample = mail.sample(token);
                if ("NEW".equals(string(sample, "state"))) {
                    var transfer = ForgeFeudalItems.change(u.player(),
                            new Movement(ForgeContactMail.paper(), 1, false),
                            new Movement(ForgeFeudalItems.prototype(string(sample, "item")), 1, true));
                    String op = mail.issue(u.actor(), token, transfer.intent());
                    ForgeFeudalItems.commit(u.player(), f.commerce, op, transfer);
                } else {
                    require("ISSUED".equals(string(sample, "state")), "Завершіть передачу зразка через /commerce recover.");
                }
                int copies = mail.targets(token).size();
                u.say("Прочитайте листівку Contact і візьміть її в головну руку. На розсилку потрібно " + copies + " аркушів паперу.");
                u.link("Підготувати підтвердження", "/market fair letter confirm " + id + " " + number(sample, "version") + " " + token, true);
            }
            case "confirm" -> {
                long version = positive(a, 4);
                String token = at(a, 5);
                var sample = mail.sample(token);
                require(id.equals(string(sample, "fair")) && u.actor().id().equals(string(sample, "actor"))
                        && version == number(sample, "version"), "Зразок не відповідає цій редакції.");
                var card = ForgeFeudalItems.prototype(string(sample, "item"));
                var held = u.player().m_21205_();
                require(!held.m_41619_() && ForgeFeudalItems.prototype(held).equals(ForgeFeudalItems.prototype(card)),
                        "Тримайте видану листівку-зразок у головній руці.");
                int copies = mail.targets(token).size();
                var transfer = ForgeFeudalItems.change(u.player(), new Movement(card, 1, false),
                        new Movement(ForgeContactMail.paper(), copies, false));
                String op = mail.confirm(u.actor(), request(), id, version, token, place, transfer.intent());
                ForgeFeudalItems.commit(u.player(), f.commerce, op, transfer);
                u.say("Розсилку поставлено в чергу на " + copies + " листівок.");
                u.link("Стан розсилки", "/market mail status " + id, false);
            }
            case "cancel" -> {
                fairs.cancelLetter(u.actor(), request(), id);
                u.say("Зразок цієї редакції скасовано.");
            }
            default -> help(u);
        }
    }

    private static String book(User user) {
        var item = user.player().m_21205_();
        require(item.m_41720_() == Items.f_42614_, "Візьміть книгу з пером у головну руку.");
        var tag = item.m_41783_();
        require(tag != null, "У книзі ще немає тексту.");
        var pages = tag.m_128437_("pages", 8);
        var text = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) text.append('\n');
            text.append(pages.m_128778_(i));
            require(text.length() <= 4000, "Запрошення має містити до 4000 символів.");
        }
        require(!text.toString().isBlank(), "У книзі ще немає тексту.");
        return text.toString();
    }

    private void list(User u) throws SQLException {
        var rows = f.db.query("SELECT f.* FROM cm_fairs f JOIN cm_markets m ON f.market=m.id "
                + "WHERE f.kind='FAIR' AND f.state IN('ANNOUNCED','ACTIVE') AND f.ends>? AND m.state='OPEN' "
                + "ORDER BY f.starts,f.id LIMIT 20", f.realms.now());
        if (rows.isEmpty()) u.say("Оголошених ярмарків поки немає.");
        for (var row : rows) u.link(string(row, "name") + " · " + time(number(row, "starts")),
                "/market fair view " + string(row, "id"), false);
    }

    private Map<String, Object> fairForViewer(User u, String id) throws SQLException {
        var row = f.markets.fairs.fair(id);
        require("FAIR".equals(string(row, "kind")), "Ярмарок не знайдено.");
        if (!Set.of("ANNOUNCED", "ACTIVE").contains(string(row, "state"))) f.markets.manage(u.actor(), string(row, "market"));
        return row;
    }

    private static long date(String input) {
        try {
            var local = LocalDateTime.parse(input);
            var choices = KYIV.getRules().getValidOffsets(local);
            require(choices.size() == 1, "На цю годину припадає переведення годинника. Виберіть інший час.");
            return local.toInstant(choices.get(0)).toEpochMilli();
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Вкажіть київський час у форматі YYYY-MM-DDTHH:mm.");
        }
    }

    private static void help(User u) {
        u.say("/market fair list | view <ярмарок> | cancel <ярмарок>");
        u.say("/market fair create <ринок> <YYYY-MM-DDTHH:mm> <тривалість_год> <відділи_через_кому> <каталог_або_*> <покупець_бп> <продавець_бп> <послуги_бп> <назва_через_підкреслення> <тема>");
        u.say("/market fair letter edit <ярмарок> <текст> | editbook|preview|cancel <ярмарок>");
        u.say("/market fair letter confirm <ярмарок> <версія> <токен> · /market mail status [ярмарок]");
    }
}
