package ua.empirecraft.feudal;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;

/** Canonical, bounded terms. Money is in hundredths; time is in real UTC days. */
public final class ContractTerms {
    public static final LinkedHashMap<String,String> LABELS=new LinkedHashMap<>();
    static {
        String[][] fields={{"days","Строк у добах (0 — безстроково)"},{"renew","Дозволити продовження"},{"extend","Продовжити чинний строк на діб (0 — без зміни)"},{"amount","Грошова данина"},{"code","Валюта данини"},{"period","Період данини, діб"},{"first","Перший платіж через діб"},{"first_date","Дата першого платежу UTC (YYYY-MM-DD або порожньо)"},{"resources","Ресурсна данина: WHEAT:128,IRON_INGOT:64"},{"alliance","Власні союзи"},{"diplomacy","Власна дипломатія"},{"war","Оголошення воєн (рольове)"},{"vassals","Прийняття васалів"},{"military","Військова участь (рольове)"},{"war_money","Військовий внесок (рольове)"},{"war_gear","Військове спорядження (рольове)"},{"defend","Захист сюзереном (рольове)"},{"subsidy","Субсидія сюзерена"},{"subcode","Валюта субсидії / допомоги"},{"subperiod","Період субсидії / провізії, діб"},{"provisions","Провізія сюзерена: BREAD:64"},{"aid","Одноразова допомога при підписанні"},{"grace","Допустиме прострочення, діб"},{"fine","Фіксований штраф за період"},{"misses","Допустимо прострочених періодів"},{"breach","Повторне порушення: RECORD або TERMINATE"},{"unilateral","Односторонній вихід дозволено"},{"notice","Попередження про вихід, діб"},{"buyout","Викуп при односторонньому виході"},{"extra","Додаткові рольові умови"}};
        for(var f:fields)LABELS.put(f[0],f[1]);
    }
    private final TreeMap<String,String> values=new TreeMap<>();
    public ContractTerms(){
        values.put("first_date","");values.put("extend","0");
        values.putAll(Map.of("days","0","renew","true","amount","0.00","code","-","period","7","first","7","resources","","alliance","ALLOW","diplomacy","ALLOW","war","CONSENT"));
        values.putAll(Map.of("vassals","CONSENT","military","DEFENSIVE","war_money","немає","war_gear","немає","defend","true","subsidy","0.00","subcode","-","subperiod","7","provisions","","aid","0.00"));
        values.putAll(Map.of("grace","3","fine","0.00","misses","2","breach","RECORD","unilateral","false","notice","7","buyout","0.00","extra",""));
    }
    public String get(String k){return values.get(k);}
    public long n(String k){return Long.parseLong(get(k));}
    public long money(String k){return Model.money(get(k));}
    public boolean yes(String k){return Boolean.parseBoolean(get(k));}
    public ContractTerms with(String key,String value){
        require(LABELS.containsKey(key),"Невідоме поле договору.");
        var t=decode(encode());t.values.put(key,value.trim());t.validate();return t;
    }
    public long firstDue(long now){return get("first_date").isEmpty()?Math.addExact(now,n("first")*FeudalService.DAY):java.time.LocalDate.parse(get("first_date")).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();}
    public void validate(){
        require(get("first_date").isEmpty() || get("first_date").matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),"Дата: YYYY-MM-DD (UTC) або порожньо.");
        if(!get("first_date").isEmpty())try{java.time.LocalDate.parse(get("first_date"));}catch(java.time.DateTimeException e){throw new IllegalArgumentException("Неправильна календарна дата.");}
        for(String k:List.of("days","period","first","subperiod","grace","misses","notice","extend")){
            require(get(k).matches("[0-9]{1,4}"),"Потрібне ціле число діб / періодів.");long n=n(k);
            require(n<=3650 && (Set.of("period","subperiod").contains(k)?n>=1:n>=0),"Межі: 0–3650; період від 1 доби.");
        }
        for(String k:List.of("amount","subsidy","aid","fine","buyout"))money(k);
        for(String k:List.of("code","subcode"))require(get(k).matches("-|[A-Z]{3,8}"),"Валюта: код із 3–8 великих літер або -.");
        for(String k:List.of("renew","defend","unilateral"))require(Set.of("true","false").contains(get(k)),"Значення: true або false.");
        for(String k:List.of("alliance","diplomacy","war","vassals"))require(Set.of("ALLOW","CONSENT","DENY").contains(get(k)),"Право: ALLOW, CONSENT або DENY.");
        require(Set.of("NONE","DEFENSIVE","ALL").contains(get("military")),"Військова участь: NONE, DEFENSIVE або ALL.");
        require(Set.of("RECORD","TERMINATE").contains(get("breach")),"Наслідок: RECORD або TERMINATE.");
        for(String k:List.of("resources","provisions"))items(k);
        for(String k:List.of("extra","war_money","war_gear"))require(get(k).length()<= (k.equals("extra")?4000:300) && !get(k).matches("(?s).*[\\p{Cntrl}§].*"),"Текст завеликий або містить керівні знаки / форматування.");
    }
    public SortedMap<String,Long> items(String key){
        var result=new TreeMap<String,Long>();String input=get(key);if(input.isBlank())return result;
        require(input.length()<=700,"Забагато ресурсів.");
        for(String part:input.split(",",-1)){
            var p=part.trim().split(":",-1);require(p.length==2 && p[0].matches("[A-Z][A-Z0-9_]{0,60}") && p[1].matches("[0-9]{1,6}"),"Ресурси: WHEAT:128,IRON_INGOT:64; порожньо — немає.");
            long n=Long.parseLong(p[1]);require(n>0 && n<=100000 && result.put(p[0],n)==null,"Кількість 1–100000, без повторів.");
        }
        require(result.size()<=12,"Не більше 12 видів ресурсів.");return result;
    }
    public String encode(){var b=new StringBuilder();values.forEach((k,v)->b.append(k).append('=').append(Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8))).append('\n'));return b.toString();}
    public static ContractTerms decode(String data){var t=new ContractTerms();require(data!=null && data.length()<30000,"Пошкоджені умови.");for(String line:data.split("\n")){int i=line.indexOf('=');require(i>0 && LABELS.containsKey(line.substring(0,i)),"Невідоме поле.");t.values.put(line.substring(0,i),new String(Base64.getDecoder().decode(line.substring(i+1)),StandardCharsets.UTF_8));}t.validate();return t;}
}
