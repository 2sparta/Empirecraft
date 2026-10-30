package ua.empirecraft.feudal;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static ua.empirecraft.feudal.Database.*;

/** Argument-specific completion. In particular, private pending operations never leak to other players. */
final class CommandHints {
    static final List<String[]> PATTERNS=new ArrayList<>();
    static {String schema="""
currency reserve deposit gold :integer :currency
currency reserve deposit :integer :currency
currency reserve :currency
currency info :currency
currency value :currency
currency audit :currency
currency log :currency :integer
currency history :currency
currency print :currency
currency mint :currency
currency fees :currency
currency settings :currency
currency create :text :text :estate
currency banker add :player :estate
currency banker remove :player :estate
currency treasury :estate deposit :money :currency
currency treasury :estate withdraw :money :currency
currency treasury :estate
currency burn :money :currency
currency pay :player :money :currency
currency upgrade :currency
currency redeem :money :currency :money
currency collect :integer
currency gold inspect :goldop
currency gold resolve :goldop applied
currency gold resolve :goldop failed
currency exchange quote :money :asset :asset :money
currency exchange convert :money :asset :asset :money
trade :player
trade accept :player
trade coins :currency :money
order create :currency :money :integer :integer :estate
order show :order
order deliver :order :integer
order collect :order :integer
order cancel :order
commerce inspect :itemop
commerce recover :itemop
commerce abort :itemop
town treasury deposit :money :currency
town treasury withdraw :money :currency
town treasury banker add :player
town treasury banker remove :player
town treasury gold withdraw :money
town treasury log :integer
town vassal info :estate
town vassal tree :estate :integer
town vassal list :estate
town vassal liege :estate
town vassal contract :estate
town vassal history :estate
town vassal independence :estate
town vassal release :estate
town vassal create :estate :region :text
town vassal appoint :estate :player
town vassal offer :estate :estate :money :currency :autonomy :military :integer
town vassal petition :estate :estate :money :currency :autonomy :military :integer
town vassal accept :proposal
town vassal reject :proposal
town vassal tribute status :estate
town vassal tribute pay :estate :money :currency
town vassal charter :estate :player
town vassal preview :text
town vassal found :text
town vassal admin setliege :estate :estate
town vassal admin release :estate
town title info :player
town title grant :estate :player :noble
town title revoke :estate :player
town state status :estate
town state progress :estate :stage
town state advance preview :estate :stage
town state advance submit :estate :stage
town state advance confirm :estate
town state charter :estate :text
town state capital :estate :estate
town state government :estate :government
town state territory :estate :territory
town state budget :estate :owner :currency :money
town state vote :decision yes
town state vote :decision no
town state execute :decision
town state certify :estate :feature yes :text
town state certify :estate :feature no :text
town state decisions :estate
town state decision :decision
town state history :estate
town state releasevote :estate :estate
town state adminvote :estate :text
town state unionvote :estate :union
town state exitvote :estate :union
town state councilvote :estate :text yes
town state councilvote :estate :text no
town state vassalvote :estate :proposal
town state advancevote :estate :stage
town state election open :estate
town state election candidate :election
town state election vote :election :player
town state election close :election
town state election resume :election
town state election abort :election
town state election cancel :election
town state province propose :estate :estate :currency :money :text
town state province sign :text
town state province release :estate
town state debug info :estate
town state debug next :estate :stage
town state debug setstage :estate :stage
town state debug ready :estate :stage
town state debug government :estate :government
town state debug territory :estate :territory
town castle pos1 :estate
town castle pos2 :estate
town castle inspect :estate
town castle status :estate
town castle history :estate
town castle review :estate :score :text
town fief info :estate
town fief vassals :estate
town fief liege :estate
town fief tree :estate
town fief contract :estate
town union create :text :estate
town union invite :estate
town union invite :union :estate :estate
town union amend :union :estate :text
town union propose :estate :uniontype :text :estate :currency :money :text :text
town union terms :union
town union charter :union
town union powers :union
town union status :estate
town union sign :union :estate
town union accept :union :estate
town union confirm :union :estate
town union reject :union :estate
town union contribute :union :estate :money
town union treasury :union
town union join :union :estate
town union leave :union :estate
town union debt :union :estate
town union terminate :union :estate
town union vote propose :union :estate budget :owner :currency :money
town union vote propose :union :estate invite :estate
town union vote propose :union :estate charter :text
town union vote propose :union :estate terminate
town union vote cast :text :estate yes
town union vote cast :text :estate no
town diplomacy offer :estate :estate :treatytype :integer :text
town diplomacy sign :treaty
town diplomacy break :treaty :text
town diplomacy show :treaty
town diplomacy history :treaty
town diplomacy list :estate
town dynasty name :estate :text
town dynasty nominate :estate :player
town dynasty accept :estate
town dynasty revoke :estate
town dynasty abdicate :estate confirm
town dynasty show :estate
town dynasty history :estate
town estateinfo :estate
town estateinfo at :world :coordinate :coordinate
town estateinfo marker :estate trade :text
town estateinfo marker :estate capital :text
town estateinfo remove-marker :estate trade
town estateinfo remove-marker :estate capital
town provision status :town
town provision history :town
""";
        for(String line:schema.split("\n"))if(!line.isBlank())PATTERNS.add(line.split(" "));
        var extra=new ArrayList<String[]>();
        for(String[] row:PATTERNS){if(row.length>2&&row[0].equals("town"))for(String[] aliases:List.of(new String[]{"treasury","bank"},new String[]{"union","confed"},new String[]{"union","federation"},new String[]{"vassal","feudal"})){if(row[1].equals(aliases[0])){var copy=row.clone();copy[1]=aliases[1];extra.add(copy);}}
            if(row.length>3&&row[1].equals("state")&&row[2].equals("debug")){String[] copy=new String[row.length-2];copy[0]="statedebug";System.arraycopy(row,3,copy,1,row.length-3);extra.add(copy);}}
        PATTERNS.addAll(extra);
    }
    static boolean valid(String type,String value){return switch(type){case "integer","score"->value.matches("[0-9]+");case "money"->value.matches("[0-9]+([.,][0-9]+)?");case "coordinate"->value.matches("-?[0-9]+");default->true;};}
    static Set<String> types(String root,String[] args){String[] full=new String[args.length+1];full[0]=CommandLanguage.canonicalRoot(root);System.arraycopy(CommandLanguage.normalize(root,args),0,full,1,args.length);int index=full.length-1;Set<String> out=new LinkedHashSet<>();for(String[] pattern:PATTERNS){if(pattern.length<=index)continue;boolean matches=true;for(int i=0;i<index;i++)if(pattern[i].startsWith(":" )? !valid(pattern[i].substring(1),full[i]):!pattern[i].equals(full[i])){matches=false;break;}if(matches&&pattern[index].startsWith(":"))out.add(pattern[index].substring(1));}return out;}
    static List<String> complete(FeudalService s,CommandSender sender,String root,String[] args){
        if(args.length==0)return List.of();String locale=sender instanceof Player p?p.getLocale():"uk_ua",person=sender instanceof Player p?p.getUniqueId().toString():"console";boolean admin=sender.hasPermission("empirecraft.feudal.admin");
        var out=new LinkedHashSet<String>(CommandLanguage.suggest(root,args,locale));
        try{for(String type:types(root,args))switch(type){
            case "currency","asset"->{for(var row:s.db.query("SELECT code FROM currencies WHERE backing='GOLD' ORDER BY code"))out.add(string(row,"code"));if(type.equals("asset"))out.add("@GOLD");}
            case "estate","town","owner"->{for(var e:s.estates())if(e.active()&&(!type.equals("town")||e.kind()==Model.Kind.BARONY))out.add(type.equals("owner")?"e:"+e.id():(e.kind()==Model.Kind.KINGDOM?"держава:":e.kind()==Model.Kind.BARONY?"місто:":"")+e.name().replace(' ','_'));if(type.equals("owner"))out.add("p:"+person);}
            case "player"->{for(var p:Bukkit.getOnlinePlayers())out.add(p.getName());}
            case "money","integer"->out.addAll(List.of("1","10","64","100"));
            case "score"->out.addAll(List.of("0","1","2","3","4"));
            case "coordinate"->out.add("0");
            case "world"->{for(var w:Bukkit.getWorlds())out.add(w.getName());}
            case "stage"->{String command=String.join(" ",CommandLanguage.normalize(root,args));boolean next=command.contains("debug next")||command.contains("debug ready")||command.startsWith("next ")||command.startsWith("ready ")||command.contains("advance preview")||command.contains("advance submit");
                for(var v:PoliticalModel.Stage.values())if(!next||s.market==null||s.market.politics.rules.transition(s.market.politics.state(s.resolve(args[args.length-2]).id()).stage(),v))out.add(v.name().toLowerCase(Locale.ROOT));}
            case "government"->{for(var v:PoliticalModel.Government.values())out.add(v.name().toLowerCase(Locale.ROOT));}
            case "territory"->{for(var v:PoliticalModel.Territory.values())out.add(v.name().toLowerCase(Locale.ROOT));}
            case "uniontype"->{for(var v:PoliticalModel.UnionType.values())out.add(v.name().toLowerCase(Locale.ROOT));}
            case "feature"->out.addAll(PoliticsService.FEATURES);
            case "region"->out.addAll(List.of("графство","герцогство"));
            case "noble"->out.addAll(List.of("барон","граф","герцог"));
            case "autonomy"->out.addAll(List.of("висока","обмежена"));
            case "military"->out.addAll(List.of("добровільно","оборона","повний"));
            case "treatytype"->out.addAll(List.of("alliance","nonaggression","trade","passage"));
            case "proposal"->{for(var row:s.db.query("SELECT id,required FROM proposals WHERE expires>?",s.now()))if(admin||Arrays.asList(string(row,"required").split(",")).contains(person))out.add(string(row,"id"));}
            case "goldop","vaultop"->{for(var row:s.db.query("SELECT id,actor FROM "+(type.equals("goldop")?"gold_ops":"vault_ops")+" WHERE status='PREPARED'"))if(admin||person.equals(string(row,"actor")))out.add(string(row,"id"));}
            case "itemop"->{for(var row:s.db.query(admin?"SELECT id FROM item_ops WHERE status='PREPARED'":"SELECT o.id FROM item_ops o JOIN item_participants p ON p.op=o.id WHERE o.status='PREPARED' AND p.person=?",admin?new Object[]{}:new Object[]{person}))out.add(string(row,"id"));}
            case "order","decision","election","union","treaty"->{String table=switch(type){case "order"->"orders";case "decision"->"political_decisions";case "election"->"political_elections";case "union"->"political_unions";default->"diplomacy";};for(var row:s.db.query("SELECT id FROM "+table+" ORDER BY id LIMIT 100"))out.add(string(row,"id"));}
            default->{} // Free text must stay free; never suggest unrelated objects.
        }}catch(Exception ignored){}
        String typed=args[args.length-1].toLowerCase(Locale.ROOT);if(!admin){out.remove("admin");out.remove("адмін");out.remove("админ");}if(!sender.hasPermission(PoliticsDebug.PERMISSION)){out.remove("debug");out.remove("відлагодження");out.remove("отладка");}return out.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(typed)).limit(100).toList();
    }
}

