package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyTests.*;
import static ua.empirecraft.feudal.CityMarketTests.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;

/** H2 outbox/intent tests. The separate native suite exercises the installed Contact implementation. */
public final class MarketMailTests {
    static final class Mail implements Function<Map<String,Object>,Map<String,Object>> {
        final List<Map<String,Object>> addresses=new ArrayList<>();final Set<String> receipts=new HashSet<>();
        final Set<String> full=new HashSet<>(),stale=new HashSet<>();boolean failAfterInsert;int inserted,batches,largest;
        Mail(int count){for(int i=0;i<count;i++)addresses.add(Map.of("person",req(),"address","overworld;"+i+";64;0","generation",req(),"instance",req()));}
        public Map<String,Object> apply(Map<String,Object> request){return switch(request.get("action").toString()){
            case "snapshot"->Map.of("recipients",addresses);
            case "template"->Map.of("item","physical-sample:"+request.get("sample"),"template",request.get("text"));
            case "deliver"->{var jobs=(List<Map<String,Object>>)request.get("jobs");batches++;largest=Math.max(largest,jobs.size());var result=new HashMap<String,Object>();
                for(var job:jobs){String id=string(job,"id"),person=string(job,"person");String state=receipts.contains(id)?"DELIVERED":!Boolean.TRUE.equals(job.get("send"))?"CANCELLED":stale.contains(person)?"STALE":full.contains(person)?"WAITING":"DELIVERED";
                    if(state.equals("DELIVERED")&&receipts.add(id))inserted++;result.put(id,state);}
                if(failAfterInsert){failAfterInsert=false;throw new IllegalStateException("Lost SQL acknowledgement after native persistence");}yield result;}
            default->throw new AssertionError(request);
        };}
        void install(F f)throws Exception {f.m.fairs.mail.gateway=this;f.m.fairs.mail.paperSpec=f.m.registerSpec("plain-paper",false);}
    }
    static String draft(F f)throws Exception {String fair=f.m.fairs.create(d,req(),f.market,f.p(Department.GOODS),"Ярмарок","Ремесла",f.s.now()+2*FeudalService.DAY,24,"GOODS","*",0,50,50);f.m.fairs.edit(d,req(),fair,"Запрошуємо на ярмарок!");return fair;}
    static String sample(F f,String fair)throws Exception {String token=f.m.fairs.preview(d,fair,f.p(Department.GOODS));String op=f.m.fairs.mail.issue(d,token,change(d));f.commerce.finish(op,true);return token;}
    static String confirm(F f,String fair,String token)throws Exception {return f.m.fairs.mail.confirm(d,req(),fair,number(f.m.fairs.fair(fair),"version"),token,f.p(Department.GOODS),change(d));}
    static void publish(F f,String fair,String token)throws Exception {f.commerce.finish(confirm(f,fair,token),true);}
    static long jobs(F f,String state)throws Exception {return number(f.db.one("SELECT COUNT(*) AS n FROM cm_mail_jobs WHERE state=?",state),"n");}
    static long refunds(F f)throws Exception {return f.db.query("SELECT quantity FROM cm_assets WHERE category='MAIL_PAPER_REFUND'").stream().mapToLong(r->number(r,"quantity")).sum();}
    static void interrupted(Runnable action){try{action.run();throw new AssertionError("Expected the injected native failure");}catch(IllegalStateException expected){}}
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Чернетка і неоплачений зразок не розсилають пошту",()->{try(var f=new F()){
            var mail=new Mail(3);mail.install(f);String fair=draft(f),token=f.m.fairs.preview(d,fair,f.p(Department.GOODS));f.m.fairs.mail.deliver();eq(mail.inserted,0);eq(string(f.m.fairs.fair(fair),"state"),"DRAFT");
            no(()->confirm(f,fair,token));String op=f.m.fairs.mail.issue(d,token,change(d));f.commerce.finish(op,false);no(()->confirm(f,fair,token));eq(mail.inserted,0);
        }});
        test("Зразок повторно не друкується; оплата фіксується рівно один раз",()->{try(var f=new F()){
            var mail=new Mail(3);mail.install(f);String fair=draft(f),token=sample(f,fair);eq(f.m.fairs.preview(d,fair,f.p(Department.GOODS)),token);
            String request=req(),op=f.m.fairs.mail.confirm(d,request,fair,2,token,f.p(Department.GOODS),change(d));eq(f.m.fairs.mail.confirm(d,request,fair,2,token,null,null),op);
            f.m.fairs.mail.deliver();eq(mail.inserted,0);f.commerce.finish(op,true);f.commerce.finish(op,true);eq(string(f.m.fairs.fair(fair),"state"),"ANNOUNCED");
            f.m.fairs.mail.deliver();f.m.fairs.mail.deliver();eq(mail.inserted,3);eq(jobs(f,"DELIVERED"),3L);eq(refunds(f),0L);
        }});
        test("Виправлення тексту, скасування підготовки й строк знецінюють зразок",()->{try(var f=new F()){
            var mail=new Mail(1);mail.install(f);String fair=draft(f),old=sample(f,fair);f.m.fairs.edit(d,req(),fair,"Змінене запрошення");no(()->confirm(f,fair,old));
            String token=sample(f,fair);f.m.fairs.cancelLetter(d,req(),fair);no(()->confirm(f,fair,token));String expired=sample(f,fair);f.time.value+=1800001;no(()->confirm(f,fair,expired));eq(mail.inserted,0);
        }});
        test("Сторонній гравець і дистанційне підтвердження не отримують доступ",()->{try(var f=new F()){
            var mail=new Mail(1);mail.install(f);String fair=draft(f),token=sample(f,fair);
            no(()->f.m.fairs.preview(e,fair,f.p(Department.GOODS)));no(()->f.m.fairs.mail.confirm(e,req(),fair,2,token,f.p(Department.GOODS),change(e)));
            no(()->f.m.fairs.mail.confirm(d,req(),fair,2,token,new Place(WORLD,1000,64,0),change(d)));eq(mail.inserted,0);
        }});
        test("Втрачений SQL ACK після Contact не дублює листівку",()->{try(var f=new F()){
            var mail=new Mail(3);mail.install(f);String fair=draft(f),token=sample(f,fair);publish(f,fair,token);mail.failAfterInsert=true;
            interrupted(()->f.m.fairs.mail.deliver());eq(mail.inserted,3);eq(jobs(f,"SENDING"),3L);f.time.value+=30001;f.m.fairs.mail.deliver();eq(mail.inserted,3);eq(jobs(f,"DELIVERED"),3L);
        }});
        test("Повна скринька чекає, змінена адреса пропускається з поверненням паперу",()->{try(var f=new F()){
            var mail=new Mail(2);mail.install(f);String first=mail.addresses.get(0).get("person").toString(),second=mail.addresses.get(1).get("person").toString();mail.full.add(first);mail.stale.add(second);
            String fair=draft(f),token=sample(f,fair);publish(f,fair,token);f.m.fairs.mail.deliver();eq(jobs(f,"WAITING"),1L);eq(jobs(f,"STALE"),1L);eq(refunds(f),1L);
            f.m.fairs.cancel(d,req(),fair);f.time.value+=30001;f.m.fairs.mail.deliver();f.m.fairs.mail.deliver();eq(refunds(f),2L);eq(mail.inserted,0);
        }});
        test("Скасування після втрати ACK спочатку звіряє доставлені квитанції",()->{try(var f=new F()){
            var mail=new Mail(1);mail.install(f);String fair=draft(f),token=sample(f,fair);publish(f,fair,token);mail.failAfterInsert=true;interrupted(()->f.m.fairs.mail.deliver());
            f.m.fairs.cancel(d,req(),fair);f.time.value+=30001;f.m.fairs.mail.deliver();eq(jobs(f,"DELIVERED"),1L);eq(refunds(f),0L);eq(mail.inserted,1);
        }});
        test("Пакет обмежений вісьмома; ярмарковий тариф працює тільки після публікації",()->{try(var f=new F()){
            var mail=new Mail(19);mail.install(f);String fair=draft(f),token=sample(f,fair);eq(f.m.fairs.benefits(f.market,Department.GOODS,f.iron,"seller").size(),0);publish(f,fair,token);
            f.m.fairs.mail.deliver();eq(mail.inserted,8);f.m.fairs.mail.deliver();eq(mail.inserted,16);f.m.fairs.mail.deliver();eq(mail.inserted,19);eq(mail.largest,8);
            f.time.value=number(f.m.fairs.fair(fair),"starts");f.m.tick();eq(f.m.fairs.benefits(f.market,Department.GOODS,f.iron,"seller").size(),1);eq(string(f.m.fairs.fair(fair),"state"),"ACTIVE");
        }});
        test("Перезапуск відновлює outbox без повторної оплати чи доставки",()->{
            Path root=Files.createTempDirectory("fair-mail-disk-");String url="jdbc:h2:"+root.resolve("db")+";DATABASE_TO_LOWER=TRUE";var mail=new Mail(2);
            try(var f=new F(url,true)){mail.install(f);String fair=draft(f),token=sample(f,fair);publish(f,fair,token);mail.failAfterInsert=true;interrupted(()->f.m.fairs.mail.deliver());}
            try(var f=new F(url,false)){mail.install(f);f.time.value+=30001;f.m.fairs.mail.deliver();eq(jobs(f,"DELIVERED"),2L);eq(mail.inserted,2);eq(refunds(f),0L);}remove(root);
        });
        System.out.println("Contact fair mail scenarios: "+count);
    }
}
