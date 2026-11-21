package ua.townyrecruits;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** UTF-8 configuration; decimal costs never pass through floating point. */
public final class RecruitmentConfig {
    public final Properties values=new Properties();
    public final int total,perPlayer,interval;
    public final BigDecimal initialDevelopment;
    public final boolean adopt,dryRun;
    public RecruitmentConfig(Path path)throws IOException {
        var defaults=new LinkedHashMap<String,String>();
        defaults.put("limits.new-nation-total","15");defaults.put("limits.new-nation-per-player","15");
        defaults.put("development.new-town","0");defaults.put("sync.interval-ticks","40");
        defaults.put("sync.adopt-existing-factions","true");defaults.put("sync.dry-run","false");
        defaults.put("hire.permission","townyrecruits.hire");defaults.put("hire.residents-by-default","true");
        defaults.put("recruit.gold","5");defaults.put("recruit.development","0.5");
        defaults.put("shieldman.gold","10");defaults.put("shieldman.development","1");
        defaults.put("message.development","Вам не вистачає розвитку! Будуйте споруди та розвивайте своє місто");
        defaults.put("message.gold","Вам не вистачає коштів в інвентарі!");
        defaults.put("message.limit","Ваш ліміт військ перевищений! Будуйте військові споруди, щоб збільшити кількість військ");
        defaults.put("message.locked","Цей тип військ ще недоступний для вашої держави!");
        defaults.put("message.success","Вітаємо! Ви успішно найняли рекрута. Він очікує на вас у місті!");
        defaults.put("message.permission","У вас недостатньо прав для наймання військ!");
        defaults.put("message.failure","Не вдалося найняти рекрута. Ваші ресурси не були витрачені");
        defaults.put("message.native-hire","Наймайте військо через /t menu → Рекрути.");
        defaults.forEach(values::setProperty);
        if(Files.exists(path))try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){values.load(reader);}
        total=integer("limits.new-nation-total",0,1000000);perPlayer=integer("limits.new-nation-per-player",0,1000000);
        interval=integer("sync.interval-ticks",20,40);initialDevelopment=decimal(values.getProperty("development.new-town"));
        adopt=Boolean.parseBoolean(values.getProperty("sync.adopt-existing-factions"));dryRun=Boolean.parseBoolean(values.getProperty("sync.dry-run"));
        for(String type:List.of("recruit","shieldman")){gold(type);cost(type);}
        if(!Files.exists(path)){Files.createDirectories(path.getParent());try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){writer.write("# TownyRecruits 2.0.1. Restart after editing. Existing nation limits are preserved.\n");for(var e:defaults.entrySet())writer.write(e.getKey()+"="+e.getValue()+"\n");}}
    }
    public static BigDecimal decimal(String text){
        if(text==null || !text.matches("[0-9]{1,15}(\\.[0-9]{1,6})?"))throw new IllegalArgumentException("Вкажіть невід’ємне число, до 6 знаків після крапки.");
        return new BigDecimal(text).stripTrailingZeros();
    }
    int integer(String key,int min,int max){int value=Integer.parseInt(values.getProperty(key));if(value<min||value>max)throw new IllegalArgumentException(key+": "+min+".."+max);return value;}
    public int gold(String type){return type.equals("militia")?0:integer(type+".gold",1,2304);}
    public BigDecimal cost(String type){return decimal(values.getProperty(type+".development"));}
    public String message(String key){return values.getProperty("message."+key,key);}
    public static String number(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
}
