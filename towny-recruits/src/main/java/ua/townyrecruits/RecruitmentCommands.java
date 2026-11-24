package ua.townyrecruits;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

public final class RecruitmentCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.m_82127_("trconfirm").executes(c->TownyCommandCompat.confirm(c.getSource(),true)));
        dispatcher.register(Commands.m_82127_("trcancel").executes(c->TownyCommandCompat.confirm(c.getSource(),false)));
        for(String root:List.of("trbridge","tarmyadmin"))dispatcher.register(Commands.m_82127_(root).requires(RecruitmentCommands::admin)
            .executes(c->run(c.getSource(),root,""))
            .then(Commands.m_82129_("arguments",StringArgumentType.greedyString()).suggests((context,builder)->complete(context.getSource(),root,builder)).executes(c->run(c.getSource(),root,StringArgumentType.getString(c,"arguments")))));
        if (nativeForge()) {
            var recruiting = Commands.m_82127_("trecruit").executes(c -> openForgeHireMenu(c.getSource()));
            var hire = Commands.m_82127_("hire");
            for (String type : List.of("recruit", "shieldman", "militia")) hire.then(Commands.m_82127_(type).executes(c -> {
                Bridge bridge = Bridge.INSTANCE;
                if (bridge == null || !bridge.ready) { say(c.getSource(), "Towny ще не готовий."); return 0; }
                bridge.recruitment.purchase(c.getSource().m_81375_(), type);
                return 1;
            }));
            dispatcher.register(recruiting.then(hire)
                    .then(Commands.m_82127_("dismiss").then(Commands.m_82129_("id", StringArgumentType.word())
                            .executes(c -> dismiss(c.getSource(), StringArgumentType.getString(c, "id"))))));
            for (String path : List.of("nation", "n")) for (String name : List.of("regiment", "army")) {
                dispatcher.register(Commands.m_82127_(path).then(Commands.m_82127_(name)
                        .executes(c -> military(c.getSource(), name, ""))
                        .then(Commands.m_82129_("input", StringArgumentType.greedyString())
                                .executes(c -> military(c.getSource(), name, StringArgumentType.getString(c, "input"))))));
            }
        } else {
            dispatcher.register(Commands.m_82127_("trecruit").executes(c->{Bridge b=Bridge.INSTANCE;if(b!=null&&b.recruitment.menu!=null&&c.getSource().m_81373_() instanceof ServerPlayer p){var bp=org.bukkit.Bukkit.getPlayer(p.m_20148_());if(bp!=null)b.recruitment.menu.open(bp);}return 1;}));
        }
    }
    private static boolean nativeForge() {
        try { return TownyAccess.forgeProvider() != null; }
        catch (ReflectiveOperationException invalid) { throw new IllegalStateException("Native Towny API is incompatible", invalid); }
    }

    private static int openForgeHireMenu(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Bridge bridge = Bridge.INSTANCE;
        if (bridge == null || !bridge.ready) { say(source, "Towny ще не готовий."); return 0; }
        var player = source.m_81375_();
        player.m_5893_(new SimpleMenuProvider((id, inventory, ignored) -> new ForgeHireMenu(id, inventory, player, bridge.recruitment),
                Component.m_237113_("Найм війська")));
        return 1;
    }

    private static int dismiss(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Bridge bridge = Bridge.INSTANCE;
        if (bridge == null || !bridge.ready) { say(source, "Towny ще не готовий."); return 0; }
        var player = source.m_81375_();
        try {
            bridge.recruitment.dismiss(player, UUID.fromString(id));
            return 1;
        } catch (Exception refused) {
            source.m_81352_(Component.m_237113_(Bridge.errorText(refused)));
            return 0;
        }
    }

    private static final class ForgeHireMenu extends ChestMenu {
        private final ServerPlayer owner;
        private final Recruitment recruitment;

        ForgeHireMenu(int id, Inventory inventory, ServerPlayer owner, Recruitment recruitment) {
            super(MenuType.f_39959_, id, inventory, new SimpleContainer(27), 3);
            this.owner = owner;
            this.recruitment = recruitment;
            render();
        }

        private void render() {
            var bridge = recruitment.bridge;
            String population = "Людський ресурс недоступний";
            try {
                UUID town = bridge.towny == null ? null : bridge.towny.city(owner.m_20148_());
                if (town != null) population = "Людський ресурс: " + recruitment.state.people(town);
            } catch (ReflectiveOperationException ignored) {
                // A stale menu can still close while the Towny bridge reconnects.
            }
            m_39261_().m_6836_(4, icon(Items.f_42516_, population));
            button(11, Items.f_42383_, "Рекрут", "recruit");
            button(13, Items.f_42740_, "Щитоносець", "shieldman");
            button(15, Items.f_42407_, "Ополчення · 120 хв", "militia");
        }

        private void button(int slot, Item item, String label, String type) {
            m_39261_().m_6836_(slot, icon(item, label + " · " + recruitment.config.gold(type) + " золота"));
        }

        private static ItemStack icon(Item item, String text) {
            return new ItemStack(item).m_41714_(Component.m_237113_(text));
        }

        @Override
        public void m_150399_(int slot, int button, ClickType action, net.minecraft.world.entity.player.Player player) {
            if (player != owner) return;
            if (action == ClickType.PICKUP && button == 0) {
                String type = switch (slot) {
                    case 11 -> "recruit";
                    case 13 -> "shieldman";
                    case 15 -> "militia";
                    default -> null;
                };
                if (type != null && recruitment.bridge.ready) {
                    recruitment.purchase(owner, type);
                    render();
                }
            }
            m_150429_();
        }

        @Override
        public ItemStack m_7648_(net.minecraft.world.entity.player.Player player, int index) {
            return ItemStack.f_41583_;
        }
    }
    private static int military(CommandSourceStack source, String name, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Bridge bridge = Bridge.INSTANCE;
        if (bridge == null || !bridge.ready) { say(source, "Towny ще не готовий."); return 0; }
        bridge.military.command(source.m_81375_(), name.equals("army"), arguments(text).toArray(String[]::new));
        return 1;
    }
    static boolean admin(CommandSourceStack source){
        if(!(source.m_81373_() instanceof ServerPlayer player))return true;
        if(nativeForge())return source.m_6761_(2);
        var p=org.bukkit.Bukkit.getPlayer(player.m_20148_());return p!=null&&(p.hasPermission("trbridge.admin")||p.hasPermission("townyrecruits.admin")||p.hasPermission("tarmyadmin.admin"));
    }
    static void say(CommandSourceStack source,String text){source.m_288197_(()->Component.m_237113_(text),false);}
    static List<String> arguments(String text){return CommandWords.parse(text);}
    static Policy.Nation nation(Bridge bridge,String text){
        try{
            UUID id=TownyNames.resolve(bridge.towny.names("nation"),text);
            var nation=bridge.snapshot.nations().get(id);if(nation==null)throw new IllegalArgumentException("Нація ще не синхронізована. Повторіть команду.");
            return nation;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Не вдалося прочитати назви Towny.",ex);}
    }
    static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> choices(com.mojang.brigadier.suggestion.SuggestionsBuilder builder,Collection<String> choices){
        String remaining=builder.getRemaining();if(remaining.startsWith("\""))remaining=remaining.substring(1);
        String prefix=TownyNames.fold(remaining.replace("\\\"","\"").replace("\\\\","\\"));
        choices.stream().distinct().sorted().filter(value->TownyNames.fold(value).startsWith(prefix)).limit(128)
            .forEach(value->builder.suggest(StringArgumentType.escapeIfRequired(value)));
        return builder.buildFuture();
    }
    static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> nationSuggestions(CommandSourceStack source,com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        Bridge bridge=Bridge.INSTANCE;
        if(bridge==null||!bridge.ready)return builder.buildFuture();
        try{return choices(builder,bridge.towny.names("nation").stream().flatMap(e->java.util.stream.Stream.concat(java.util.stream.Stream.of(e.name()),e.aliases().stream())).toList());}
        catch(Exception unavailable){return builder.buildFuture();}
    }
    static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> complete(CommandSourceStack source,String root,com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        Bridge bridge=Bridge.INSTANCE;if(bridge==null||!bridge.ready)return builder.buildFuture();
        try{
            String input=builder.getRemaining();boolean quoted=false,escaped=false;int start=0;
            for(int i=0;i<input.length();i++){char ch=input.charAt(i);if(escaped){escaped=false;continue;}if(ch=='\\'&&quoted){escaped=true;continue;}if(ch=='"')quoted=!quoted;else if(ch==' '&&!quoted)start=i+1;}
            var words=arguments(input.substring(0,start).strip());var out=builder.createOffset(builder.getStart()+start);
            if(root.equals("trbridge")){
                if(words.isEmpty())return choices(out,List.of("status","limit"));
                if(words.equals(List.of("limit"))){var all=new ArrayList<String>();all.add("list");bridge.towny.names("nation").forEach(e->{all.add(e.name());all.addAll(e.aliases());});return choices(out,all);}
            }else{
                if(words.isEmpty())return choices(out,List.of("village","villages","unlock","lock","pending","recover"));
                if(words.equals(List.of("dev")))return choices(out,List.of("add","remove","set","info"));
                if(words.size()==2&&words.get(0).equals("dev"))return choices(out,bridge.towny.names("town").stream().flatMap(e->java.util.stream.Stream.concat(java.util.stream.Stream.of(e.name()),e.aliases().stream())).toList());
                if(words.size()==1&&Set.of("unlock","lock").contains(words.get(0)))return choices(out,List.of("shieldman"));
                if(words.size()==2&&Set.of("unlock","lock").contains(words.get(0))&&words.get(1).equals("shieldman"))return nationSuggestions(source,out);
            }
            return builder.buildFuture();
        }catch(Exception unavailable){return builder.buildFuture();}
    }
    static int run(CommandSourceStack source,String root,String input){
        try{
            Bridge b=Bridge.INSTANCE;if(b==null||!b.ready)throw new IllegalStateException("Towny ще не готовий.");b.refreshNow();if(!b.ready)throw new IllegalStateException("Towny ще не готовий.");var r=b.recruitment;var args=arguments(input);
            if(root.equals("trbridge")){
                if(args.equals(List.of("status"))){status(source,b);return 1;}
                if(args.size()==2&&args.get(0).equals("limit")&&args.get(1).equals("list")){for(var n:b.snapshot.nations().values())show(source,b,n);return 1;}
                if((args.size()==2||args.size()==4)&&args.get(0).equals("limit")){var n=nation(b,args.get(1));if(args.size()==4){int total=Integer.parseInt(args.get(2));r.limits(n.id(),total,Integer.parseInt(args.get(3)),total==0);}show(source,b,n);return 1;}
                say(source,"/trbridge status | limit list | limit <нація> [загальний] [на_гравця]");return 1;
            }
            if(args.size()>=1&&args.get(0).equals("dev"))throw new IllegalArgumentException("Розвиток більше не використовується для найму. /tarmyadmin village <ID> <місто> <домів> [годин_відновлення]");
            if((args.size()==4||args.size()==5)&&args.get(0).equals("village")){
                Object town=b.towny.named("town",args.get(2));if(town==null)throw new IllegalArgumentException("Місто не знайдено.");
                r.state.approveVillage(args.get(1),b.towny.id(town),Integer.parseInt(args.get(3)),args.size()==5?Integer.parseInt(args.get(4)):6,System.currentTimeMillis(),new java.util.Random());
                var v=r.state.villages.get(args.get(1));say(source,"Село "+args.get(1)+": домів "+v.households.size()+", людський ресурс "+v.available+"/"+v.capacity()+"; +1 кожні "+(v.regenMillis/3600000)+" год.");return 1;
            }
            if(args.size()==1&&args.get(0).equals("villages")){for(var e:r.state.villages.entrySet())say(source,e.getKey()+": "+e.getValue().town+" · "+e.getValue().available+"/"+e.getValue().capacity());return 1;}
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
            say(source,"/tarmyadmin village <ID> <місто> <домів> [годин_відновлення]; villages; unlock|lock shieldman <нація>; pending; recover <UUID>");return 1;
        }catch(Exception ex){source.m_81352_(Component.m_237113_(Bridge.errorText(ex)));return 0;}
    }
    static void show(CommandSourceStack source,Bridge b,Policy.Nation n){var limits=b.recruitment.state.nations.get(n.id());say(source,n.name()+": військо "+b.count(n.id())+"/"+TownyRecruitMenu.cap(b.limit(n.id()))+", на гравця "+limits.perPlayer);}
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
