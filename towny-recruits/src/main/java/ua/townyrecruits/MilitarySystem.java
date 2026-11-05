package ua.townyrecruits;

import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ua.townyrecruits.MilitaryData.*;
import ua.townyrecruits.tactics.*;

/** Towny owns regiments; the native Recruits groups remain the companies used by the command menu. */
public final class MilitarySystem {
    final Bridge bridge;
    public final MilitaryData data;
    public final MoraleSystem morale;
    private boolean indexed;
    MilitarySystem(Bridge bridge) { this.bridge = bridge; data = MilitaryData.get(bridge.server.m_129783_()); morale = new MoraleSystem(this); }
    public static MilitarySystem current() { return Bridge.INSTANCE == null ? null : Bridge.INSTANCE.military; }
    public static UUID group(AbstractRecruitEntity r) { return r.getGroup() == null ? r.getOwnerUUID() : r.getGroup(); }
    public int count(UUID group) { return (int)data.members.entrySet().stream().filter(e -> e.getValue().equals(group) && active(e.getKey())).count(); }
    boolean active(UUID id) { var u = bridge.data.units.get(id); return u != null && !u.retired(); }
    List<AbstractRecruitEntity> loaded(UUID group) { return bridge.loaded.stream().filter(r -> r.getIsOwned() && r.m_6084_() && group.equals(data.members.get(r.m_20148_()))).toList(); }
    public void reconcile(Policy.Snapshot snapshot) {
        if (!indexed && RecruitEvents.recruitsGroupsManager != null && bridge.censusReady()) {
            for (var g : RecruitEvents.recruitsGroupsManager.getAllGroups()) for (UUID id : g.members)
                if (active(id)) data.members.putIfAbsent(id, g.getUUID());
            indexed = true; data.m_77762_();
        }
        for (Regiment r : List.copyOf(data.regiments.values())) {
            UUID regimentId = r.id();
            var nation = snapshot.nations().get(r.nation());
            if (nation == null) { data.companies.entrySet().removeIf(e -> e.getValue().regiment().equals(regimentId)); data.regiments.remove(regimentId); data.m_77762_(); continue; }
            if (!r.nation().equals(snapshot.residents().get(r.leader()))) {
                r = new Regiment(r.id(), r.nation(), r.name(), nation.king()); data.regiments.put(r.id(), r); data.m_77762_();
            }
            for (var e : List.copyOf(data.companies.entrySet())) if (e.getValue().regiment().equals(r.id())) {
                UUID owner = e.getValue().commander();
                if (!r.nation().equals(snapshot.residents().get(owner))) { owner = r.leader(); setCommander(e.getKey(), r, owner, snapshot); }
                RecruitsGroup nativeGroup = RecruitEvents.recruitsGroupsManager.getGroup(e.getKey());
                if (nativeGroup != null && !owner.equals(nativeGroup.getPlayerUUID())) {
                    UUID previous = nativeGroup.getPlayerUUID(); nativeGroup.setPlayer(new RecruitsPlayerInfo(owner, nation.members().getOrDefault(owner, "Towny")));
                    RecruitEvents.recruitsGroupsManager.save(bridge.server.m_129783_());
                    RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(bridge.server.m_129783_(), previous);
                    RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(bridge.server.m_129783_(), owner);
                }
            }
        }
        if (data.members.keySet().removeIf(id -> !active(id))) data.m_77762_();
        // Managed membership is authoritative even if a native packet first changes the
        // saved group's list. Repair it before an unloaded soldier's updateGroup runs.
        for (var g : RecruitEvents.recruitsGroupsManager.getAllGroups()) {
            boolean changed = g.members.removeIf(id -> protectedGroup(data.members.get(id)) && !g.getUUID().equals(data.members.get(id)));
            if (data.companies.containsKey(g.getUUID())) {
                List<UUID> wanted = data.members.entrySet().stream().filter(e -> g.getUUID().equals(e.getValue()) && active(e.getKey())).map(Map.Entry::getKey).sorted().toList();
                if (!new HashSet<>(g.members).equals(new HashSet<>(wanted))) { g.members = new ArrayList<>(wanted); changed = true; }
                if (g.disbandContext != null && g.disbandContext.disband) { g.disbandContext = null; changed = true; }
            }
            if (changed) { g.setSize(g.members.size()); RecruitEvents.recruitsGroupsManager.save(bridge.server.m_129783_()); RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(bridge.server.m_129783_(), g.getPlayerUUID()); }
        }
    }
    public void observe(AbstractRecruitEntity r) {
        if (!r.getIsOwned() || r.getOwnerUUID() == null) return;
        UUID assigned = data.members.get(r.m_20148_()); Company company = data.companies.get(assigned);
        if (company != null) {
            Regiment regiment = data.regiments.get(company.regiment());
            if (regiment != null && regiment.nation().equals(bridge.snapshot.residents().get(company.commander()))) applyCommander(r, assigned, company.commander());
        }
        UUID group = group(r);
        if (!Objects.equals(group, data.members.put(r.m_20148_(), group))) data.m_77762_();
    }
    public void forget(AbstractRecruitEntity r) { if (data.members.remove(r.m_20148_()) != null) data.m_77762_(); }
    static void applyCommander(AbstractRecruitEntity r, UUID group, UUID commander) {
        if (!commander.equals(r.getOwnerUUID())) {
            TacticalOrders.cancel(r);
            RecruitEvents.recruitsPlayerUnitManager.removeRecruits(r.getOwnerUUID(), 1);
            r.setOwnerUUID(Optional.of(commander)); r.setIsOwned(true);
            if (!MoraleSystem.routing(r)) r.setListen(true);
            RecruitEvents.recruitsPlayerUnitManager.addRecruits(commander, 1);
        }
        if (!group.equals(r.getGroup())) r.setGroupUUID(group);
    }
    void setCommander(UUID group, Regiment regiment, UUID commander, Policy.Snapshot snapshot) {
        MilitaryPolicy.transfer(bridge.data, data, group, regiment, commander, snapshot);
        var g = RecruitEvents.recruitsGroupsManager.getGroup(group);
        if (g != null) {
            UUID previous = g.getPlayerUUID(); g.setPlayer(new RecruitsPlayerInfo(commander, snapshot.nations().get(regiment.nation()).members().get(commander)));
            for (var r : loaded(group)) { TacticalOrders.cancel(r); applyCommander(r, group, commander); }
            RecruitEvents.recruitsGroupsManager.save(bridge.server.m_129783_());
            RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(bridge.server.m_129783_(), previous);
            RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(bridge.server.m_129783_(), commander);
        }
    }
    public static boolean protectedGroup(UUID group) { var s = current(); return s != null && group != null && s.data.companies.containsKey(group); }
    public static boolean protectsAssignment(AbstractRecruitEntity r, UUID requested) {
        var s = current(); if (s == null || !r.getIsOwned()) return false;
        UUID assigned = s.data.members.get(r.m_20148_());
        return protectedGroup(assigned) && !Objects.equals(assigned, requested);
    }
    public static boolean controlledTransfer(AbstractRecruitEntity r, UUID owner, UUID group) {
        var s = current(); if (s == null) return false;
        UUID assigned = s.data.members.get(r.m_20148_()); Company company = s.data.companies.get(assigned);
        if (company == null) return false;
        if (assigned.equals(group) && company.commander().equals(owner)) s.observe(r);
        return true;
    }
    public void desert(AbstractRecruitEntity r, String reason) {
        if (!r.getIsOwned()) return;
        UUID group = data.members.getOrDefault(r.m_20148_(), group(r)); UUID owner = r.getOwnerUUID();
        var escape = TacticalActor.position(r);
        TacticalOrders.cancel(r); r.disband(null, false, false);
        if (r.getIsOwned()) return; // Respect another mod's cancelled dismissal; retry while routing.
        if (r.m_20202_() != null) r.m_8127_();
        r.setFleeing(false); r.setListen(true); r.setFollowState(0); r.setAggroState(0); r.setMoral(50);
        r.isInFormation = false; r.holdFormation = false; r.getPersistentData().m_128473_("TRRouting"); clearStraggler(r);
        bridge.data.remove(r.m_20148_()); forget(r);
        data.squad(group).deserted++; data.m_77762_();
        var squad = data.squad(group);
        if (squad.routing) {
            String dimension = TacticalActor.dimension(r);
            if (!dimension.equals(squad.escapeDimension)) squad.escaped = 0;
            squad.escaped++; squad.escapeX = escape.f_82479_; squad.escapeY = escape.f_82480_; squad.escapeZ = escape.f_82481_; squad.escapeDimension = dimension;
        }
        var player = bridge.server.m_6846_().m_11259_(owner);
        if (player != null) player.m_213846_(Component.m_237110_("army.townyrecruits.deserted", Component.m_237115_(reason)));
    }
    public static void stranded(AbstractRecruitEntity r) { r.getPersistentData().m_128379_("TRStranded", true); }
    public static void clearStraggler(AbstractRecruitEntity r) {
        for (String key : List.of("TRStranded", "TRLagAt", "TRLagX", "TRLagZ")) r.getPersistentData().m_128473_(key);
    }
    private Regiment find(UUID nation, String name) {
        return data.regiments.values().stream().filter(r -> r.nation().equals(nation) && (r.name().equalsIgnoreCase(name) || r.id().toString().equals(name))).findFirst().orElseThrow(() -> new IllegalArgumentException("Полк не знайдено."));
    }
    private RecruitsGroup findGroup(UUID nation, String text) {
        var groups = RecruitEvents.recruitsGroupsManager.getAllGroups().stream().filter(g -> nation.equals(bridge.snapshot.residents().get(g.getPlayerUUID()))
            && (g.getUUID().toString().equals(text) || g.getName().equalsIgnoreCase(text) || g.getUUID().toString().startsWith(text))).toList();
        if (groups.size() != 1) throw new IllegalArgumentException("Вкажіть однозначну назву або UUID загону з /nation army."); return groups.get(0);
    }
    private UUID resident(UUID nation, String name) {
        return bridge.snapshot.nations().get(nation).members().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name) || e.getKey().toString().equals(name))
            .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalArgumentException("Цього жителя немає в нації."));
    }
    static boolean name(String value) { return value.matches("[\\p{L}\\p{N}_-]{1,32}"); }
    public List<String> complete(UUID actor, String[] args) {
        UUID nation = bridge.snapshot.residents().get(actor); if (nation == null || args.length == 0 || args.length > 4) return List.of();
        Collection<String> choices;
        if (args.length == 1) choices = List.of("list", "info", "create", "form", "add", "commander", "leader", "detach", "disband");
        else if (args.length == 2) choices = data.regiments.values().stream().filter(r -> r.nation().equals(nation)).map(Regiment::name).toList();
        else if (args.length == 4 || args[0].equalsIgnoreCase("leader")) choices = bridge.snapshot.nations().get(nation).members().values();
        else if (args[0].equalsIgnoreCase("form")) choices = List.of();
        else choices = RecruitEvents.recruitsGroupsManager.getAllGroups().stream().filter(g -> nation.equals(bridge.snapshot.residents().get(g.getPlayerUUID())))
                .map(g -> g.getUUID().toString().substring(0,8)).toList();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().limit(64).sorted().toList();
    }
    public static boolean manages(UUID nation, UUID actor, UUID leader, boolean officer, Policy.Snapshot snapshot) {
        return MilitaryPolicy.manages(nation, actor, leader, officer, snapshot);
    }
    public void command(ServerPlayer player, boolean army, String[] args) {
        try {
            bridge.refreshNow();
            if (!bridge.ready || !bridge.censusReady()) throw new IllegalArgumentException("Towny або облік армії ще не готові.");
            UUID actor = player.m_20148_(), nation = bridge.snapshot.residents().get(actor);
            if (nation == null) throw new IllegalArgumentException("Потрібно бути жителем нації Towny.");
            if (args.length > 6 || Arrays.stream(args).anyMatch(a -> a.length() > 64)) throw new IllegalArgumentException("Надто довгий наказ.");
            if (army || args.length == 0 || args[0].equalsIgnoreCase("list")) { overview(player, nation); return; }
            String action = args[0].toLowerCase(Locale.ROOT);
            if (args.length < 2) throw new IllegalArgumentException("/nation regiment info|create|form|add|commander|leader|detach|disband <полк> ...");
            if (action.equals("create")) {
                require(manages(nation, actor, null, bridge.towny.officer(actor), bridge.snapshot));
                if (data.regiments.values().stream().filter(r -> r.nation().equals(nation)).count() >= 64) throw new IllegalArgumentException("Нація вже має 64 полки.");
                if (!name(args[1]) || data.regiments.values().stream().anyMatch(r -> r.nation().equals(nation) && r.name().equalsIgnoreCase(args[1]))) throw new IllegalArgumentException("Назва полку має бути унікальною, до 32 літер/цифр, із _ замість пробілів.");
                UUID id = UUID.randomUUID(); data.regiments.put(id, new Regiment(id, nation, args[1], actor)); data.m_77762_(); say(player, "Створено полк «" + args[1] + "»."); return;
            }
            Regiment regiment = find(nation, args[1]);
            if (action.equals("info")) { info(player, regiment); return; }
            require(manages(nation, actor, regiment.leader(), bridge.towny.officer(actor), bridge.snapshot));
            switch (action) {
                case "form" -> {
                    if (data.companies.values().stream().filter(c -> c.regiment().equals(regiment.id())).count() >= 64) throw new IllegalArgumentException("Полк уже має 64 загони.");
                    if (args.length != 4 || !name(args[2])) throw new IllegalArgumentException("/nation regiment form <полк> <назва_загону> <командир>");
                    UUID owner = resident(nation, args[3]); var g = new RecruitsGroup(args[2], owner, bridge.snapshot.nations().get(nation).members().get(owner), 0);
                    RecruitEvents.recruitsGroupsManager.addPatrolGroup(g, bridge.server.m_129783_()); data.companies.put(g.getUUID(), new Company(regiment.id(), owner)); data.m_77762_();
                    RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(bridge.server.m_129783_(), owner);
                }
                case "leader" -> {
                    require(manages(nation, actor, null, bridge.towny.officer(actor), bridge.snapshot));
                    if (args.length != 3) throw new IllegalArgumentException("/nation regiment leader <полк> <житель>");
                    data.regiments.put(regiment.id(), new Regiment(regiment.id(), nation, regiment.name(), resident(nation, args[2]))); data.m_77762_();
                }
                case "add", "detach", "commander" -> {
                    if (args.length != (action.equals("commander") ? 4 : 3)) throw new IllegalArgumentException("/nation regiment " + action + " <полк> <загін>" + (action.equals("commander") ? " <житель>" : ""));
                    var g = findGroup(nation, args[2]); var company = data.companies.get(g.getUUID());
                    if (action.equals("add")) {
                        if (data.companies.values().stream().filter(c -> c.regiment().equals(regiment.id())).count() >= 64) throw new IllegalArgumentException("Полк уже має 64 загони.");
                        if (company != null) throw new IllegalArgumentException("Загін уже належить до полку; спочатку від'єднайте його.");
                        if (data.squad(g.getUUID()).routing) throw new IllegalArgumentException("Загін утікає й не виконує організаційні накази.");
                        if (g.members.stream().anyMatch(id -> active(id) && !nation.equals(bridge.data.units.get(id).nation()))) throw new IllegalArgumentException("У загоні є солдати іншої нації.");
                        for (UUID id : g.members) if (active(id)) data.members.put(id, g.getUUID());
                        data.companies.put(g.getUUID(), new Company(regiment.id(), g.getPlayerUUID())); data.m_77762_();
                    } else {
                        if (company == null || !company.regiment().equals(regiment.id())) throw new IllegalArgumentException("Цей загін не належить до полку.");
                        if (action.equals("detach")) { data.companies.remove(g.getUUID()); data.m_77762_(); }
                        else { if (data.squad(g.getUUID()).routing) throw new IllegalArgumentException("Загін утікає: командира зараз змінити не можна."); setCommander(g.getUUID(), regiment, resident(nation, args[3]), bridge.snapshot); }
                    }
                }
                case "disband" -> { data.companies.entrySet().removeIf(e -> e.getValue().regiment().equals(regiment.id())); data.regiments.remove(regiment.id()); data.m_77762_(); }
                default -> throw new IllegalArgumentException("Невідома дія. /nation regiment info|create|form|add|commander|leader|detach|disband <полк> ...");
            }
            say(player, "Наказ виконано. Солдати з вивантажених чанків отримають нового командира при завантаженні.");
        } catch (IllegalArgumentException e) { say(player, e.getMessage()); }
        catch (ReflectiveOperationException e) { TownyRecruits.LOG.warn("Towny regiment permission lookup failed: {}", e.toString()); say(player, "Не вдалося перевірити права Towny."); }
    }
    static void require(boolean allowed) { if (!allowed) throw new IllegalArgumentException("Керування дозволене королю, уповноваженому офіцеру нації або командиру цього полку."); }
    static void say(ServerPlayer p, String text) { p.m_213846_(Component.m_237113_(text)); }
    void overview(ServerPlayer p, UUID nation) {
        say(p, "Армія «" + bridge.snapshot.nations().get(nation).name() + "»: " + bridge.count(nation) + "/" + bridge.limit(nation) + ". Полки:");
        data.regiments.values().stream().filter(r -> r.nation().equals(nation)).limit(32).forEach(r -> info(p, r));
        say(p, "Загони нації (назва / ID / чисельність / командир):");
        RecruitEvents.recruitsGroupsManager.getAllGroups().stream().filter(g -> nation.equals(bridge.snapshot.residents().get(g.getPlayerUUID()))).limit(64)
            .forEach(g -> say(p, g.getName() + " / " + g.getUUID().toString().substring(0,8) + " / " + count(g.getUUID()) + " / " + g.getPlayerName()));
    }
    void info(ServerPlayer p, Regiment r) {
        var entries = data.companies.entrySet().stream().filter(e -> e.getValue().regiment().equals(r.id())).toList();
        say(p, "Полк «" + r.name() + "», командир: " + bridge.snapshot.nations().get(r.nation()).members().get(r.leader()));
        for (var e : entries) { var g = RecruitEvents.recruitsGroupsManager.getGroup(e.getKey()); var s = data.squad(e.getKey());
            say(p, "  " + (g == null ? e.getKey().toString().substring(0,8) : g.getName()) + ": " + count(e.getKey()) + " (завантажено " + loaded(e.getKey()).size() + "), "
                + bridge.snapshot.nations().get(r.nation()).members().get(e.getValue().commander()) + ", мораль " + (int)s.morale + ", втрати " + s.deaths + ", дезертири " + s.deserted + (s.routing ? " — УТІКАЄ" : "")); }
    }
}
