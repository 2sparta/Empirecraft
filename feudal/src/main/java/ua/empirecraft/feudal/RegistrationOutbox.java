package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import static ua.empirecraft.feudal.Database.*;

/** SQL is used only by the server thread; network workers receive immutable deliveries. */
final class RegistrationOutbox {
    record Delivery(long id,String kind,String channel,String payload,int attempts){}
    final Database db;
    RegistrationOutbox(Database db){this.db=db;}
    void enqueue(String kind,String key,String reference,String title,String description,Map<String,String> fields,long now){db.atomic(()->{
        if(db.one("SELECT id FROM registration_outbox WHERE event_key=?",key)==null)
            db.execute("INSERT INTO registration_outbox(event_key,kind,reference,payload,next_attempt,created) VALUES(?,?,?,?,?,?)",key,kind,reference,DiscordMessage.create(title,description,fields,now),now,now);
        return null;
    });}
    Delivery claim(Function<String,String> channels,long now){return db.atomic(()->{
        var kinds=new ArrayList<String>();for(String kind:List.of("CASTLE","MARKET","SHIP")){String channel=channels.apply(kind);if(channel!=null&&channel.matches("[0-9]{17,20}"))kinds.add(kind);}
        if(kinds.isEmpty())return null;var args=new ArrayList<Object>();args.add(now);args.addAll(kinds);
        String placeholders=String.join(",",Collections.nCopies(kinds.size(),"?"));
        for(var row:db.query("SELECT * FROM registration_outbox WHERE state='PENDING' AND next_attempt<=? AND kind IN ("+placeholders+") ORDER BY id LIMIT 1",args.toArray())){
            String channel=channels.apply(string(row,"kind"));if(channel==null||!channel.matches("[0-9]{17,20}"))continue;
            long id=number(row,"id");db.execute("UPDATE registration_outbox SET state='SENDING',channel=? WHERE id=?",channel,id);
            return new Delivery(id,string(row,"kind"),channel,DiscordMessage.nonce(string(row,"payload"),id),(int)number(row,"attempts"));
        }return null;
    });}
    void delivered(Delivery delivery,String message){db.atomic(()->{db.execute("UPDATE registration_outbox SET state='SENT',message_id=?,last_status=200 WHERE id=? AND state='SENDING'",message,delivery.id);return null;});}
    void retry(Delivery delivery,int status,long next){db.atomic(()->{db.execute("UPDATE registration_outbox SET state='PENDING',attempts=attempts+1,next_attempt=?,last_status=? WHERE id=? AND state='SENDING'",next,status,delivery.id);return null;});}
    void recover(){db.atomic(()->{db.execute("UPDATE registration_outbox SET state='PENDING' WHERE state='SENDING'");return null;});}
    void retryNow(long now){db.atomic(()->{db.execute("UPDATE registration_outbox SET next_attempt=? WHERE state='PENDING'",now);return null;});}
    Map<String,Long> counts()throws SQLException {var out=new LinkedHashMap<String,Long>();for(var row:db.query("SELECT state,COUNT(*) AS count FROM registration_outbox GROUP BY state"))out.put(string(row,"state"),number(row,"count"));return out;}
}
