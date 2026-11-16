package ua.townyrecruits;

import java.lang.reflect.Method;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

/** Read-only presentation over Feudal's existing journal and nutrition ledger. No second food store. */
final class RecruitProvisionView {
    private RecruitProvisionView(){}
    static boolean isProvision(Inventory inventory){
        var holder=inventory.getHolder();
        return holder!=null&&holder.getClass().getName().equals("ua.empirecraft.feudal.ProvisionController$Chest");
    }
    static Object call(Object target,String name,Object... args)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(Method method:type.getDeclaredMethods())
            if(method.getName().equals(name)&&method.getParameterCount()==args.length){method.setAccessible(true);return method.invoke(target,args);}
        throw new NoSuchMethodException(name);
    }
    static void refresh(Recruitment r,Inventory inventory)throws Exception {
        if(!isProvision(inventory))return;
        Object feudal=r.bridge.towny.feudal();if(feudal==null)throw new IllegalStateException("Feudal недоступний.");
        UUID city=(UUID)Recruitment.field(inventory.getHolder(),"town");
        Object controller=Recruitment.field(feudal,"provisions");
        if(!Boolean.TRUE.equals(call(controller,"settleArmy","t:"+city))){
            unavailable(inventory);return;
        }
        Object town=r.bridge.towny.api.getClass().getMethod("getTown",UUID.class).invoke(r.bridge.towny.api,city);
        if(town==null){unavailable(inventory);return;}
        Player viewer=inventory.getViewers().stream().filter(Player.class::isInstance).map(Player.class::cast).findFirst().orElse(null);
        if(viewer==null)return;
        Class<?> text=Class.forName("ua.empirecraft.feudal.LocalText",true,feudal.getClass().getClassLoader());
        Method use=text.getDeclaredMethod("useMenu",org.bukkit.command.CommandSender.class);use.setAccessible(true);
        try(AutoCloseable scope=(AutoCloseable)use.invoke(null,viewer)){
            call(controller,"render",inventory.getHolder(),town,call(controller,"clock",town));
        }
        decorate(r,inventory);
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> query(Object db,String sql,Object... args)throws Exception {
        return (List<Map<String,Object>>)db.getClass().getMethod("query",String.class,Object[].class).invoke(db,sql,args);
    }
    static long number(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Number n?n.longValue():Long.parseLong(value.toString());}
    static String energy(BigInteger value,BigInteger unit){return new BigDecimal(value).divide(new BigDecimal(unit),2,RoundingMode.DOWN).toPlainString();}
    static ItemStack icon(Material material,String title,String... lore){
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName("§6"+title);
        meta.setLore(Arrays.stream(lore).map(line->"§7"+line).toList());item.setItemMeta(meta);return item;
    }
    static void unavailable(Inventory inventory){
        inventory.setItem(47,icon(Material.BARRIER,"Облік провізії тимчасово недоступний","Не вважайте старі числа актуальними.","Перевірте календар та незавершені передачі."));
        inventory.setItem(50,icon(Material.BARRIER,"Забезпеченість ще не визначена","Дочекайтеся відновлення обліку."));
    }
    @SuppressWarnings("unchecked")
    static void decorate(Recruitment r,Inventory inventory)throws Exception {
        if(!isProvision(inventory))return;
        UUID city=(UUID)Recruitment.field(inventory.getHolder(),"town");String key="t:"+city;
        Object feudal=r.bridge.towny.feudal();if(feudal==null){unavailable(inventory);return;}
        Object controller=Recruitment.field(feudal,"provisions"),service=Recruitment.field(controller,"service"),db=Recruitment.field(service,"db");
        Object town=r.bridge.towny.api.getClass().getMethod("getTown",UUID.class).invoke(r.bridge.towny.api,city);
        if(town==null){unavailable(inventory);return;}
        Object clock=call(controller,"clock",town);
        BigInteger unit=(BigInteger)clock.getClass().getField("UNIT").get(null);
        Map<String,Object> state=(Map<String,Object>)call(service,"town",key);
        if(state==null||Boolean.TRUE.equals(call(service,"pending",key))){unavailable(inventory);return;}
        List<Map<String,Object>> stock=(List<Map<String,Object>>)call(service,"stock",key);
        BigInteger total=(BigInteger)call(service,"reserve",key),credit=new BigInteger(state.get("credit").toString());
        long items=0;BigInteger itemEnergy=BigInteger.ZERO;
        for(var row:stock){long quantity=number(row,"quantity");items+=quantity;itemEnergy=itemEnergy.add(unit.multiply(BigInteger.valueOf(quantity*number(row,"nutrition"))));}
        inventory.setItem(46,icon(Material.CHEST,"Їжа в комірках: "+items+" шт.",
            "Це кількість цілих предметів, а не одиниці голоду.",
            "Їхня поживність: "+energy(itemEnergy,unit)+" од."));
        long now=((Number)call(Recruitment.field(service,"s"),"now")).longValue();
        long chunks=number(state,"chunks");
        BigInteger daily=(BigInteger)call(clock,"cost",now,now+86_400_000L,chunks);
        inventory.setItem(47,icon(Material.WHEAT,"Загальна поживність: "+energy(total,unit)+" од.",
            "Цілі предмети + залишок від уже відкритої їжі.",
            "Місто на наступні 24 реальні год: "+energy(daily,unit)+" од.",
            "Витрати рекрутів додаються за їхнім фактичним голодом."));
        inventory.setItem(48,icon(Material.BOWL,"Залишок поживності: "+energy(credit,unit)+" од.",
            "Після часткового споживання їжа не зникає безслідно.",
            "Залишок зберігається й витрачається наступним.",
            "Це поживність, яку не можна забрати як цілий предмет."));
        int alive=0,loaded=0;long need=0;
        for(var entry:r.bridge.data.units.entrySet())if(!entry.getValue().retired()&&city.equals(r.state.homes.get(entry.getKey())))alive++;
        for(var recruit:r.bridge.loaded)if(recruit.getIsOwned()&&recruit.m_6084_()&&city.equals(r.state.homes.get(recruit.m_20148_()))){
            loaded++;need+=Math.max(0,100-recruit.getHunger());
        }
        boolean enough=total.compareTo(unit.multiply(BigInteger.valueOf(need)))>=0;
        inventory.setItem(50,icon(enough?Material.BREAD:Material.ROTTEN_FLESH,"Постачання військових міста",
            "Живих: "+alive+"; завантажених: "+loaded+".",
            "До повної ситості завантажених: "+need+" од.",
            enough?"Запас покриває цю поточну потребу.":"Запас не покриває цю поточну потребу.",
            "Це не прогноз на добу; голод вивантажених тут не оцінено."));
        var history=query(db,"SELECT at_time,amount FROM army_log WHERE estate=? AND event='FOOD' ORDER BY seq DESC LIMIT 3",key);
        List<String> lines=new ArrayList<>();
        for(var row:history){
            String time=DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.of("Europe/Kyiv")).format(Instant.ofEpochMilli(number(row,"at_time")));
            lines.add(time+": війську видано "+number(row,"amount")+" од.");
        }
        if(lines.isEmpty())lines.add("Видачі їжі війську ще не зафіксовано.");
        inventory.setItem(51,icon(Material.PAPER,"Останнє харчування війська",lines.toArray(String[]::new)));
        inventory.setItem(52,icon(Material.BOOK,"Як працює спільний запас",
            "Їжу споживає і територія міста, і його рекрути.",
            "Новому місту потрібно внести початкову провізію.",
            "Числа оновлюються приблизно раз на 2 секунди.",
            "Натисніть їжу у своєму інвентарі: ЛКМ — стак, ПКМ — 1."));
    }
}
