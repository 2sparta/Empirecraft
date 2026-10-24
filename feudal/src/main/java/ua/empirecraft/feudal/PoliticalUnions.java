package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.PoliticalModel.*;
import static ua.empirecraft.feudal.PoliticsService.csv;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Sovereign signatures, council votes and a separate ordinary-money union treasury. */
public final class PoliticalUnions {
    final PoliticsService p;final FeudalService s;final Database db;
    public PoliticalUnions(PoliticsService p) throws SQLException {
        this.p=p;s=p.s;db=p.db;
        for(String ddl:List.of(
            "CREATE TABLE IF NOT EXISTS political_unions(id VARCHAR(36) PRIMARY KEY,name VARCHAR(48),kind VARCHAR(20),charter VARCHAR(8000),powers VARCHAR(500),contribution BIGINT,code VARCHAR(8),fingerprint CLOB,status VARCHAR(16),deadline BIGINT,leader VARCHAR(36))",
            "CREATE TABLE IF NOT EXISTS political_union_members(union_id VARCHAR(36),state VARCHAR(80),lord VARCHAR(36),signed BOOLEAN,confirmed BOOLEAN,exit_at BIGINT,next_due BIGINT,PRIMARY KEY(union_id,state))",
            "CREATE TABLE IF NOT EXISTS political_union_votes(id VARCHAR(36) PRIMARY KEY,union_id VARCHAR(36),kind VARCHAR(16),arg1 VARCHAR(8000),arg2 VARCHAR(120),amount BIGINT,members CLOB,fingerprint CLOB,deadline BIGINT,status VARCHAR(16))",
            "CREATE TABLE IF NOT EXISTS political_union_ballots(vote VARCHAR(36),state VARCHAR(80),lord VARCHAR(36),choice BOOLEAN,PRIMARY KEY(vote,state))",
            "CREATE TABLE IF NOT EXISTS political_union_debts(union_id VARCHAR(36),state VARCHAR(80),due BIGINT,code VARCHAR(8),amount BIGINT,paid BOOLEAN,PRIMARY KEY(union_id,state,due))"
        ))db.execute(ddl);
    }
    public Map<String,Object> get(String key) throws SQLException {var row=db.one("SELECT * FROM political_unions WHERE id=?",key);require(row!=null,"Союз не знайдено.");return row;}
    public List<Map<String,Object>> members(String key) throws SQLException{return db.query("SELECT * FROM political_union_members WHERE union_id=? ORDER BY state",key);}
    public List<Map<String,Object>> forState(String id) throws SQLException{return db.query("SELECT u.* FROM political_unions u JOIN political_union_members m ON m.union_id=u.id WHERE m.state=? ORDER BY u.id",id);}
    private void memberRuler(Actor actor,String key,String id) throws SQLException {p.ruler(actor,id);require(db.one("SELECT state FROM political_union_members WHERE union_id=? AND state=?",key,id)!=null,"Держава не входить до союзу.");}
    private void signedMember(Actor actor,String key,String id) throws SQLException {
        memberRuler(actor,key,id);require(db.one("SELECT state FROM political_union_members WHERE union_id=? AND state=? AND signed=TRUE",key,id)!=null,"Спочатку підтвердіть вступ; непідписаний учасник не має голосу або бюджету.");
    }
    String fingerprint(String key) throws SQLException {
        var result=new StringBuilder();for(var row:members(key)){String id=string(row,"state");result.append(id).append(':').append(p.fingerprint(id)).append(';');}return result.toString();
    }
    String planHash(String key) throws SQLException {
        var u=get(key);String text=string(u,"name")+"|"+string(u,"kind")+"|"+string(u,"charter")+"|"+string(u,"powers")+"|"+number(u,"contribution")+"|"+string(u,"code")+"|"+members(key).stream().map(m->string(m,"state")).toList();
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void freeToJoin(String id,UnionType type) throws SQLException {
        require(p.sovereign(id),"Учасник має бути незалежною державою: "+id);
        if(type==UnionType.MERGE)require(forState(id).stream().noneMatch(r->Set.of("DRAFT","ACTIVE","SUSPENDED","MERGED").contains(string(r,"status"))),"Перед об’єднанням завершіть інші союзи.");
        else require(forState(id).stream().noneMatch(r->"MERGE".equals(string(r,"kind")) && "DRAFT".equals(string(r,"status")) && number(r,"deadline")>s.now()),"Спочатку завершіть або відхиліть план повного об’єднання.");
    }
    public String propose(Actor actor,String source,Collection<String> states,UnionType type,String name,String charter,String powers,long contribution,String code) {
        return propose(actor,source,states,type,name,charter,powers,contribution,code,false);
    }
    public String create(Actor actor,String source,UnionType type,String name) {
        require(Set.of(UnionType.CONFEDERATION,UnionType.FEDERATION,UnionType.TRADE_LEAGUE).contains(type),"Поетапне заснування доступне конфедерації, федерації або лізі.");
        return propose(actor,source,List.of(),type,name,"","defense,trade",0,"BASE",true);
    }
    private String propose(Actor actor,String source,Collection<String> states,UnionType type,String name,String charter,String powers,long contribution,String code,boolean founding) {
        return db.transaction(()->{p.ruler(actor,source);var ids=new TreeSet<>(states);ids.add(source);int minimum=switch(type){case PERSONAL,REAL,MERGE->2;default->p.rules.threshold("union-min-members",3);};
            require(ids.size()>=(founding?1:minimum) && ids.size()<=32,"Потрібно від "+minimum+" до 32 різних учасників.");require((type!=UnionType.PERSONAL && type!=UnionType.REAL && type!=UnionType.MERGE) || ids.size()==2,"Цей тип унії потребує рівно двох держав.");
            validName(name);PoliticsService.text(charter,founding?0:20,8000);PoliticsService.text(powers,5,500);require(contribution>=0,"Внесок невід’ємний.");s.currency(code);
            for(String id:ids){freeToJoin(id,type);}
            String leader=s.estate(source).lord();
            if(type==UnionType.PERSONAL || type==UnionType.REAL)for(String id:ids)require(Objects.equals(s.estate(id).lord(),leader),"Для цієї унії потрібен уже спільний чинний правитель; чужа влада не передається командою союзу.");
            if(type==UnionType.MERGE)for(String section:List.of("capital=","institutions=","rulers=","vassals=","budgets=","currencies=","territories="))require(charter.contains(section),"План об’єднання має містити: "+section);
            String key=UUID.randomUUID().toString();db.execute("INSERT INTO political_unions VALUES(?,?,?,?,?,?,?,'','DRAFT',?,?)",key,name,type.name(),charter,powers,contribution,code,s.now()+p.rules.proposal(),leader);
            for(String id:ids)db.execute("INSERT INTO political_union_members VALUES(?,?,?,FALSE,FALSE,0,?)",key,id,s.estate(id).lord(),s.now()+FeudalService.DAY);
            db.execute("UPDATE political_unions SET fingerprint=? WHERE id=?",fingerprint(key),key);p.history(actor,source,"UNION_PROPOSE",key+" "+type+" "+ids);return key;});
    }
    private void draftFounder(Actor actor,String key,String source) throws SQLException {
        var u=get(key);memberRuler(actor,key,source);
        require("DRAFT".equals(string(u,"status")) && number(u,"deadline")>s.now(),"Немає чинного проєкту союзу.");
        require(Objects.equals(actor.id(),string(u,"leader")),"Зміни проєкту пропонує його засновник.");
        require(!Set.of("MERGE","PERSONAL","REAL").contains(string(u,"kind")),"Для двосторонньої унії створіть новий незмінний план.");
    }
    private void resetDraft(String key) throws SQLException {
        db.execute("UPDATE political_union_members SET signed=FALSE,confirmed=FALSE WHERE union_id=?",key);
        db.execute("UPDATE political_unions SET fingerprint=? WHERE id=?",fingerprint(key),key);
    }
    public void inviteDraft(Actor actor,String key,String source,String invited){db.transaction(()->{
        draftFounder(actor,key,source);freeToJoin(invited,UnionType.valueOf(string(get(key),"kind")));
        require(members(key).size()<32 && db.one("SELECT state FROM political_union_members WHERE union_id=? AND state=?",key,invited)==null,"Учасник уже запрошений або досягнуто ліміт.");
        db.execute("INSERT INTO political_union_members VALUES(?,?,?,FALSE,FALSE,0,?)",key,invited,s.estate(invited).lord(),s.now()+FeudalService.DAY);
        resetDraft(key);p.history(actor,source,"UNION_DRAFT_INVITE",key+" "+invited+"; потрібні нові підписи");return null;
    });}
    public void amendDraft(Actor actor,String key,String source,String charter){db.transaction(()->{
        draftFounder(actor,key,source);PoliticsService.text(charter,20,8000);
        db.execute("UPDATE political_unions SET charter=? WHERE id=?",charter,key);resetDraft(key);
        p.history(actor,source,"UNION_DRAFT_CHARTER",key+"; потрібні нові підписи");return null;
    });}
    public void sign(Actor actor,String key,String id){db.transaction(()->{var u=get(key);require("DRAFT".equals(string(u,"status")) && number(u,"deadline")>s.now(),"Пропозиція вже не чинна.");memberRuler(actor,key,id);
        int minimum=Set.of("PERSONAL","REAL","MERGE").contains(string(u,"kind"))?2:p.rules.threshold("union-min-members",3);
        require(members(key).size()>=minimum && string(u,"charter").length()>=20,"Спочатку запросіть потрібних учасників і погодьте письмовий статут.");
        require(Objects.equals(string(u,"fingerprint"),fingerprint(key)),"Правителі, конституції або території змінилися: потрібна нова пропозиція.");
        for(var row:members(key))require(p.sovereign(string(row,"state")),"Учасник утратив незалежність.");
        if(p.state(id).government()==Government.REPUBLIC)require(p.mandate(id,"UNION",key),"Рада має схвалити саме цей план: /state unionvote "+id+" "+key);
        db.execute("UPDATE political_union_members SET signed=TRUE WHERE union_id=? AND state=?",key,id);p.history(actor,id,"UNION_SIGN",key);
        if(members(key).stream().allMatch(r->bool(r,"signed")) && !"MERGE".equals(string(u,"kind"))) {
            db.execute("UPDATE political_unions SET status='ACTIVE' WHERE id=?",key);for(var r:members(key))p.history(actor,string(r,"state"),"UNION_ACTIVE",key);
        }return null;});}
    public void reject(Actor actor,String key,String id){db.transaction(()->{var u=get(key);memberRuler(actor,key,id);require("DRAFT".equals(string(u,"status")),"Чинний союз завершується процедурою виходу.");db.execute("UPDATE political_unions SET status='REJECTED' WHERE id=?",key);p.history(actor,id,"UNION_REJECT",key);return null;});}
    /** First every state signs the immutable plan; then each state separately confirms execution. */
    public void confirmMerge(Actor actor,String key,String id){db.transaction(()->{var u=get(key);require("DRAFT".equals(string(u,"status")) && "MERGE".equals(string(u,"kind")) && number(u,"deadline")>s.now(),"Немає чинного плану об’єднання.");memberRuler(actor,key,id);
        require(members(key).stream().allMatch(r->bool(r,"signed")),"Спочатку всі сторони підписують план.");require(Objects.equals(string(u,"fingerprint"),fingerprint(key)),"Умови об’єднання змінилися.");
        db.execute("UPDATE political_union_members SET confirmed=TRUE WHERE union_id=? AND state=?",key,id);p.history(actor,id,"MERGE_CONFIRM",key);
        if(members(key).stream().allMatch(r->bool(r,"confirmed")))merge(u,actor);
        return null;});}
    private void merge(Map<String,Object> u,Actor actor) throws SQLException {
        String key=string(u,"id"),plan=string(u,"charter");var fields=new HashMap<String,String>();for(String section:plan.split(";")){int sep=section.indexOf('=');if(sep>0)fields.put(section.substring(0,sep).trim(),section.substring(sep+1).trim());}
        var ms=members(key);String capital=fields.get("capital");require(capital!=null,"Не визначено столицю.");
        String center=null;for(var m:ms)if(p.directTowns(string(m,"state")).contains(capital))center=string(m,"state");require(center!=null,"Столиця не є прямою територією жодного учасника.");
        require(Set.of("keep","sum").contains(fields.get("budgets")),"budgets=keep або budgets=sum.");
        require("preserve".equals(fields.get("vassals")) && "preserve".equals(fields.get("currencies")) && "provinces".equals(fields.get("territories")),"Підтримано: vassals=preserve; currencies=preserve; territories=provinces.");
        require("governors".equals(fields.get("rulers")) && "charter".equals(fields.get("institutions")),"Підтримано: rulers=governors; institutions=charter.");
        for(var m:ms){String id=string(m,"state");require(p.sovereign(id),"Учасник утратив незалежність.");
            require(forState(id).stream().noneMatch(other->!key.equals(string(other,"id")) && Set.of("DRAFT","ACTIVE","SUSPENDED","MERGED").contains(string(other,"status"))),"З’явилися інші союзні зобов’язання; потрібен новий план.");
            if(id.equals(center))continue;
            require(db.one("SELECT id FROM item_ops WHERE related=? AND status='PREPARED'",id)==null,"Спочатку узгодьте предметні операції.");
            db.execute("INSERT INTO political_admin VALUES(?,?,?,'MERGE_CHARTER',0,'BASE',?)",id,center,s.estate(id).lord(),s.now()+FeudalService.DAY);
            db.execute("UPDATE political_states SET merged_into=? WHERE estate=?",center,id);
            if(fields.get("budgets").equals("sum"))for(var balance:db.query("SELECT code,balance FROM accounts WHERE owner=? AND balance>0","e:"+id))s.move("e:"+id,"e:"+center,string(balance,"code"),number(balance,"balance"));
            p.changed(actor,id,"MERGED_INTO",center+"; старі договори та всі золоті рахунки збережено");
        }
        db.execute("UPDATE political_states SET capital=?,charter=?,territory='DIRECT' WHERE estate=?",capital,plan,center);
        p.changed(actor,center,"MERGE_COMPLETE",key+" "+string(u,"name"));db.execute("UPDATE political_unions SET status='MERGED' WHERE id=?",key);
    }
    public void contribute(Actor actor,String key,String state,long amount){db.transaction(()->{var u=get(key);require("ACTIVE".equals(string(u,"status")),"Союз не активний.");signedMember(actor,key,state);require(amount>0,"Внесок додатний.");
        require(p.state(state).government()!=Government.REPUBLIC,"Республіканський внесок затверджується бюджетним голосуванням на рахунок u:ID.");
        s.move("e:"+state,"u:"+key,string(u,"code"),amount);p.history(actor,state,"UNION_CONTRIBUTE",key+" "+Model.format(amount));return null;});}
    String councilFingerprint(String key) throws SQLException {return get(key).toString()+"|"+members(key).stream().map(r->string(r,"state")+":"+string(r,"lord")+":"+bool(r,"signed")+":"+number(r,"exit_at")).toList()+"|"+fingerprint(key);}
    public String proposeVote(Actor actor,String key,String state,String kind,String arg1,String arg2,long amount){return db.transaction(()->{var u=get(key);require(Set.of("ACTIVE","SUSPENDED").contains(string(u,"status")),"Союз не активний.");signedMember(actor,key,state);
        require(Set.of("BUDGET","INVITE","CHARTER","TERMINATE").contains(kind),"Рішення: BUDGET, INVITE, CHARTER, TERMINATE.");
        if(kind.equals("BUDGET")){s.currency(arg2);require(amount>0 && arg1.matches("[pe]:[A-Za-z0-9:._-]{1,100}"),"Одержувач: p:UUID або e:ID; сума додатна.");if(arg1.startsWith("e:"))s.estate(arg1.substring(2));}
        if(kind.equals("INVITE")){require(!Set.of("PERSONAL","REAL").contains(string(u,"kind")),"Двостороння унія не приймає третьої держави.");freeToJoin(arg1,UnionType.valueOf(string(u,"kind")));require(db.one("SELECT state FROM political_union_members WHERE union_id=? AND state=?",key,arg1)==null,"Держава вже є учасником.");}
        if(kind.equals("CHARTER"))PoliticsService.text(arg1,20,8000);
        var ids=members(key).stream().filter(r->bool(r,"signed")).map(r->string(r,"state")).toList();String vote=UUID.randomUUID().toString();db.execute("INSERT INTO political_union_votes VALUES(?,?,?,?,?,?,?,?,?,'OPEN')",vote,key,kind,arg1,arg2,amount,csv(ids),councilFingerprint(key),s.now()+p.rules.proposal());p.history(actor,state,"COUNCIL_PROPOSE",vote+" "+kind);return vote;});}
    public void vote(Actor actor,String vote,String state,boolean yes){db.transaction(()->{var v=db.one("SELECT * FROM political_union_votes WHERE id=?",vote);require(v!=null && "OPEN".equals(string(v,"status")) && number(v,"deadline")>s.now(),"Голосування завершене.");String key=string(v,"union_id");signedMember(actor,key,state);require(csv(string(v,"members")).contains(state),"Держава не входить до списку голосування.");
        if(p.state(state).government()==Government.REPUBLIC)require(p.councilMandate(state,vote,yes),"Спочатку схваліть цей голос республіканською радою: /state councilvote "+state+" "+vote+" "+(yes?"yes":"no"));
        require(Objects.equals(string(v,"fingerprint"),councilFingerprint(key)),"Склад союзу або повноваження змінилися.");require(db.one("SELECT state FROM political_union_ballots WHERE vote=? AND state=?",vote,state)==null,"Держава вже проголосувала.");
        db.execute("INSERT INTO political_union_ballots VALUES(?,?,?,?)",vote,state,actor.id(),yes);p.history(actor,state,"COUNCIL_VOTE",vote+" "+yes);
        int total=csv(string(v,"members")).size(),approved=db.query("SELECT state FROM political_union_ballots WHERE vote=? AND choice=TRUE",vote).size();
        boolean passed=string(v,"kind").equals("BUDGET")?approved*3>=total*2:approved==total;
        if(passed){switch(string(v,"kind")){
            case "BUDGET" -> s.move("u:"+key,string(v,"arg1"),string(v,"arg2"),number(v,"amount"));
            case "INVITE" -> {String invited=string(v,"arg1");freeToJoin(invited,UnionType.valueOf(string(get(key),"kind")));db.execute("INSERT INTO political_union_members VALUES(?,?,?,FALSE,FALSE,0,?)",key,invited,s.estate(invited).lord(),s.now()+FeudalService.DAY);}
            case "CHARTER" -> db.execute("UPDATE political_unions SET charter=? WHERE id=?",string(v,"arg1"),key);
            case "TERMINATE" -> {require(db.one("SELECT owner FROM accounts WHERE owner=? AND balance>0","u:"+key)==null,"Спочатку розподіліть спільні кошти.");require(db.one("SELECT state FROM political_union_debts WHERE union_id=? AND paid=FALSE",key)==null,"Залишилися прийняті фінансові зобов’язання.");db.execute("UPDATE political_unions SET status='ENDED' WHERE id=?",key);}
        }db.execute("UPDATE political_union_votes SET status='APPLIED' WHERE id=?",vote);for(var m:members(key))p.history(actor,string(m,"state"),"COUNCIL_APPLIED",vote);}
        return null;});}
    public void acceptInvite(Actor actor,String key,String id){db.transaction(()->{var u=get(key);require(Set.of("ACTIVE","SUSPENDED").contains(string(u,"status")),"Союз не активний.");memberRuler(actor,key,id);var row=db.one("SELECT * FROM political_union_members WHERE union_id=? AND state=?",key,id);require(!bool(row,"signed"),"Участь уже підтверджена.");freeToJoin(id,UnionType.valueOf(string(u,"kind")));if(p.state(id).government()==Government.REPUBLIC)require(p.mandate(id,"UNION",key),"Потрібен мандат ради запрошеної республіки /state unionvote.");db.execute("UPDATE political_union_members SET signed=TRUE,lord=? WHERE union_id=? AND state=?",actor.id(),key,id);p.history(actor,id,"UNION_JOIN",key);
        int minimum=Set.of("PERSONAL","REAL").contains(string(u,"kind"))?2:p.rules.threshold("union-min-members",3);
        if(members(key).stream().filter(r->bool(r,"signed")).count()>=minimum)db.execute("UPDATE political_unions SET status='ACTIVE' WHERE id=?",key);
        return null;});}
    public void leave(Actor actor,String key,String id){db.transaction(()->{var u=get(key);signedMember(actor,key,id);require(Set.of("ACTIVE","SUSPENDED").contains(string(u,"status")),"Союз не активний.");
        if(p.state(id).government()==Government.REPUBLIC)require(p.mandate(id,"EXIT",key),"Республіканський вихід потребує рішення /state exitvote.");
        if("FEDERATION".equals(string(u,"kind")))require(string(u,"charter").contains("exit:notice"),"Конституція не передбачає одностороннього виходу; потрібне одностайне рішення ради.");
        require(db.one("SELECT state FROM political_union_debts WHERE union_id=? AND state=? AND paid=FALSE",key,id)==null,"Спочатку сплатіть прийняті внески.");
        db.execute("UPDATE political_union_members SET exit_at=? WHERE union_id=? AND state=?",s.now()+p.rules.days("union-exit-days",3),key,id);p.history(actor,id,"UNION_EXIT_NOTICE",key);return null;});}
    public void payDebt(Actor actor,String key,String id){db.transaction(()->{memberRuler(actor,key,id);for(var row:db.query("SELECT * FROM political_union_debts WHERE union_id=? AND state=? AND paid=FALSE ORDER BY due",key,id)) {
        s.move("e:"+id,"u:"+key,string(row,"code"),number(row,"amount"));db.execute("UPDATE political_union_debts SET paid=TRUE WHERE union_id=? AND state=? AND due=?",key,id,number(row,"due"));}p.history(actor,id,"UNION_DEBT_PAID",key);return null;});}
    public void tick(){db.transaction(()->{
        Actor system=new Actor("union",true);
        for(var u:db.query("SELECT * FROM political_unions WHERE status IN('DRAFT','ACTIVE','SUSPENDED')")){
            String key=string(u,"id");
            if("DRAFT".equals(string(u,"status"))){
                if(number(u,"deadline")<=s.now())db.execute("UPDATE political_unions SET status='EXPIRED' WHERE id=?",key);
                continue;
            }
            int minimum=Set.of("PERSONAL","REAL").contains(string(u,"kind"))?2:p.rules.threshold("union-min-members",3);
            boolean valid=members(key).stream().filter(r->bool(r,"signed")).count()>=minimum;
            for(var row:members(key))if(bool(row,"signed")){
                String id=string(row,"state");
                if(!p.sovereign(id))valid=false;
                if(Set.of("PERSONAL","REAL").contains(string(u,"kind")) && !Objects.equals(s.estate(id).lord(),string(u,"leader")))valid=false;
            }
            // A suspended council can settle debts, complete notices and admit a
            // replacement member. Suspension must not trap its remaining states.
            for(var row:members(key)){
                String id=string(row,"state");if(!bool(row,"signed"))continue;
                db.execute("UPDATE political_union_members SET lord=? WHERE union_id=? AND state=?",s.estate(id).lord(),key,id);
                long exit=number(row,"exit_at"),due=number(row,"next_due"),cutoff=exit>0?Math.min(exit,s.now()):s.now();
                if(valid && due<=cutoff && number(u,"contribution")>0){
                    long amount=number(u,"contribution");String code=string(u,"code");boolean paid=s.balance("e:"+id,code)>=amount;
                    if(paid)s.move("e:"+id,"u:"+key,code,amount);
                    db.execute("MERGE INTO political_union_debts KEY(union_id,state,due) VALUES(?,?,?,?,?,?)",key,id,due,code,amount,paid);
                    db.execute("UPDATE political_union_members SET next_due=? WHERE union_id=? AND state=?",s.now()+FeudalService.DAY,key,id);
                    p.history(system,id,paid?"UNION_DUES_PAID":"UNION_DUES_WARNING",key+" "+Model.format(amount));
                }
                if(exit>0 && exit<=s.now() && db.one("SELECT state FROM political_union_debts WHERE union_id=? AND state=? AND paid=FALSE",key,id)==null){
                    db.execute("DELETE FROM political_union_members WHERE union_id=? AND state=?",key,id);
                    p.history(system,id,"UNION_LEFT",key);
                }
            }
            valid &= members(key).stream().filter(r->bool(r,"signed")).count()>=minimum;
            String next=valid?"ACTIVE":"SUSPENDED";
            if(!next.equals(string(u,"status"))){
                db.execute("UPDATE political_unions SET status=? WHERE id=?",next,key);
                for(var row:members(key))p.history(system,string(row,"state"),"UNION_"+next,key);
            }
        }
        return null;
    });}
}
