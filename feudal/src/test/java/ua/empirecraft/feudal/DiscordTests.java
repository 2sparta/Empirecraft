package ua.empirecraft.feudal;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Database.*;

/** Real SQL/restart and loopback Discord REST. No Discord credentials or external messages. */
public final class DiscordTests {
    static final String CHANNEL="111122223333444455",MESSAGE="222233334444555566";
    static String url(){return "jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE";}
    static void notice(RegistrationOutbox outbox,String key,String kind,long now){outbox.enqueue(kind,key,key,"Заявка @everyone","Опис",Map.of("Заявник","Player"),now);}
    static void waitFor(BooleanSupplier condition)throws Exception {long until=System.nanoTime()+3_000_000_000L;while(!condition.getAsBoolean()){if(System.nanoTime()>until)throw new AssertionError("Worker timeout");Thread.sleep(2);}}
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Черга: ідемпотентність, відкат, відсутній канал і маршрутизація",()->{try(var db=new Database(url())){
            var box=new RegistrationOutbox(db);notice(box,"one","CASTLE",10);notice(box,"one","CASTLE",10);
            eq(number(db.one("SELECT COUNT(*) AS n FROM registration_outbox"),"n"),1L);
            no(()->db.atomic(()->{notice(box,"rolled-back","MARKET",10);throw new IllegalArgumentException("rollback");}));
            eq(box.claim(kind->"",10),null);
            for(int i=0;i<70;i++)notice(box,"unconfigured-"+i,"CASTLE",10);notice(box,"market","MARKET",10);
            var delivery=box.claim(kind->kind.equals("MARKET")?CHANNEL:"",10);eq(delivery.kind(),"MARKET");
            var json=JsonParser.parseString(delivery.payload()).getAsJsonObject();eq(json.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size(),0);
            eq(json.get("enforce_nonce").getAsBoolean(),true);eq(json.get("nonce").getAsString(),"ec"+delivery.id());
        }});
        test("Перезапуск зберігає чергу й nonce незавершеної доставки",()->{
            Path directory=Files.createTempDirectory("discord-outbox-");String address="jdbc:h2:"+directory.resolve("queue")+";DATABASE_TO_LOWER=TRUE";String payload;long id;
            try(var db=new Database(address)){var box=new RegistrationOutbox(db);notice(box,"durable","SHIP",10);var delivery=box.claim(kind->CHANNEL,10);payload=delivery.payload();id=delivery.id();}
            try(var db=new Database(address)){var box=new RegistrationOutbox(db);box.recover();var delivery=box.claim(kind->CHANNEL,11);eq(delivery.id(),id);eq(delivery.payload(),payload);box.delivered(delivery,MESSAGE);eq(box.claim(kind->CHANNEL,12),null);}
            try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        });
        test("Discord REST: правильний Bot заголовок, UTF-8, nonce та Retry-After",()->{
            var received=new AtomicReference<String>();var auth=new AtomicReference<String>();var path=new AtomicReference<String>();var call=new AtomicInteger();
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v10/",exchange->{
                received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));path.set(exchange.getRequestURI().getPath());
                boolean limited=call.getAndIncrement()==0;String body=limited?"{\"retry_after\":1.25,\"global\":true}":"{\"id\":\""+MESSAGE+"\"}";
                exchange.getResponseHeaders().set("Content-Type","application/json");if(limited)exchange.getResponseHeaders().set("Retry-After","2");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(limited?429:200,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}
            });server.start();
            try{
                var transport=new DiscordBot.HttpTransport("dummy-test-token",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v10/"));
                String payload=DiscordMessage.nonce(DiscordMessage.create("Замок","Український опис",Map.of("Поле","Значення"),1000),5);
                var delivery=new RegistrationOutbox.Delivery(5,"CASTLE",CHANNEL,payload,0);var limited=transport.send(delivery);
                eq(limited.status(),429);eq(limited.retryAfter(),2000L);eq(auth.get(),"Bot dummy-test-token");eq(path.get(),"/api/v10/channels/"+CHANNEL+"/messages");eq(received.get(),payload);
                eq(transport.send(delivery).success(),true);eq(DiscordBot.HttpTransport.millis(Double.NaN),0L);
            }finally{server.stop(0);}
        });
        test("429 затримує інші канали; HTTP-потік не змінює SQL",()->{try(var db=new Database(url())){
            var box=new RegistrationOutbox(db);notice(box,"first","CASTLE",1000);notice(box,"second","MARKET",1000);
            var clock=new AtomicLong(1000);var calls=new AtomicInteger();var worker=new AtomicReference<Thread>();Thread main=Thread.currentThread();
            try(var bot=new DiscordBot(box,kind->CHANNEL,delivery->{worker.set(Thread.currentThread());return calls.getAndIncrement()==0?new DiscordBot.Result(429,"",2500):new DiscordBot.Result(200,MESSAGE,0);},Executors.newSingleThreadExecutor(),clock::get,message->{})){
                bot.tick();waitFor(()->calls.get()==1);eq(worker.get()==main,false);
                waitFor(()->{bot.tick();try{return box.counts().getOrDefault("SENDING",0L)==0;}catch(Exception ex){throw new RuntimeException(ex);}});
                eq(number(db.one("SELECT next_attempt FROM registration_outbox WHERE event_key='first'"),"next_attempt"),3500L);
                bot.tick();eq(calls.get(),1);clock.set(3499);bot.tick();eq(calls.get(),1);clock.set(3500);
                waitFor(()->{bot.tick();try{return box.counts().getOrDefault("SENT",0L)==2;}catch(Exception ex){throw new RuntimeException(ex);}});eq(calls.get(),3);
            }
        }});
        test("Скасування незавершеного надсилання та відкладення збоїв",()->{try(var db=new Database(url())){
            var box=new RegistrationOutbox(db);notice(box,"shutdown","SHIP",0);var latch=new CountDownLatch(1);var entered=new CountDownLatch(1);
            var bot=new DiscordBot(box,kind->CHANNEL,delivery->{entered.countDown();latch.await();return new DiscordBot.Result(200,MESSAGE,0);},Executors.newSingleThreadExecutor(),()->0,message->{});
            bot.tick();if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("worker not entered");bot.close();
            eq(box.counts().get("PENDING"),1L);eq(DiscordBot.delay(new DiscordBot.Result(503,"",0),0),5000L);eq(DiscordBot.delay(new DiscordBot.Result(0,"",0),50),900000L);eq(DiscordBot.delay(new DiscordBot.Result(403,"",0),0),900000L);
            latch.countDown();
        }});
        test("Великі поля обмежені без пошкодження Unicode",()->{
            var fields=new LinkedHashMap<String,String>();for(int i=0;i<30;i++)fields.put("Назва"+i,"🚢".repeat(1500));
            var json=JsonParser.parseString(DiscordMessage.create("🚢".repeat(1000),"Опис".repeat(3000),fields,0)).getAsJsonObject();var embed=json.getAsJsonArray("embeds").get(0).getAsJsonObject();
            eq(embed.getAsJsonArray("fields").size(),8);eq(embed.get("title").getAsString().length()<=200,true);
            eq(DiscordMessage.text("\u0000",200),"—");
            if(embed.get("title").getAsString().codePoints().anyMatch(c->c>=0xd800&&c<=0xdfff))throw new AssertionError("broken surrogate pair");
            if(json.toString().contains("dummy-test-token"))throw new AssertionError("credential leaked");
        });
        test("Справжнє подання базару і повідомлення фіксуються одним комітом",()->{try(var f=new CityMarketTests.F()){
            long before=number(f.db.one("SELECT COUNT(*) AS n FROM registration_outbox"),"n");
            String project=f.m.create(FeudalTests.d,CityMarketTests.req(),"t:D","world","Другий базар","Для реєстрації");
            f.m.staff(FeudalTests.d,CityMarketTests.req(),project,FeudalTests.B,true);
            no(()->f.db.atomic(()->{f.m.submit(FeudalTests.d,CityMarketTests.req(),project);throw new IllegalArgumentException("rollback");}));
            eq(string(f.m.market(project),"state"),"DRAFT");eq(number(f.db.one("SELECT COUNT(*) AS n FROM registration_outbox"),"n"),before);
            String request=CityMarketTests.req();f.m.submit(FeudalTests.b,request,project);f.m.submit(FeudalTests.b,request,project);
            eq(number(f.db.one("SELECT COUNT(*) AS n FROM registration_outbox"),"n"),before+1);
            var payload=JsonParser.parseString(string(f.db.one("SELECT payload FROM registration_outbox WHERE kind='MARKET' AND reference=?",project),"payload")).getAsJsonObject();
            boolean applicant=false;for(var value:payload.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonArray("fields")){var item=value.getAsJsonObject();if(item.get("name").getAsString().equals("Заявник"))applicant=item.get("value").getAsString().equals(FeudalTests.B);}
            eq(applicant,true);
        }});
        test("Запит на огляд замку не дублюється для тієї самої споруди",()->{try(var f=new PoliticsTests.F()){
            f.p.castleInspect(PoliticsTests.a,"n:A","world:0:11:64:0:11:geometry-one",144,true,true,true);
            RegistrationNotices.castle(f.p,"n:A",PoliticsTests.a);RegistrationNotices.castle(f.p,"n:A",PoliticsTests.a);
            eq(number(f.db.one("SELECT COUNT(*) AS n FROM registration_outbox WHERE kind='CASTLE'"),"n"),1L);
            f.p.castleReview(PoliticsTests.reviewer,"n:A",3,"Огляд");RegistrationNotices.castle(f.p,"n:A",PoliticsTests.a);
            eq(number(f.db.one("SELECT COUNT(*) AS n FROM registration_outbox WHERE kind='CASTLE'"),"n"),1L);
            f.p.castleInspect(PoliticsTests.a,"n:A","world:0:11:64:0:11:geometry-two",144,true,true,true);RegistrationNotices.castle(f.p,"n:A",PoliticsTests.a);
            eq(number(f.db.one("SELECT COUNT(*) AS n FROM registration_outbox WHERE kind='CASTLE'"),"n"),2L);
        }});
        System.out.println("DISCORD_TESTS_PASS scenarios="+count);
    }
}
