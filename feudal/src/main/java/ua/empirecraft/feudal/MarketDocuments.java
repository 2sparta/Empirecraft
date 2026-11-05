package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Books are presentation. Only a server view of every page authorizes a short-lived signature. */
public final class MarketDocuments {
    final CityMarketService m;
    private String acceptingPerson,acceptingVassal;
    MarketDocuments(CityMarketService m)throws SQLException {
        this.m=m;
        m.db.execute("CREATE TABLE IF NOT EXISTS cm_documents(kind VARCHAR(12),related VARCHAR(36),version BIGINT,mode VARCHAR(8),body CLOB,PRIMARY KEY(kind,related))");
        m.db.execute("CREATE TABLE IF NOT EXISTS cm_document_reads(person VARCHAR(36),kind VARCHAR(12),related VARCHAR(36),version BIGINT,stamp VARCHAR(64),page INT,expires BIGINT,PRIMARY KEY(person,kind,related,version,page))");
        m.db.execute("CREATE TABLE IF NOT EXISTS cm_document_tokens(token VARCHAR(36) PRIMARY KEY,person VARCHAR(36),kind VARCHAR(12),related VARCHAR(36),version BIGINT,stamp VARCHAR(64),expires BIGINT)");
    }
    public record Document(String kind,String id,long version,String stamp,List<String> lines) {}
    public record Page(List<String> lines,int page,int pages,long version,String token) {}
    public Page view(Actor a,Document d,int page)throws SQLException {
        int pages=Math.max(1,(d.lines.size()+7)/8);require(page>=1&&page<=pages,LocalText.tr("Некоректна сторінка договору."));
        m.db.execute("MERGE INTO cm_document_reads KEY(person,kind,related,version,page) VALUES(?,?,?,?,?,?,?)",a.id(),d.kind,d.id,d.version,d.stamp,page,m.s.now()+60000);
        long read=number(m.db.one("SELECT COUNT(*) AS n FROM cm_document_reads WHERE person=? AND kind=? AND related=? AND version=? AND stamp=? AND expires>?",a.id(),d.kind,d.id,d.version,d.stamp,m.s.now()),"n");
        String token="";
        if(read==pages){token=id();m.db.execute("INSERT INTO cm_document_tokens VALUES(?,?,?,?,?,?,?)",token,a.id(),d.kind,d.id,d.version,d.stamp,m.s.now()+60000);}
        return new Page(d.lines.subList((page-1)*8,Math.min(page*8,d.lines.size())),page,pages,d.version,token);
    }
    void consume(Actor a,Document d,long version,String token)throws SQLException {
        var row=m.db.one("SELECT * FROM cm_document_tokens WHERE token=?",token);
        require(row!=null && a.id().equals(string(row,"person")) && d.kind.equals(string(row,"kind")) && d.id.equals(string(row,"related"))
            && version==d.version && number(row,"version")==version && d.stamp.equals(string(row,"stamp")) && number(row,"expires")>m.s.now(),
            LocalText.tr("Спочатку перегляньте всі сторінки чинних механічних умов; токен прострочений, чужий або застарілий."));
        m.db.execute("DELETE FROM cm_document_tokens WHERE token=?",token);
    }
    public Map<String,Object> book(String kind,String related)throws SQLException {
        var row=m.db.one("SELECT * FROM cm_documents WHERE kind=? AND related=?",kind,related);
        return row==null?Map.of("version",1L,"mode","AUTO","body",""):row;
    }
    public Document vassal(String related)throws SQLException {
        var p=m.db.one("SELECT * FROM proposals WHERE id=?",related);require(p!=null,LocalText.tr("Пропозицію договору не знайдено."));
        var book=book("VASSAL",related);long version=number(book,"version");
        List<String> lines=new ArrayList<>();
        lines.add(LocalText.tr("Васальний договір ")+related+LocalText.tr(", версія ")+version+"; "+string(p,"action"));
        lines.add(LocalText.tr("Васал: ")+m.s.estate(string(p,"child")).name()+" ["+string(p,"child")+"]");
        if(string(p,"parent")!=null)lines.add(LocalText.tr("Сюзерен: ")+m.s.estate(string(p,"parent")).name()+" ["+string(p,"parent")+"]");
        lines.add(LocalText.tr("Данина: ")+format(number(p,"amount"))+" "+string(p,"code")+LocalText.tr(" кожні 7 реальних діб."));
        lines.add(LocalText.tr("Перший платіж: через 7 діб після остаточного погодження; точна дата буде записана при активації."));
        lines.add(LocalText.tr("Строк: ")+(number(p,"duration_days")==0?LocalText.tr("безстроково"):number(p,"duration_days")+LocalText.tr(" діб після активації")));
        lines.add(LocalText.tr("Автономія: ")+LocalText.tr(string(p,"autonomy"))+LocalText.tr("; військові обіцянки для відіграшу: ")+LocalText.tr(string(p,"military")));
        lines.add(LocalText.tr("Вихід потребує погодження сторін за чинними політичними правилами. Борг зберігається."));
        lines.add(LocalText.tr("Прострочення: рахунки та чинний серверний ліміт несплаченої данини; автоматичної передачі територій немає."));
        lines.add(LocalText.tr("Пропозиція чинна до ")+time(number(p,"expires"))+LocalText.tr(". Книга не замінює цих умов."));
        lines.add(LocalText.tr("Оформлення: ")+string(book,"mode")+LocalText.tr(". Вільний текст не виконується сервером."));
        String authorities=string(p,"required")+"|"+m.s.estate(string(p,"child"))+"|"+(string(p,"parent")==null?"":m.s.estate(string(p,"parent")));
        return new Document("VASSAL",related,version,hash(p+"|"+book+"|"+authorities),List.copyOf(lines));
    }
    boolean authorizing(Actor a,String related){return a.id().equals(acceptingPerson)&&related.equals(acceptingVassal);}
    boolean authorizingBuilder(Actor a,long related){return authorizing(a,"vc:"+related);}
    public Document vassalBuilder(long id)throws SQLException {
        var builder=m.s.contractBuilder;require(builder!=null,LocalText.tr("Конструктор договорів ще не готовий."));var c=builder.get(id);int version=builder.rev(c);var revision=builder.revision(id,version);var terms=builder.terms(id,version);
        var lines=new ArrayList<String>();lines.add(LocalText.tr("Васальний конструктор №")+id+LocalText.tr(", редакція ")+version+LocalText.tr("; стан ")+string(c,"status"));
        for(String side:List.of("child","parent")){String estate=string(c,side);lines.add((side.equals("child")?LocalText.tr("Васал: "):LocalText.tr("Сюзерен: "))+m.s.estate(estate).name()+" ["+estate+"]");}
        for(var field:ContractTerms.LABELS.entrySet())lines.add(LocalText.tr(field.getValue())+": "+terms.get(field.getKey()));
        lines.add(LocalText.tr("Перший платіж за вибраною датою/відліком: ")+time(terms.firstDue(m.s.now()))+LocalText.tr(" (відносний відлік починається при активації)."));
        lines.add(LocalText.tr("Рольові умови не виконуються як команди. Потрібні попередні погодження сторін та рішення рад, якщо вони передбачені політичною системою."));
        String authority=builder.fingerprint(string(c,"child"))+"|"+builder.fingerprint(string(c,"parent"));
        return new Document("VASSAL2",""+id,version,hash(c+"|"+revision+"|"+authority),List.copyOf(lines));
    }
    public String acceptBuilder(Actor a,long id,long version,String token){return m.once(a,token,"sign-vassal-builder|"+id+version,()->{
        consume(a,vassalBuilder(id),version,token);acceptingPerson=a.id();acceptingVassal="vc:"+id;
        try{m.s.contractBuilder.sign(a,id,Math.toIntExact(version));}finally{acceptingPerson=null;acceptingVassal=null;}return ""+id;
    });}
    public String acceptVassal(Actor a,String related,long version,String token){return m.once(a,token,"sign-vassal|"+related+version,()->{
        consume(a,vassal(related),version,token);acceptingPerson=a.id();acceptingVassal=related;
        try{m.s.accept(a,related);}finally{acceptingPerson=null;acceptingVassal=null;}return related;
    });}
    public String vassalBook(Actor a,String req,String related,String mode,String body){return m.once(a,req,"vassal-book|"+related+mode+body,()->{
        var p=m.db.one("SELECT * FROM proposals WHERE id=?",related);require(p!=null&&number(p,"expires")>m.s.now(),LocalText.tr("Пропозиція недоступна."));
        require(Arrays.asList(string(p,"required").split(",")).contains(a.id()),LocalText.tr("Лише уповноважений учасник договору."));
        require(Set.of("AUTO","MANUAL").contains(mode)&&body.length()<=12000,LocalText.tr("Оформлення AUTO або MANUAL; до 12000 символів."));
        var old=book("VASSAL",related);m.db.execute("MERGE INTO cm_documents KEY(kind,related) VALUES('VASSAL',?,?,?,?)",related,number(old,"version")+1,mode,body);
        m.db.execute("UPDATE proposals SET signed='' WHERE id=?",related);m.s.audit(a,"CONTRACT_BOOK",related+" version="+(number(old,"version")+1));return related;
    });}
    public void tick()throws SQLException {
        m.db.execute("DELETE FROM cm_document_reads WHERE expires<=?",m.s.now());
        m.db.execute("DELETE FROM cm_document_tokens WHERE expires<=?",m.s.now());
    }
}
