package ua.empirecraft.feudal;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.MarketCommands.*;

/** Native-screen model and form allowlist. Clients never provide executable command templates. */
public final class MarketScreens {
    final MarketCommands c;final CityMarketService m;
    record Session(String id,long expires,String focus) {}
    final Map<UUID,Session> sessions=new HashMap<>();
    final Map<UUID,Long> throttles=new HashMap<>();
    final Map<UUID,Map<String,Object>> opens=new LinkedHashMap<>();
    final Map<String,String> initialSections=new HashMap<>();
    public record Form(String id,String title,String section,List<String> path,List<String> fields) {
        Map<String,Object> view(){return Map.of("id",id,"title",LocalText.tr(title),"fields",fields.stream().map(LocalText::tr).toList(),"prefill",fields.stream().map(v->v.startsWith("Специфікація")?"SPEC":v.startsWith("ID")?"ID":"").toList());}
    }
    static final Map<String,Form> FORMS=new LinkedHashMap<>();
    static void form(String id,String title,String section,String path,String fields){FORMS.put(id,new Form(id,title,section,List.of(path.split("\\|")),fields.isEmpty()?List.of():List.of(fields.split("\\|"))));}
    static {
        form("board-read","Прочитати оголошення","notice","board|read|$0","ID оголошення");
        form("board-event","Переглянути міську подію","notice","board|event|$0","ID події");
        form("board-post","Подати оголошення","notice","board|post|$0|$1|$2","Строк 1–30 діб|Заголовок до 100 символів|Текст до 4000 символів");
        form("board-page","Сторінка оголошень","notice","board|list|$0","Номер сторінки");
        form("board-review","Черга перевірки оголошень","notice","board|review","");
        form("board-approve","Схвалити оголошення","notice","board|approve|$0|$1","ID оголошення|Примітка");
        form("board-reject","Відхилити з поверненням плати","notice","board|reject|$0|$1","ID оголошення|Причина");
        form("board-remove","Зняти оголошення","notice","board|remove|$0|$1","ID оголошення|Причина");
        form("board-place","Установити дошку на табличці","notice","board|place|$0","ID ринку");
        form("board-configure","Налаштувати плату й перевірку","notice","board|configure|$0|$1|$2|$3","ID ринку|Плата (0 — безкоштовно)|Валюта або -|Перевірка true/false");
        form("board-herald","Призначити міського вісника","notice","board|herald|$0|$1|$2","ID ринку|Ім’я гравця або UUID|Призначити true/false");
        form("fishing-menu","Рибальські турніри","notice","fishing|menu","");
        form("fishing-view","Правила, нагороди й таблиця турніру","fishing","fishing|view|$0","ID турніру");
        form("fishing-join","Зареєструватися на турнір","fishing","fishing|join|$0","ID турніру");
        form("fishing-create","Запланувати міський турнір","fishing","fishing|create|$0|$1|$2|$3|$4","Назва турніру|Опис і правила|Початок YYYY-MM-DDTHH:mm (Київ)|Тривалість 5–240 хвилин|Залік COUNT/TOTAL_WEIGHT/LARGEST");
        form("fishing-fee","Вступний внесок у призовий фонд","fishing","fishing|fee|$0|$1|$2","ID турніру|Внесок (0 — безкоштовно)|Валюта або -");
        form("fishing-money","Унести валютну нагороду","fishing","fishing|prize_money|$0|$1|$2|$3|$4","ID турніру|Призове місце 1–3|Валюта|Сума нагороди|З міської скарбниці true/false");
        form("fishing-item","Унести нагороду предметом із руки","fishing","fishing|prize_item|$0|$1|$2","ID турніру|Призове місце 1–3|Кількість предметів");
        form("fishing-announce","Оголосити турнір","fishing","fishing|announce|$0","ID турніру");
        form("fishing-cancel","Скасувати турнір і повернути резерви","fishing","fishing|cancel|$0","ID турніру");
        form("fishing-letter","Написати запрошення Contact","fishing","fishing|letter|edit|$0|$1","ID турніру|Текст запрошення");
        form("fishing-sample","Отримати зразок запрошення Contact","fishing","fishing|letter|preview|$0","ID турніру");
        form("notice-menu","Дошка оголошень","fishing","board|menu","");
        form("buy","Купити точний ресурс","goods","buy|$0|$1|$2|$3|$4","Кількість|Валюта|Гранична ціна за одиницю|Строк 1/3/7 діб|Специфікація (порожньо: предмет у руці)");
        form("sell","Продати предмет у руці","goods","sell|$0|$1|$2|$3","Кількість|Валюта|Мінімальна ціна за одиницю|Строк 1/3/7 діб");
        form("auction-create","Виставити аукціон","goods","auction|create|$0|$1|$2|$3|$4|$5","Кількість лота з руки|Валюта|Початкова ціна|Мінімальний крок|Викуп (0 без викупу)|Строк 1/24/72 години");
        form("auction-city","Виставити міський актив на аукціон","manage","auction|create_asset|$0|$1|$2|$3|$4|$5|$6","ID предметного активу міста|Кількість|Валюта|Початкова ціна|Мінімальний крок|Викуп (0 без викупу)|Строк 1/24/72 години");
        form("auction-bid","Зробити ставку","goods","auction|bid|$0|$1|$2","ID аукціону|Сума ставки|Рахунок (порожньо: особистий)");
        form("auction-buyout","Викупити лот","goods","auction|buyout|$0|$1","ID аукціону|Рахунок (порожньо: особистий)");
        form("fx-buy","Купити базову валюту","fx","exchange|buy|$0|$1|$2|$3|$4","Базова валюта (код менший за абеткою)|Котирувана валюта|Кількість цілих базових монет|Ліміт ціни у котируваній|Строк 1/3/7 діб");
        form("fx-sell","Продати базову валюту","fx","exchange|sell|$0|$1|$2|$3|$4","Базова валюта|Котирувана валюта|Кількість цілих базових монет|Мінімальна ціна у котируваній|Строк 1/3/7 діб");
        form("official","Офіційний обмін","fx","exchange|official|$0|$1|$2","Передати валюту|Отримати валюту|Сума передавання (без міського збору)");
        form("supply","Замовити постачання","jobs","jobs|supply|$0|$1|$2|$3|$4|$5","Валюта|Повна винагорода|Строк у добах|Кількість предметів з руки|Часткове виконання true/false|Опис і місце здавання");
        form("work","Замовити роботу з етапами","jobs","jobs|work|$0|$1|$2|$3|$4","Валюта|Повна винагорода|Строк у добах|Етапи сума:критерій;сума:критерій|Опис, координати й умови");
        form("contract-accept","Прийняти роботу","jobs","contract|$0|accept","ID контракту");
        form("deliver","Здати постачання","jobs","contract|$0|deliver|$1","ID контракту|Кількість");
        form("loan-apply","Подати заявку на позику","credit","loan|apply|$0","ID пропозиції");
        form("loan-offer","Створити кредитну пропозицію","credit","loan|offer|$0|$1|$2|$3|$4|$5","ID ринку|Валюта|Основна сума|Ставка за весь строк у б.п.|Строк у добах|Кількість застави з руки");
        form("loan-fund","Унести капітал кредитора","credit","loan|deposit|$0|$1|$2","ID ринку|Валюта|Сума");
        form("loan-approve","Схвалити позику","credit","loan|approve|$0","ID заявки");
        form("loan-take","Отримати схвалену позику","credit","loan|take|$0","ID позики (застава має бути в інвентарі)");
        form("load","Отримати власний актив","assets","load|$0|$1","ID активу|Кількість предметів (порожньо: всі)");
        form("recover","Повернути актив недоступного відділу","assets","recover|$0|$1","ID активу|Кількість предметів (порожньо: всі)");
        form("cancel","Скасувати залишок заявки","mine","cancel|$0","ID заявки");
        form("reduce","Зменшити залишок заявки","mine","reduce|$0|$1","ID заявки|Новий залишок");
        form("auction-cancel","Скасувати аукціон без ставок","mine","auction|cancel|$0","ID аукціону");
        form("repay","Дистанційно погасити позику","mine","loan|repay|$0|$1","ID позики|Сума платежу");
        form("submit","Подати етап роботи","mine","contract|$0|submit|$1|$2","ID контракту|Номер етапу|Опис результату й докази");
        form("accept-stage","Прийняти етап роботи","mine","contract|$0|approve","ID контракту");
        form("dispute","Відкрити спір","mine","contract|$0|dispute|$1","ID контракту|Причина й докази");
        form("foreclose","Прийняти заставу й закрити борг","mine","loan|foreclose|$0","ID позики");
        form("share","Запропонувати передавання відомостей","mine","share_info|$0","Ім’я гравця поруч (або accept для приймання)");
        form("found","Створити проєкт ринку","manage","founding|create|$0|$1","Назва|Опис комплексу");
        form("found-submit","Подати проєкт на огляд","manage","founding|submit|$0","ID ринку");
        form("tariff","Оголосити тариф через 24 години","manage","manage|$0|tariff|$1|$2|$3","ID ринку|Покупець 0–500 б.п.|Продавець 0–500 б.п.|Послуги 0–500 б.п.");
        form("payment","Платіжна валюта","manage","manage|$0|payment|$1|$2","ID ринку|Код валюти|Дозвіл true/false");
        form("host","Допуск валюти містом","manage","manage|$0|host|$1|$2","ID ринку|Код валюти|Допуск true/false");
        form("issuer","Допуск валюти Банкіром","manage","exchange|permit|$0|$1|issuer|$2","ID ринку|Код власної валюти|Допуск true/false");
        form("staff","Призначити керівника","manage","manage|$0|staff|$1|$2","ID ринку|Ім’я гравця|Увімкнути true/false");
        form("official-enable","Увімкнути офіційний пункт","manage","manage|$0|official|$1","ID ринку|Увімкнути true/false");
        form("official-contribute","Унести власні кошти до міського валютного фонду","manage","manage|$0|official_contribute|$1|$2","ID ринку|Валюта|Сума добровільного внеску (стане власністю міського фонду)");
        form("official-fund","Поповнити валютний фонд із міської казни","manage","manage|$0|official_deposit|$1|$2","ID ринку|Валюта|Сума");
        form("official-withdraw","Повернути вільні кошти фонду в казну","manage","manage|$0|official_withdraw|$1|$2","ID ринку|Валюта|Сума");
        form("procurement-create","Постійна міська закупівля","manage","procurement|create|$0|$1|$2|$3|$4|$5|$6|$7","Валюта|Нижній поріг запасу|Цільовий запас|Гранична ціна за одиницю|Міський бюджет фонду|Ліміт витрат за 24 години|Максимальна кількість заявки|Строк заявки 1/3/7 діб");
        for(String mode:List.of("pause","resume","stop"))form("procurement-"+mode,LocalText.tr("Закупівля: ")+mode,"manage","procurement|"+mode+"|$0","ID доручення");
        form("procurement-fund","Поповнити фонд закупівлі","manage","procurement|deposit|$0|$1","ID доручення|Сума з міської казни");
        form("procurement-amend","Змінити умови закупівлі","manage","procurement|amend|$0|$1|$2|$3|$4|$5","ID доручення|Нижній поріг|Ціль|Ціна|Ліміт за 24 години|Максимальний розмір заявки");
        form("license","Ліцензія кредитора","manage","loan|license|$0|$1|$2|$3|$4|$5|$6|$7|$8","ID ринку|Кредитор|Валюта|Максимум однієї позики|Максимум основного боргу|Максимальна ставка б.п.|Максимальний строк діб|Максимум договорів|Дозволити true/false (застава в руці)");
        form("treaty-create","Підготувати двосторонній торговий договір","jobs","treaty|create|$0|$1|$2|$3|$4|$5|$6|$7|$8|$9|$10|$11|$12","ID першого міста/держави|ID другої сторони|ID другого ринку|Збір покупця б.п.|Збір продавця б.п.|Збір послуг б.п.|Ліміт пільгового обороту|Валюта ліміту та пільгових операцій|Період ліміту діб|Специфікації через кому або *|Початок: YYYY-MM-DDTHH:mm Київ|Кінець: YYYY-MM-DDTHH:mm Київ|Повідомлення про припинення годин");
        form("vassal-offer","Підготувати васальний договір","mine","@vassal|offer|$0|$1|$2|$3|$4|$5|$6","ID васала|ID сюзерена|Данина за 7 діб|Валюта (або - для нульової данини)|Автономія: висока/обмежена|Військова умова: добровільно/оборона/повний|Строк 0 або 7–365 діб");
        form("vassal-book","Оформити васальну книгу","mine","@vassal|book|$0|$1|$2","AUTO або MANUAL|ID васальної пропозиції|Авторський текст (не механічні умови)");
        form("treaty-book","Оформити книгу договору","mine","treaty|book|$0|$1|$2","AUTO або MANUAL|ID договору|Авторський текст (не механічні умови)");
        for(String mode:List.of("publish","reject","terminate"))form("treaty-"+mode,"Торговий договір: "+mode,"mine","treaty|"+mode+"|$0","ID договору");
        form("treaty-amend","Запропонувати поправку","mine","treaty|amend|$0|$1|$2|$3|$4|$5","ID договору|Збір покупця б.п.|Збір продавця б.п.|Збір послуг б.п.|Ліміт обороту|Новий кінець YYYY-MM-DDTHH:mm Київ");
        form("fair-create","Підготувати ярмарок","manage","fair|create|$0|$1|$2|$3|$4|$5|$6|$7|$8","Назва|Тема|Початок YYYY-MM-DDTHH:mm Київ|Тривалість 2–72 години|Відділи GOODS,FX,JOBS,CREDIT|Специфікації або *|Збір покупця б.п.|Збір продавця б.п.|Збір послуг б.п.");
        form("fair-text","Написати запрошення вручну","mail","fair|letter|edit|$0|$1","ID ярмарку|Авторський текст до 4000 символів");
        form("fair-preview","Запросити фізичний лист-зразок","mail","fair|letter|preview|$0","ID ярмарку");
        form("fair-confirm","Підтвердити розсилку за фізичним зразком","mail","fair|letter|confirm|$0|$1|$2","ID ярмарку|Версія зразка|Токен зразка (листівка в головній руці)");
        form("fair-letter-cancel","Скасувати підготовку листа","mail","fair|letter|cancel|$0","ID ярмарку");
        form("fair-cancel","Скасувати ярмарок","manage","fair|cancel|$0","ID ярмарку");
        form("admin-claim","Чанки ринку","admin","admin|claims|$0|$1|$2|$3|$4","ID ринку|add або remove|Чанк X|Чанк Z|Причина");
        form("admin-building","Зареєструвати будівлю (дивіться на стійку)","admin","admin|department|$0|$1|building|$2|$3|$4|$5|$6|$7|$8","ID ринку|GOODS/FX/JOBS/CREDIT|X1|Y1|Z1|X2|Y2|Z2|Причина");
        for(String mode:List.of("approve","reject","suspend","close","recovery"))form("admin-"+mode,"Ринок: "+mode,"admin","admin|"+mode+"|$0|$1","ID ринку|Причина");
        form("admin-department","Оглянути або призупинити відділ","admin","admin|department|$0|$1|$2|$3","ID ринку|GOODS/FX/JOBS/CREDIT|approve або suspend|Результат особистого огляду");
        form("arbitrate","Незалежний арбітраж роботи","admin","contract|$0|arbitrate|$1|$2","ID контракту|Сума виконавцю|Причина рішення");
    }
    MarketScreens(MarketCommands c){this.c=c;m=c.m;c.screens=this;}
    public void open(Player p){open(p,"");}
    public void open(Player p,String focus){String id=id();var previous=sessions.put(p.getUniqueId(),new Session(id,m.s.now()+600000,focus));if(previous!=null)initialSections.remove(previous.id);opens.put(p.getUniqueId(),Map.of("player",p.getUniqueId().toString(),"session",id,"expires",m.s.now()+10000));}
    void openSection(Player p,String section){open(p);initialSections.put(sessions.get(p.getUniqueId()).id,section);}
    public List<Map<String,Object>> poll(){var values=new ArrayList<>(opens.values());opens.clear();return values;}
    List<Map<String,Object>> assets(FeudalService.Actor who)throws Exception {
        var result=new ArrayList<Map<String,Object>>(m.db.query("SELECT * FROM cm_assets WHERE owner=? AND quantity>0",wallet(who)));
        for(var r:m.db.query("SELECT * FROM cm_assets WHERE owner LIKE 'e:%' AND quantity>0"))try{m.ownAsset(who,string(r,"id"));result.add(r);}catch(IllegalArgumentException ignored){}
        return result;
    }
    static String[] command(Form form,List<String> values){
        require(values.size()==form.fields.size(),LocalText.tr("Форма змінилася; відкрийте її повторно."));
        var args=new ArrayList<String>();for(String token:form.path){String value=token.startsWith("$")?values.get(Integer.parseInt(token.substring(1))):token;require(value.length()<=4000&&!value.contains("\0"),LocalText.tr("Завелике поле."));args.add(value);}
        while(!args.isEmpty()&&args.get(args.size()-1).isBlank())args.remove(args.size()-1);
        return args.toArray(String[]::new);
    }
    public Object item(UUID id,String session,String spec)throws Exception {
        require(Bukkit.isPrimaryThread(),LocalText.tr("Лише головний потік."));var sessionState=sessions.get(id);require(sessionState!=null&&sessionState.id.equals(session)&&sessionState.expires>m.s.now(),LocalText.tr("Сесію завершено."));Player p=Bukkit.getPlayer(id);require(p!=null&&p.isOnline(),LocalText.tr("Гравець недоступний."));
        boolean allowed=assets(actor(p)).stream().anyMatch(r->spec.equals(string(r,"spec")));
        if(!allowed){String market=c.current(p);m.access(market,Department.GOODS,place(p));allowed=m.db.one("SELECT id FROM cm_orders WHERE market=? AND spec=? AND state='OPEN'",market,spec)!=null||m.db.one("SELECT id FROM cm_auctions WHERE market=? AND spec=? AND state='OPEN'",market,spec)!=null;}
        require(allowed,LocalText.tr("Предмет недоступний для перегляду."));return ItemTransactions.prototype(string(m.spec(spec),"item"));
    }
    public Map<String,Object> request(UUID id,String session,String action,String section,String form,List<String> values,String token,int page){
        var current=sessions.get(id);if(action.equals("view")&&current!=null&&current.id.equals(session)){String initial=initialSections.remove(session);if(initial!=null)section=initial;}
        return render(id,session,action,section,form,values,token,page);
    }
    void civicRead(Player p,String kind,String id)throws Exception {
        String[] args=switch(kind){case "NOTICE"->new String[]{"board","read",id};case "EVENT"->new String[]{"board","event",id};case "FISHING"->new String[]{"fishing","view",id};case "PAGE"->new String[]{"board","list",id};case "REVIEW"->new String[]{"board","review"};default->throw new IllegalArgumentException(LocalText.tr("Невідома дія екрана."));};
        c.extensions.civic.read(p,args);
    }
    void reading(Map<String,Object> output,Player p,String kind,String id)throws Exception {
        if(kind.equals("EVENT")&&string(m.fairs.fair(id),"kind").equals("FISHING"))kind="FISHING";
        output.put("readId",id);output.put("readKind",kind);
        if(kind.equals("FISHING")){var event=m.fishing.event(id);output.put("readCanJoin",Set.of("ANNOUNCED","ACTIVE").contains(string(event,"state"))&&number(event,"ends")>m.s.now()&&m.db.one("SELECT person FROM cm_fish_scores WHERE event=? AND person=?",id,p.getUniqueId().toString())==null);}
    }
    private Map<String,Object> render(UUID id,String session,String action,String section,String form,List<String> values,String token,int page){
 try(var languageScope=LocalText.useMenu(id)){

        require(Bukkit.isPrimaryThread(),LocalText.tr("Лише головний потік сервера."));Player p=Bukkit.getPlayer(id);require(p!=null&&p.isOnline()&&p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Гравець недоступний."));
        var current=sessions.get(id);require(current!=null&&current.id.equals(session)&&current.expires>m.s.now(),LocalText.tr("Екран прострочений. /market menu"));
        sessions.put(id,new Session(session,m.s.now()+600000,current.focus));require(values.size()<=20&&values.stream().mapToInt(String::length).sum()<=8000,LocalText.tr("Завелика форма."));
        var output=new LinkedHashMap<String,Object>();output.put("locale",CommandLanguage.menuLocale(id));output.put("session",session);output.put("section",section);if(!current.focus.isEmpty())output.put("focusSpec",current.focus);var messages=new ArrayList<String>();
        try {
            long last=throttles.getOrDefault(id,0L);require(m.s.now()-last>=200,LocalText.tr("Зачекайте перед наступним запитом."));throttles.put(id,m.s.now());c.towny.sync();
            if(action.equals("prepare")){Form schema=FORMS.get(form);require(schema!=null,LocalText.tr("Невідома форма."));require(!schema.section.equals("admin")||actor(p).admin(),LocalText.tr("Потрібне право адміністратора."));c.confirmations.remove(id);c.extensions.pending.remove(id);c.uiMessages=messages;try{String[] args=command(schema,values);String kind=switch(form){case "board-read"->"NOTICE";case "board-event"->"EVENT";case "fishing-view"->"FISHING";case "board-page"->"PAGE";case "board-review"->"REVIEW";default->"";};if(!kind.isEmpty()){String related=values.isEmpty()?"queue":values.get(0);civicRead(p,kind,related);reading(output,p,kind,related);}else if(args[0].equals("@vassal"))c.extensions.prepareVassal(p,args);else c.onCommand(p,c.plugin.getCommand("market"),"market",args);}finally{c.uiMessages=null;}}
            else if(action.equals("civicRead")){
                require(values.size()==1,LocalText.tr("Виберіть оголошення або подію."));c.uiMessages=messages;try{civicRead(p,form,values.get(0));reading(output,p,form,values.get(0));}finally{c.uiMessages=null;}
            }
            else if(action.equals("mailSend")){
                require(values.size()==1,LocalText.tr("Виберіть фізичний зразок."));var sample=m.fairs.mail.sample(values.get(0));require(p.getUniqueId().toString().equals(string(sample,"actor")),LocalText.tr("Це зразок іншого гравця."));
                c.confirmations.remove(id);c.extensions.pending.remove(id);c.uiMessages=messages;
                try{c.onCommand(p,c.plugin.getCommand("market"),"market",new String[]{"fair","letter","confirm",string(sample,"fair"),Long.toString(number(sample,"version")),values.get(0)});}finally{c.uiMessages=null;}
            }
            else if(action.equals("confirm")){c.uiMessages=messages;try{var old=c.confirmations.get(id);var extra=c.extensions.pending.get(id);String mode=old!=null&&old.id().equals(token)?"confirm":extra!=null&&extra.id().equals(token)?"extension_confirm":"";require(!mode.isEmpty(),LocalText.tr("Підтвердження прострочене."));c.onCommand(p,c.plugin.getCommand("market"),"market",new String[]{mode,token});}finally{c.uiMessages=null;}}
            else if(action.equals("document")||action.equals("sign")){
                require(values.size()==1&&Set.of("TREATY","VASSAL","VASSAL2").contains(form),LocalText.tr("Виберіть договір."));String related=values.get(0);var doc=form.equals("TREATY")?m.treaties.document(related):form.equals("VASSAL2")?m.documents.vassalBuilder(Long.parseLong(related)):m.documents.vassal(related);
                if(action.equals("sign")){String result=form.equals("TREATY")?m.treaties.accept(actor(p),related,doc.version(),token):form.equals("VASSAL2")?m.documents.acceptBuilder(actor(p),Long.parseLong(related),doc.version(),token):m.documents.acceptVassal(actor(p),related,doc.version(),token);messages.add(LocalText.tr("Підпис збережено: ")+result);}
                else {var shown=m.documents.view(actor(p),doc,page);output.put("document",Map.of("kind",form,"id",related,"version",shown.version(),"page",shown.page(),"pages",shown.pages(),"token",shown.token()));messages.addAll(shown.lines());}
            }else require(action.equals("view"),LocalText.tr("Невідома дія екрана."));
            String market="";try{market=c.current(p);}catch(IllegalArgumentException ignored){}
            output.put("market",market);output.put("marketName",market.isEmpty()?LocalText.tr("Поза ринком"):string(m.market(market),"name"));output.put("now",m.s.now());
            var departments=new ArrayList<Map<String,Object>>();for(var d:Department.values()){String reason="";try{m.access(market,d,place(p));}catch(Exception ex){reason=market.isEmpty()?LocalText.tr("Відвідайте будівлю цього відділу"):ex.getMessage();}departments.add(Map.of("id",d.name().toLowerCase(Locale.ROOT),"open",reason.isEmpty(),"reason",reason));}output.put("departments",departments);
            String[] read=switch(section){case "goods"->new String[]{"book"};case "fx"->new String[]{"exchange","rates"};case "jobs"->new String[]{"jobs"};case "credit"->new String[]{"loan","offers"};case "assets"->new String[]{"assets"};case "mail"->new String[]{"mail","status"};default->new String[]{"orders"};};
            if(action.equals("view")){c.uiMessages=messages;try{if(section.equals("notice"))c.extensions.civic.read(p,new String[]{"board","list",Integer.toString(page)});else if(section.equals("fishing"))c.extensions.civic.read(p,new String[]{"fishing","list"});else c.read(p,read[0],read);}catch(IllegalArgumentException ex){messages.add(ex.getMessage());}finally{c.uiMessages=null;}}
            var rows=new ArrayList<Map<String,Object>>();String person=p.getUniqueId().toString();
            if(section.equals("notice")&&!market.isEmpty()){
                int boardPage="PAGE".equals(output.get("readKind"))?Integer.parseInt(output.get("readId").toString()):page;
                for(var r:m.notices.publicEntries(market,place(p),boardPage))rows.add(Map.of("id",string(r,"id"),"label",string(r,"title"),"kind","NOTICE"));
                for(var r:m.db.query("SELECT id,title,state FROM cm_notices WHERE market=? AND author=? AND state='PENDING' AND ends>? ORDER BY created DESC LIMIT 20",market,person,m.s.now()))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("На перевірці: ")+string(r,"title"),"kind","NOTICE"));
                if(m.notices.moderator(actor(p),market))for(var r:m.db.query("SELECT id,title FROM cm_notices WHERE market=? AND state='PENDING' AND ends>? ORDER BY created LIMIT 50",market,m.s.now()))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Перевірити: ")+string(r,"title"),"kind","NOTICE"));
            }
            if(section.equals("fishing")||section.equals("notice")){
                var events=section.equals("fishing")?m.db.query("SELECT id,name,state,kind FROM cm_fairs WHERE kind='FISHING' AND state IN('ANNOUNCED','ACTIVE','FINISHED') ORDER BY starts DESC LIMIT 50"):m.db.query("SELECT id,name,state,kind FROM cm_fairs WHERE market=? AND state IN('ANNOUNCED','ACTIVE') AND ends>? ORDER BY starts LIMIT 50",market,m.s.now());
                for(var r:events)rows.add(Map.of("id",string(r,"id"),"label",string(r,"kind")+" · "+string(r,"name")+" · "+string(r,"state"),"kind","CIVIC_EVENT"));
            }
            if(section.equals("fishing")&&!market.isEmpty())try{m.manage(actor(p),market);for(var r:m.db.query("SELECT id,name FROM cm_fairs WHERE market=? AND kind='FISHING' AND state='DRAFT'",market))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Чернетка: ")+string(r,"name"),"kind","CIVIC_EVENT"));}catch(IllegalArgumentException ignored){}
            if(section.equals("assets"))for(var r:assets(actor(p)))rows.add(Map.of("id",string(r,"id"),"label",string(r,"owner")+" · "+string(r,"category")+" · "+number(r,"quantity")+" "+string(r,"code"),"kind","ASSET","spec",string(r,"spec")));
            if(section.equals("mine"))for(var r:m.db.query("SELECT id,side,remaining FROM cm_orders WHERE owner=? AND state='OPEN'",person))rows.add(Map.of("id",string(r,"id"),"label",string(r,"side")+LocalText.tr(" · залишок ")+number(r,"remaining"),"kind","ORDER"));
            if(section.equals("goods")&&!market.isEmpty())try{m.access(market,Department.GOODS,place(p));for(var r:m.db.query("SELECT id,side,remaining,spec,price,code FROM cm_orders WHERE market=? AND department='GOODS' AND state='OPEN' AND ends>? ORDER BY price,priority LIMIT 50",market,m.s.now()))rows.add(Map.of("id",string(r,"id"),"label",string(r,"side")+" · "+number(r,"remaining")+" × "+format(number(r,"price"))+" "+string(r,"code"),"kind","LOT","spec",string(r,"spec")));}catch(IllegalArgumentException ignored){}
            if(section.equals("mine")){
                for(var r:m.db.query("SELECT id,state FROM cm_contracts WHERE customer=? OR worker=?",person,person))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Робота: ")+string(r,"state"),"kind","CONTRACT"));
                for(var r:m.db.query("SELECT id,state FROM cm_loans WHERE borrower=? OR lender=?",person,person))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Позика: ")+string(r,"state"),"kind","LOAN"));
                for(var r:m.db.query("SELECT id FROM proposals WHERE required LIKE ?","%"+person+"%"))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Переглянути васальні умови"),"kind","VASSAL"));
                for(var r:m.db.query("SELECT id FROM cm_treaties"))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Переглянути торгові умови"),"kind","TREATY"));
                if(m.s.contractBuilder!=null)for(var r:m.s.contractBuilder.list())rows.add(Map.of("id",""+number(r,"id"),"label",LocalText.tr("Конструктор васалітету: ")+string(r,"status"),"kind","VASSAL2"));
            }
            if(section.equals("goods")&&!market.isEmpty()){try{m.access(market,Department.GOODS,place(p));for(var r:m.db.query("SELECT id,state,bid,code,spec FROM cm_auctions WHERE market=? AND state='OPEN'",market))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Аукціон: ")+format(number(r,"bid"))+" "+string(r,"code"),"kind","AUCTION","spec",string(r,"spec")));}catch(IllegalArgumentException ignored){}}
            if(section.equals("manage")&&!market.isEmpty()){try{m.manage(actor(p),market);for(var r:m.db.query("SELECT id,reason FROM cm_procurements WHERE market=?",market))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Закупівля: ")+string(r,"reason"),"kind","PROCUREMENT"));for(var r:m.db.query("SELECT id,name,state FROM cm_fairs WHERE market=?",market))rows.add(Map.of("id",string(r,"id"),"label",LocalText.tr("Ярмарок: ")+string(r,"name")+" "+string(r,"state"),"kind","FAIR"));}catch(IllegalArgumentException ignored){}}
            if(section.equals("mail"))for(var sample:m.db.query("SELECT s.id,f.market,f.name FROM cm_mail_samples s JOIN cm_fairs f ON f.id=s.fair WHERE s.actor=? AND s.state='ISSUED' AND s.version=f.version AND s.expires>? AND (f.state='DRAFT' OR f.kind='FISHING' AND f.state='ANNOUNCED')",person,m.s.now()))try{
                m.manage(actor(p),string(sample,"market"));rows.add(Map.of("id",string(sample,"id"),"kind","MAIL_SAMPLE","label",LocalText.tr("Розіслати за зразком: ")+string(sample,"name")+" · "+m.fairs.mail.targets(string(sample,"id")).size()+LocalText.tr(" листівок")));
            }catch(IllegalArgumentException ignored){}
            output.put("rows",rows.stream().limit(100).toList());
            output.put("forms",FORMS.values().stream().filter(f->f.section.equals(section)).filter(f->!f.section.equals("admin")||actor(p).admin()).map(Form::view).toList());
            var pending=c.confirmations.get(id);var extra=c.extensions.pending.get(id);if(pending!=null&&pending.expires()>m.s.now())output.put("confirmation",pending.id());else if(extra!=null&&extra.expires()>m.s.now())output.put("confirmation",extra.id());
            if(section.equals("mail"))messages.add(LocalText.tr("Запрошення доставляються у скриньки Contact. Зразок: 1 папір; копії: 1 папір на адресата. Розсилка починається тільки після окремого підтвердження фізичного зразка."));
            if(section.equals("fx")&&!market.isEmpty())try{
                var pairs=m.charts.pairs(market,place(p));output.put("pairs",pairs);long hours=action.equals("view")&&!values.isEmpty()?Long.parseLong(values.get(0)):24;String pair=action.equals("view")&&values.size()>1?values.get(1):"";if(!pairs.contains(pair))pair=pairs.isEmpty()?"":pairs.get(0);if(!pair.isEmpty())output.put("chart",m.charts.chart(market,place(p),pair,hours));
                if(!pair.isEmpty()){String[] codes=pair.split("/");output.put("depth",m.db.query("SELECT side,base,code,price,SUM(remaining) AS quantity FROM cm_orders WHERE market=? AND department='FX' AND base=? AND code=? AND state='OPEN' AND ends>? GROUP BY side,base,code,price ORDER BY price LIMIT 100",market,codes[0],codes[1],m.s.now()));}
            }catch(IllegalArgumentException unavailable){messages.add(unavailable.getMessage());}
        }catch(Exception ex){output.put("error",ex.getMessage()==null?LocalText.tr("Дію не завершено"):ex.getMessage());}
        output.put("messages",messages.stream().limit(100).map(s->s.length()>1800?s.substring(0,1800)+"…":s).toList());return output;

 }
}
}
