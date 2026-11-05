package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Fixed, collateralized loans. Approved principal is already real reserved money. */
public final class MarketCredit {
    final CityMarketService m;final Database db;final FeudalService s;
    MarketCredit(CityMarketService m)throws SQLException {this.m=m;db=m.db;s=m.s;
        db.execute("CREATE TABLE IF NOT EXISTS cm_licenses(market VARCHAR(36),lender VARCHAR(36),code VARCHAR(8),spec VARCHAR(64),max_loan BIGINT,max_total BIGINT,max_rate INT,max_days INT,max_count INT,active BOOLEAN,PRIMARY KEY(market,lender,code))");
        db.execute("CREATE TABLE IF NOT EXISTS cm_license_requests(market VARCHAR(36),lender VARCHAR(36),code VARCHAR(8),at_time BIGINT,state VARCHAR(20),PRIMARY KEY(market,lender,code))");
        db.execute("CREATE TABLE IF NOT EXISTS cm_funds(id VARCHAR(36) PRIMARY KEY,market VARCHAR(36),lender VARCHAR(36),code VARCHAR(8),UNIQUE(market,lender,code))");
        db.execute("CREATE TABLE IF NOT EXISTS cm_offers(id VARCHAR(36) PRIMARY KEY,market VARCHAR(36),lender VARCHAR(36),code VARCHAR(8),principal BIGINT,rate INT,days INT,spec VARCHAR(64),collateral BIGINT,state VARCHAR(20))");
        db.execute("CREATE TABLE IF NOT EXISTS cm_loans(id VARCHAR(36) PRIMARY KEY,offer VARCHAR(36),market VARCHAR(36),lender VARCHAR(36),borrower VARCHAR(36),code VARCHAR(8),principal BIGINT,interest BIGINT,paid BIGINT DEFAULT 0,days INT,spec VARCHAR(64),collateral BIGINT,state VARCHAR(20),approved_until BIGINT DEFAULT 0,due BIGINT DEFAULT 0,grace BIGINT,terms VARCHAR(2000))");
    }
    Map<String,Object> license(String market,String lender,String code)throws SQLException {var r=db.one("SELECT * FROM cm_licenses WHERE market=? AND lender=? AND code=?",market,lender,code);require(r!=null && bool(r,"active"),LocalText.tr("Чинної ліцензії кредитора немає."));return r;}
    public String requestLicense(Actor a,String req,String market,Place p,String code){return m.once(a,req,"licenseApply|"+market+code,()->{m.access(market,Department.CREDIT,p);s.currency(code);db.execute("MERGE INTO cm_license_requests KEY(market,lender,code) VALUES(?,?,?,?,'PENDING')",market,a.id(),code,s.now());m.event(a,market,"LICENSE_APPLY",code);return market;});}
    Map<String,Object> fund(String market,String lender,String code)throws SQLException {var r=db.one("SELECT * FROM cm_funds WHERE market=? AND lender=? AND code=?",market,lender,code);if(r==null){String key=id();db.execute("INSERT INTO cm_funds VALUES(?,?,?,?)",key,market,lender,code);r=db.one("SELECT * FROM cm_funds WHERE id=?",key);}return r;}
    public String license(Actor a,String req,String market,String lender,String code,String spec,long maxLoan,long maxTotal,int maxRate,int maxDays,int maxCount,boolean active){return m.once(a,req,"license|"+market+lender+code+spec+maxLoan+","+maxTotal+","+maxRate+","+maxDays+","+maxCount+active,()->{
        m.manage(a,market);UUID.fromString(lender);s.currency(code);m.spec(spec);require(maxLoan>0 && maxTotal>=maxLoan && maxTotal<=CurrencyService.MAX_MONEY && maxRate>=0 && maxRate<=10000 && maxDays>=1 && maxDays<=365 && maxCount>0 && maxCount<=100,LocalText.tr("Некоректні ліміти ліцензії."));
        db.execute("MERGE INTO cm_licenses KEY(market,lender,code) VALUES(?,?,?,?,?,?,?,?,?,?)",market,lender,code,spec,maxLoan,maxTotal,maxRate,maxDays,maxCount,active);
        db.execute("UPDATE cm_license_requests SET state=? WHERE market=? AND lender=? AND code=?",active?"APPROVED":"REVOKED",market,lender,code);
        if(!active){for(var row:db.query("SELECT * FROM cm_loans WHERE market=? AND lender=? AND code=? AND state='APPROVED'",market,lender,code))cancelApproval(row);db.execute("UPDATE cm_offers SET state='REVOKED' WHERE market=? AND lender=? AND code=?",market,lender,code);}
        m.event(a,market,"LICENSE",lender+" "+code+" "+active+" limits="+maxLoan+","+maxTotal+","+maxRate+","+maxDays+","+maxCount);return market;
    });}
    public String fund(Actor a,String req,String market,Place p,String code,long amount,boolean deposit){return m.once(a,req,"fund|"+market+code+amount+deposit,()->{
        require(amount>0 && amount<=CurrencyService.MAX_MONEY,LocalText.tr("Неправильна сума."));var f=fund(market,a.id(),code);String key=string(f,"id");
        if(deposit){m.access(market,Department.CREDIT,p);license(market,a.id(),code);m.move(wallet(a),account(key),code,amount);}
        else m.moneyAsset(market,Department.CREDIT,wallet(a),code,amount,account(key),key,"FUND_WITHDRAW");
        m.event(a,market,"FUND",key+" "+amount+" deposit="+deposit);return key;
    });}
    public String offer(Actor a,String req,String market,Place p,String code,long principal,int rate,int days,String spec,long collateral){return m.once(a,req,"offer|"+market+code+principal+","+rate+","+days+spec+collateral,()->{
        m.access(market,Department.CREDIT,p);var l=license(market,a.id(),code);validate(l,principal,rate,days,spec,collateral);String key=id();
        require(db.query("SELECT id FROM cm_offers WHERE market=? AND lender=? AND state='OPEN'",market,a.id()).size()<20,LocalText.tr("Ліміт пропозицій кредитора."));
        db.execute("INSERT INTO cm_offers VALUES(?,?,?,?,?,?,?,?,?,'OPEN')",key,market,a.id(),code,principal,rate,days,spec,collateral);m.event(a,market,"LOAN_OFFER",key);return key;
    });}
    void validate(Map<String,Object> l,long principal,int rate,int days,String spec,long collateral){require(principal>0&&principal<=number(l,"max_loan")&&rate>=0&&rate<=number(l,"max_rate")&&days>0&&days<=number(l,"max_days")&&spec.equals(string(l,"spec"))&&collateral>0&&collateral<=1_000_000,LocalText.tr("Пропозиція виходить за межі ліцензії."));}
    public String apply(Actor a,String req,String offer,Place p){return m.once(a,req,"apply|"+offer,()->{
        var o=db.one("SELECT * FROM cm_offers WHERE id=?",offer);require(o!=null&&"OPEN".equals(string(o,"state")),LocalText.tr("Пропозицію закрито."));String market=string(o,"market"),lender=string(o,"lender");m.access(market,Department.CREDIT,p);require(!a.id().equals(lender),LocalText.tr("Не можна позичити самому собі."));
        var l=license(market,lender,string(o,"code"));validate(l,number(o,"principal"),(int)number(o,"rate"),(int)number(o,"days"),string(o,"spec"),number(o,"collateral"));
        require(db.query("SELECT id FROM cm_loans WHERE borrower=? AND state='REQUESTED'",a.id()).size()<20,LocalText.tr("Ліміт кредитних заявок."));String key=id();long interest=fee(number(o,"principal"),(int)number(o,"rate"));
        db.execute("INSERT INTO cm_loans(id,offer,market,lender,borrower,code,principal,interest,days,spec,collateral,state,grace,terms) VALUES(?,?,?,?,?,?,?,?,?,?,?,'REQUESTED',?,?)",key,offer,market,lender,a.id(),string(o,"code"),number(o,"principal"),interest,number(o,"days"),string(o,"spec"),number(o,"collateral"),FeudalService.DAY,"Фіксоване повернення; спочатку відсотки; без знижки за дострокове погашення; вся застава закриває борг після пільгового строку; пошта не зупиняє строки.");
        m.event(a,market,"LOAN_APPLY",key);return key;
    });}
    public Map<String,Object> loan(String id)throws SQLException {var r=db.one("SELECT * FROM cm_loans WHERE id=?",id);require(r!=null,LocalText.tr("Позики немає."));return r;}
    public String cancel(Actor a,String req,String id){return m.once(a,req,"cancelLoan|"+id,()->{var r=loan(id);require(a.id().equals(string(r,"borrower"))||a.id().equals(string(r,"lender")),LocalText.tr("Це чужа кредитна заявка."));require(Set.of("REQUESTED","APPROVED").contains(string(r,"state")),LocalText.tr("Після початку видачі скасування недоступне; використовуйте погашення."));if("APPROVED".equals(string(r,"state")))cancelApproval(r);else db.execute("UPDATE cm_loans SET state='CANCELLED' WHERE id=?",id);m.event(a,string(r,"market"),"LOAN_CANCEL",id);return id;});}
    public String closeOffer(Actor a,String req,String id){return m.once(a,req,"closeOffer|"+id,()->{var r=db.one("SELECT * FROM cm_offers WHERE id=?",id);require(r!=null&&a.id().equals(string(r,"lender")),LocalText.tr("Це чужа пропозиція."));db.execute("UPDATE cm_offers SET state='CLOSED' WHERE id=?",id);m.event(a,string(r,"market"),"OFFER_CLOSE",id);return id;});}
    public String approve(Actor a,String req,String id,Place p){return m.once(a,req,"approve|"+id,()->{
        var r=loan(id);String market=string(r,"market"),code=string(r,"code");m.access(market,Department.CREDIT,p);require(a.id().equals(string(r,"lender")) && "REQUESTED".equals(string(r,"state")),LocalText.tr("Погоджує кредитор чинної заявки."));var l=license(market,a.id(),code);
        var offer=db.one("SELECT * FROM cm_offers WHERE id=?",string(r,"offer"));validate(l,number(r,"principal"),(int)number(offer,"rate"),(int)number(r,"days"),string(r,"spec"),number(r,"collateral"));
        long outstanding=0;var loans=db.query("SELECT * FROM cm_loans WHERE market=? AND lender=? AND code=? AND state IN('APPROVED','PREPARED','ACTIVE')",market,a.id(),code);
        for(var row:loans)outstanding=Math.addExact(outstanding,number(row,"principal")-Math.max(0,number(row,"paid")-number(row,"interest")));
        require(loans.size()<number(l,"max_count")&&Math.addExact(outstanding,number(r,"principal"))<=number(l,"max_total"),LocalText.tr("Вичерпано ліміти ліцензії."));var f=fund(market,a.id(),code);m.move(account(string(f,"id")),account(id),code,number(r,"principal"));
        db.execute("UPDATE cm_loans SET state='APPROVED',approved_until=? WHERE id=?",s.now()+FeudalService.DAY,id);m.event(a,market,"LOAN_APPROVE",id+" until="+(s.now()+FeudalService.DAY));return id;
    });}
    public String take(Actor a,String req,String id,Place p,CommerceService.InventoryChange change){return m.once(a,req,"take|"+id,()->{
        var r=loan(id);m.access(string(r,"market"),Department.CREDIT,p);require(a.id().equals(string(r,"borrower")) && "APPROVED".equals(string(r,"state")) && number(r,"approved_until")>s.now(),LocalText.tr("Схвалення недоступне або прострочене."));license(string(r,"market"),string(r,"lender"),string(r,"code"));require(change.person().equals(a.id()),LocalText.tr("Неправильний інвентар застави."));
        String op=m.commerce.intent(a,"CM_IN",id,number(r,"collateral"),List.of(change));db.execute("INSERT INTO cm_transfers VALUES(?,'LOAN',?,?)",op,id,number(r,"collateral"));db.execute("UPDATE cm_loans SET state='PREPARED' WHERE id=?",id);return op;
    });}
    void finish(String id,boolean applied)throws SQLException {
        var r=loan(id);require("PREPARED".equals(string(r,"state")),LocalText.tr("Позика вже оброблена."));
        if(applied){m.move(account(id),"p:"+string(r,"borrower"),string(r,"code"),number(r,"principal"));db.execute("UPDATE cm_loans SET state='ACTIVE',due=? WHERE id=?",Math.addExact(s.now(),Math.multiplyExact(number(r,"days"),FeudalService.DAY)),id);m.event(new Actor(string(r,"borrower"),false),string(r,"market"),"LOAN_ISSUED",id+" principal="+number(r,"principal")+" interest="+number(r,"interest"));}
        else db.execute("UPDATE cm_loans SET state='APPROVED' WHERE id=?",id);
    }
    public String repay(Actor a,String req,String id,long amount){return m.once(a,req,"repay|"+id+"|"+amount,()->{
        var r=loan(id);require(a.id().equals(string(r,"borrower"))&&"ACTIVE".equals(string(r,"state")),LocalText.tr("Немає вашого чинного боргу."));long total=Math.addExact(number(r,"principal"),number(r,"interest")),left=total-number(r,"paid");require(amount>0&&amount<=left,LocalText.tr("Платіж має бути додатним і не перевищувати борг."));
        m.moneyAsset(string(r,"market"),Department.CREDIT,"p:"+string(r,"lender"),string(r,"code"),amount,wallet(a),id,"LOAN_PAYMENT");db.execute("UPDATE cm_loans SET paid=paid+?,state=? WHERE id=?",amount,amount==left?"REPAID":"ACTIVE",id);
        if(amount==left)m.asset(string(r,"market"),Department.CREDIT,wallet(a),string(r,"spec"),"",number(r,"collateral"),id,"COLLATERAL_RETURN");m.event(a,string(r,"market"),"LOAN_REPAY",id+" "+amount+" interestFirst=true");return id;
    });}
    public String foreclose(Actor a,String req,String id){return m.once(a,req,"foreclose|"+id,()->{
        var r=loan(id);require(a.id().equals(string(r,"lender"))&&"ACTIVE".equals(string(r,"state"))&&s.now()>Math.addExact(number(r,"due"),number(r,"grace")),LocalText.tr("Стягнення ще недоступне."));
        m.asset(string(r,"market"),Department.CREDIT,wallet(a),string(r,"spec"),"",number(r,"collateral"),id,"FORECLOSURE");db.execute("UPDATE cm_loans SET state='SETTLED' WHERE id=?",id);m.event(a,string(r,"market"),"FORECLOSE",id+LocalText.tr(" весь залишок боргу закрито заставою"));return id;
    });}
    void cancelApproval(Map<String,Object> r)throws SQLException {String market=string(r,"market"),lender=string(r,"lender"),code=string(r,"code"),id=string(r,"id");var f=fund(market,lender,code);m.move(account(id),account(string(f,"id")),code,number(r,"principal"));db.execute("UPDATE cm_loans SET state='CANCELLED' WHERE id=?",id);m.event(new Actor("credit",true),market,"LOAN_EXPIRE",id+LocalText.tr(" без боргу"));}
    void cancelApprovals(String market,String lender)throws SQLException {for(var r:db.query("SELECT * FROM cm_loans WHERE market=? AND state='APPROVED'",market))if(lender==null||lender.equals(string(r,"lender")))cancelApproval(r);}
    void tick()throws SQLException {for(var r:db.query("SELECT * FROM cm_loans WHERE state='APPROVED' AND approved_until<=?",s.now()))cancelApproval(r);}
    boolean obligations(String market)throws SQLException {return db.one("SELECT id FROM cm_loans WHERE market=? AND state IN('APPROVED','PREPARED','ACTIVE')",market)!=null;}
}
