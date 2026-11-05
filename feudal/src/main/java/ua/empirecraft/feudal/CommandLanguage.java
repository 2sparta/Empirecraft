package ua.empirecraft.feudal;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

/** Server-side, grammar-aware command aliases; names, amounts, codes and free text are never translated. */
public final class CommandLanguage implements Listener,CommandExecutor,TabCompleter {
    static final class Node {final Map<String,Node> words=new LinkedHashMap<>();Node argument;}
    static final Node ROOT=new Node();
    static final Map<String,String[]> WORDS=new LinkedHashMap<>();
    static { load(); grammar(); }
    private final FeudalPlugin plugin;
    private static volatile CommandPreferences preferences;
    CommandLanguage(FeudalPlugin plugin,CommandPreferences preferences){this.plugin=plugin;CommandLanguage.preferences=preferences;}
    public static String locale(CommandSender sender){return sender instanceof Player p&&preferences!=null?preferences.get(p.getUniqueId()):"en";}
    public static String locale(UUID person){return person!=null&&preferences!=null?preferences.get(person):"en";}
    static void reset(){preferences=null;}
    static void load(){
        String dictionary="""
army|рекрути|рекруты
agreement|погодження|согласование
inverse|зворотна_книга|обратная_книга
license_apply|запит_ліцензії|запрос_лицензии
license_requests|запити_ліцензій|запросы_лицензий
extend|продовжити_строк|продлить_срок
view|переглянути|посмотреть
orders|замовлення|заказы
recovery|пункт_повернення|пункт_возврата
suspend|призупинити|приостановить
language|мова|язык
apply|заявка|заявка
approve|схвалити|одобрить
arbitrate|арбітраж|арбитраж
building|будівля|здание
claims|чанки|чанки
department|відділ|отдел
dispute|спір|спор
host|допуск_міста|допуск_города
issuer|емітент|эмитент
license|ліцензія|лицензия
official_contribute|офіційний_внесок|официальный_взнос
official_deposit|фонд_внести|фонд_внести
official_withdraw|фонд_зняти|фонд_снять
payment|оплата|оплата
permit|допуск|допуск
reduce|зменшити|уменьшить
sell|продати|продать
staff|персонал|персонал
tariff|тариф|тариф
auction|аукціон|аукцион
procurement|закупівля|закупка
treaty|угода|соглашение
fair|ярмарок|ярмарка
founding|заснування|основание
assets|активи|активы
jobs|роботи|работы
loan|позика|заём
book|книга|книга
share_info|поділитися_даними|поделиться_данными
inspect_lot|оглянути_лот|осмотреть_лот
extension_confirm|підтвердити_дію|подтвердить_действие
manual|вручну|вручную
take|взяти|взять
repay|повернути_борг|погасить_долг
foreclose|стягнути|взыскать
supply|постачання|поставка
work|робота|работа
pause|пауза|пауза
stop|зупинити|остановить
bid|ставка|ставка
buyout|викуп|выкуп
create_asset|продати_актив|продать_актив
publish|опублікувати|опубликовать
letter|лист|письмо
edit|редагувати|редактировать
manage|керувати|управлять
mail|пошта|почта
funds|фонди|фонды
official|офіційний|официальный
town|місто|город
nation|нація|нация
resident|житель|житель
plot|ділянка|участок
towny|тауні|тауни
townyadmin|адмінміст|админгородов
townyworld|світміст|миргородов
invite|запрошення|приглашение
currency|валюта|валюта
trade|торг|торг
order|замовлення|заказ
commerce|торгівля|торговля
statedebug|налагодження|отладка
help|допомога|помощь
balance|баланс|баланс
deposit|внести|внести
withdraw|зняти|снять
banker|банкір|банкир
add|додати|добавить
remove|прибрати|убрать
gold|золото|золото
collect|отримати|получить
pending|незавершені|незавершённые
inspect|перевірити|проверить
resolve|узгодити|согласовать
applied|виконано|выполнено
failed|скасовано|отменено
info|інфо|инфо
list|список|список
pay|сплатити|оплатить
market|ринок|рынок
menu|меню|меню
chart|графік|график
fees|комісії|комиссии
settings|налаштування|настройки
redemptions|погашення|погашения
log|журнал|журнал
history|історія|история
value|курс|курс
reserve|резерв|резерв
audit|аудит|аудит
policy|політика|политика
create|створити|создать
print|друк|печать
mint|карбувати|чеканить
burn|спалити|сжечь
upgrade|оновити|обновить
redeem|погасити|погасить
exchange|обмін|обмен
quote|розрахувати|рассчитать
convert|обміняти|обменять
vault|зовнішнійбанк|внешнийбанк
treasury|казна|казна
bank|банк|банк
state|держава|государство
fief|вотчина|вотчина
castle|замок|замок
union|унія|уния
confed|конфедерація|конфедерация
federation|федерація|федерация
vassal|васал|вассал
feudal|феодал|феодал
title|титул|титул
diplomacy|дипломатія|дипломатия
dynasty|династія|династия
estateinfo|володіння|владение
provision|провізія|провизия
status|стан|состояние
progress|прогрес|прогресс
advance|розвиток|развитие
preview|перегляд|просмотр
submit|подати|подать
confirm|підтвердити|подтвердить
cancel|скасувати|отменить
charter|грамота|грамота
capital|столиця|столица
government|правління|правление
territory|устрій|устройство
budget|бюджет|бюджет
vote|голос|голос
execute|застосувати|применить
certify|засвідчити|засвидетельствовать
decisions|рішення|решения
decision|рішення_інфо|решение_инфо
releasevote|голос_звільнення|голос_освобождения
adminvote|голос_адміністрації|голос_администрации
unionvote|голос_союзу|голос_союза
exitvote|голос_виходу|голос_выхода
councilvote|голос_ради|голос_совета
vassalvote|голос_васалітету|голос_вассалитета
advancevote|голос_розвитку|голос_развития
province|провінція|провинция
election|вибори|выборы
open|відкрити|открыть
candidate|кандидат|кандидат
close|закрити|закрыть
resume|продовжити|продолжить
abort|відкотити|откатить
propose|запропонувати|предложить
sign|підписати|подписать
release|звільнити|освободить
pos1|точка1|точка1
pos2|точка2|точка2
review|оцінити|оценить
vassals|васали|вассалы
liege|сюзерен|сюзерен
tree|дерево|дерево
contract|договір|договор
amend|змінити|изменить
terminate|припинити|прекратить
terms|умови|условия
powers|повноваження|полномочия
accept|прийняти|принять
reject|відхилити|отклонить
contribute|внесок|взнос
join|вступити|вступить
leave|вийти|выйти
debt|борг|долг
cast|проголосувати|проголосовать
offer|пропозиція|предложение
petition|прохання|прошение
offers|пропозиції|предложения
appoint|призначити|назначить
independence|незалежність|независимость
tribute|данина|дань
found|заснувати|основать
admin|адмін|админ
check|перевірка|проверка
import|імпорт|импорт
backup|копія|копия
setliege|встановити_сюзерена|установить_сюзерена
grant|видати|выдать
revoke|відкликати|отозвать
break|розірвати|расторгнуть
name|назва|название
nominate|спадкоємець|наследник
abdicate|зректися|отречься
show|показати|показать
at|координати|координаты
marker|позначка|метка
remove-marker|прибрати_позначку|убрать_метку
mine|мої|мои
deliver|доставити|доставить
recover|відновити|восстановить
coins|монети|монеты
debug|відлагодження|отладка
next|далі|дальше
setstage|встановити_рівень|установить_уровень
ready|готовність|готовность
new|нове|новый
here|тут|здесь
online|онлайн|онлайн
reslist|жителі|жители
ranklist|ранги|ранги
allylist|союзники|союзники
enemylist|вороги|враги
outlawlist|злочинці|преступники
plotgrouplist|групи_ділянок|группы_участков
plots|ділянки|участки
spawn|спавн|спавн
outpost|аванпост|аванпост
claim|приват|приват
unclaim|зняти_приват|снять_приват
kick|вигнати|выгнать
set|встановити|установить
toggle|перемкнути|переключить
rank|ранг|ранг
delete|видалити|удалить
merge|об'єднати|объединить
reclaim|повернути|вернуть
buytown|купити_місто|купить_город
forsale|продати|продать
notforsale|не_продавати|не_продавать
fs|продаж|продажа
nfs|зняти_продаж|снять_продажу
ban|заборона|запрет
outlaw|злочинець|преступник
jail|тюрма|тюрьма
unjail|випустити|выпустить
purge|очистити|очистить
buy|купити|купить
cede|передати|передать
say|сказати|сказать
mayor|мер|мэр
king|король|король
leader|лідер|лидер
enemy|ворог|враг
ally|союзник|союзник
sanctiontown|санкції|санкции
townlist|міста|города
trust|довіра|доверие
trusttown|довіра_місту|доверие_городу
bankhistory|історія_банку|история_банка
baltop|багатії|богачи
takeoverclaim|захопити|захватить
board|оголошення|объявление
homeblock|головний_чанк|главный_чанк
perm|права|права
taxes|податок|налог
plottax|податок_ділянки|налог_участка
shoptax|податок_магазину|налог_магазина
embassytax|податок_посольства|налог_посольства
plotprice|ціна_ділянки|цена_участка
shopprice|ціна_магазину|цена_магазина
embassyprice|ціна_посольства|цена_посольства
spawncost|ціна_спавну|цена_спавна
tag|тег|тег
surname|прізвище|фамилия
taxpercentcap|межа_податку|предел_налога
primaryjail|головна_тюрма|главная_тюрьма
mapcolor|колір_карти|цвет_карты
conqueredtax|податок_завойованих|налог_завоёванных
pvp|пвп|пвп
public|публічний|публичный
explosion|вибухи|взрывы
fire|вогонь|огонь
mobs|моби|мобы
taxpercent|податок_відсоток|налог_процент
nationzone|зона_нації|зона_нации
neutral|нейтралітет|нейтралитет
peaceful|мирний|мирный
rect|квадрат|квадрат
circle|коло|круг
auto|авто|авто
fill|заповнити|заполнить
all|усі|все
sent|надіслані|отправленные
received|отримані|полученные
deny|відмовити|отказать
removeall|прибрати_всі|убрать_все
bonus|бонус|бонус
friend|друг|друг
clear|очистити_список|очистить_список
clearlist|стерти_список|стереть_список
reset|скинути|сбросить
mode|режим|режим
about|опис|описание
plotlist|список_ділянок|список_участков
paybail|сплатити_заставу|внести_залог
bail|застава|залог
plotborder|межа_ділянки|граница_участка
constantplotborder|постійна_межа|постоянная_граница
townborder|межа_міста|граница_города
ignoreplots|ігнор_ділянок|игнор_участков
bordertitles|назви_меж|названия_границ
townclaim|автоприват|автоприват
townunclaim|авторозприват|авторасприват
plotgroup|група_ділянок|группа_участков
map|карта|карта
spy|стеження|слежение
infotool|інфоінструмент|инфоинструмент
adminbypass|обхід_адміна|обход_админа
evict|виселити|выселить
group|група|группа
district|район|район
jailcell|камера|камера
hud|панель|панель
gui|вікно|окно
shop|магазин|магазин
embassy|посольство|посольство
arena|арена|арена
wilds|дикі_землі|дикие_земли
inn|готель|гостиница
farm|ферма|ферма
minjoindays|мін_днів|мин_дней
maxjoindays|макс_днів|макс_дней
taxed|оподаткування|налогообложение
prices|ціни|цены
top|рейтинг|рейтинг
time|час|время
universe|всесвіт|вселенная
version|версія|версия
v|верс|верс
switches|перемикачі|переключатели
itemuse|використання|использование
allowedblocks|дозволені_блоки|разрешённые_блоки
wildsblocks|дикі_блоки|дикие_блоки
plotclearblocks|блоки_очистки|блоки_очистки
residents|населення|население
land|землі|земли
towns|міст|городов
founded|засновано|основано
upkeep|утримання|содержание
bankrupt|банкрут|банкрот
ruined|руїни|руины
by|за|по
townblocks|чанки|чанки
reload|перезавантажити|перезагрузить
database|база|база
save|зберегти|сохранить
load|завантажити|загрузить
mysqldump|дамп|дамп
newday|нова_доба|новые_сутки
newhour|нова_година|новый_час
checkperm|перевірити_право|проверить_право
checkoutposts|перевірити_аванпости|проверить_аванпосты
tpplot|телепорт_ділянка|телепорт_участок
givebonus|дати_бонус|дать_бонус
giveplots|дати_ділянки|дать_участки
townyperms|права_тауні|права_тауни
install|встановлення|установка
eco|економіка|экономика
rename|перейменувати|переименовать
meta|метадані|метаданные
transfer|перенести|перенести
recheck|перевірити_знову|проверить_снова
forcemerge|примусово_об'єднати|принудительно_объединить
leavenation|вийти_з_нації|выйти_из_нации
unruin|відновити_місто|восстановить_город
settownlevel|рівень_міста|уровень_города
giveboughtblocks|куплені_чанки|купленные_чанки
foundingdate|дата_заснування|дата_основания
founder|засновник|основатель
nationzoneoverride|розмір_зони|размер_зоны
forcepvp|примусове_пвп|принудительное_пвп
forcedisablepvp|заборонити_пвп|запретить_пвп
forcemobs|примусові_моби|принудительные_мобы
unlimitedclaims|необмежені_привати|неограниченные_приваты
allowedtowar|дозвіл_війни|разрешение_войны
conquered|завойований|завоёванный
visibleontoplists|видимість_рейтингів|видимость_рейтингов
wildernessuse|дика_місцевість|дикая_местность
regenerations|відновлення|восстановление
devmode|режим_розробника|режим_разработчика
townwithdraw|вивід_міста|вывод_города
nationwithdraw|вивід_нації|вывод_нации
npc|нпс|нпс
claimedat|дата_привату|дата_привата
listgroups|список_груп|список_групп
addperm|дати_право|дать_право
removeperm|зняти_право|снять_право
townrank|ранг_міста|ранг_города
nationrank|ранг_нації|ранг_нации
addrank|додати_ранг|добавить_ранг
removerank|прибрати_ранг|убрать_ранг
resetbanks|скинути_банки|сбросить_банки
depositall|внести_всім|внести_всем
modern|сучасна|современная
serveraccount|рахунок_сервера|счёт_сервера
claimable|приватизація|приватизация
usingtowny|захист_тауні|защита_тауни
warallowed|війни|войны
friendlyfire|дружній_вогонь|дружественный_огонь
forceexplosion|примусові_вибухи|принудительные_взрывы
forcefire|примусовий_вогонь|принудительный_огонь
townmobs|моби_міста|мобы_города
wildernessmobs|моби_диких|мобы_диких
worldmobs|моби_світу|мобы_мира
revertunclaim|відкат_привату|откат_привата
revertentityexpl|відкат_вибухів_істот|откат_взрывов_существ
revertblockexpl|відкат_вибухів_блоків|откат_взрывов_блоков
plotcleardelete|очистка_блоків|очистка_блоков
unclaimblockdelete|видалення_блоків|удаление_блоков
unclaimentitydelete|видалення_істот|удаление_существ
usedefault|типові|стандартные
wildperm|права_диких|права_диких
wildignore|винятки_диких|исключения_диких
wildregen|відновлення_диких|восстановление_диких
wildname|назва_диких|название_диких
on|увімк|вкл
off|вимк|выкл
yes|так|да
no|ні|нет
tax|податки|налоги
config|конфіг|конфиг
lang|мова|язык
perms|дозволи|разрешения
big|велика|большая
titles|титули|титулы
domain|область|область
outsider|сторонній|посторонний
build|будувати|строить
destroy|ламати|ломать
switch|взаємодія|взаимодействие
""";
        for(String row:dictionary.split("\n")){String[] v=row.split("\\|",-1);if(v.length==3)WORDS.put(v[0],new String[]{v[1],v[2]});}
    }
    static void choices(String prefix,String words){for(String word:words.split(" "))if(!word.isEmpty())path(prefix.isEmpty()?word:prefix+" "+word);}
    static void path(String route){Node n=ROOT;for(String word:route.split(" "))if(word.equals("*")){if(n.argument==null)n.argument=new Node();n=n.argument;}else n=n.words.computeIfAbsent(word,k->new Node());}
    static String literal(Node n,String value){if(n==null)return null;String lower=value.toLowerCase(Locale.ROOT);if(lower.equals("diposit")&&n.words.containsKey("deposit"))return "deposit";for(String word:n.words.keySet()){
        if(word.equalsIgnoreCase(lower))return word;var v=WORDS.get(word);if(v!=null&&(v[0].equalsIgnoreCase(lower)||v[1].equalsIgnoreCase(lower)))return word;
    }return null;}
    public static String[] normalize(String root,String[] args){String[] full=new String[args.length+1];full[0]=canonicalRoot(root);System.arraycopy(args,0,full,1,args.length);return Arrays.copyOfRange(normalize(full),1,full.length);}
    static String canonicalRoot(String root){String v=root.toLowerCase(Locale.ROOT);if(v.contains(":"))v=v.substring(v.indexOf(':')+1);return switch(v){case "lang","мова","язык"->"language";case "t"->"town";case "n","nat"->"nation";case "res","p","player"->"resident";case "ta"->"townyadmin";case "tw"->"townyworld";case "cu","валюта"->"currency";case "invites"->"invite";case "торг"->"trade";case "замовлення"->"order";default->{String key=literal(ROOT,v);yield key==null?v:key;}};}
    static String[] normalize(String[] input){String[] result=input.clone();Node n=ROOT;for(int i=0;i<result.length;i++){
        String v=i==0?canonicalRoot(result[i]):result[i],word=literal(n,v);
        if(word!=null){result[i]=word;n=n.words.get(word);}else if(n!=null && n.argument!=null)n=n.argument;else break;
    }return result;}
    static String local(String word,String locale){var v=WORDS.get(word);if(v==null||locale==null||locale.toLowerCase(Locale.ROOT).startsWith("en"))return word;return v[locale.toLowerCase(Locale.ROOT).startsWith("ru")?1:0];}
    static Node node(String[] input){Node n=ROOT;for(String value:normalize(input)){if(n==null)return null;n=n.words.containsKey(value)?n.words.get(value):n.argument;}return n;}
    static List<String> suggest(String root,String[] args,String locale){if(args.length==0)return suggest(root,new String[]{""},locale);String[] prefix=new String[args.length];prefix[0]=canonicalRoot(root);if(args.length>1)System.arraycopy(args,0,prefix,1,args.length-1);Node n=node(prefix);if(n==null)return List.of();String partial=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);List<String> out=new ArrayList<>();
        for(String word:n.words.keySet()){String value=local(word,locale);if(value.toLowerCase(Locale.ROOT).startsWith(partial))out.add(value);else if(!partial.isEmpty()&&word.startsWith(partial))out.add(word);}return out;
    }
    public void register(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        plugin.getCommand("language").setExecutor(this);plugin.getCommand("language").setTabCompleter(this);
        for(String root:ROOT.words.keySet()){if(root.equals("market")||root.equals("language"))continue;String[] words=WORDS.get(root);if(words==null)continue;for(String word:new LinkedHashSet<>(List.of(words))){if(word.equals("валюта")||word.equals("торг")||word.equals("замовлення"))continue;PluginCommand c=plugin.getCommand(word);if(c!=null){c.setExecutor(this);c.setTabCompleter(this);}}}
    }
    public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(canonicalRoot(label).equals("language")){
            if(!(sender instanceof Player p)){sender.sendMessage("/language en|uk|ru — players only.");return true;}
            if(!p.hasPermission("empirecraft.feudal.use")){sender.sendMessage("You do not have permission to change your language.");return true;}
            try{if(args.length==0){sender.sendMessage(switch(locale(sender)){case "ru"->"Язык команд: ru. /language en|uk|ru";case "uk"->"Мова команд: uk. /language en|uk|ru";default->"Command language: en. /language en|uk|ru";});return true;}
                if(args.length!=1)throw new IllegalArgumentException("/language en|uk|ru");preferences.set(p.getUniqueId(),args[0]);
                sender.sendMessage(switch(locale(p)){case "ru"->"Язык команд: русский. Настройка сохранена. /language en|uk|ru";case "uk"->"Мова команд: українська. Налаштування збережено. /language en|uk|ru";default->"Command language: English. Preference saved. /language en|uk|ru";});
            }catch(IllegalArgumentException ex){sender.sendMessage(ex.getMessage());}return true;
        }
        String root=canonicalRoot(label);PluginCommand c=Bukkit.getPluginCommand(root);if(c==null)return true;return c.execute(sender,root,normalize(root,args));}
    public List<String> onTabComplete(CommandSender sender,Command c,String label,String[] args){return complete(sender,canonicalRoot(label),args);}
    List<String> complete(CommandSender sender,String root,String[] args){if(args.length==0)return complete(sender,root,new String[]{""});if(root.equals("language"))return args.length<=1?List.of("en","uk","ru").stream().filter(v->args.length==0||v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();String locale=locale(sender);String[] canonical=normalize(root,args);var out=new LinkedHashSet<>(suggest(root,args,locale));PluginCommand original=Bukkit.getPluginCommand(root);
        if(Bukkit.isPrimaryThread()&&original!=null&&original.getExecutor()!=this&&original.testPermissionSilent(sender)){
            // Delegate dynamic values (town names, players, currencies) to the owning plugin.
            var prefix=new String[canonical.length];prefix[0]=root;if(canonical.length>1)System.arraycopy(canonical,0,prefix,1,canonical.length-1);Node n=node(prefix);
            for(String v:original.tabComplete(sender,root,canonical)){String value=n!=null&&n.words.containsKey(v)?local(v,locale):v;String partial=args[args.length-1].toLowerCase(Locale.ROOT);if(value.toLowerCase(Locale.ROOT).startsWith(partial))out.add(value);else if(v.toLowerCase(Locale.ROOT).startsWith(partial))out.add(v);}
        }return List.copyOf(out);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){String text=e.getMessage();String[] args=text.substring(1).split(" ",-1);if(args[0].contains(":")&&!args[0].startsWith("towny:")&&!args[0].startsWith("empirecraftfeudal:"))return;if(!ROOT.words.containsKey(canonicalRoot(args[0])))return;String[] translated=normalize(args);if(!Arrays.equals(args,translated))e.setMessage("/"+String.join(" ",translated));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void tab(TabCompleteEvent e){if(!(e.getSender() instanceof Player p))return;String text=e.getBuffer();if(!text.startsWith("/"))return;String[] words=text.substring(1).split(" ",-1);String root=canonicalRoot(words[0]);if(words.length<2||!ROOT.words.containsKey(root))return;e.setCompletions(complete(p,root,Arrays.copyOfRange(words,1,words.length)));}
    static String text(String message,String locale){
        var matcher=java.util.regex.Pattern.compile("/(?:cu|currency|t|town|statedebug|trade|order|commerce|market|language)(?: [\\p{L}0-9_<>:|.\\[\\]-]+)*").matcher(message);var out=new StringBuffer();
        while(matcher.find()){String[] words=matcher.group().substring(1).split(" ");Node n=ROOT;for(int i=0;i<words.length;i++){
            String canonical=i==0?canonicalRoot(words[i]):literal(n,words[i]);
            if(canonical!=null&&n!=null&&n.words.containsKey(canonical)){words[i]=local(canonical,locale);n=n.words.get(canonical);}
            else if(n!=null&&n.argument!=null)n=n.argument;else break;
        }matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement("/"+String.join(" ",words)));}matcher.appendTail(out);return out.toString();
    }
    static void grammar(){
        path("language *");
        choices("market","list info cancel reduce");
        choices("market exchange","chart inverse buy sell official permit");
        choices("market founding","status");
        choices("market contract *","accept deliver recover submit approve dispute cancel arbitrate amend terminate");
        choices("market loan","license_apply license_requests license deposit withdraw offer offers apply approve take repay foreclose mine funds extend");
        choices("market agreement","accept");
        choices("market fair letter","edit preview confirm cancel");
        choices("market treaty","view accept publish reject terminate amend book");
        for(String group:List.of("auction","procurement","fair"))choices("market "+group,"view");
        choices("market share_info","accept");
        choices("market","help menu book orders assets load recover founding share_info inspect_lot confirm extension_confirm auction procurement treaty fair exchange jobs loan contract agreement manage admin mail");
        for(var form:MarketScreens.FORMS.values())if(!form.path().get(0).startsWith("@"))path("market "+String.join(" ",form.path().stream().map(v->v.startsWith("$")?"*":v).toList()));
        choices("","market language town nation resident plot towny townyadmin townyworld invite currency trade order commerce statedebug");
        choices("currency","help market menu chart fees settings redemptions log balance gold reserve list info value audit policy create banker print mint burn pay history upgrade redeem collect exchange vault treasury");
        choices("currency reserve","deposit");path("currency reserve deposit gold * *");path("currency reserve deposit * *");
        choices("currency gold","pending inspect resolve");choices("currency gold resolve *","applied failed");
        choices("currency exchange","help quote convert");choices("currency vault","deposit withdraw");choices("currency banker","add remove");choices("currency treasury *","deposit withdraw");
        choices("trade","accept coins cancel help");choices("order","create list mine show deliver collect cancel help");choices("commerce","pending inspect recover abort");
        choices("statedebug","help info next setstage government territory ready");
        choices("town","menu treaty army help new create here list online leave reclaim reslist ranklist outlawlist plotgrouplist plots outlaw ban say spawn outpost forsale notforsale fs nfs buytown deposit withdraw rank mayor delete claim unclaim kick add set toggle cede jail unjail purge invite buy trust trusttown bankhistory baltop allylist enemylist join merge takeoverclaim treasury bank state fief castle union confed federation vassal feudal title diplomacy dynasty estateinfo provision");
        choices("town set","board mayor homeblock spawn outpost perm taxes plottax shoptax embassytax plotprice shopprice embassyprice spawncost name tag title surname taxpercentcap primaryjail mapcolor");
        choices("town toggle","pvp public explosion fire mobs taxpercent open nationzone neutral peaceful");
        for(String flag:node(new String[]{"town","toggle"}).words.keySet())choices("town toggle "+flag,"on off");
        choices("town claim","outpost auto rect circle fill");choices("town unclaim","rect circle all");
        for(String cmd:List.of("rank","outlaw","ban","trust","trusttown"))choices("town "+cmd,"add remove list");
        choices("town invite","add sent received accept deny removeall");choices("town buy","bonus");choices("town cede","plot");choices("town jail","list bail");
        for(String family:List.of("treasury","bank")){choices("town "+family,"help balance info deposit withdraw banker gold log");choices("town "+family+" banker","add remove");choices("town "+family+" gold","balance withdraw");}
        for(String family:List.of("vassal","feudal")){
            choices("town "+family,"help info domain tree list liege menu create appoint offer petition offers accept reject cancel contract terms release independence tribute history charter preview found admin");
            choices("town "+family+" tribute","status pay");choices("town "+family+" found","confirm");choices("town "+family+" admin","help preview import check backup pending setliege release vault");choices("town "+family+" admin vault *","applied failed");
        }
        choices("town title","help list info domain grant revoke");
        choices("town state","help status progress advance charter capital government territory budget vote execute certify decisions decision releasevote adminvote unionvote exitvote councilvote vassalvote advancevote history province election debug");
        choices("town state advance","preview submit confirm");choices("town state election","open candidate vote close resume abort cancel");choices("town state province","propose sign release");choices("town state vote *","yes no");choices("town state certify * *","yes no");
        choices("town state debug","help info next setstage government territory ready");
        choices("town castle","help pos1 pos2 inspect status review history");choices("town fief","info vassals liege tree contract");
        for(String family:List.of("union","confed","federation")){
            String p="town "+family;choices(p,"help create invite amend terminate charter powers status propose terms sign accept confirm reject contribute treasury join leave debt vote");choices(p+" vote","propose cast");choices(p+" vote propose * *","budget invite charter terminate");choices(p+" vote cast * *","yes no");
        }
        choices("town diplomacy","offer sign break reject show history list");choices("town dynasty","name nominate accept revoke abdicate show history");choices("town dynasty abdicate *","confirm");choices("town estateinfo","at marker remove-marker");choices("town estateinfo marker *","trade capital");choices("town estateinfo remove-marker *","trade capital");choices("town provision","status history");
        choices("nation","help new create list online leave withdraw deposit add kick delete enemy rank ranklist say set toggle invite join merge townlist allylist enemylist ally spawn sanctiontown king leader bankhistory baltop");
        choices("nation set","king leader capital taxes conqueredtax name title surname tag board spawn spawncost mapcolor taxpercentcap");choices("nation toggle","peaceful neutral public open taxpercent");
        for(String cmd:List.of("rank","enemy","sanctiontown"))choices("nation "+cmd,"add remove list");choices("nation ally","add remove sent received accept deny");choices("nation invite","sent");
        choices("resident","help list tax jail toggle set friend spawn plotlist outlawlist");choices("resident friend","add remove list clear clearlist");choices("resident jail","paybail");choices("resident set","about perm mode");choices("resident toggle","pvp fire mobs explosion plotborder constantplotborder townborder ignoreplots bordertitles townclaim townunclaim plotgroup map reset clear spy infotool adminbypass");choices("resident set mode","clear reset plotborder constantplotborder townborder ignoreplots bordertitles townclaim townunclaim plotgroup map spy infotool adminbypass");
        choices("plot","help claim unclaim forsale notforsale fs nfs evict clear set trust toggle group district jailcell info perm");
        choices("plot set","shop embassy arena wilds inn jail farm bank outpost minjoindays maxjoindays reset name perm");choices("plot toggle","pvp explosion fire mobs taxed");choices("plot group","add new create remove delete rename set toggle forsale fs notforsale nfs trust perm");choices("plot district","add new create remove delete rename");choices("plot jailcell","add remove");choices("plot perm","hud add remove gui");choices("plot trust","add remove");choices("plot group trust","add remove");choices("plot group perm","gui add remove");choices("plot group set","maxjoindays minjoindays shop embassy arena wilds inn jail farm bank perm");choices("plot group toggle","pvp explosion fire mobs taxed");
        choices("invite","accept deny list");choices("invite deny","all");
        choices("towny","help map prices top time universe version v tree spy switches itemuse allowedblocks wildsblocks plotclearblocks");choices("towny map","big hud");choices("towny top","residents land balance");
        choices("townyadmin","help set unclaim plot givebonus giveplots toggle resident town nation tpplot checkperm reload reset backup mysqldump database newday newhour purge checkoutposts townyperms install eco");
        choices("townyadmin set","mayor capital nationzoneoverride title surname plot founder about");choices("townyadmin toggle","wildernessuse regenerations devmode debug townwithdraw nationwithdraw npc");choices("townyadmin reload","database config lang perms all");choices("townyadmin database","save load remove");choices("townyadmin database remove","titles");choices("townyadmin plot","claim meta claimedat trust");choices("townyadmin plot trust","add remove");
        choices("townyadmin resident *","rename friend meta unjail delete about");choices("townyadmin resident * friend","add remove list clear");choices("townyadmin town","new");choices("townyadmin nation","new");
        choices("townyadmin town *","add kick rename delete spawn outpost rank set toggle meta merge forcemerge deposit withdraw bankhistory outlaw leavenation invite unruin trust trusttown checkoutposts settownlevel giveboughtblocks buy forsale fs notforsale nfs");
        choices("townyadmin nation *","add kick rename delete recheck merge forcemerge toggle set deposit withdraw bankhistory transfer rank sanctiontown ally enemy meta");
        for(String kind:List.of("town","nation")){
            String p="townyadmin "+kind+" *";
            for(String branch:List.of("set","toggle","rank","invite","trust","trusttown","ally","enemy","sanctiontown")){Node source=node(new String[]{kind,branch});if(source!=null)for(String key:source.words.keySet())path(p+" "+branch+" "+key);}
            choices(p+" set","foundingdate");choices(p+" meta","set add remove");
        }
        choices("townyadmin town * toggle","forcepvp forcedisablepvp forcemobs unlimitedclaims upkeep allowedtowar conquered visibleontoplists");
        for(String prefix:List.of("townyadmin plot meta","townyadmin resident * meta"))choices(prefix,"set add remove");
        choices("townyadmin townyperms","listgroups group townrank nationrank");choices("townyadmin townyperms group *","addperm removeperm");for(String branch:List.of("townrank","nationrank"))choices("townyadmin townyperms "+branch,"addrank removerank");
        choices("townyadmin eco","resetbanks depositall convert info");choices("townyadmin eco convert","modern");choices("townyadmin eco info","nation resident serveraccount town");
        for(String prefix:List.of("townyworld","townyworld *")){
            choices(prefix,"list set toggle");choices(prefix+" set","usedefault wildperm wildignore wildregen wildname");choices(prefix+" toggle","claimable usingtowny warallowed pvp forcepvp friendlyfire explosion forceexplosion fire forcefire townmobs wildernessmobs worldmobs revertunclaim revertentityexpl revertblockexpl plotcleardelete unclaimblockdelete unclaimentitydelete wildernessuse");
        }
        for(String prefix:List.of("town set perm","resident set perm","plot set perm","plot group set perm","townyadmin town * set perm")){
            String first="friend resident nation ally outsider build destroy switch itemuse on off reset";choices(prefix,first);
            for(String a:List.of("friend","resident","nation","ally","outsider","build","destroy","switch","itemuse")){
                choices(prefix+" "+a,"build destroy switch itemuse on off");for(String b:List.of("build","destroy","switch","itemuse"))choices(prefix+" "+a+" "+b,"on off");
            }
        }
        for(String prefix:List.of("town list","town list *","nation list","nation list *")){choices(prefix,"by");choices(prefix+" by","residents balance bankrupt founded name online open public ruined townblocks towns upkeep");}
        // The two prefixed aliases must have exactly the same subtree.
        node(new String[]{"town"}).words.put("bank",node(new String[]{"town","treasury"}));
    }
}
