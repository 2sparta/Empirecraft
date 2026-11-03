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
        int pages=Math.max(1,(d.lines.size()+7)/8);require(page>=1&&page<=pages,"Некоректна сторінка договору.");
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
            "Спочатку перегляньте всі сторінки чинних механічних умов; токен прострочений, чужий або застарілий.");
        m.db.execute("DELETE FROM cm_document_tokens WHERE token=?",token);
    }
    public Map<String,Object> book(String kind,String related)throws SQLException {
        var row=m.db.one("SELECT * FROM cm_documents WHERE kind=? AND related=?",kind,related);
        return row==null?Map.of("version",1L,"mode","AUTO","body",""):row;
    }
    public Document vassal(String related)throws SQLException {
        var p=m.db.one("SELECT * FROM proposals WHERE id=?",related);require(p!=null,"Пропозицію договору не знайдено.");
        var book=book("VASSAL",related);long version=number(book,"version");
        List<String> lines=new ArrayList<>();
        lines.add("Васальний договір "+related+", версія "+version+"; "+string(p,"action"));
        lines.add("Васал: "+m.s.estate(string(p,"child")).name()+" ["+string(p,"child")+"]");
        if(string(p,"parent")!=null)lines.add("Сюзерен: "+m.s.estate(string(p,"parent")).name()+" ["+string(p,"parent")+"]");
        lines.add("Данина: "+format(number(p,"amount"))+" "+string(p,"code")+" кожні 7 реальних діб.");
        lines.add("Перший платіж: через 7 діб після остаточного погодження; точна дата буде записана при активації.");
        lines.add("Строк: "+(number(p,"duration_days")==0?"безстроково":number(p,"duration_days")+" діб після активації"));
        lines.add("Автономія: "+string(p,"autonomy")+"; військові обіцянки для відіграшу: "+string(p,"military"));
        lines.add("Вихід потребує погодження сторін за чинними політичними правилами. Борг зберігається.");
        lines.add("Прострочення: рахунки та чинний серверний ліміт несплаченої данини; автоматичної передачі територій немає.");
        lines.add("Пропозиція чинна до "+time(number(p,"expires"))+". Книга не замінює цих умов.");
        lines.add("Оформлення: "+string(book,"mode")+". Вільний текст не виконується сервером.");
        String authorities=string(p,"required")+"|"+m.s.estate(string(p,"child"))+"|"+(string(p,"parent")==null?"":m.s.estate(string(p,"parent")));
        return new Document("VASSAL",related,version,hash(p+"|"+book+"|"+authorities),List.copyOf(lines));
    }
    boolean authorizing(Actor a,String related){return a.id().equals(acceptingPerson)&&related.equals(acceptingVassal);}
    public String acceptVassal(Actor a,String related,long version,String token){return m.once(a,token,"sign-vassal|"+related+version,()->{
        consume(a,vassal(related),version,token);acceptingPerson=a.id();acceptingVassal=related;
        try{m.s.accept(a,related);}finally{acceptingPerson=null;acceptingVassal=null;}return related;
    });}
    public String vassalBook(Actor a,String req,String related,String mode,String body){return m.once(a,req,"vassal-book|"+related+mode+body,()->{
        var p=m.db.one("SELECT * FROM proposals WHERE id=?",related);require(p!=null&&number(p,"expires")>m.s.now(),"Пропозиція недоступна.");
        require(Arrays.asList(string(p,"required").split(",")).contains(a.id()),"Лише уповноважений учасник договору.");
        require(Set.of("AUTO","MANUAL").contains(mode)&&body.length()<=12000,"Оформлення AUTO або MANUAL; до 12000 символів.");
        var old=book("VASSAL",related);m.db.execute("MERGE INTO cm_documents KEY(kind,related) VALUES('VASSAL',?,?,?,?)",related,number(old,"version")+1,mode,body);
        m.db.execute("UPDATE proposals SET signed='' WHERE id=?",related);m.s.audit(a,"CONTRACT_BOOK",related+" version="+(number(old,"version")+1));return related;
    });}
    public void tick()throws SQLException {
        m.db.execute("DELETE FROM cm_document_reads WHERE expires<=?",m.s.now());
        m.db.execute("DELETE FROM cm_document_tokens WHERE expires<=?",m.s.now());
    }
}
