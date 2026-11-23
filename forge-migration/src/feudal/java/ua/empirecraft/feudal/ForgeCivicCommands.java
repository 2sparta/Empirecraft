package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ua.empirecraft.feudal.ForgeFeudalCommands.User;
import ua.empirecraft.feudal.FeudalService.Actor;
import ua.empirecraft.feudal.Model.Estate;
import ua.empirecraft.townyforge.TownyForge;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.ForgeFeudalCommands.*;
import static ua.empirecraft.feudal.Model.*;

final class ForgeCivicCommands {
    private final ForgeFeudal f;

    ForgeCivicCommands(ForgeFeudal f) {
        this.f = f;
    }

    void diplomacy(User u, String[] a) throws SQLException {
        var diplomacy = f.diplomacy;
        switch (a[0]) {
            case "help", "menu" -> {
                u.say("/town diplomacy list [держава] | view <договір> | sign <договір> | end <договір> <причина>");
                u.say("/town diplomacy offer <держава> <інша_держава> <alliance|nonaggression|trade|passage> <дні> <умови>");
            }
            case "list" -> {
                String id = a.length > 1 ? estate(a[1]) : state(u);
                for (var row : diplomacy.treaties(id)) {
                    if ("DRAFT".equals(string(row, "status")) && !party(u.actor(), row)) continue;
                    u.link(string(row, "kind") + " · " + string(row, "status"), "/town diplomacy view " + string(row, "id"), false);
                }
            }
            case "view" -> {
                var row = diplomacy.treaty(at(a, 1));
                require(!"DRAFT".equals(string(row, "status")) || party(u.actor(), row), "Чернетка доступна сторонам договору.");
                u.say(f.realms.estate(string(row, "a")).name() + " ↔ " + f.realms.estate(string(row, "b")).name()
                        + " · " + string(row, "kind") + " · " + string(row, "status") + " · " + number(row, "days") + " діб");
                u.say(string(row, "terms"));
            }
            case "offer" -> u.say("Проєкт дипломатичного договору "
                    + diplomacy.offer(u.actor(), estate(at(a, 1)), estate(at(a, 2)), at(a, 3), positive(a, 4), tail(a, 5)));
            case "sign" -> { diplomacy.sign(u.actor(), at(a, 1)); u.say("Договір підписано."); }
            case "end" -> { diplomacy.end(u.actor(), at(a, 1), tail(a, 2)); u.say("Договір завершено."); }
            default -> throw new IllegalArgumentException("Див. /town diplomacy help.");
        }
    }

    private boolean party(Actor actor, Map<String, Object> row) throws SQLException {
        return actor.id().equals(f.realms.estate(string(row, "a")).lord()) || actor.id().equals(f.realms.estate(string(row, "b")).lord());
    }

