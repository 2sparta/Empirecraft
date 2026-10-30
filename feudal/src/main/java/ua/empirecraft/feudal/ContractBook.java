package ua.empirecraft.feudal;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Wording changes style only: all variants append the same unambiguous operative terms. */
public final class ContractBook {
    private ContractBook(){}
    static final String[] DUTY={
        "Належить стороні {s} сумлінно чинити таке: ",
        "Сторона {s} цим бере на себе повинність: ",
        "За цією грамотою сторона {s} зобов’язується: ",
        "Сторона {s} скріплює словом обов’язок: ",
        "Силою сього договору на сторону {s} покладено: "
    };
    static final String[] RIGHT={"Цією грамотою установлено: ","За волею обох сторін визначено: ","Непорушним приписом договору є таке: ","До погоджених прав належить таке: ","Сторони скріплюють такий порядок: "};
    public static String duty(int variant,String subject,String operative){return DUTY[Math.floorMod(variant,5)].replace("{s}","«"+subject+"»")+operative;}
    static String when(long time){return time==0?"немає":DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(time));}
    static String right(String mode){return switch(mode){case "ALLOW"->"дозволено самостійно";case "CONSENT"->"лише за окремим погодженням сюзерена";case "DENY"->"заборонено";default->throw new IllegalArgumentException("Невідоме право");};}
    public static List<String> chapters(long id,int rev,Map<String,Object> record,ContractTerms t,String child,String parent,Function<String,String> names){
        int seed=Objects.hash(id,rev);String a=string(record,"child_sign"),b=string(record,"parent_sign");
        String an=string(record,"child_sign_name"),bn=string(record,"parent_sign_name");
        String childSigner=a==null?"ще не підписано":(an==null?names.apply(a):an)+" ["+a+"]",parentSigner=b==null?"ще не підписано":(bn==null?names.apply(b):bn)+" ["+b+"]";
        var chapters=new ArrayList<String>();
        chapters.add("Васальний договір №"+id+"\nРедакція "+rev+"\nУкладено: "+when(number(record,"signed_at"))+"\nВасал: "+child+"\nСюзерен: "+parent+"\nПідписанти: "+childSigner+"; "+parentSigner+".\nСторони — володіння; зміна правителя не скасовує договору.");
        chapters.add(RIGHT[Math.floorMod(seed,5)]+"строк — "+(t.n("days")==0?"безстроковий":t.n("days")+" реальних діб (базова тривалість)")+". Остаточний строк цієї редакції: "+(number(record,"signed_at")==0?"ще не підписано":record.get("expires_at")==null || number(record,"expires_at")==0?"безстроково":when(number(record,"expires_at")))+". Продовження "+(t.yes("renew")?"дозволене лише новою редакцією з двома повторними підписами":"не дозволене цією редакцією")+". У цій редакції додано до погодженого строку "+t.n("extend")+" діб. Попередня редакція діє до підписання нової.");
        chapters.add(duty(seed+1,child,"сплачувати "+t.get("amount")+" "+t.get("code")+" та постачати ресурси: "+items(t,"resources")+". Період — кожні "+t.n("period")+" реальних діб; перший строк — через "+t.n("first")+" діб після набрання цією редакцією чинності, якщо не встановлено точну дату: "+(t.get("first_date").isEmpty()?"не встановлено":t.get("first_date")+" 00:00 UTC")+". Гроші списуються зі скарбниці володіння; ресурси здаються через меню постачання. Данина фіксована, не відсоток доходу."));
        chapters.add(RIGHT[Math.floorMod(seed+2,5)]+"автономія васала.\nСоюзи: "+right(t.get("alliance"))+".\nДипломатія: "+right(t.get("diplomacy"))+".\nОголошення воєн: "+right(t.get("war"))+" (рольове).\nВласні васали: "+right(t.get("vassals"))+".\nАвтоматичний контроль — лише в інтегрованих діях Feudal; сторонні механіки воєн не автоматизовані.");
        String military=switch(t.get("military")){case "NONE"->"обов’язкової участі у війнах немає";case "DEFENSIVE"->"допомагати лише в оборонних війнах сюзерена";case "ALL"->"допомагати в оборонних і наступальних війнах сюзерена";default->throw new IllegalArgumentException();};
        chapters.add((t.get("military").equals("NONE")?RIGHT[Math.floorMod(seed+3,5)]+"для сторони "+child+": ":duty(seed+3,child,""))+military+"; військовий грошовий внесок: "+t.get("war_money")+"; спорядження: "+t.get("war_gear")+". Це рольові військові умови: автоматичних викликів у війну та стягнень немає.");
        chapters.add(duty(seed+4,parent,"надавати субсидію "+t.get("subsidy")+" "+t.get("subcode")+" кожні "+t.n("subperiod")+" реальних діб; перший строк через "+t.n("subperiod")+" діб. Провізія за той самий період: "+items(t,"provisions")+". Одноразова допомога лише при першому набранні договором чинності (не повторюється при зміні редакції): "+t.get("aid")+" "+t.get("subcode")+" зі скарбниці сюзерена. Провізія здається й отримується через меню.")+" Захист васала: "+(t.yes("defend")?"сюзерен зобов’язаний захищати васала (рольова умова).":"військовий захист цим договором не вимагається."));
        chapters.add(RIGHT[Math.floorMod(seed+5,5)]+"для обох сторін допустиме прострочення — "+t.n("grace")+" реальних діб. Фіксований штраф — "+t.get("fine")+" "+t.get("code")+" один раз за прострочений період кожного боржника, незалежно від кількості ресурсів. Допустимо "+t.n("misses")+" прострочених періодів за всю історію договору; після перевищення — "+(t.get("breach").equals("TERMINATE")?"автоматичне припинення договору":"запис порушення без автоматичного розірвання")+". Старі борги зберігаються після зміни редакції й припинення договору.");
        chapters.add(RIGHT[Math.floorMod(seed+6,5)]+"вихід за взаємною згодою дозволено; строковий договір припиняється після строку без автоматичного продовження. "+(t.yes("unilateral")?"Кожна сторона може вийти односторонньо: сплачує іншій стороні викуп "+t.get("buyout")+" "+t.get("code")+" при заяві та очікує "+t.n("notice")+" реальних діб. До виходу обов’язки чинні.":"Односторонній вихід не дозволено."));
        chapters.add("Додаткові рольові умови\n"+(t.get("extra").isEmpty()?"Не встановлено.":t.get("extra"))+"\nЦі положення не виконуються автоматично системами плагіна.");
        chapters.add("Підписи\nВасал: "+childSigner+"\nСюзерен: "+parentSigner+"\nЦя книга — історична копія редакції "+rev+". Втрата, передача чи знищення книги не впливає на договір. Актуальні дані й борги зберігаються на сервері.");
        return chapters;
    }
    static final Map<String,String> ITEM_NAMES=Map.ofEntries(
        Map.entry("WHEAT","пшениці"),Map.entry("IRON_INGOT","залізних зливків"),Map.entry("GOLD_INGOT","золотих зливків"),Map.entry("BREAD","хліба"),Map.entry("OAK_LOG","дубової деревини"),Map.entry("SPRUCE_LOG","смерекової деревини"),Map.entry("BIRCH_LOG","березової деревини"),Map.entry("OAK_PLANKS","дубових дощок"),Map.entry("COBBLESTONE","бруківки"),Map.entry("STONE","каменю"),Map.entry("COAL","вугілля"),Map.entry("CHARCOAL","деревного вугілля"),Map.entry("CARROT","моркви"),Map.entry("POTATO","картоплі"),Map.entry("COOKED_BEEF","смаженої яловичини"),Map.entry("LEATHER","шкіри"),Map.entry("WHITE_WOOL","білої вовни"),Map.entry("DIAMOND","діамантів"),Map.entry("ARROW","стріл"),Map.entry("IRON_SWORD","залізних мечів"));
    static String items(ContractTerms t,String key){var items=t.items(key);return items.isEmpty()?"немає":String.join(", ",items.entrySet().stream().map(e->e.getValue()+" од. "+ITEM_NAMES.getOrDefault(e.getKey(),e.getKey())+" ["+e.getKey()+"]").toList());}
    public static List<String> pages(List<String> chapters){
        var pages=new ArrayList<String>();for(String chapter:chapters){while(!chapter.isEmpty()){
            int limit=Math.min(210,chapter.length());if(limit<chapter.length()){int space=chapter.lastIndexOf(' ',limit);if(space>limit/2)limit=space;}
            if(limit>0 && limit<chapter.length() && Character.isHighSurrogate(chapter.charAt(limit-1)) && Character.isLowSurrogate(chapter.charAt(limit)))limit--;pages.add(chapter.substring(0,limit));chapter=chapter.substring(limit).stripLeading();
        }}require(pages.size()<100,"Договір перевищує обсяг книги.");return pages;
    }
}
