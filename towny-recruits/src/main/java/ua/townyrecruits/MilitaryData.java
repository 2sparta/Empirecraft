package ua.townyrecruits;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** National organization and group morale survive chunk unloads and server restarts. */
public final class MilitaryData extends SavedData {
    public record Regiment(UUID id, UUID nation, String name, UUID leader) {}
    public record Company(UUID regiment, UUID commander) {}
    public static final class Squad {
        public double morale = 100;
        public int initial, lost, deaths, deserted;
        public int routSize, escaped;
        public double escapeX, escapeY, escapeZ;
        public String escapeDimension = "";
        public long lastLoss, combat;
        public boolean routing;
    }
    public final Map<UUID,Regiment> regiments = new LinkedHashMap<>();
    public final Map<UUID,Company> companies = new HashMap<>();
    public final Map<UUID,UUID> members = new HashMap<>();
    public final Map<UUID,Squad> squads = new HashMap<>();
    public final Map<UUID,RegimentalBanners.Design> banners = new HashMap<>();
    public final Map<UUID,UUID> bearers = new HashMap<>();
    public final Map<UUID,UUID> cities = new HashMap<>();
    public final Map<String,Long> exchangeVersions = new HashMap<>();
    public record Loan(UUID group,UUID temporary,Regiment original,UUID town,UUID commander,UUID employer,UUID nation) {}
    public final Map<UUID,Loan> loans = new HashMap<>();
    public static MilitaryData get(ServerLevel level) { return level.m_8895_().m_164861_(MilitaryData::load, MilitaryData::new, "townyrecruits_military"); }
    public Squad squad(UUID group) { return squads.computeIfAbsent(group, id -> new Squad()); }
    public static MilitaryData load(CompoundTag tag) {
        MilitaryData d = new MilitaryData();
        ListTag list = tag.m_128437_("regiments", 10);
        for (int i = 0; i < list.size(); i++) { var t = list.m_128728_(i); UUID id = t.m_128342_("id");
            d.regiments.put(id, new Regiment(id, t.m_128342_("nation"), t.m_128461_("name"), t.m_128342_("leader"))); }
        list = tag.m_128437_("companies", 10);
        for (int i = 0; i < list.size(); i++) { var t = list.m_128728_(i); d.companies.put(t.m_128342_("group"), new Company(t.m_128342_("regiment"), t.m_128342_("commander"))); }
        list = tag.m_128437_("members", 10);
        for (int i = 0; i < list.size(); i++) { var t = list.m_128728_(i); d.members.put(t.m_128342_("id"), t.m_128342_("group")); }
        list = tag.m_128437_("morale", 10);
        for (int i = 0; i < list.size(); i++) { var t = list.m_128728_(i); Squad s = new Squad();
            s.morale = MoralePolicy.clamp(t.m_128459_("value")); s.initial = Math.max(0, t.m_128451_("initial")); s.lost = Math.max(0, t.m_128451_("lost"));
            s.deaths = Math.max(0, t.m_128451_("deaths")); s.deserted = Math.max(0, t.m_128451_("deserted"));
            s.lastLoss = t.m_128454_("lastLoss"); s.combat = t.m_128454_("combat"); s.routing = t.m_128471_("routing"); d.squads.put(t.m_128342_("group"), s); }
        for (int i = 0; i < list.size(); i++) { var t = list.m_128728_(i); Squad s = d.squads.get(t.m_128342_("group")); s.routSize = t.m_128451_("routSize"); s.escaped = t.m_128451_("escaped"); s.escapeX = t.m_128459_("escapeX"); s.escapeY = t.m_128459_("escapeY"); s.escapeZ = t.m_128459_("escapeZ"); s.escapeDimension = t.m_128461_("escapeDimension"); }
        list = tag.m_128437_("heraldry",10);
        for (int i=0;i<list.size();i++) { var t=list.m_128728_(i); UUID id=t.m_128342_("regiment"); var design=RegimentalBanners.Design.load(t); if(d.regiments.containsKey(id) && design!=null)d.banners.put(id,design); }
        list = tag.m_128437_("bearers",10);
        for (int i=0;i<list.size();i++) { var t=list.m_128728_(i); UUID id=t.m_128342_("regiment"); if(d.regiments.containsKey(id))d.bearers.put(id,t.m_128342_("soldier")); }
        list = tag.m_128437_("cities",10);
        for(int i=0;i<list.size();i++){var t=list.m_128728_(i);d.cities.put(t.m_128342_("regiment"),t.m_128342_("town"));}
        var versions=tag.m_128469_("exchangeVersions");for(String key:versions.m_128431_())d.exchangeVersions.put(key,versions.m_128454_(key));
        list=tag.m_128437_("loans",10);
        for(int i=0;i<list.size();i++){var t=list.m_128728_(i);var original=new Regiment(t.m_128342_("regiment"),t.m_128342_("originNation"),t.m_128461_("name"),t.m_128342_("leader"));
            UUID group=t.m_128342_("group");d.loans.put(group,new Loan(group,t.m_128342_("temporary"),original,t.m_128342_("town"),t.m_128342_("commander"),t.m_128342_("employer"),t.m_128342_("nation")));}
        return d;
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Regiment r : regiments.values()) { var t = new CompoundTag(); t.m_128362_("id", r.id()); t.m_128362_("nation", r.nation()); t.m_128359_("name", r.name()); t.m_128362_("leader", r.leader()); list.add(t); }
        tag.m_128365_("regiments", list); list = new ListTag();
        for (var e : companies.entrySet()) { var t = new CompoundTag(); t.m_128362_("group", e.getKey()); t.m_128362_("regiment", e.getValue().regiment()); t.m_128362_("commander", e.getValue().commander()); list.add(t); }
        tag.m_128365_("companies", list); list = new ListTag();
        for (var e : members.entrySet()) { var t = new CompoundTag(); t.m_128362_("id", e.getKey()); t.m_128362_("group", e.getValue()); list.add(t); }
        tag.m_128365_("members", list); list = new ListTag();
        for (var e : squads.entrySet()) { var t = new CompoundTag(); var s = e.getValue(); t.m_128362_("group", e.getKey()); t.m_128347_("value", s.morale);
            t.m_128405_("initial", s.initial); t.m_128405_("lost", s.lost); t.m_128405_("deaths", s.deaths); t.m_128405_("deserted", s.deserted);
            t.m_128356_("lastLoss", s.lastLoss); t.m_128356_("combat", s.combat); t.m_128379_("routing", s.routing); list.add(t); }
        for (int i = 0; i < list.size(); i++) { var t = list.m_128728_(i); Squad s = squads.get(t.m_128342_("group")); t.m_128405_("routSize", s.routSize); t.m_128405_("escaped", s.escaped); t.m_128347_("escapeX", s.escapeX); t.m_128347_("escapeY", s.escapeY); t.m_128347_("escapeZ", s.escapeZ); t.m_128359_("escapeDimension", s.escapeDimension); }
        tag.m_128365_("morale", list); list = new ListTag();
        for(var e:banners.entrySet()){var t=e.getValue().save();t.m_128362_("regiment",e.getKey());list.add(t);}tag.m_128365_("heraldry",list);list=new ListTag();
        for(var e:bearers.entrySet()){var t=new CompoundTag();t.m_128362_("regiment",e.getKey());t.m_128362_("soldier",e.getValue());list.add(t);}tag.m_128365_("bearers",list);
        list=new ListTag();for(var e:cities.entrySet()){var t=new CompoundTag();t.m_128362_("regiment",e.getKey());t.m_128362_("town",e.getValue());list.add(t);}tag.m_128365_("cities",list);
        var versions=new CompoundTag();exchangeVersions.forEach((key,value)->versions.m_128356_(key,value));tag.m_128365_("exchangeVersions",versions);
        list=new ListTag();for(var loan:loans.values()){var t=new CompoundTag();t.m_128362_("group",loan.group());t.m_128362_("temporary",loan.temporary());t.m_128362_("regiment",loan.original().id());t.m_128362_("originNation",loan.original().nation());t.m_128359_("name",loan.original().name());t.m_128362_("leader",loan.original().leader());
            t.m_128362_("town",loan.town());t.m_128362_("commander",loan.commander());t.m_128362_("employer",loan.employer());t.m_128362_("nation",loan.nation());list.add(t);}tag.m_128365_("loans",list);
        return tag;
    }
}