    void union(User u, String[] a) throws SQLException {
        var unions = f.politics.unions;
        if (Set.of("help", "menu").contains(a[0])) {
            u.say("/town union list [володіння] | view <союз> | create <держава> <тип> <назва>");
            u.say("/town union propose <держава> <тип> <назва_через_підкреслення> <учасники_через_кому> <повноваження> <внесок> <код_або_-> <статут>");
            u.say("/town union invite|charter|sign|reject|merge|accept|leave|contribute <союз> <держава> [умови]");
            u.say("/town union decision <союз> <держава> <BUDGET|INVITE|CHARTER|TERMINATE> [умови] | vote <рішення> <держава> <true|false>");
            return;
        }
        if (a[0].equals("list")) {
            for (var row : unions.forState(a.length > 1 ? estate(a[1]) : state(u))) {
                u.link(string(row, "name") + " · " + string(row, "status"), "/town union view " + string(row, "id"), false);
            }
            return;
        }
        String id = at(a, 1);
        if (a[0].equals("view")) {
            var row = unions.get(id);
            if ("DRAFT".equals(string(row, "status")) && !u.actor().admin()) {
                boolean allowed = false;
                for (var member : unions.members(id)) {
                    if (u.actor().id().equals(f.realms.estate(string(member, "state")).lord())) { allowed = true; break; }
                }
                require(allowed, "Чернетка союзу доступна сторонам.");
            }
            u.say(string(row, "name") + " · " + string(row, "kind") + " · " + string(row, "status"));
            u.say(string(row, "charter"));
            for (var member : unions.members(id)) u.say(f.realms.estate(string(member, "state")).name()
                    + (bool(member, "signed") ? " · підписано" : " · очікує підпису"));
            for (var decision : f.db.query("SELECT id,kind FROM political_union_votes WHERE union_id=? AND status='OPEN' "
                    + "AND deadline>? ORDER BY deadline LIMIT 15", id, f.realms.now())) {
                u.link(string(decision, "kind") + " · " + string(decision, "id"), "/town union vote " + string(decision, "id") + " ", true);
            }
            return;
        }
        if (a[0].equals("create") || a[0].equals("propose")) {
            String source = estate(id);
            var type = PoliticalModel.parse(PoliticalModel.UnionType.class, at(a, 2));
            if (a[0].equals("create")) u.say("Проєкт " + unions.create(u.actor(), source, type, tail(a, 3)));
            else {
                var members = new ArrayList<String>();
                for (String member : at(a, 4).split(",")) members.add(estate(member));
                String currency = code(a, 7);
                u.say("Проєкт " + unions.propose(u.actor(), source, members, type, at(a, 3).replace('_', ' '),
                        tail(a, 8), at(a, 5), money(at(a, 6)), currency.equals("-") ? "" : currency));
            }
            return;
        }
        String state = estate(at(a, 2));
        switch (a[0]) {
            case "invite" -> unions.inviteDraft(u.actor(), id, state, estate(at(a, 3)));
            case "charter" -> unions.amendDraft(u.actor(), id, state, tail(a, 3));
            case "sign" -> unions.sign(u.actor(), id, state);
            case "reject" -> unions.reject(u.actor(), id, state);
            case "merge" -> unions.confirmMerge(u.actor(), id, state);
            case "accept" -> unions.acceptInvite(u.actor(), id, state);
            case "leave" -> unions.leave(u.actor(), id, state);
            case "contribute" -> unions.contribute(u.actor(), id, state, money(at(a, 3)));
            case "vote" -> unions.vote(u.actor(), id, state, flag(a, 3));
            case "decision" -> {
                String kind = code(a, 3);
                String vote = switch (kind) {
                    case "BUDGET" -> unions.proposeVote(u.actor(), id, state, kind, receiver(at(a, 4)), code(a, 5), money(at(a, 6)));
                    case "INVITE" -> unions.proposeVote(u.actor(), id, state, kind, estate(at(a, 4)), "", 0);
                    case "CHARTER" -> unions.proposeVote(u.actor(), id, state, kind, tail(a, 4), "", 0);
                    case "TERMINATE" -> unions.proposeVote(u.actor(), id, state, kind, "", "", 0);
                    default -> throw new IllegalArgumentException("Див. /town union help.");
                };
                u.say("Голосування " + vote);
                return;
            }
            default -> throw new IllegalArgumentException("Див. /town union help.");
        }
        u.say("Дію щодо об’єднання збережено.");
    }

    String receiver(String key) throws SQLException {
        if (key.startsWith("p:")) return "p:" + person(key.substring(2));
        if (key.startsWith("e:")) return "e:" + estate(key.substring(2));
        throw new IllegalArgumentException("Укажіть p:гравець або e:володіння.");
    }

    void election(User u, String[] a) throws Exception {
        String action = at(a, 1);
        if (action.equals("help")) {
            u.say("/town state election open <володіння> | list [володіння] | candidate <вибори> | ballot <вибори> <гравець>");
            u.say("/town state election close|recover|cancel <вибори>");
            return;
        }
        if (action.equals("list")) {
            for (var row : f.db.query("SELECT id,status FROM political_elections WHERE state=? ORDER BY deadline DESC LIMIT 20",
                    a.length > 2 ? estate(a[2]) : state(u))) {
                u.say(string(row, "id") + " · " + string(row, "status"));
            }
            return;
        }
        String id = at(a, 2);
        if (action.equals("open")) {
            u.say("Вибори " + f.politics.election(u.actor(), estate(id)));
            return;
        }
        var row = f.db.one("SELECT * FROM political_elections WHERE id=?", id);
        require(row != null, "Вибори не знайдено.");
        switch (action) {
            case "candidate" -> {
                validate(f.realms.estate(string(row, "state")), u.actor().id());
                f.politics.candidate(u.actor(), id);
            }
            case "ballot" -> f.politics.ballot(u.actor(), id, person(at(a, 3)));
            case "close", "recover" -> {
                String estate = string(row, "state");
                require(u.actor().admin() || f.politics.electorate(estate).contains(u.actor().id()), "Завершити можуть виборці або адміністратор.");
                if (action.equals("close")) f.politics.closeElection(id);
                var prepared = f.db.one("SELECT * FROM political_elections WHERE id=? AND status='PREPARED'", id);
                require(prepared != null, "Немає незавершеної виборної передачі.");
                apply(estate, string(prepared, "old_lord"), string(prepared, "winner"), true);
                f.politics.finishElection(id);
            }
            case "cancel" -> f.politics.cancelElection(u.actor(), id);
            default -> throw new IllegalArgumentException("Див. /town state election help.");
        }
        u.say("Дію щодо виборів збережено.");
    }

