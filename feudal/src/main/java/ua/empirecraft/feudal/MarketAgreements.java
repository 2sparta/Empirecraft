package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Changes never take effect until the other party signs the exact, still-current proposal. */
public final class MarketAgreements {
    final CityMarketService m;final Database db;
    MarketAgreements(CityMarketService m)throws SQLException {this.m=m;db=m.db;db.execute("CREATE TABLE IF NOT EXISTS cm_agreements(id VARCHAR(36) PRIMARY KEY,kind VARCHAR(12),related VARCHAR(36),proposer VARCHAR(36),partner VARCHAR(36),fingerprint VARCHAR(64),deadline BIGINT,amount BIGINT,description VARCHAR(4000),expires BIGINT,state VARCHAR(20))");}
    String fingerprint(String kind,String related)throws SQLException {
        var r=kind.equals("LOAN")?m.credit.loan(related):m.contracts.contract(related);return hash(r.toString()+(kind.equals("LOAN")?"":db.query("SELECT * FROM cm_stages WHERE contract=? ORDER BY stage",related).toString()));
    }
    public String propose(Actor a,String req,String kind,String related,int days,long amount,String description){return m.once(a,req,"agreement|"+kind+related+days+","+amount+description,()->{
        require(Set.of("LOAN","WORK","TERMINATE").contains(kind),LocalText.tr("Невідомий тип погодження."));require(description.length()<=4000,LocalText.tr("Опис завеликий."));var r=kind.equals("LOAN")?m.credit.loan(related):m.contracts.contract(related);
        String first=string(r,kind.equals("LOAN")?"borrower":"customer"),second=string(r,kind.equals("LOAN")?"lender":"worker");require(a.id().equals(first)||a.id().equals(second),LocalText.tr("Лише сторона договору."));require(!first.equals(second)&&!second.isEmpty(),LocalText.tr("Потрібні дві визначені сторони."));
        if(kind.equals("LOAN"))require("ACTIVE".equals(string(r,"state")),LocalText.tr("Позика не є чинною."));else require(Set.of("WORKING","REVIEW","DISPUTE").contains(string(r,"state")),LocalText.tr("Роботу вже завершено або не прийнято."));
        long deadline=0;if(kind.equals("TERMINATE")){require(!description.isBlank()&&amount>=0&&amount<=m.s.balance(account(related),string(r,"code")),LocalText.tr("Потрібні причина й розподіл наявного резерву."));}else {require(days>=1&&days<=365,LocalText.tr("Новий строк: 1–365 діб від погодження пропозиції."));deadline=Math.addExact(m.s.now(),Math.multiplyExact(days,FeudalService.DAY));}
        require(db.query("SELECT id FROM cm_agreements WHERE related=? AND state='PENDING' AND expires>?",related,m.s.now()).size()<5,LocalText.tr("Забагато непідписаних пропозицій."));String key=id();db.execute("INSERT INTO cm_agreements VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING')",key,kind,related,a.id(),a.id().equals(first)?second:first,fingerprint(kind,related),deadline,amount,description,m.s.now()+FeudalService.DAY);m.event(a,string(r,"market"),"CHANGE_PROPOSE",key+" "+related+" "+kind+" "+description);return key;
    });}
    public Map<String,Object> proposal(Actor a,String id)throws SQLException {var r=db.one("SELECT * FROM cm_agreements WHERE id=?",id);require(r!=null&&(a.id().equals(string(r,"proposer"))||a.id().equals(string(r,"partner"))),LocalText.tr("Це чуже погодження."));return r;}
    public String accept(Actor a,String req,String id){return m.once(a,req,"agreementAccept|"+id,()->{
        var proposal=proposal(a,id);require(a.id().equals(string(proposal,"partner"))&&"PENDING".equals(string(proposal,"state"))&&number(proposal,"expires")>m.s.now(),LocalText.tr("Потрібен підпис другої сторони до завершення строку."));String kind=string(proposal,"kind"),related=string(proposal,"related");require(fingerprint(kind,related).equals(string(proposal,"fingerprint")),LocalText.tr("Договір змінився; потрібна нова пропозиція."));
        var r=kind.equals("LOAN")?m.credit.loan(related):m.contracts.contract(related);
        if(kind.equals("LOAN")){require(number(proposal,"deadline")>m.s.now(),LocalText.tr("Новий строк уже минув."));db.execute("UPDATE cm_loans SET due=? WHERE id=?",number(proposal,"deadline"),related);}
        else if(kind.equals("WORK")){require(number(proposal,"deadline")>m.s.now()&&!string(proposal,"description").isBlank(),LocalText.tr("Потрібні майбутній строк і опис."));db.execute("UPDATE cm_contracts SET ends=?,description=? WHERE id=?",number(proposal,"deadline"),string(proposal,"description"),related);}
        else {long payment=number(proposal,"amount");if(payment>0)m.contracts.pay(r,string(r,"worker"),payment);m.contracts.refund(r);db.execute("UPDATE cm_contracts SET state='TERMINATED' WHERE id=?",related);}
        db.execute("UPDATE cm_agreements SET state='ACCEPTED' WHERE id=?",id);m.event(a,string(r,"market"),"CHANGE_ACCEPT",id+" "+related+" "+kind+" "+string(proposal,"description"));return related;
    });}
}
