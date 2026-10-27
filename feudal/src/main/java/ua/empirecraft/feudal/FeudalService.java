package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.time.Clock;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Domain rules independent of Bukkit. Monetary and political mutations are atomic. */
public final class FeudalService {
    public record Actor(String id, boolean admin) {}
    public static final long DAY = 86_400_000L;
    public final Database db;
    public final CurrencyService currencies;
    public CurrencyMarket market;
    private final Clock clock;
    private final long period, proposalLifetime;
    private final int maxChildren, maxDepth, maxUnpaid;
    private Snapshot snapshot = new Snapshot(List.of(),Set.of());
    @FunctionalInterface public interface PoliticalLinks { boolean allows(Estate child,Estate parent) throws SQLException; }
    public PoliticalLinks politicalLinks;
    public java.util.function.Predicate<String> appointmentPermission=id->true;
    public java.util.function.BiPredicate<Actor,String> treasuryPermission=(a,id)->true;
    @FunctionalInterface public interface TitlePermission { boolean allows(String nation,String person,Kind kind) throws SQLException; }
    public TitlePermission titlePermission=(nation,person,kind)->true;
    @FunctionalInterface public interface AgreementPermission { boolean allows(Map<String,Object> proposal) throws SQLException; }
    public AgreementPermission agreementPermission=proposal->true;
    public FeudalService(Database db,Clock clock,long period,long proposalLifetime,int maxChildren,int maxDepth,int maxUnpaid) {
        require(period>0 && proposalLifetime>0 && maxChildren>0 && maxDepth>0 && maxUnpaid>0,"Некоректна конфігурація");
        this.db=db; this.currencies=new CurrencyService(this); this.clock=clock; this.period=period; this.proposalLifetime=proposalLifetime;
        this.maxChildren=maxChildren; this.maxDepth=maxDepth; this.maxUnpaid=maxUnpaid;
    }
    public long now() { return clock.millis(); }
    public boolean citizen(String person,String nation) { return snapshot.citizen(person,nation); }
    public List<Estate> estates() throws SQLException { return db.query("SELECT * FROM estates ORDER BY kind,name").stream().map(Estate::from).toList(); }
    public Estate estate(String id) throws SQLException {
        var row=db.one("SELECT * FROM estates WHERE id=?",id);
        require(row!=null,"Володіння не знайдено: "+id); return Estate.from(row);
    }
    public Estate resolve(String text) throws SQLException {
        var direct=db.one("SELECT * FROM estates WHERE id=?",text);
        if(direct!=null) return Estate.from(direct);
        // Explicit prefix disambiguates a nation and its capital with the same name.
        Kind kind=null; String name=text;
        if(text.startsWith("держава:") || text.startsWith("nation:")) {kind=Kind.KINGDOM; name=text.substring(text.indexOf(':')+1);}
        if(text.startsWith("місто:") || text.startsWith("town:")) {kind=Kind.BARONY; name=text.substring(text.indexOf(':')+1);}
        final Kind filter=kind; final String query=name.replace('_',' ');
        var found=estates().stream().filter(Estate::active).filter(e->filter==null || e.kind()==filter)
            .filter(e->e.name().replace('_',' ').equalsIgnoreCase(query)).toList();
        require(found.size()==1,found.isEmpty()?"Володіння не знайдено: "+text:"Назва неоднозначна. Вкажіть держава:Назва, місто:Назва або ID.");
        return found.get(0);
    }
    public String parent(String id) throws SQLException {
        var r=db.one("SELECT parent FROM ties WHERE child=?",id); return r==null?null:string(r,"parent");
    }
    public List<Estate> children(String id) throws SQLException {
        return db.query("SELECT e.* FROM estates e JOIN ties t ON e.id=t.child WHERE t.parent=? AND e.active=TRUE ORDER BY e.kind,e.name",id).stream().map(Estate::from).toList();
    }
    public boolean controls(Actor actor,Estate e) { return e.active() && (actor.admin() || Objects.equals(actor.id(),e.lord())); }
    public void control(Actor actor,Estate e) { require(controls(actor,e),"Керувати може лише чинний правитель цього володіння."); }
    /** Archived land has no political powers; its last lord can still recover its money. */
    public boolean controlsTreasury(Actor actor,Estate e) { if(market==null || !e.active())return actor.admin() || Objects.equals(actor.id(),e.lord());
        try{return market.manages(actor,e.id());}catch(SQLException ex){throw new IllegalStateException(ex);} }
    public void controlTreasury(Actor actor,Estate e) { require(controlsTreasury(actor,e),e.active()?"Скарбниця доступна лише чинному правителю або Банкіру.":"Архівна скарбниця доступна останньому правителю або адміністратору."); require(treasuryPermission.test(actor,e.id()),"Республіканські видатки потребують голосування /t state budget."); }
    public void audit(Actor actor,String event,String detail) throws SQLException {
        db.execute("INSERT INTO audit(at_time,actor,event,detail) VALUES(?,?,?,?)",now(),actor.id(),event,detail);
    }
    public void sync(Snapshot incoming) {
        db.transaction(()-> {
            Set<String> seen=new HashSet<>();
            for(Estate e:incoming.estates()) {
                require(e.kind()==Kind.KINGDOM || e.kind()==Kind.BARONY,"Неправильний імпорт");
                seen.add(e.id());
                var previous=db.one("SELECT * FROM estates WHERE id=?",e.id());
                if(previous!=null && !Objects.equals(string(previous,"nation"),e.nation())) {
                    detach(e.id());
                    audit(new Actor("Towny",true),"NATION_CHANGED",e.id()+" -> "+e.nation());
                }
                if(previous==null || !Estate.from(previous).equals(e)) {
                    upsert(e);
                    audit(new Actor("Towny",true),"IMPORT",e.id()+" "+e.name()+" правитель="+e.lord());
                }
            }
            for(Estate e:estates()) {
                boolean regional=e.kind()==Kind.COUNTY || e.kind()==Kind.DUCHY;
                boolean active=regional ? seen.contains(e.nation()) : seen.contains(e.id());
                if(regional && active && !incoming.citizen(e.lord(),e.nation())) {
                    db.execute("UPDATE estates SET lord=? WHERE id=?",estate(e.nation()).lord(),e.id());
                    audit(new Actor("Towny",true),"REGENT",e.id()+" -> корона "+e.nation());
                }
                if(e.active()!=active) {
                    db.execute("UPDATE estates SET active=? WHERE id=?",active,e.id());
                    audit(new Actor("Towny",true),active?"RESTORE":"SUSPEND",e.id());
                }
            }
            // Reconcile membership before charging: stale land never creates new tribute.
            for(var t:db.query("SELECT * FROM ties")) {
                Estate child=estate(string(t,"child")), liege=estate(string(t,"parent"));
                boolean external=child.kind()==Kind.KINGDOM && liege.kind()==Kind.KINGDOM;
                if(!child.active() || !liege.active() || (!external && !Objects.equals(child.nation(),liege.nation()) && (politicalLinks==null || !politicalLinks.allows(child,liege)))) detach(child.id());
            }
            for(Estate e:estates()) if(e.active() && e.kind()!=Kind.KINGDOM && e.nation()!=null && parent(e.id())==null) {
                db.execute("INSERT INTO ties(child,parent,contract) VALUES(?,?,NULL)",e.id(),e.nation());
            }
            db.execute("DELETE FROM proposals WHERE expires<?",now());
            return null;
        });
        snapshot=incoming;
    }
    private void upsert(Estate e) throws SQLException {
        db.execute("MERGE INTO estates KEY(id) VALUES(?,?,?,?,?,?)",e.id(),e.kind().name(),e.name(),e.nation(),e.lord(),e.active());
    }
    private void detach(String child) throws SQLException {
        var t=db.one("SELECT * FROM ties WHERE child=?",child);
        if(t!=null && string(t,"contract")!=null) stopContract(string(t,"contract"),false);
        db.execute("DELETE FROM ties WHERE child=?",child);
    }
    private boolean crownAuthority(Actor actor,String nation) throws SQLException { return nation!=null && controls(actor,estate(nation)); }
    public void validateLink(Estate child,Estate liege) throws SQLException {
        require(child.active() && liege.active(),"Володіння або правитель неактивні.");
        require(!child.id().equals(liege.id()),"Не можна підкоритися самому собі.");
        boolean external=child.kind()==Kind.KINGDOM && liege.kind()==Kind.KINGDOM;
        require(politicalLinks!=null?politicalLinks.allows(child,liege):external || (Objects.equals(child.nation(),liege.nation()) && child.nation()!=null && child.kind().level<liege.kind().level),
            "Внутрішня ієрархія: король → герцог → граф → барон у межах однієї держави. Зовнішній договір — між державами.");
        int depth=0; Set<String> visited=new HashSet<>();
        for(String cursor=liege.id(); cursor!=null; cursor=parent(cursor)) {
            require(!cursor.equals(child.id()) && visited.add(cursor),"Цикли підпорядкування заборонені."); depth++;
        }
        require(depth+subtreeDepth(child.id(),new HashSet<>())<=maxDepth,"Перевищено максимальну глибину ієрархії.");
        require(Objects.equals(parent(child.id()),liege.id()) || children(liege.id()).size()<maxChildren,"У сюзерена вже максимальна кількість васалів.");
    }
    private int subtreeDepth(String id,Set<String> seen) throws SQLException {
        require(seen.add(id),"Цикл у дереві: потрібне виправлення адміністратора.");
        int depth=1; for(Estate child:children(id)) depth=Math.max(depth,1+subtreeDepth(child.id(),seen)); return depth;
    }
    public String create(Actor actor,String parentId,Kind kind,String name) {
        return db.transaction(()-> {
            Estate liege=estate(parentId); control(actor,liege);
            require(kind==Kind.COUNTY || kind==Kind.DUCHY,"Міста й держави створюються засобами Towny.");
            require(kind.level<liege.kind().level,"Рівень нового володіння має бути нижчим за рівень сюзерена.");
            if(!crownAuthority(actor,liege.nation())) {
                var contract=contract(liege.id());
                require(contract!=null && "висока".equals(string(contract,"autonomy")),"Для створення підлеглих потрібна висока автономія за договором.");
            }
            validName(name);
            require(estates().stream().noneMatch(e->e.active() && e.name().equalsIgnoreCase(name)),"Назва вже зайнята.");
            String id="f:"+UUID.randomUUID();
            Estate e=new Estate(id,kind,name,liege.nation(),actor.id(),true);
            if(actor.admin() && !snapshot.citizen(actor.id(),liege.nation())) e=new Estate(id,kind,name,liege.nation(),liege.lord(),true);
            upsert(e); validateLink(e,liege);
            db.execute("INSERT INTO ties VALUES(?,?,NULL)",id,parentId);
            audit(actor,"CREATE",id+" "+name); return id;
        });
    }
    private String fingerprint(String childId,String parentId) throws SQLException {
        Estate child=estate(childId); String oldParent=parent(childId);
        return child.toString()+"|"+(parentId==null?"":estate(parentId))+"|"+oldParent+"|"+(oldParent==null?"":estate(oldParent))
            +"|"+db.query("SELECT id FROM contracts WHERE child=? AND active=TRUE",childId);
    }
    private Set<String> parties(Estate child,Estate liege) throws SQLException {
        Set<String> parties=new TreeSet<>(); parties.add(child.lord()); if(liege!=null) parties.add(liege.lord());
        String oldParent=parent(child.id()); if(oldParent!=null) parties.add(estate(oldParent).lord()); return parties;
    }
    public String offer(Actor actor,String childId,String parentId,long amount,String code,String autonomy,String military,int durationDays) {
        return db.transaction(()-> {
            Estate child=estate(childId),liege=estate(parentId); validateLink(child,liege);
            require(controls(actor,child) || controls(actor,liege) || crownAuthority(actor,child.nation()),"Пропозицію надсилає правитель однієї зі сторін або корона.");
            require(amount>=0,"Данина не може бути від’ємною."); currency(code);if(market!=null)market.tribute(parentId,code);
            require(Set.of("висока","обмежена").contains(autonomy),"Автономія: висока або обмежена.");
            require(Set.of("добровільно","оборона","повний").contains(military),"Військовий обов’язок: добровільно, оборона або повний.");
            require(durationDays==0 || durationDays>=7 && durationDays<=365,"Строк: 0 (безстроково) або 7–365 днів.");
            return proposal(actor,"LINK",childId,parentId,null,amount,code,autonomy,military,durationDays,parties(child,liege));
        });
    }
    public String nominate(Actor actor,String id,String nominee) {
        return db.transaction(()-> {
            require(appointmentPermission.test(id),"Республіканські посади обираються, спадкове призначення вимкнене.");
            Estate e=estate(id);
            require(e.kind()==Kind.COUNTY || e.kind()==Kind.DUCHY,"Мер і король змінюються через Towny.");
            require(controls(actor,e) || crownAuthority(actor,e.nation()),"Призначити може чинний правитель або корона.");
            require(snapshot.citizen(nominee,e.nation()),"Кандидат має бути громадянином цієї держави.");
            Set<String> parties=parties(e,null); parties.add(nominee);
            return proposal(actor,"APPOINT",id,null,nominee,0,"BASE","висока","добровільно",0,parties);
        });
    }
    public String independence(Actor actor,String id) {
        return db.transaction(()-> {
            Estate e=estate(id); control(actor,e); String p=parent(id);
            require(p!=null,"Володіння вже незалежне.");
            require(e.kind()==Kind.KINGDOM || !p.equals(e.nation()),"Вихід міста з держави виконується через Towny після завершення договору.");
            return proposal(actor,"END",id,p,null,0,"BASE","висока","добровільно",0,parties(e,estate(p)));
        });
    }
    private String proposal(Actor actor,String action,String child,String parent,String nominee,long amount,String code,String autonomy,String military,int days,Set<String> parties) throws SQLException {
        require(parties.stream().allMatch(Objects::nonNull),"Немає чинного правителя.");
        require(db.query("SELECT id FROM proposals WHERE child=? AND expires>?",child,now()).isEmpty(),"Для цього володіння вже є пропозиція. Спершу прийміть або скасуйте її.");
        require(parties.size()>1 || actor.admin() || parties.contains(actor.id()),"Немає права підпису.");
        String id=UUID.randomUUID().toString();
        Set<String> signed=new TreeSet<>(); if(parties.contains(actor.id())) signed.add(actor.id());
        db.execute("INSERT INTO proposals VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,action,child,parent,nominee,amount,code,autonomy,military,
            Math.addExact(now(),proposalLifetime),days,fingerprint(child,parent),String.join(",",parties),String.join(",",signed));
        audit(actor,"PROPOSE",id+" "+action+" "+child+" -> "+parent);
        var proposed=db.one("SELECT * FROM proposals WHERE id=?",id);
        if(signed.containsAll(parties) && agreementPermission.allows(proposed)) apply(proposed,actor);
        return id;
    }
    private Set<String> csv(String value) { return value==null || value.isBlank()?new TreeSet<>():new TreeSet<>(List.of(value.split(","))); }
    public void accept(Actor actor,String id) {
        db.transaction(()-> {
            var p=db.one("SELECT * FROM proposals WHERE id=?",id); require(p!=null,"Пропозицію не знайдено.");
            require(number(p,"expires")>now(),"Строк пропозиції минув.");
            require(Objects.equals(string(p,"fingerprint"),fingerprint(string(p,"child"),string(p,"parent"))),"Склад держави, правитель або договір змінився. Створіть нову пропозицію.");
            Set<String> required=csv(string(p,"required")),signed=csv(string(p,"signed"));
            require(required.contains(actor.id()),"Підписати має один із зазначених правителів; права адміністратора не замінюють його згоду.");
            signed.add(actor.id()); db.execute("UPDATE proposals SET signed=? WHERE id=?",String.join(",",signed),id);
            audit(actor,"SIGN",id);
            if(signed.containsAll(required)) apply(p,actor);
            return null;
        });
    }
    public void reject(Actor actor,String id) {
        db.transaction(()-> {
            var p=db.one("SELECT * FROM proposals WHERE id=?",id); require(p!=null,"Пропозицію не знайдено.");
            require(actor.admin() || csv(string(p,"required")).contains(actor.id()) || controls(actor,estate(string(p,"child")))
                || crownAuthority(actor,estate(string(p,"child")).nation())
                || string(p,"parent")!=null && controls(actor,estate(string(p,"parent"))),"Це не ваша пропозиція.");
            db.execute("DELETE FROM proposals WHERE id=?",id); audit(actor,"REJECT",id); return null;
        });
    }
    private void apply(Map<String,Object> p,Actor actor) throws SQLException {
        require(agreementPermission.allows(p),"Республіканська сторона має схвалити саме цей договір: /t state vassalvote <володіння> "+string(p,"id"));
        String child=string(p,"child"),parent=string(p,"parent");
        switch(string(p,"action")) {
            case "LINK" -> {
                if(market!=null)market.tribute(string(p,"parent"),string(p,"code"));
                validateLink(estate(child),estate(parent));
                detachWithBilling(child);
                String id=UUID.randomUUID().toString(); int days=(int)number(p,"duration_days");
                long ends=days==0?0:Math.addExact(now(),Math.multiplyExact((long)days,DAY));
                db.execute("INSERT INTO contracts VALUES(?,?,?,?,?,?,?,?,?,TRUE)",id,child,parent,number(p,"amount"),string(p,"code"),string(p,"autonomy"),string(p,"military"),Math.addExact(now(),period),ends);
                db.execute("MERGE INTO ties KEY(child) VALUES(?,?,?)",child,parent,id);
            }
            case "APPOINT" -> {
                require(appointmentPermission.test(child),"Республіканська посада потребує виборів; старе призначення втратило силу.");
                Estate e=estate(child); require(snapshot.citizen(string(p,"nominee"),e.nation()),"Кандидат уже не громадянин держави.");
                db.execute("UPDATE estates SET lord=? WHERE id=?",string(p,"nominee"),child);
            }
            case "END" -> releaseInternal(child);
            default -> throw new IllegalStateException("Невідомий тип пропозиції");
        }
        audit(actor,"AGREEMENT",string(p,"id")+" "+string(p,"action")+" "+child);
        db.execute("DELETE FROM proposals WHERE id=?",string(p,"id"));
    }
    public void release(Actor actor,String child) {
        db.transaction(()-> {
            String p=parent(child); require(p!=null,"Володіння вже незалежне."); control(actor,estate(p));
            releaseInternal(child); audit(actor,"RELEASE",child); return null;
        });
    }
    private void releaseInternal(String child) throws SQLException {
        detachWithBilling(child); Estate e=estate(child);
        if(e.kind()!=Kind.KINGDOM && e.nation()!=null && e.active()) db.execute("INSERT INTO ties VALUES(?,?,NULL)",child,e.nation());
    }
    private void detachWithBilling(String child) throws SQLException {
        var c=contract(child); if(c!=null) stopContract(string(c,"id"),true);
        db.execute("DELETE FROM ties WHERE child=?",child);
    }
    public Map<String,Object> contract(String child) throws SQLException { return db.one("SELECT * FROM contracts WHERE child=? AND active=TRUE",child); }
    private void stopContract(String id,boolean bill) throws SQLException {
        var c=db.one("SELECT * FROM contracts WHERE id=? AND active=TRUE",id);
        if(c!=null && bill) bill(c);
        db.execute("UPDATE contracts SET active=FALSE WHERE id=?",id);
    }
    public void tick() {
        db.transaction(()-> {
            for(var c:db.query("SELECT * FROM contracts WHERE active=TRUE")) {
                bill(c);
                if(number(c,"ends")>0 && now()>=number(c,"ends")) {
                    String child=string(c,"child");
                    db.execute("UPDATE contracts SET active=FALSE WHERE id=?",string(c,"id"));
                    db.execute("DELETE FROM ties WHERE child=?",child);
                    Estate e=estate(child);
                    if(e.kind()!=Kind.KINGDOM && e.nation()!=null && e.active()) db.execute("INSERT INTO ties VALUES(?,?,NULL)",child,e.nation());
                    audit(new Actor("система",true),"EXPIRE",string(c,"id"));
                }
            }
            for(var i:db.query("SELECT * FROM invoices WHERE paid<amount ORDER BY due,id")) autoPay(i);
            return null;
        });
    }
    private void bill(Map<String,Object> c) throws SQLException {
        if(market!=null){c=market.contractCurrency(c);if(c==null)return;}
        long due=number(c,"next_due"),end=number(c,"ends"),last=end==0?now():Math.min(now(),end);
        if(due>last) return;
        long count=(last-due)/period+1;
        long unpaid=db.query("SELECT id FROM invoices WHERE contract=? AND paid<amount",string(c,"id")).size();
        int slots=(int)Math.max(0,maxUnpaid-unpaid);
        // Bounded catch-up. Older downtime periods beyond the debt cap are explicitly waived.
        for(long n=0;n<Math.min(count,slots);n++) {
            long date=Math.addExact(due,Math.multiplyExact(n,period)); String id=string(c,"id")+":"+date;
            if(number(c,"amount")>0 && db.one("SELECT id FROM invoices WHERE id=?",id)==null) {
                db.execute("INSERT INTO invoices VALUES(?,?,?,?,?,?,0,?)",id,string(c,"id"),string(c,"child"),string(c,"parent"),string(c,"code"),number(c,"amount"),date);
                audit(new Actor("система",true),"INVOICE",id+" "+string(c,"child")+" -> "+string(c,"parent")+" "+format(number(c,"amount"))+" "+string(c,"code"));
            }
        }
        if(count>slots && number(c,"amount")>0) audit(new Actor("система",true),"DEBT_CAP",string(c,"id")+" пропущено періодів="+(count-slots));
        db.execute("UPDATE contracts SET next_due=? WHERE id=?",Math.addExact(due,Math.multiplyExact(count,period)),string(c,"id"));
    }
    private void autoPay(Map<String,Object> i) throws SQLException {
        long owed=number(i,"amount")-number(i,"paid");
        String owner="e:"+string(i,"child"),code=string(i,"code");
        if(balance(owner,code)>=owed) settle(i,owed,new Actor("система",true));
    }
    public long payDebt(Actor actor,String child,String code,long requested) {
        return db.transaction(()-> {
            controlTreasury(actor,estate(child)); require(requested>0,"Сума має бути більшою за нуль.");
            long left=requested,paid=0;
            currency(code);
            for(var i:db.query("SELECT * FROM invoices WHERE child=? AND code=? AND paid<amount ORDER BY due,id",child,code)) {
                long amount=Math.min(left,number(i,"amount")-number(i,"paid"));
                if(market!=null && !market.treasuryCurrency(child).equals(code)){
                    long missing=Math.max(0,amount-balance("e:"+child,code));long topup=Math.min(missing,balance("p:"+actor.id(),code));
                    if(topup>0)restore("p:"+actor.id(),"e:"+child,code,topup);
                }
                amount=Math.min(amount,balance("e:"+child,string(i,"code")));
                if(amount>0) {settle(i,amount,actor); paid=Math.addExact(paid,amount); left-=amount;}
                if(left==0) break;
            }
            require(paid>0,"Немає боргу або коштів у відповідній валюті."); return paid;
        });
    }
    private void settle(Map<String,Object> i,long amount,Actor actor) throws SQLException {
        restore("e:"+string(i,"child"),"e:"+string(i,"parent"),string(i,"code"),amount);
        db.execute("UPDATE invoices SET paid=paid+? WHERE id=?",amount,string(i,"id"));
        currencies.log(actor,string(i,"code"),"e:"+string(i,"child"),"TRIBUTE",string(i,"id")+" -> "+string(i,"parent")+" "+format(amount));
        currencies.log(actor,string(i,"code"),"e:"+string(i,"parent"),"TRIBUTE_RECEIVE",string(i,"id")+" <- "+string(i,"child")+" "+format(amount));
    }
    public Map<String,Object> currency(String code) throws SQLException {
        var c=db.one("SELECT * FROM currencies WHERE code=?",code); require(c!=null,"Невідома валюта: "+code); return c;
    }
    public long balance(String owner,String code) throws SQLException {
        var r=db.one("SELECT balance FROM accounts WHERE owner=? AND code=?",owner,code); return r==null?0:number(r,"balance");
    }
    void setBalance(String owner,String code,long value) throws SQLException {
        require(value>=0,"Недостатньо коштів."); db.execute("MERGE INTO accounts KEY(owner,code) VALUES(?,?,?)",owner,code,value);
    }
    void move(String from,String to,String code,long amount) throws SQLException {
        if(market!=null && to.startsWith("e:"))market.deposit(to.substring(2),code);
        restore(from,to,code,amount);
    }
    /** Only existing invoices, escrow refunds and signed mergers may restore historical holdings. */
    void restore(String from,String to,String code,long amount) throws SQLException {
        currency(code); require(amount>0,"Сума має бути більшою за нуль."); require(!from.equals(to),"Рахунки мають бути різними.");
        long debit=Math.subtractExact(balance(from,code),amount),credit=Math.addExact(balance(to,code),amount);
        require(debit>=0,"Недостатньо коштів: "+code); setBalance(from,code,debit); setBalance(to,code,credit);
    }
    public void transfer(Actor actor,String from,String to,String code,long amount) {
        db.transaction(()-> {
            require(from.equals("p:"+actor.id()) || from.startsWith("e:"),"Недоступний рахунок.");
            if(from.startsWith("e:")) controlTreasury(actor,estate(from.substring(2)));
            require(to.matches("p:[0-9a-fA-F-]{36}") || to.startsWith("e:"),"Недоступний одержувач.");
            if(to.startsWith("e:")) {
                Estate target=estate(to.substring(2));
                if(market!=null && target.active())market.deposit(target.id(),code);
                require(target.active() || controlsTreasury(actor,target),"Архівну скарбницю може поповнити лише її останній правитель або адміністратор.");
            }
            move(from,to,code,amount);
            currencies.log(actor,code,from,"TRANSFER",from+" -> "+to+" "+format(amount)+" "+code);
            currencies.log(actor,code,to,"RECEIVE",from+" -> "+to+" "+format(amount)+" "+code);return null;
        });
    }
    public void createCurrency(Actor actor,String kingdom,String code,String name,String symbol) {
        db.transaction(()-> {
            Estate e=estate(kingdom); if(market!=null)market.creation(actor,kingdom);else{control(actor,e);require(e.kind()==Kind.KINGDOM,"Валюту засновує держава.");}
            require(code.matches("[A-Z]{3,8}") && !code.equals("BASE"),"Код: 3–8 великих латинських літер; BASE зарезервовано.");
            validName(name); require(symbol!=null && symbol.matches("[^\\p{Cntrl}§]{1,4}"),"Символ: 1–4 друковані знаки.");
            require(db.one("SELECT code FROM currencies WHERE code=? OR issuer=?",code,kingdom)==null,"Код зайнято або держава вже має валюту.");
            db.execute("INSERT INTO currencies(code,name,symbol,issuer,supply,backing) VALUES(?,?,?,?,0,'GOLD')",code,name,symbol,kingdom); currencies.log(actor,code,"e:"+kingdom,"CURRENCY_CREATE",code+" "+kingdom); return null;
        });
    }
    public void mint(Actor actor,String code,long amount) { currencies.mint(actor,code,amount); }
    public void redeem(Actor actor,String from,String code,long amount) { currencies.redeem(actor,from,code,amount); }
    public String prepareVault(Actor actor,String direction,long amount) {
        return db.transaction(()-> {
            require(Set.of("IN","OUT").contains(direction) && amount>0 && amount<=100_000_000_000L,"Сума обміну: від 0.01 до 1 000 000 000.00.");
            require(db.one("SELECT id FROM vault_ops WHERE actor=? AND status='PREPARED'",actor.id())==null,"Попередній переказ потребує перевірки адміністратора.");
            String id=UUID.randomUUID().toString();
            if(direction.equals("OUT")) move("p:"+actor.id(),"v:"+id,"BASE",amount);
            db.execute("INSERT INTO vault_ops VALUES(?,?,?,?,?,?)",id,actor.id(),direction,amount,"PREPARED",now());
            audit(actor,"VAULT_PREPARE",id+" "+direction+" "+format(amount)); return id;
        });
    }
    /** Called only with a known response, or after explicit administrator reconciliation. No blind retries. */
    public void finishVault(Actor reviewer,String id,boolean applied) {
        db.transaction(()-> {
            var op=db.one("SELECT * FROM vault_ops WHERE id=?",id); require(op!=null,"Операцію не знайдено.");
            require(reviewer.admin() || reviewer.id().equals(string(op,"actor")),"Немає доступу.");
            require("PREPARED".equals(string(op,"status")),"Операцію вже завершено; повторне зарахування заборонене.");
            String owner="p:"+string(op,"actor"); long amount=number(op,"amount");
            if("IN".equals(string(op,"direction")) && applied) setBalance(owner,"BASE",Math.addExact(balance(owner,"BASE"),amount));
            if("OUT".equals(string(op,"direction"))) {
                if(!applied) move("v:"+id,owner,"BASE",amount);
                else setBalance("v:"+id,"BASE",0);
            }
            db.execute("UPDATE vault_ops SET status=? WHERE id=?",applied?"APPLIED":"FAILED",id);
            audit(reviewer,"VAULT_RESOLVE",id+" "+applied); return null;
        });
    }
    public void forceParent(Actor actor,String child,String liege) {
        require(actor.admin(),"Лише адміністратор.");
        db.transaction(()-> {
            validateLink(estate(child),estate(liege)); detachWithBilling(child);
            db.execute("MERGE INTO ties KEY(child) VALUES(?,?,NULL)",child,liege);
            audit(actor,"ADMIN_PARENT",child+" -> "+liege); return null;
        });
    }
    public void grant(Actor actor,String nation,String person,Kind kind) {
        db.transaction(()-> {
            control(actor,estate(nation)); require(estate(nation).kind()==Kind.KINGDOM,"Вкажіть державу.");
            require(kind!=Kind.KINGDOM,"Король визначається керівництвом держави Towny.");
            require(snapshot.citizen(person,nation),"Титул надається громадянину своєї держави.");
            require(titlePermission.allows(nation,person,kind),"Титул потребує власного затвердженого володіння відповідного статусу.");
            db.execute("MERGE INTO grants KEY(person,nation) VALUES(?,?,?)",person,nation,kind.name());
            audit(actor,"GRANT",person+" "+nation+" "+kind); return null;
        });
    }
    public void revoke(Actor actor,String nation,String person) {
        db.transaction(()-> {control(actor,estate(nation)); db.execute("DELETE FROM grants WHERE person=? AND nation=?",person,nation);audit(actor,"REVOKE",person+" "+nation);return null;});
    }
    public void charter(Actor actor,String parentId,String person) {
        db.transaction(()-> {
            Estate liege=estate(parentId); control(actor,liege);
            require(liege.kind()!=Kind.BARONY && liege.nation()!=null,"Баронство не може мати підлеглих міст.");
            if(liege.kind()!=Kind.KINGDOM) {
                var c=contract(parentId);
                require(c!=null && "висока".equals(string(c,"autonomy")),"Для грамоти потрібна висока автономія за договором.");
            }
            require(snapshot.citizen(person,liege.nation()),"Грамоту слід видати громадянину перед його виходом зі старого міста.");
            db.execute("MERGE INTO charters KEY(person) VALUES(?,?,?,?)",person,liege.nation(),parentId,Math.addExact(now(),3*DAY));
            audit(actor,"CHARTER",person+" -> "+parentId); return null;
        });
    }
    public Map<String,Object> charter(String person) throws SQLException {
        var c=db.one("SELECT * FROM charters WHERE person=?",person);
        require(c!=null && number(c,"expires")>now(),"Потрібна чинна грамота на заснування (видається на 3 доби).");
        require(estate(string(c,"parent")).active() && estate(string(c,"nation")).active(),"Сюзерен або держава неактивні.");
        require(Objects.equals(estate(string(c,"parent")).nation(),string(c,"nation")),"Сюзерен змінив державу. Потрібна нова грамота.");
        return c;
    }
    public void completeFounding(Actor actor,String townId) {
        db.transaction(()-> {
            var c=charter(actor.id()); Estate town=estate(townId);
            require(town.kind()==Kind.BARONY && Objects.equals(town.lord(),actor.id()) && Objects.equals(town.nation(),string(c,"nation")),"Towny не підтвердив заснування у погодженій державі.");
            Estate liege=estate(string(c,"parent")); validateLink(town,liege);
            require(contract(townId)==null,"Місто вже має договір.");
            db.execute("MERGE INTO ties KEY(child) VALUES(?,?,NULL)",townId,liege.id());
            db.execute("DELETE FROM charters WHERE person=?",actor.id());
            db.execute("DELETE FROM pending_foundings WHERE person=?",actor.id());
            audit(actor,"FOUND",townId+" -> "+liege.id()); return null;
        });
    }
    public List<String> check() throws SQLException {
        var problems=new ArrayList<String>();
        problems.addAll(currencies.check());
        for(Estate e:estates()) {
            Set<String> seen=new HashSet<>(); for(String id=e.id();id!=null;id=parent(id)) if(!seen.add(id)){problems.add("Цикл "+id);break;}
        }
        for(var op:db.query("SELECT id FROM vault_ops WHERE status='PREPARED'")) problems.add("Невизначений переказ Vault: "+string(op,"id"));
        for(var op:db.query("SELECT id FROM item_ops WHERE status='PREPARED'")) problems.add("Незавершена передача предметів: "+string(op,"id"));
        return problems;
    }
}



