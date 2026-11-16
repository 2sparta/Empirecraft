package ua.townyrecruits;

import com.google.gson.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable write-ahead journal. Ambiguous inventory recovery stays reserved until resolved. */
public final class RecruitmentState {
    public static final class Nation {
        public String faction;public int total,perPlayer;public boolean shieldman,deleted,unlimited;
        public Nation(String faction,int total,int perPlayer){this.faction=faction;this.total=total;this.perPlayer=perPlayer;}
    }
    public static final class Purchase {
        public UUID unit,player,town,nation;public String type,phase,before,after,entity,world;
        public double x,y,z;public float yaw,pitch;public BigDecimal development;public int gold;public boolean refunding;
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public Map<UUID,Nation> nations=new LinkedHashMap<>();
    public Map<UUID,BigDecimal> development=new LinkedHashMap<>();
    public Map<UUID,UUID> homes=new LinkedHashMap<>();
    public Map<UUID,Purchase> purchases=new LinkedHashMap<>();
    public Set<UUID> dismissed=new LinkedHashSet<>();
    private final transient Path file;
    private static final class Document {
        Set<UUID> dismissed=new LinkedHashSet<>();
        int schema=1;Map<UUID,Nation> nations=new LinkedHashMap<>();Map<UUID,BigDecimal> development=new LinkedHashMap<>();Map<UUID,UUID> homes=new LinkedHashMap<>();Map<UUID,Purchase> purchases=new LinkedHashMap<>();
    }
    public RecruitmentState(Path file)throws IOException {
        this.file=file;
        if(Files.exists(file))decode(Files.readString(file));
    }
    String encode(){Document d=new Document();d.nations=nations;d.development=development;d.homes=homes;d.purchases=purchases;d.dismissed=dismissed;return JSON.toJson(d);}
    void decode(String json)throws IOException {
        try{Document d=JSON.fromJson(json,Document.class);if(d==null||d.schema!=1||d.nations==null||d.development==null||d.homes==null||d.purchases==null)throw new IllegalArgumentException("Invalid recruitment data");
            for(var e:d.development.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue().signum()<0)throw new IllegalArgumentException("Invalid development");
            for(var n:d.nations.values())if(n.faction==null||n.faction.isBlank()||n.total<0||n.perPlayer<0)throw new IllegalArgumentException("Invalid nation");
            nations=d.nations;development=d.development;homes=d.homes;purchases=d.purchases;dismissed=d.dismissed==null?new LinkedHashSet<>():d.dismissed;
            if(dismissed.contains(null))throw new IllegalArgumentException("Invalid dismissal");
        }catch(RuntimeException ex){throw new IOException("Recruitment data is invalid; restore its backup rather than resetting it",ex);}
    }
    public synchronized void change(Runnable mutation){
        String old=encode();try{mutation.run();save();}catch(RuntimeException|IOException ex){try{decode(old);}catch(IOException impossible){ex.addSuppressed(impossible);}throw ex instanceof RuntimeException r?r:new UncheckedIOException((IOException)ex);}
    }
    public void save()throws IOException {
        Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try(var out=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(encode());while(bytes.hasRemaining())out.write(bytes);out.force(true);}
        if(Files.exists(file))Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        // Windows does not expose directory handles through FileChannel. The file
        // is already forced and atomically committed; never roll memory back after
        // that commit just because the optional directory barrier is unavailable.
        if(File.separatorChar!='\\')try(var parent=FileChannel.open(file.getParent(),StandardOpenOption.READ)){parent.force(true);}catch(IOException|UnsupportedOperationException unavailable){}
    }
    public BigDecimal balance(UUID town){return development.getOrDefault(town,BigDecimal.ZERO);}
    public void development(UUID town,String action,BigDecimal amount){
        if(amount.signum()<0)throw new IllegalArgumentException("Розвиток не може бути від’ємним.");
        change(()->{BigDecimal next=switch(action){case "add"->balance(town).add(amount);case "remove"->balance(town).subtract(amount);case "set"->amount;default->throw new IllegalArgumentException("add | remove | set | info");};if(next.signum()<0)throw new IllegalArgumentException("У міста недостатньо розвитку.");development.put(town,next);});
    }
    public static boolean pending(Purchase p){return Set.of("PREPARED","PAID","REVIEW","REFUNDING").contains(p.phase);}
    public boolean blocked(UUID town,UUID player){return purchases.values().stream().anyMatch(p->pending(p)&&(p.town.equals(town)||p.player.equals(player)));}
    public void reserve(Purchase p){change(()->{if(blocked(p.town,p.player))throw new IllegalStateException("Попередню покупку ще не завершено.");if(balance(p.town).compareTo(p.development)<0)throw new IllegalStateException("Недостатньо розвитку.");development.put(p.town,balance(p.town).subtract(p.development));purchases.put(p.unit,p);});}
    public void phase(UUID unit,String phase){change(()->{Purchase p=purchases.get(unit);p.phase=phase;if(Set.of("COMMITTED","SETTLED","VOID").contains(phase)){p.before=null;p.after=null;p.entity=null;}});}
    public void refund(UUID unit){change(()->{Purchase p=purchases.get(unit);if(p==null||p.phase.equals("REFUNDED"))return;if(p.phase.equals("COMMITTED"))throw new IllegalStateException("Покупку вже завершено.");development.put(p.town,balance(p.town).add(p.development));p.phase="REFUNDED";p.before=null;p.after=null;p.entity=null;});}
}
