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
    public static String duty(int variant,String subject,String operative){return LocalText.tr(DUTY[Math.floorMod(variant,5)]).replace("{s}","«"+subject+"»")+operative;}
    static String roleplay(String text){return text.equals("немає")?LocalText.tr("немає"):text;}
    static String when(long time){return time==0?LocalText.tr("немає"):DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(time));}
    static String right(String mode){return switch(mode){case "ALLOW"->LocalText.tr("дозволено самостійно");case "CONSENT"->LocalText.tr("лише за окремим погодженням сюзерена");case "DENY"->LocalText.tr("заборонено");default->throw new IllegalArgumentException(LocalText.tr("Невідоме право"));};}
    public static List<String> chapters(long id,int rev,Map<String,Object> record,ContractTerms t,String child,String parent,Function<String,String> names){
        int seed=Objects.hash(id,rev);String a=string(record,"child_sign"),b=string(record,"parent_sign");
        String an=string(record,"child_sign_name"),bn=string(record,"parent_sign_name");
        String childSigner=a==null?LocalText.tr("ще не підписано"):(an==null?names.apply(a):an)+" ["+a+"]",parentSigner=b==null?LocalText.tr("ще не підписано"):(bn==null?names.apply(b):bn)+" ["+b+"]";
        var chapters=new ArrayList<String>();
        chapters.add(LocalText.tr("Васальний договір №")+id+LocalText.tr("\nРедакція ")+rev+LocalText.tr("\nУкладено: ")+when(number(record,"signed_at"))+LocalText.tr("\nВасал: ")+child+LocalText.tr("\nСюзерен: ")+parent+LocalText.tr("\nПідписанти: ")+childSigner+"; "+parentSigner+LocalText.tr(".\nСторони — володіння; зміна правителя не скасовує договору."));
        chapters.add(LocalText.tr(RIGHT[Math.floorMod(seed,5)])+LocalText.tr("строк — ")+(t.n("days")==0?LocalText.tr("безстроковий"):t.n("days")+LocalText.tr(" реальних діб (базова тривалість)"))+LocalText.tr(". Остаточний строк цієї редакції: ")+(number(record,"signed_at")==0?LocalText.tr("ще не підписано"):record.get("expires_at")==null || number(record,"expires_at")==0?LocalText.tr("безстроково"):when(number(record,"expires_at")))+LocalText.tr(". Продовження ")+(t.yes("renew")?LocalText.tr("дозволене лише новою редакцією з двома повторними підписами"):LocalText.tr("не дозволене цією редакцією"))+LocalText.tr(". У цій редакції додано до погодженого строку ")+t.n("extend")+LocalText.tr(" діб. Попередня редакція діє до підписання нової."));
        chapters.add(duty(seed+1,child,LocalText.tr("сплачувати ")+t.get("amount")+" "+t.get("code")+LocalText.tr(" та постачати ресурси: ")+items(t,"resources")+LocalText.tr(". Період — кожні ")+t.n("period")+LocalText.tr(" реальних діб; перший строк — через ")+t.n("first")+LocalText.tr(" діб після набрання цією редакцією чинності, якщо не встановлено точну дату: ")+(t.get("first_date").isEmpty()?LocalText.tr("не встановлено"):t.get("first_date")+" 00:00 UTC")+LocalText.tr(". Гроші списуються зі скарбниці володіння; ресурси здаються через меню постачання. Данина фіксована, не відсоток доходу.")));
        chapters.add(LocalText.tr(RIGHT[Math.floorMod(seed+2,5)])+LocalText.tr("автономія васала.\nСоюзи: ")+right(t.get("alliance"))+LocalText.tr(".\nДипломатія: ")+right(t.get("diplomacy"))+LocalText.tr(".\nОголошення воєн: ")+right(t.get("war"))+LocalText.tr(" (рольове).\nВласні васали: ")+right(t.get("vassals"))+LocalText.tr(".\nАвтоматичний контроль — лише в інтегрованих діях Feudal; сторонні механіки воєн не автоматизовані."));
        String military=switch(t.get("military")){case "NONE"->LocalText.tr("обов’язкової участі у війнах немає");case "DEFENSIVE"->LocalText.tr("допомагати лише в оборонних війнах сюзерена");case "ALL"->LocalText.tr("допомагати в оборонних і наступальних війнах сюзерена");default->throw new IllegalArgumentException();};
        chapters.add((t.get("military").equals("NONE")?LocalText.tr(RIGHT[Math.floorMod(seed+3,5)])+LocalText.tr("для сторони ")+child+": ":duty(seed+3,child,""))+military+LocalText.tr("; військовий грошовий внесок: ")+roleplay(t.get("war_money"))+LocalText.tr("; спорядження: ")+roleplay(t.get("war_gear"))+LocalText.tr(". Це рольові військові умови: автоматичних викликів у війну та стягнень немає."));
        chapters.add(duty(seed+4,parent,LocalText.tr("надавати субсидію ")+t.get("subsidy")+" "+t.get("subcode")+LocalText.tr(" кожні ")+t.n("subperiod")+LocalText.tr(" реальних діб; перший строк через ")+t.n("subperiod")+LocalText.tr(" діб. Провізія за той самий період: ")+items(t,"provisions")+LocalText.tr(". Одноразова допомога лише при першому набранні договором чинності (не повторюється при зміні редакції): ")+t.get("aid")+" "+t.get("subcode")+LocalText.tr(" зі скарбниці сюзерена. Провізія здається й отримується через меню."))+LocalText.tr(" Захист васала: ")+(t.yes("defend")?LocalText.tr("сюзерен зобов’язаний захищати васала (рольова умова)."):LocalText.tr("військовий захист цим договором не вимагається.")));
        chapters.add(LocalText.tr(RIGHT[Math.floorMod(seed+5,5)])+LocalText.tr("для обох сторін допустиме прострочення — ")+t.n("grace")+LocalText.tr(" реальних діб. Фіксований штраф — ")+t.get("fine")+" "+t.get("code")+LocalText.tr(" один раз за прострочений період кожного боржника, незалежно від кількості ресурсів. Допустимо ")+t.n("misses")+LocalText.tr(" прострочених періодів за всю історію договору; після перевищення — ")+(t.get("breach").equals("TERMINATE")?LocalText.tr("автоматичне припинення договору"):LocalText.tr("запис порушення без автоматичного розірвання"))+LocalText.tr(". Старі борги зберігаються після зміни редакції й припинення договору."));
        chapters.add(LocalText.tr(RIGHT[Math.floorMod(seed+6,5)])+LocalText.tr("вихід за взаємною згодою дозволено; строковий договір припиняється після строку без автоматичного продовження. ")+(t.yes("unilateral")?LocalText.tr("Кожна сторона може вийти односторонньо: сплачує іншій стороні викуп ")+t.get("buyout")+" "+t.get("code")+LocalText.tr(" при заяві та очікує ")+t.n("notice")+LocalText.tr(" реальних діб. До виходу обов’язки чинні."):LocalText.tr("Односторонній вихід не дозволено.")));
        chapters.add(LocalText.tr("Додаткові рольові умови\n")+(t.get("extra").isEmpty()?LocalText.tr("Не встановлено."):t.get("extra"))+LocalText.tr("\nЦі положення не виконуються автоматично системами плагіна."));
        chapters.add(LocalText.tr("Підписи\nВасал: ")+childSigner+LocalText.tr("\nСюзерен: ")+parentSigner+LocalText.tr("\nЦя книга — історична копія редакції ")+rev+LocalText.tr(". Втрата, передача чи знищення книги не впливає на договір. Актуальні дані й борги зберігаються на сервері."));
        return chapters;
    }
    static final Map<String,String> ITEM_NAMES=Map.ofEntries(
        Map.entry("WHEAT","пшениці"),Map.entry("IRON_INGOT","залізних зливків"),Map.entry("GOLD_INGOT","золотих зливків"),Map.entry("BREAD","хліба"),Map.entry("OAK_LOG","дубової деревини"),Map.entry("SPRUCE_LOG","смерекової деревини"),Map.entry("BIRCH_LOG","березової деревини"),Map.entry("OAK_PLANKS","дубових дощок"),Map.entry("COBBLESTONE","бруківки"),Map.entry("STONE","каменю"),Map.entry("COAL","вугілля"),Map.entry("CHARCOAL","деревного вугілля"),Map.entry("CARROT","моркви"),Map.entry("POTATO","картоплі"),Map.entry("COOKED_BEEF","смаженої яловичини"),Map.entry("LEATHER","шкіри"),Map.entry("WHITE_WOOL","білої вовни"),Map.entry("DIAMOND","діамантів"),Map.entry("ARROW","стріл"),Map.entry("IRON_SWORD","залізних мечів"));
    static String items(ContractTerms t,String key){var items=t.items(key);return items.isEmpty()?LocalText.tr("немає"):String.join(", ",items.entrySet().stream().map(e->e.getValue()+LocalText.tr(" од. ")+LocalText.tr(ITEM_NAMES.getOrDefault(e.getKey(),e.getKey()))+" ["+e.getKey()+"]").toList());}
    public static List<String> pages(List<String> chapters){
        var pages=new ArrayList<String>();for(String chapter:chapters){while(!chapter.isEmpty()){
            int limit=Math.min(210,chapter.length());if(limit<chapter.length()){int space=chapter.lastIndexOf(' ',limit);if(space>limit/2)limit=space;}
            if(limit>0 && limit<chapter.length() && Character.isHighSurrogate(chapter.charAt(limit-1)) && Character.isLowSurrogate(chapter.charAt(limit)))limit--;pages.add(chapter.substring(0,limit));chapter=chapter.substring(limit).stripLeading();
        }}require(pages.size()<100,LocalText.tr("Договір перевищує обсяг книги."));return pages;
    }
}
