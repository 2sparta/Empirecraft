package ua.empirecraft.feudal;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.*;

/** A notification-only Discord bot hosted inside the Minecraft server JVM. */
final class DiscordBot implements AutoCloseable {
    record Result(int status,String message,long retryAfter){boolean success(){return status>=200&&status<300&&message.matches("[0-9]{17,20}");}}
    interface Transport {
        Result send(RegistrationOutbox.Delivery delivery)throws Exception;
        /** Returns HTTP 200 with the guild id in message when the channel is reachable. */
        default Result guildOf(String channel)throws Exception{return new Result(200,"",0);}
    }
    private record Completion(RegistrationOutbox.Delivery delivery,Result result){}
    private final RegistrationOutbox outbox;private final Function<String,String> channels;private final Transport transport;
    private final ExecutorService executor;private final LongSupplier clock;private final Consumer<String> log;
    private final ConcurrentLinkedQueue<Completion> completed=new ConcurrentLinkedQueue<>();
    private final Set<String> allowedGuilds;private final Set<String> verified=new HashSet<>();
    private Future<?> future;private RegistrationOutbox.Delivery active;private long cooldown;private volatile boolean closed;
    DiscordBot(RegistrationOutbox outbox,Function<String,String> channels,Transport transport,ExecutorService executor,LongSupplier clock,Consumer<String> log){
        this(outbox,channels,Set.of(),transport,executor,clock,log);
    }
    /** allowedGuilds: empty set permits every guild; otherwise a channel must belong to one of them. */
    DiscordBot(RegistrationOutbox outbox,Function<String,String> channels,Set<String> allowedGuilds,Transport transport,ExecutorService executor,LongSupplier clock,Consumer<String> log){
        this.outbox=outbox;this.channels=channels;this.allowedGuilds=Set.copyOf(allowedGuilds);this.transport=transport;this.executor=executor;this.clock=clock;this.log=log;outbox.recover();
    }
    void tick(){
        if(closed)return;long now=clock.getAsLong();Completion result;
        while((result=completed.poll())!=null){
            var delivery=result.delivery;var response=result.result;
            if(response.success())outbox.delivered(delivery,response.message);
            else {
                long delay=delay(response,delivery.attempts());outbox.retry(delivery,response.status,now+delay);
                if(response.status==429)cooldown=Math.max(cooldown,now+delay);
                log.accept("Discord registration #"+delivery.id()+" ("+delivery.kind()+"): HTTP "+response.status+"; retry in "+(delay/1000)+"s");
            }active=null;future=null;
        }
        if(active!=null||now<cooldown)return;
        active=outbox.claim(channels,now);if(active==null)return;var delivery=active;
        if(!verified.contains(delivery.channel())){
            // Guild allowlist: before the first post into a channel, confirm it belongs
            // to an allowed guild, so the token can never be used on foreign servers.
            if(allowedGuilds.isEmpty()){verified.add(delivery.channel());}
            else{
                Result probe;try{probe=transport.guildOf(delivery.channel());}catch(Exception error){probe=new Result(0,"",0);}
                if(!probe.success()||!allowedGuilds.contains(probe.message())){
                    log.accept("Discord registration #"+delivery.id()+": channel "+delivery.channel()+" is not on the allowed guild list; message held back");
                    completed.add(new Completion(delivery,new Result(403,"",0)));active=null;return;
                }
                verified.add(delivery.channel());
            }
        }
        future=executor.submit(()->{Result response;try{response=transport.send(delivery);}catch(Exception error){response=new Result(0,"",0);if(error instanceof InterruptedException)Thread.currentThread().interrupt();}
            if(!closed)completed.add(new Completion(delivery,response));});
    }
    static long delay(Result result,int attempts){
        if(result.status==429)return Math.max(1000,Math.min(86_400_000,result.retryAfter));
        if(result.status==400||result.status==401||result.status==403||result.status==404)return 900_000;
        return Math.min(900_000,5000L<<Math.min(8,Math.max(0,attempts)));
    }
    @Override public void close(){if(closed)return;closed=true;if(future!=null)future.cancel(true);executor.shutdownNow();outbox.recover();}
    static final class HttpTransport implements Transport {
        private final HttpClient client;private final URI endpoint;private final String token;
        HttpTransport(String token){this(token,URI.create("https://discord.com/api/v10/"));}
        /** Package-private loopback constructor is used only by local integration tests. */
        HttpTransport(String token,URI endpoint){this.token=token;this.endpoint=endpoint;client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();}
        public Result send(RegistrationOutbox.Delivery delivery)throws Exception {
            var request=HttpRequest.newBuilder(endpoint.resolve("channels/"+delivery.channel()+"/messages")).timeout(Duration.ofSeconds(20))
                .header("Authorization","Bot "+token).header("Content-Type","application/json; charset=utf-8").header("User-Agent","DiscordBot (https://github.com/2sparta/Empirecraft, 1.0)")
                .POST(HttpRequest.BodyPublishers.ofString(delivery.payload(),StandardCharsets.UTF_8)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());String body;
            try(InputStream input=response.body()){body=new String(input.readNBytes(65536),StandardCharsets.UTF_8);}
            JsonObject json;try{json=JsonParser.parseString(body).getAsJsonObject();}catch(RuntimeException invalid){json=new JsonObject();}
            String message=json.has("id")&&json.get("id").isJsonPrimitive()?json.get("id").getAsString():"";
            long retry=0;try{if(json.has("retry_after"))retry=millis(json.get("retry_after").getAsDouble());}catch(RuntimeException ignored){}
            try{retry=Math.max(retry,millis(Double.parseDouble(response.headers().firstValue("Retry-After").orElse("0"))));}catch(RuntimeException ignored){}
            return new Result(response.statusCode(),message,retry);
        }
        /** GET /channels/{id}: HTTP 200 with guild_id in message, or the error status. */
        public Result guildOf(String channel)throws Exception {
            var request=HttpRequest.newBuilder(endpoint.resolve("channels/"+channel)).timeout(Duration.ofSeconds(20))
                .header("Authorization","Bot "+token).header("User-Agent","DiscordBot (https://github.com/2sparta/Empirecraft, 1.0)").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());String body;
            try(InputStream input=response.body()){body=new String(input.readNBytes(65536),StandardCharsets.UTF_8);}
            String guild="";try{var json=JsonParser.parseString(body).getAsJsonObject();
                if(json.has("guild_id")&&json.get("guild_id").isJsonPrimitive())guild=json.get("guild_id").getAsString();
            }catch(RuntimeException ignored){}
            return new Result(response.statusCode(),guild,0);
        }
        static long millis(double seconds){return !Double.isFinite(seconds)||seconds<=0?0:(long)Math.ceil(Math.min(86400,seconds)*1000);}
    }
}