    void dynasty(User u, String[] a) throws Exception {
        if (Set.of("help", "menu").contains(a[0])) {
            u.say("/town dynasty show|accept|revoke|abdicate <володіння> | house <володіння> <назва> | nominate <володіння> <гравець>");
            u.say("/town dynasty recover <операція> · для завершення зафіксованої передачі влади");
            return;
        }
        String id = at(a, 1);
        var service = f.diplomacy;
        if (a[0].equals("recover")) {
            var prepared = f.db.one("SELECT * FROM succession_ops WHERE id=? AND status='PREPARED'", id);
            require(prepared != null, "Незавершену передачу не знайдено.");
            require(u.actor().admin() || Set.of(string(prepared, "old_lord"), string(prepared, "new_lord")).contains(u.actor().id()),
                    "Доступ мають учасники передачі або адміністратор.");
            succession(prepared);
            u.say("Передачу влади завершено.");
            return;
        }
        String estate = estate(id);
        switch (a[0]) {
            case "show" -> {
                var house = f.db.one("SELECT name FROM dynasties WHERE estate=?", estate);
                var heir = service.heir(estate);
                u.say("Дім " + (house == null ? "не названо" : string(house, "name")));
                u.say(heir == null ? "Спадкоємця не призначено." : "Спадкоємець " + f.playerName(string(heir, "heir"))
                        + (bool(heir, "accepted") ? " · погодився" : " · очікує підтвердження"));
                return;
            }
            case "house" -> service.house(u.actor(), estate, tail(a, 2));
            case "nominate" -> {
                String successor = person(at(a, 2));
                validate(f.realms.estate(estate), successor);
                service.nominate(u.actor(), estate, successor);
            }
            case "accept" -> {
                validate(f.realms.estate(estate), u.actor().id());
                service.acceptHeir(u.actor(), estate);
            }
            case "revoke" -> service.revoke(u.actor(), estate);
            case "abdicate" -> {
                var heir = service.heir(estate);
                require(heir != null, "Спадкоємця не призначено.");
                validate(f.realms.estate(estate), string(heir, "heir"));
                String operation = service.prepareSuccession(u.actor(), estate, false, 0);
                u.say("Запис передачі влади " + operation);
                succession(f.db.one("SELECT * FROM succession_ops WHERE id=?", operation));
            }
            default -> throw new IllegalArgumentException("Див. /town dynasty help.");
        }
        u.say("Дію династії збережено.");
    }

    private void succession(Map<String, Object> row) throws Exception {
        String estate = string(row, "estate");
        require(f.politics.hereditary(estate), "У володінні влада передається виборами.");
        apply(estate, string(row, "old_lord"), string(row, "new_lord"), false);
        f.diplomacy.finishSuccession(string(row, "id"));
    }

    private void validate(Estate estate, String successor) {
        require(estate.active(), "Володіння неактивне.");
        String town = authorityTown(estate.id());
        require(town == null ? f.realms.citizen(successor, estate.nation()) : town.equals(f.hometown(successor)),
                "Наступник має бути жителем міста або столиці, для якої передається влада.");
    }

    private String authorityTown(String estate) {
        return estate.startsWith("t:") ? estate : f.realms.kingdomCapitals.get(estate);
    }

    private void apply(String id, String expected, String successor, boolean election) throws Exception {
        var estate = f.realms.estate(id);
        validate(estate, successor);
        if (successor.equals(estate.lord())) return;
        require(expected.equals(estate.lord()), "Правитель уже змінився.");
        String town = authorityTown(id);
        if (town != null) {
            TownyForge.applyFeudalMayor(UUID.fromString(town.substring(2)), UUID.fromString(expected), UUID.fromString(successor));
            f.synchronizeTowns();
        } else if (election) {
            require(f.db.execute("UPDATE estates SET lord=? WHERE id=? AND lord=?", successor, id, expected) == 1, "Правитель уже змінився.");
            f.politics.syncDomains();
        }
    }

    private String estate(String input) throws SQLException {
        return f.realms.resolve(input).id();
    }

    private String state(User u) {
        var view = TownyForge.view();
        UUID townId = view.townOf(u.player().m_20148_());
        require(townId != null, "Для цієї дії потрібне місто.");
        var town = view.towns().get(townId);
        return town.nation() == null ? "t:" + town.id() : "n:" + town.nation();
    }
}
