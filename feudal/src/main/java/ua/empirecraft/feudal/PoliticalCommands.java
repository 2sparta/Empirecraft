package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.PoliticalModel.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Player-facing commands share the same domain checks as scheduled reconciliation. */
public final class PoliticalCommands implements CommandExecutor,TabCompleter {
    private final FeudalPlugin plugin;private final FeudalService s;private final TownyBridge towny;final PoliticsService p;final CastleInspector castles;private long inspectionCursor;
    public PoliticalCommands(FeudalPlugin plugin,FeudalService s,TownyBridge towny,PoliticsService p) throws Exception {this.plugin=plugin;this.s=s;this.towny=towny;this.p=p;castles=new CastleInspector(p);}
    private Actor actor(CommandSender sender){return new Actor(sender instanceof Player who?who.getUniqueId().toString():"console",sender.hasPermission("empirecraft.feudal.admin"));}
    private Actor reviewer(CommandSender sender){Actor a=actor(sender);return new Actor(a.id(),sender.hasPermission("empirecraft.feudal.castle.review"));}
    private String id(String value) throws Exception{return s.resolve(value).id();}
    private String defaultId(CommandSender sender) throws Exception {require(sender instanceof Player,"Укажіть ID володіння.");return towny.defaultEstate((Player)sender);}
    private String at(String[] args,int n){require(args.length>n,"Недостатньо аргументів. Дивіться help.");return args[n];}
    private static String rest(String[] args,int n){require(args.length>n,"Додайте текст.");return String.join(" ",Arrays.copyOfRange(args,n,args.length));}
    private static boolean yes(String value){require(Set.of("yes","no","так","ні").contains(value.toLowerCase(Locale.ROOT)),"Відповідь: yes/no або так/ні.");return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("так");}
    private static int integer(String value,int max){require(value.matches("[0-9]{1,6}"),"Потрібне ціле число.");int n=Integer.parseInt(value);require(n<=max,"Завелике число.");return n;}
    private void say(CommandSender sender,String value){plugin.message(sender,value);}
    public void refresh() throws Exception {
        var people=new HashMap<String,Set<String>>();long since=s.now()-p.rules.days("active-days",30);
        for(var town:TownyAPI.getInstance().getTowns()){
            var ids=new TreeSet<String>();for(var resident:town.getResidents())if(resident.getLastOnline()>=since || Bukkit.getPlayer(resident.getUUID())!=null)ids.add(resident.getUUID().toString());
            people.put(TownyBridge.townId(town),ids);
        }p.population(people);p.syncDomains();
    }
    public void tick() throws Exception {
        refresh();
        var pending=s.db.query("SELECT state FROM political_ready WHERE target='BARONY' ORDER BY state");
        int scans=Math.max(1,Math.min(16,p.rules.count("castle.scans-per-sync",2)));
        for(int i=0;i<Math.min(scans,pending.size());i++){
            String id=string(pending.get((int)Math.floorMod(inspectionCursor++,pending.size())),"state");
            if(castles.selected(id))try{castles.inspect(new Actor(s.estate(id).lord(),false),id);}catch(IllegalArgumentException paused){/* Inspection requires loaded chunks and current ownership. */}
        }
        p.tick();
        for(var election:s.db.query("SELECT * FROM political_elections WHERE status='PREPARED'")) {
            String state=string(election,"state");if(Objects.equals(s.estate(state).lord(),string(election,"winner")))p.finishElection(string(election,"id"));
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{require(sender.hasPermission("empirecraft.feudal.use"),"Немає дозволу.");towny.sync();refresh();Actor a=actor(sender);String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            switch(command.getName()){
                case "state" -> state(sender,a,sub,args);
                case "castle" -> castle(sender,a,sub,args);
                case "fief" -> fief(sender,a,sub,args);
                case "union","confed","federation" -> union(sender,a,sub,args,command.getName());
            }
        }catch(IllegalArgumentException e){say(sender,e.getMessage());}catch(Exception e){plugin.failure("Політична команда",e);say(sender,"Зміни не завершені. Перевірте журнал сервера; не повторюйте невизначену передачу влади вручну.");}return true;
    }
    private void state(CommandSender sender,Actor actor,String sub,String[] a) throws Exception {
        switch(sub){
            case "status" -> {String id=a.length>1?id(a[1]):defaultId(sender);var st=p.state(id);say(sender,s.estate(id).name()+": "+st.stage().label+"; "+st.government()+"; "+st.territory()+"; титул: "+plugin.politicalTitle(s.estate(id)));say(sender,"Столиця: "+st.capital()+"; активні громадяни: "+p.electorate(id).size()+"; централізація: "+p.centralization(id)+"/100; об’єднано до: "+st.mergedInto());var health=s.db.one("SELECT detail,escalated FROM political_health WHERE state=?",id);if(health!=null)say(sender,"Попередження: "+string(health,"detail")+(bool(health,"escalated")?"; пільговий строк завершився":"; діє пільговий строк"));}
            case "progress" -> {String id=a.length>1?id(a[1]):defaultId(sender);if(a.length>2)showProgress(sender,id,PoliticalModel.parse(Stage.class,a[2]));else showNextProgress(sender,id);}
            case "advance" -> {String operation=at(a,1),id=a.length>2?id(a[2]):defaultId(sender);if(castles.selected(id))castles.inspect(new Actor(s.estate(id).lord(),false),id);
                switch(operation){case "preview"->{if(a.length>3)showProgress(sender,id,PoliticalModel.parse(Stage.class,a[3]));else showNextProgress(sender,id);}case "submit"->{p.submit(actor,id,advanceTarget(id,a));say(sender,"Заявку збережено. Остаточно: /state advance confirm "+id);}case "confirm"->{p.confirm(actor,id);say(sender,"Статус оновлено. Вотчини та васальні договори збережені.");}default->throw new IllegalArgumentException("advance preview|submit|confirm");}}
            case "charter" -> {String id=a.length>1?id(a[1]):defaultId(sender);if(a.length<=2)say(sender,p.state(id).charter());else propose(sender,actor,id,"CHARTER",rest(a,2),"",0);}
            case "capital" -> {String id=a.length>1?id(a[1]):defaultId(sender);if(a.length<=2)say(sender,"Столиця: "+p.state(id).capital());else propose(sender,actor,id,"CAPITAL",id(at(a,2)),"",0);}
            case "government" -> {String id=id(at(a,1));if(castles.selected(id))castles.inspect(new Actor(s.estate(id).lord(),false),id);propose(sender,actor,id,"GOVERNMENT",PoliticalModel.parse(Government.class,at(a,2)).name(),"",0);}
            case "territory" -> propose(sender,actor,id(at(a,1)),"TERRITORY",PoliticalModel.parse(Territory.class,at(a,2)).name(),"",0);
            case "budget" -> propose(sender,actor,id(at(a,1)),"BUDGET",at(a,2),at(a,3),Model.money(at(a,4)));
            case "vote" -> {p.vote(actor,at(a,1),yes(at(a,2)));say(sender,"Голос збережено. Рішення застосовується після потрібної кількості голосів.");}
            case "execute" -> {p.executeDecision(actor,at(a,1));say(sender,"Рішення застосовано.");}
            case "certify" -> {p.certify(reviewer(sender),id(at(a,1)),at(a,2),rest(a,4),yes(at(a,3)));say(sender,"Підтвердження записано.");}
            case "decisions" -> {String id=a.length>1?id(a[1]):defaultId(sender);for(var row:s.db.query("SELECT id,action,arg1,deadline FROM political_decisions WHERE state=? AND status='OPEN' AND deadline>?",id,s.now()))say(sender,row.toString());}
            case "decision" -> say(sender,"Проєкт рішення: "+s.db.one("SELECT * FROM political_decisions WHERE id=?",at(a,1)));
            case "releasevote" -> propose(sender,actor,id(at(a,1)),"RELEASE",id(at(a,2)),"",0);
            case "adminvote" -> propose(sender,actor,id(at(a,1)),"ADMIN",at(a,2),"",0);
            case "unionvote" -> propose(sender,actor,id(at(a,1)),"UNION",at(a,2),"",0);
            case "exitvote" -> propose(sender,actor,id(at(a,1)),"EXIT",at(a,2),"",0);
            case "councilvote" -> propose(sender,actor,id(at(a,1)),"COUNCIL",at(a,2),yes(at(a,3))?"YES":"NO",0);
            case "vassalvote" -> propose(sender,actor,id(at(a,1)),"VASSAL",at(a,2),"",0);
            case "advancevote" -> propose(sender,actor,id(at(a,1)),"ADVANCE",PoliticalModel.parse(Stage.class,at(a,2)).name(),"",0);
            case "history" -> {String id=a.length>1?id(a[1]):defaultId(sender);for(var r:p.history(id))say(sender,string(r,"event")+": "+string(r,"detail"));}
            case "province" -> province(sender,actor,a);
            case "election" -> election(sender,actor,a);
            default -> {say(sender,"/state status [володіння] | progress <володіння> <статус> | advance preview|submit <володіння> <статус> | advance confirm <володіння>");say(sender,"/state charter|capital|government|territory <володіння> <значення>; /state budget <володіння> <p:UUID|e:ID|u:ID> <валюта> <сума>");say(sender,"/state vote <ID> yes|no; execute <ID>; election open|candidate|vote|close|cancel; province propose|sign|release; history [володіння]");}
        }
    }
    private void propose(CommandSender who,Actor actor,String id,String action,String value,String second,long amount){String key=p.decision(actor,id,action,value,second,amount);say(who,"Проєкт рішення: "+key+". Переглянути: /state decision "+key+"; голосувати: /state vote "+key+" yes|no");}
    private List<Stage> nextStages(String id) throws Exception {
        Stage current=p.state(id).stage();return Arrays.stream(Stage.values()).filter(target->p.rules.transition(current,target)).toList();
    }
    private Stage advanceTarget(String id,String[] args) throws Exception {
        if(args.length>3)return PoliticalModel.parse(Stage.class,args[3]);
        var stages=nextStages(id);require(stages.size()==1,"Укажіть цільовий статус: "+stages);return stages.get(0);
    }
    private void showNextProgress(CommandSender sender,String id) throws Exception {
        var stages=nextStages(id);if(stages.isEmpty())say(sender,"Вищих статусів цієї гілки немає. Доступні закони, устрій та союзи.");
        for(Stage target:stages){say(sender,"Наступний статус: "+target.name().toLowerCase(Locale.ROOT)+" — "+target.label);showProgress(sender,id,target);}
    }
    private void showProgress(CommandSender sender,String id,Stage target){Progress progress=p.progress(id,target);for(var check:progress.checks())say(sender,(check.met()?"§a✓ ":"§c✗ ")+check.name()+": "+check.detail());say(sender,"Строк стабільності: "+(progress.holdMillis()/3_600_000)+" год; залишилось годин: "+(progress.readySince()==0?"—":Math.max(0,(progress.holdMillis()-(s.now()-progress.readySince())+3_599_999)/3_600_000)));}
    private void province(CommandSender sender,Actor actor,String[] a) throws Exception {switch(at(a,1)){
        case "propose" -> {String key=p.administer(actor,id(at(a,2)),id(at(a,3)),rest(a,6),Model.money(at(a,5)),at(a,4));say(sender,"Пряма адміністрація: "+key+". Обидві сторони: /state province sign "+key);}
        case "sign" -> {p.signAdministration(actor,at(a,2));say(sender,"Підпис записано.");}
        case "release" -> {p.releaseAdministration(actor,id(at(a,2)));say(sender,"Адміністративне підпорядкування припинено.");}
        default -> say(sender,"/state province propose <центр> <провінція> <валюта> <податок/доба> <повноваження>; sign <ID>; release <провінція>");}}
    private void election(CommandSender sender,Actor actor,String[] a) throws Exception {switch(at(a,1)){
        case "open" -> say(sender,"Вибори: "+p.election(actor,id(at(a,2))));
        case "candidate" -> {String key=at(a,2);var e=s.db.one("SELECT * FROM political_elections WHERE id=?",key);require(e!=null,"Вибори не знайдено.");towny.validateElected(s.estate(string(e,"state")),actor.id());p.candidate(actor,key);say(sender,"Кандидатуру зареєстровано.");}
        case "vote" -> {p.ballot(actor,at(a,2),towny.residentId(at(a,3)));say(sender,"Голос збережено.");}
        case "close" -> {String key=at(a,2),winner=p.closeElection(key);var row=s.db.one("SELECT * FROM political_elections WHERE id=?",key);String id=string(row,"state");towny.transferElected(s.estate(id),winner);towny.sync();refresh();p.finishElection(key);say(sender,"Обрано: "+towny.playerName(winner));}
        case "resume" -> {
            String key=at(a,2);var row=s.db.one("SELECT * FROM political_elections WHERE id=? AND status='PREPARED'",key);require(row!=null,"Немає підготовленої передачі.");
            require(actor.admin() || actor.id().equals(string(row,"old_lord")) || actor.id().equals(string(row,"winner")),"Продовжує передачу одна зі сторін або адміністратор.");
            String id=string(row,"state"),winner=string(row,"winner");require(Objects.equals(s.estate(id).lord(),string(row,"old_lord")) || Objects.equals(s.estate(id).lord(),winner),"Конфлікт правителя; потрібна перевірка.");
            if(!Objects.equals(s.estate(id).lord(),winner))towny.transferElected(s.estate(id),winner);towny.sync();refresh();p.finishElection(key);say(sender,"Передачу завершено.");
        }
        case "abort" -> {p.abortElection(actor,at(a,2));say(sender,"Невиконану передачу скасовано; потрібні нові вибори.");}
        case "cancel" -> {p.cancelElection(actor,at(a,2));say(sender,"Призначте повторні вибори.");}
        default -> say(sender,"/state election open <володіння>; candidate <ID>; vote <ID> <гравець>; close <ID>; cancel <ID>");}}
    private void castle(CommandSender sender,Actor actor,String sub,String[] a) throws Exception {
        if(sub.equals("help")){say(sender,"/castle pos1|pos2|inspect|status|history <володіння>; /castle review <володіння> <0–4> <примітка>");return;}
        String id=a.length>1?id(a[1]):defaultId(sender);switch(sub){
            case "pos1","pos2" -> {require(sender instanceof Player,"Вибір точки доступний у грі.");castles.select(actor,id,sub.equals("pos1")?1:2,(Player)sender);say(sender,"Внутрішній кут двору збережено.");}
            case "inspect" -> {castles.inspect(actor,id);say(sender,"Огляд: "+s.db.one("SELECT area,height_ok,perimeter_ok,gates_ok FROM political_castles WHERE state=?",id));}
            case "review" -> {castles.inspect(new Actor(s.estate(id).lord(),false),id);p.castleReview(reviewer(sender),id,integer(at(a,2),4),rest(a,3));say(sender,"Незалежний висновок збережено.");}
            case "status" -> say(sender,"Замок схвалений: "+p.castleApproved(id)+"; "+s.db.one("SELECT area,height_ok,perimeter_ok,gates_ok,manual_score,reviewer FROM political_castles WHERE state=?",id));
            case "history" -> {for(var row:p.history(id))if(string(row,"event").startsWith("CASTLE"))say(sender,string(row,"event")+": "+string(row,"detail"));}
            default -> throw new IllegalArgumentException("pos1|pos2|inspect|review|status|history");
        }
    }
    private void fief(CommandSender sender,Actor actor,String sub,String[] a) throws Exception {
        String id=a.length>1?id(a[1]):defaultId(sender);switch(sub){
            case "info" -> {say(sender,"Особиста вотчина: "+p.personal(id));say(sender,"Усі землі через договори й адміністрацію: "+p.lands(id));}
            case "vassals" -> {for(var child:s.children(id))say(sender,child.name()+"; "+p.state(child.id()).stage().label+"; договір="+(s.contract(child.id())!=null));}
            case "liege" -> say(sender,"Безпосередній сюзерен: "+s.parent(id));
            case "tree" -> {for(String land:p.lands(id)){var province=s.db.one("SELECT center FROM political_admin WHERE province=?",land);say(sender,land+" → "+(province==null?s.parent(land):string(province,"center")+" (пряма адміністрація)"));}}
            case "contract" -> say(sender,"Договір: "+s.contract(id));
            default -> say(sender,"/fief info|vassals|liege|tree|contract [володіння]. Договори: /vassal; титули: /title.");
        }
    }
    private void union(CommandSender sender,Actor actor,String sub,String[] a,String family) throws Exception {
        switch(sub){
            case "create" -> {String source=a.length>2?id(a[2]):defaultId(sender);UnionType type=family.equals("federation")?UnionType.FEDERATION:UnionType.CONFEDERATION;String key=p.unions.create(actor,source,type,at(a,1));say(sender,"Проєкт союзу: "+key+". Запросіть держави: /"+family+" invite <держава>; погодьте статут: /"+family+" amend "+key+" "+source+" <текст...>.");}
            case "invite" -> {String source=a.length>3?id(a[2]):defaultId(sender),key=a.length>3?at(a,1):selectedUnion(source,family),target=id(a.length>3?a[3]:at(a,1));if("DRAFT".equals(string(p.unions.get(key),"status"))){p.unions.inviteDraft(actor,key,source,target);say(sender,"Учасника додано до проєкту; попередні підписи анульовано.");}else say(sender,"Рішення про запрошення: "+p.unions.proposeVote(actor,key,source,"INVITE",target,"BASE",0));}
            case "amend" -> {p.unions.amendDraft(actor,at(a,1),id(at(a,2)),rest(a,3));say(sender,"Статут проєкту змінено. Усім сторонам потрібні нові підписи.");}
            case "terminate" -> say(sender,"Рішення про завершення: "+p.unions.proposeVote(actor,at(a,1),id(at(a,2)),"TERMINATE","","BASE",0));
            case "status" -> {String id=a.length>1?id(a[1]):defaultId(sender);for(var u:p.unions.forState(id))say(sender,string(u,"id")+" "+string(u,"name")+" "+string(u,"kind")+" "+string(u,"status"));}
            case "propose" -> {String source=id(at(a,1));UnionType type=PoliticalModel.parse(UnionType.class,at(a,2));var states=new TreeSet<String>();for(String item:at(a,4).split(","))states.add(id(item));String key=p.unions.propose(actor,source,states,type,at(a,3),rest(a,8),at(a,7),Model.money(at(a,6)),at(a,5));say(sender,"План союзу: "+key+". /union terms "+key+"; /union sign "+key+" <держава>");}
            case "terms","charter","powers" -> {String key=a.length>1?a[1]:selectedUnion(defaultId(sender),family);var u=p.unions.get(key);say(sender,string(u,"charter"));say(sender,"Компетенція: "+string(u,"powers")+"; внесок/доба: "+Model.format(number(u,"contribution"))+" "+string(u,"code"));say(sender,"Учасники: "+p.unions.members(key));}
            case "sign","accept" -> {p.unions.sign(actor,at(a,1),id(at(a,2)));say(sender,"Підпис плану записано.");}
            case "confirm" -> {p.unions.confirmMerge(actor,at(a,1),id(at(a,2)));say(sender,"Остаточне підтвердження записано.");}
            case "reject" -> {p.unions.reject(actor,at(a,1),id(at(a,2)));say(sender,"План відхилено.");}
            case "contribute" -> {p.unions.contribute(actor,at(a,1),id(at(a,2)),Model.money(at(a,3)));say(sender,"Внесок зараховано.");}
            case "treasury" -> {String key=at(a,1);say(sender,"Унійна скарбниця: "+s.db.query("SELECT code,balance FROM accounts WHERE owner=?","u:"+key));}
            case "join" -> {p.unions.acceptInvite(actor,at(a,1),id(at(a,2)));say(sender,"Вступ підтверджено.");}
            case "leave" -> {p.unions.leave(actor,at(a,1),id(at(a,2)));say(sender,"Повідомлення про вихід записано.");}
            case "debt" -> {p.unions.payDebt(actor,at(a,1),id(at(a,2)));say(sender,"Прийняті внески сплачено.");}
            case "vote" -> {if(at(a,1).equals("cast")){p.unions.vote(actor,at(a,2),id(at(a,3)),yes(at(a,4)));say(sender,"Голос держави записано.");}
                else{require(at(a,1).equals("propose"),"vote propose|cast");String kind=at(a,4).toUpperCase(Locale.ROOT);String value=kind.equals("TERMINATE")?"":kind.equals("CHARTER")?rest(a,5):at(a,5);
                String key=p.unions.proposeVote(actor,at(a,2),id(at(a,3)),kind,value,kind.equals("BUDGET")?at(a,6):"BASE",kind.equals("BUDGET")?Model.money(at(a,7)):0);say(sender,"Голосування ради: "+key);}}
            default -> {say(sender,"/confed create <назва> [держава]; /confed invite <держава>; /union amend <ID> <держава> <статут...>");say(sender,"/union propose <держава> <тип> <назва> <учасники,через,кому> <валюта> <внесок/доба> <компетенції> <статут...>");say(sender,"/union terms|treasury <ID>; sign|confirm|reject|join|leave|debt <ID> <держава>; contribute <ID> <держава> <сума>");say(sender,"/union vote propose <союз> <держава> BUDGET|INVITE|CHARTER|TERMINATE <одержувач/значення> <валюта> <сума>; vote cast <голосування> <держава> yes|no");}
        }
    }
    private String selectedUnion(String state,String family) throws Exception {
        UnionType type=family.equals("federation")?UnionType.FEDERATION:UnionType.CONFEDERATION;
        var choices=p.unions.forState(state).stream().filter(u->Set.of("DRAFT","ACTIVE","SUSPENDED").contains(string(u,"status")))
            .filter(u->family.equals("union") || type.name().equals(string(u,"kind"))).toList();
        require(choices.size()==1,"Укажіть ID союзу й державу явно; знайдено союзів: "+choices.size());return string(choices.get(0),"id");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(args.length==1){List<String> choices=switch(cmd.getName()){case "state"->List.of("status","progress","advance","charter","capital","government","territory","budget","vote","execute","election","province","history","certify","decisions","advancevote","vassalvote","unionvote","councilvote","exitvote","adminvote","releasevote");case "castle"->List.of("pos1","pos2","inspect","review","status","history");case "fief"->List.of("info","vassals","liege","tree","contract");default->List.of("create","invite","amend","terminate","charter","powers","status","propose","terms","sign","confirm","reject","treasury","contribute","vote","join","leave","debt");};return choices.stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}
        try {String prefix=args[args.length-1].toLowerCase(Locale.ROOT);var result=new TreeSet<String>();for(var stage:Stage.values())result.add(stage.name().toLowerCase(Locale.ROOT));for(var e:s.estates())if(e.active())result.add(e.id());return result.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(30).toList();}catch(Exception e){return List.of();}
    }
}
