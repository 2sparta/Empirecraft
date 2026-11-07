package ua.empirecraft.feudal;

import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Durable source snapshots; startup also recovers pending castle and market reviews. */
final class RegistrationNotices {
    private RegistrationNotices(){}
    static void castle(PoliticsService politics,String state,Actor applicant)throws Exception {
        var s=politics.s;s.db.atomic(()->{
            politics.ruler(applicant,state);var row=s.db.one("SELECT * FROM political_castles WHERE state=?",state);
            if(row==null||string(row,"reviewer")!=null)return null;
            var fields=new LinkedHashMap<String,String>();fields.put("Володіння",s.estate(state).name()+" ("+state+")");fields.put("Заявник",applicant.id());
            fields.put("Огляд",LocalText.tr("Площа: ")+number(row,"area")+LocalText.tr("; стіни: ")+bool(row,"height_ok")+LocalText.tr("; периметр: ")+bool(row,"perimeter_ok")+LocalText.tr("; ворота: ")+bool(row,"gates_ok"));
            String geometry=string(row,"geometry");String[] parts=geometry.split(":");if(parts.length>=7)fields.put("Координати",parts[0]+": X "+parts[1]+"…"+parts[2]+", Y "+parts[3]+", Z "+parts[4]+"…"+parts[5]);
            fields.put("Перегляд у грі","/t castle status "+state);fields.put("Розгляд","/t castle review "+state+" <0–4> <примітка>");
            new RegistrationOutbox(s.db).enqueue("CASTLE","castle:"+CityMarketService.hash(state+"|"+string(row,"lord")+"|"+geometry),state,"Заявка на реєстрацію замку","Потрібен незалежний огляд адміністрації. Автоматичний огляд не замінює висновок.",fields,s.now());return null;
        });
    }
    static void market(CityMarketService markets,String market)throws Exception {
        var s=markets.s;var row=markets.market(market);var submission=s.db.one("SELECT seq,actor FROM cm_events WHERE market=? AND event='SUBMIT' ORDER BY seq DESC LIMIT 1",market);
        String sequence=submission==null||submission.get("seq")==null?"initial":submission.get("seq").toString();
        var fields=new LinkedHashMap<String,String>();fields.put("Базар",string(row,"name")+" ("+market+")");fields.put("Місто",s.estate(string(row,"town")).name()+" ("+string(row,"town")+")");
        fields.put("Заявник",submission==null?s.estate(string(row,"town")).lord():string(submission,"actor"));fields.put("Світ",string(row,"world"));fields.put("Перегляд у грі","/market info "+market);
        new RegistrationOutbox(s.db).enqueue("MARKET","market:"+market+":"+sequence,market,"Заявка на реєстрацію базару",string(row,"description"),fields,s.now());
    }
    static void shipRequest(FeudalService s,String id,String name,String town,String applicant,ShipRegistrationService.Anchor anchor)throws Exception {
        var fields=new LinkedHashMap<String,String>();fields.put("Корабель",name+" ("+id+")");
        fields.put("Місто",s.estate(town).name()+" ("+town+")");fields.put("Заявник",applicant);
        fields.put("Координати",anchor.world()+": "+anchor.x()+", "+anchor.y()+", "+anchor.z());fields.put("Схвалення","/shipadmin approve "+id);fields.put("Відмова","/shipadmin reject "+id+" <примітка>");
        new RegistrationOutbox(s.db).enqueue("SHIP","ship-request:"+id,id,"Заявка на реєстрацію корабля","Після схвалення адміністратор збирає корабель VS-паличкою біля вказаного блока. Після надання фізики відкриється меню міста-власника та назви.",fields,s.now());
    }
    static void pending(PoliticsService politics,CityMarketService markets)throws Exception {
        // Catch up requests which already existed before installing/enabling the bot.
        for(var row:markets.db.query("SELECT m.id FROM cm_markets m JOIN estates e ON e.id=m.town WHERE m.state='REVIEW' AND e.active=TRUE"))market(markets,string(row,"id"));
        for(var row:politics.db.query("SELECT c.state,c.lord FROM political_castles c JOIN estates e ON e.id=c.state WHERE c.reviewer IS NULL AND e.active=TRUE AND e.lord=c.lord"))castle(politics,string(row,"state"),new Actor(string(row,"lord"),false));
    }
}
