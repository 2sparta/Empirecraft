package ua.townyrecruits;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class RecruitmentCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        for(String root:List.of("trbridge","tarmyadmin"))dispatcher.register(Commands.m_82127_(root).requires(RecruitmentCommands::admin)
            .executes(c->run(c.getSource(),root,""))
            .then(Commands.m_82129_("arguments",StringArgumentType.greedyString()).executes(c->run(c.getSource(),root,StringArgumentType.getString(c,"arguments")))));
        dispatcher.register(Commands.m_82127_("trecruit").executes(c->{Bridge b=Bridge.INSTANCE;if(b!=null&&b.recruitment.menu!=null&&c.getSource().m_81373_() instanceof ServerPlayer p){var bp=org.bukkit.Bukkit.getPlayer(p.m_20148_());if(bp!=null)b.recruitment.menu.open(bp);}return 1;}));
    }
    static boolean admin(CommandSourceStack source){
        if(!(source.m_81373_() instanceof ServerPlayer player))return true;
        var p=org.bukkit.Bukkit.getPlayer(player.m_20148_());return p!=null&&(p.hasPermission("trbridge.admin")||p.hasPermission("townyrecruits.admin")||p.hasPermission("tarmyadmin.admin"));
    }
    static void say(CommandSourceStack source,String text){source.m_288197_(()->Component.m_237113_(text),false);}
    static List<String> arguments(String text){return CommandWords.parse(text);}
    static Policy.Nation nation(Bridge bridge,String text){return bridge.snapshot.nations().values().stream().filter(n->n.name().equalsIgnoreCase(text)||n.id().toString().equalsIgnoreCase(text)).findFirst().orElseThrow(()->new IllegalArgumentException("Націю не знайдено."));}
    static int run(CommandSourceStack source,String root,String input){
        try{
            Bridge b=Bridge.INSTANCE;if(b==null||!b.ready)throw new IllegalStateException("Towny ще не готовий.");var r=b.recruitment;var args=arguments(input);
            if(root.equals("trbridge")){
                if(args.equals(List.of("status"))){status(source,b);return 1;}
                if(args.size()==2&&args.get(0).equals("limit")&&args.get(1).equals("list")){for(var n:b.snapshot.nations().values())show(source,b,n);return 1;}
                if((args.size()==2||args.size()==4)&&args.get(0).equals("limit")){var n=nation(b,args.get(1));if(args.size()==4){int total=Integer.parseInt(args.get(2));r.limits(n.id(),total,Integer.parseInt(args.get(3)),total==0);}show(source,b,n);return 1;}
                say(source,"/trbridge status | limit list | limit <нація> [загальний] [на_гравця]");return 1;
            }
            if(args.size()>=3&&args.get(0).equals("dev")){
                Object town;try{town=b.towny.api.getClass().getMethod("getTown",UUID.class).invoke(b.towny.api,UUID.fromString(args.get(2)));}catch(IllegalArgumentException notId){town=b.towny.api.getClass().getMethod("getTown",String.class).invoke(b.towny.api,args.get(2));}
                if(town==null)throw new IllegalArgumentException("Місто не знайдено.");UUID id=b.towny.id(town);
                if(args.size()==4)r.state.development(id,args.get(1),RecruitmentConfig.decimal(args.get(3)));else if(args.size()!=3||!args.get(1).equals("info"))throw new IllegalArgumentException("/tarmyadmin dev add|remove|set <місто> <кількість> | dev info <місто>");
                say(source,b.towny.call(town,"getName")+": розвиток "+RecruitmentConfig.number(r.state.balance(id)));return 1;
            }
            if(args.size()==3&&Set.of("unlock","lock").contains(args.get(0))&&args.get(1).equals("shieldman")){var n=nation(b,args.get(2));r.state.change(()->r.state.nations.get(n.id()).shieldman=args.get(0).equals("unlock"));say(source,n.name()+": щитоносці "+(r.state.nations.get(n.id()).shieldman?"доступні":"заблоковані"));return 1;}
            if(args.equals(List.of("pending"))){for(var p:r.state.purchases.values())if(RecruitmentState.pending(p))say(source,p.unit+": покупець "+p.player+", стан "+p.phase);say(source,"Перевірка при вході гравця виконується автоматично. /tarmyadmin recover <UUID покупки> — повторити її.");return 1;}
            if((args.size()==2||args.size()==3)&&args.get(0).equals("recover")){
                var p=r.state.purchases.get(UUID.fromString(args.get(1)));if(p==null)throw new IllegalArgumentException("Покупку не знайдено.");
                var player=b.server.m_6846_().m_11259_(p.player);if(player==null)throw new IllegalArgumentException("Покупець повинен бути на сервері.");
                if(args.size()==3){
                    if(!RecruitmentState.pending(p))throw new IllegalArgumentException("Покупка не потребує ручного рішення.");
                    if(args.get(2).equals("confirm")){if(!r.present(p.unit))throw new IllegalArgumentException("Рекрута немає у світі; підтвердження заборонено.");r.state.phase(p.unit,"COMMITTED");}
                    else if(args.get(2).equals("refund")){if(r.present(p.unit))throw new IllegalArgumentException("Рекрут існує; повернення заборонено.");r.refundGold(player,p);}
                    else throw new IllegalArgumentException("confirm | refund. Використовуйте лише після перевірки оплати в журналі.");
                    TownyRecruits.LOG.warn("Admin purchase recovery by {}: {} {}",source.m_81368_(),p.unit,args.get(2));
                }else r.recover(player);
                say(source,"Стан покупки: "+r.state.purchases.get(p.unit).phase);return 1;
            }
            say(source,"/tarmyadmin dev add|remove|set <місто> <кількість>; dev info <місто>; unlock|lock shieldman <нація>; pending; recover <UUID>");return 1;
        }catch(Exception ex){source.m_81352_(Component.m_237113_(Bridge.errorText(ex)));return 0;}
    }
    static void show(CommandSourceStack source,Bridge b,Policy.Nation n){var limits=b.recruitment.state.nations.get(n.id());say(source,n.name()+": військо "+b.count(n.id())+"/"+limits.total+", на гравця "+limits.perPlayer);}
    static String nationNames(Bridge bridge,Set<UUID> ids){return ids.isEmpty()?"немає":ids.stream().map(id->{var n=bridge.snapshot.nations().get(id);return n==null?id.toString():n.name();}).sorted().collect(java.util.stream.Collectors.joining(", "));}
    static void status(CommandSourceStack source,Bridge bridge){
        say(source,bridge.status());say(source,"Усі фракції Recruits:");
        var factions=com.talhanation.recruits.FactionEvents.recruitsFactionManager;
        for(var f:factions.getFactions())say(source,"  "+f.getStringID()+" ("+f.getTeamDisplayName()+"), лідер "+f.getTeamLeaderName()+", гравців "+f.getPlayers());
        var diplomacy=com.talhanation.recruits.FactionEvents.recruitsDiplomacyManager;
        for(var a:bridge.snapshot.nations().values()){
            say(source,a.name()+": фракція "+Policy.team(a.id())+", учасників "+a.members().size()+", військо "+bridge.count(a.id())+"/"+bridge.limit(a.id()));
            say(source,"  Союзники Towny: "+nationNames(bridge,a.allies())+"; вороги: "+nationNames(bridge,a.enemies()));
            for(var b:bridge.snapshot.nations().values())if(!a.id().equals(b.id())){
                int relation=Policy.relation(bridge.snapshot,a.id(),b.id());var actual=diplomacy.getRelation(Policy.team(a.id()),Policy.team(b.id()));
                String desired=relation<0?"ENEMY":relation>0?"ALLY":"NEUTRAL",current=actual==null?"NEUTRAL":actual.name();
                if(!desired.equals("NEUTRAL")||!current.equals("NEUTRAL"))say(source,"  → "+b.name()+": Towny "+desired+", Recruits "+current);
            }
        }
    }
}
